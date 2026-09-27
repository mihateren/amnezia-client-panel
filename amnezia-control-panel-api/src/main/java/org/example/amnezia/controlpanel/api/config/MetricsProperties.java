package org.example.amnezia.controlpanel.api.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("monitoring")
public record MetricsProperties(
    @DefaultValue("") @Pattern(regexp = "^$|[A-Za-z0-9_=-]{32,256}") String token) {
  @Override
  public String toString() {
    return "MetricsProperties[redacted]";
  }
}
