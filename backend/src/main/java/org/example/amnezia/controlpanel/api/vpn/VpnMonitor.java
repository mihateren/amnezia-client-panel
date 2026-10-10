package org.example.amnezia.controlpanel.api.vpn;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.VpnState;
import org.example.amnezia.controlpanel.api.persistence.model.MetricPeer;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("vpn")
public class VpnMonitor implements HealthIndicator {
  private final VpnGateway vpn;
  private final AppProperties config;
  private final Clock clock;
  private final DeviceRepository devices;
  private final MeterRegistry metrics;
  private final AtomicLong up = new AtomicLong(),
      total = new AtomicLong(),
      active = new AtomicLong();

  private record PeerMeters(MetricPeer peer, List<Meter> meters) {}

  private final Map<UUID, PeerMeters> peerMeters = new HashMap<>();
  private volatile Map<String, PeerRuntime> runtime;
  private Instant firstFailure;
  private volatile boolean unavailable;
  private final java.util.concurrent.locks.ReentrantLock refreshLock =
      new java.util.concurrent.locks.ReentrantLock();

  public VpnMonitor(
      VpnGateway vpn,
      AppProperties config,
      Clock clock,
      DeviceRepository devices,
      MeterRegistry metrics) {
    this.vpn = vpn;
    this.config = config;
    this.clock = clock;
    this.devices = devices;
    this.metrics = metrics;
    metrics.gauge("vpn_server_up", up);
    metrics.gauge("vpn_peers_total", total);
    metrics.gauge("vpn_peers_active", active);
    metrics.counter("ssh_errors");
    metrics.counter("vpn_apply_errors");
    metrics.counter("db_errors");
  }

  @Scheduled(
      fixedDelayString = "${app.vpn.poll-delay}",
      initialDelayString = "${app.vpn.poll-delay}")
  public void poll() {
    refresh();
  }

  public VpnState refresh() {
    refreshLock.lock();
    try {
      return refreshLocked();
    } finally {
      refreshLock.unlock();
    }
  }

  private VpnState refreshLocked() {
    var now = clock.instant();
    try {
      runtime = vpn.runtime();
      firstFailure = null;
      unavailable = false;
      up.set(1);
      total.set(runtime.size());
      active.set(
          runtime.values().stream()
              .filter(p -> p.online(now, config.vpn().handshakeThreshold()))
              .count());
    } catch (RuntimeException e) {
      var failedAt = clock.instant();
      if (firstFailure == null) firstFailure = failedAt;
      else if (Duration.between(firstFailure, failedAt).compareTo(Duration.ofMinutes(1)) >= 0)
        unavailable = true;
      runtime = null;
      up.set(0);
      total.set(0);
      active.set(0);
    }
    try {
      reconcileMeters();
    } catch (RuntimeException e) {
      metrics.counter("db_errors").increment();
    }
    return new VpnState(up.get() == 1, (int) total.get(), (int) active.get(), now);
  }

  private void reconcileMeters() {
    var current = devices.metricPeers();
    var ids = new HashSet<UUID>();
    for (var device : current) {
      ids.add(device.id());
      var existing = peerMeters.get(device.id());
      if (existing != null && !existing.peer().equals(device)) {
        existing.meters().forEach(metrics::remove);
        peerMeters.remove(device.id());
      }
      peerMeters.computeIfAbsent(
          device.id(),
          id ->
              new PeerMeters(
                  device,
                  List.of(
                      gauge("vpn_peer_last_handshake_timestamp", device, 0),
                      gauge("vpn_peer_rx_bytes", device, 1),
                      gauge("vpn_peer_tx_bytes", device, 2))));
    }
    var iterator = peerMeters.entrySet().iterator();
    while (iterator.hasNext()) {
      var item = iterator.next();
      if (!ids.contains(item.getKey())) {
        item.getValue().meters().forEach(metrics::remove);
        iterator.remove();
      }
    }
  }

  private Meter gauge(String name, MetricPeer device, int field) {
    return Gauge.builder(
            name,
            this,
            monitor -> {
              var state = monitor.runtime;
              if (state == null) return Double.NaN;
              var peer = state.get(device.publicKey());
              if (peer == null) return 0;
              return switch (field) {
                case 0 -> peer.lastHandshake() == null ? 0 : peer.lastHandshake().getEpochSecond();
                case 1 -> peer.rxBytes();
                default -> peer.txBytes();
              };
            })
        .tag("peer_id", device.id().toString())
        .tag("peer_name", device.name())
        .tag("user_name", device.userName())
        .register(metrics);
  }

  /** Cached assessment only: user requests must never trigger an SSH probe. */
  public boolean unavailable() {
    return unavailable;
  }

  public Map<String, PeerRuntime> runtime() {
    return runtime;
  }

  @Override
  public Health health() {
    var state = refresh();
    return state.serverUp() ? Health.up().build() : Health.down().build();
  }
}
