package org.example.amnezia.controlpanel.api.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SqlQueryMetricsTest {
  @TempDir Path directory;

  @Test
  void measuresRealSqliteOperationsOnceAndPreservesFailuresAndRollback() throws Exception {
    var ds =
        new DriverManagerDataSource(
            "jdbc:sqlite:" + directory.resolve("metrics.db") + "?busy_timeout=0");
    var raw = new JdbcTemplate(ds);
    raw.execute(sql("create"));
    var registry = new SimpleMeterRegistry();
    try {
      var jdbc = new NamedParameterJdbcTemplate(SqlQueryMetrics.instrument(raw, registry));
      var parameters = Map.of("id", 1, "value", "sensitive-value");
      assertThat(jdbc.update(sql("insert"), parameters)).isEqualTo(1);
      assertThat(jdbc.queryForObject(sql("select"), parameters, String.class))
          .isEqualTo("sensitive-value");
      assertThat(jdbc.query(sql("select"), parameters, (rs, row) -> rs.getString(1))).hasSize(1);
      assertThatThrownBy(() -> jdbc.update(sql("insert"), parameters))
          .isInstanceOf(DataAccessException.class);
      assertThat(
              registry
                  .get("sqlite.query")
                  .tags("operation", "SELECT", "error", "none")
                  .timer()
                  .count())
          .isEqualTo(2);
      assertThat(
              registry
                  .get("sqlite.query")
                  .tags("operation", "INSERT", "error", "none")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(registry.get("sqlite.query").tags("error", "constraint").timer().count())
          .isEqualTo(1);
      try (var connection = ds.getConnection()) {
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
          statement.executeUpdate(sql("lock"));
          assertThatThrownBy(() -> jdbc.update(sql("update"), parameters))
              .isInstanceOf(DataAccessException.class);
          assertThat(registry.get("sqlite.query").tags("error", "busy").timer().count())
              .isEqualTo(1);
        } finally {
          connection.rollback();
        }
      }
      assertThat(jdbc.update(sql("update"), parameters)).isEqualTo(1);
      assertThat(jdbc.update(sql("delete"), parameters)).isEqualTo(1);
      assertThat(registry.getMeters())
          .allSatisfy(
              meter -> {
                assertThat(meter.getId().getTags()).hasSize(2);
                assertThat(meter.getId().toString())
                    .doesNotContain("sensitive-value", "metric_query_sample");
              });
    } finally {
      registry.close();
    }
  }

  private static String sql(String name) throws Exception {
    return new ClassPathResource("sql/metrics/query-" + name + ".sql")
        .getContentAsString(StandardCharsets.UTF_8);
  }
}
