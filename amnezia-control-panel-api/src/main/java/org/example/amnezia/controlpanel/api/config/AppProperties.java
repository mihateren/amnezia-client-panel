package org.example.amnezia.controlpanel.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
    @NotBlank String jwtSecret,
    @NotBlank String encryptionKey,
    @Pattern(regexp = "https?://[^\\s]+") String publicUrl,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl,
    @NotNull Duration linkTtl,
    @DefaultValue("") String bootstrapNickname,
    @DefaultValue("") String bootstrapPassword,
    @NotNull @Valid Ssh ssh,
    @NotNull @Valid Vpn vpn) {

  public AppProperties {
    positive(accessTtl);
    positive(refreshTtl);
    positive(linkTtl);
  }

  private static void positive(Duration duration) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException("Configured duration must be positive");
    }
  }

  // Configuration objects can appear in diagnostics; never render secret values.
  @Override
  public String toString() {
    return "AppProperties[redacted]";
  }

  public record Ssh(
      @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9.:-]*") String host,
      @Min(1) @Max(65535) int port,
      @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_-]*") String username,
      @NotBlank String privateKey,
      @NotBlank String knownHosts,
      @NotNull Duration timeout,
      @Pattern(regexp = "[a-zA-Z0-9_.-]*") String container) {
    public Ssh {
      positive(timeout);
    }

    @Override
    public String toString() {
      return "Ssh[redacted]";
    }
  }

  public record Vpn(
      @Pattern(regexp = "[a-zA-Z0-9_-]{1,15}") String interfaceName,
      @NotBlank String configPath,
      @NotBlank String publicKeyPath,
      @NotBlank String pskPath,
      @NotBlank String subnet,
      @Pattern(regexp = "[^\\r\\n]+") String endpoint,
      @Pattern(regexp = "[^\\r\\n]+") String dns,
      @Pattern(regexp = "[^\\r\\n]+") String allowedIps,
      @NotNull Duration handshakeThreshold,
      @Min(1000) long pollDelay) {
    public Vpn {
      positive(handshakeThreshold);
    }
  }
}
