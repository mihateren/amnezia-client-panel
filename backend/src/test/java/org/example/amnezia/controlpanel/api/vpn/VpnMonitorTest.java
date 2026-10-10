package org.example.amnezia.controlpanel.api.vpn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.junit.jupiter.api.Test;

class VpnMonitorTest {
  @Test
  void shouldWarnOnlyAfterSustainedFailuresAndClearOnRecovery() {
    var clock = mock(Clock.class);
    var gateway = mock(VpnGateway.class);
    var start = Instant.parse("2026-10-10T00:00:00Z");
    when(clock.instant()).thenReturn(start);
    var metrics = new SimpleMeterRegistry();
    try {
      var monitor = new VpnMonitor(gateway, mock(AppProperties.class), clock,
          mock(DeviceRepository.class), metrics);
      assertThat(monitor.unavailable()).isFalse();
      verifyNoInteractions(gateway);
      when(gateway.runtime()).thenThrow(new IllegalStateException("unreachable"));
      monitor.refresh();
      assertThat(monitor.unavailable()).isFalse();
      when(clock.instant()).thenReturn(start.plusSeconds(59));
      monitor.refresh();
      assertThat(monitor.unavailable()).isFalse();
      when(clock.instant()).thenReturn(start.plusSeconds(60));
      monitor.refresh();
      assertThat(monitor.unavailable()).isTrue();
      org.mockito.Mockito.doReturn(Map.of()).when(gateway).runtime();
      monitor.refresh();
      assertThat(monitor.unavailable()).isFalse();
      // No peers or active users is a healthy response, not evidence of an outage.
      monitor.refresh();
      assertThat(monitor.unavailable()).isFalse();
      when(gateway.runtime()).thenThrow(new IllegalStateException("unreachable"));
      when(clock.instant()).thenReturn(start.plusSeconds(120));
      monitor.refresh();
      assertThat(monitor.unavailable()).isFalse();
    } finally {
      metrics.close();
    }
  }
}
