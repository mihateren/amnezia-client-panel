package org.example.amnezia.controlpanel.api.config;

import io.github.mweirauch.micrometer.jvm.extras.ProcessMemoryMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusNamingConvention;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Preserve the required gauge name; Prometheus client 1.8 permits _total on gauges. */
@Configuration
public class MetricsConfig {
  @Bean
  MeterBinder processMemory() {
    return registry -> {
      if (Files.isReadable(Path.of("/proc/self/status"))) {
        new ProcessMemoryMetrics().bindTo(registry);
      }
    };
  }

  @Bean
  MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusNames() {
    return registry ->
        registry
            .config()
            .namingConvention(
                new PrometheusNamingConvention() {
                  @Override
                  public String name(String name, Meter.Type type, String unit) {
                    return name.equals("vpn_peers_total") && type == Meter.Type.GAUGE
                        ? name
                        : super.name(name, type, unit);
                  }
                });
  }
}
