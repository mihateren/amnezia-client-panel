package org.example.amnezia.controlpanel.api.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.example.amnezia.controlpanel.api.persistence.model.TableStorage;
import org.example.amnezia.controlpanel.api.persistence.repository.DatabaseMetricRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStorageMetrics {
  private final DatabaseMetricRepository repository;
  private final MeterRegistry metrics;
  private final Map<String, List<Meter>> tableMeters = new HashMap<>();
  private volatile Map<String, TableStorage> sizes = Map.of();
  private volatile double total = Double.NaN;

  public DatabaseStorageMetrics(DatabaseMetricRepository repository, MeterRegistry metrics) {
    this.repository = repository;
    this.metrics = metrics;
    Gauge.builder("sqlite_database_allocated_bytes", this, monitor -> monitor.total)
        .register(metrics);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(
      fixedDelayString = "${monitoring.storage-poll-delay:30000}",
      initialDelayString = "${monitoring.storage-poll-delay:30000}")
  public synchronized void refresh() {
    try {
      var storage = repository.storage();
      var current = new HashMap<String, TableStorage>();
      for (var table : storage.tables()) current.put(table.tableName(), table);
      sizes = Map.copyOf(current);
      total = storage.allocatedBytes();
      for (var table : current.keySet()) {
        tableMeters.computeIfAbsent(
            table,
            name ->
                List.of(
                    gauge("sqlite_table_allocated_bytes", name, false),
                    gauge("sqlite_table_payload_bytes", name, true)));
      }
      var deleted = new HashSet<>(tableMeters.keySet());
      deleted.removeAll(current.keySet());
      for (var table : deleted) tableMeters.remove(table).forEach(metrics::remove);
    } catch (DataAccessException e) {
      total = Double.NaN;
      sizes = Map.of();
      metrics.counter("db_errors").increment();
    }
  }

  private Meter gauge(String name, String table, boolean payload) {
    return Gauge.builder(
            name,
            this,
            monitor -> {
              var size = monitor.sizes.get(table);
              if (size == null) return Double.NaN;
              return payload ? size.payloadBytes() : size.allocatedBytes();
            })
        .tag("table", table)
        .register(metrics);
  }
}
