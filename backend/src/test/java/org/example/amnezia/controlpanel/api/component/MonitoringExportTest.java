package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.example.amnezia.controlpanel.api.monitoring.DatabaseStorageMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

class MonitoringExportTest extends BaseComponentTest {
  private final PrometheusMeterRegistry metrics;
  private final DatabaseStorageMetrics storage;
  private final int port;

  @Autowired
  MonitoringExportTest(
      PrometheusMeterRegistry metrics,
      DatabaseStorageMetrics storage,
      @Value("${local.server.port}") int port) {
    this.metrics = metrics;
    this.storage = storage;
    this.port = port;
  }

  @Test
  void exportsActualHistogramBucketsAndDatabaseHealth() throws Exception {
    storage.refresh();
    try (var client = HttpClient.newHttpClient()) {
      client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/me"))
              .build(),
          HttpResponse.BodyHandlers.discarding());
    }
    assertThat(metrics.scrape())
        .contains(
            "sqlite_query_seconds_bucket{",
            "operation=\"SELECT\"",
            "http_server_requests_seconds_bucket{",
            "sqlite_database_up{",
            "sqlite_database_checked_timestamp_seconds{");
    assertThat(metrics.get("sqlite_database_up").gauge().value()).isEqualTo(1);
    assertThat(metrics.get("sqlite_database_checked_timestamp_seconds").gauge().value())
        .isPositive();
  }
}
