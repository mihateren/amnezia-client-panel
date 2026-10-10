package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.monitoring.DatabaseStorageMetrics;
import org.example.amnezia.controlpanel.api.persistence.repository.DatabaseMetricRepository;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DatabaseStorageMetricsTest extends BaseComponentTest {
  private final NamedParameterJdbcTemplate jdbc;
  private final DatabaseMetricRepository repository;
  private final DatabaseStorageMetrics monitor;
  private final MeterRegistry metrics;

  @Autowired
  DatabaseStorageMetricsTest(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      DatabaseMetricRepository repository,
      DatabaseStorageMetrics monitor,
      MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.repository = repository;
    this.monitor = monitor;
    this.metrics = metrics;
  }

  @Test
  void shouldMeasureRealSqliteTableGrowthIncludeIndexesAndRemoveDroppedTables() {
    var parameters = new MapSqlParameterSource();
    jdbc.update(TestQuery.CREATE_METRIC_SAMPLE.query(), parameters);
    try {
      var empty = tableBytes();
      for (int i = 0; i < 20; i++) {
        jdbc.update(
            TestQuery.INSERT_METRIC_SAMPLE.query(),
            new MapSqlParameterSource("payload", new byte[4096]));
      }
      var populated = tableBytes();
      assertThat(populated).isGreaterThan(empty);
      jdbc.update(TestQuery.INDEX_METRIC_SAMPLE.query(), parameters);
      assertThat(tableBytes()).isGreaterThan(populated);
      monitor.refresh();
      assertThat(
              metrics
                  .get("sqlite_table_allocated_bytes")
                  .tag("table", "metric_size_sample")
                  .gauge()
                  .value())
          .isEqualTo(tableBytes());
      assertThat(
              metrics
                  .get("sqlite_table_payload_bytes")
                  .tag("table", "metric_size_sample")
                  .gauge()
                  .value())
          .isPositive()
          .isLessThan(tableBytes());
      assertThat(metrics.get("sqlite_database_allocated_bytes").gauge().value())
          .isGreaterThanOrEqualTo(tableBytes());
    } finally {
      jdbc.update(TestQuery.DROP_METRIC_SAMPLE.query(), parameters);
      monitor.refresh();
    }
    assertThat(
            metrics
                .find("sqlite_table_allocated_bytes")
                .tag("table", "metric_size_sample")
                .meters())
        .isEmpty();
  }

  private long tableBytes() {
    return repository.storage().tables().stream()
        .filter(table -> table.tableName().equals("metric_size_sample"))
        .findFirst()
        .orElseThrow()
        .allocatedBytes();
  }
}
