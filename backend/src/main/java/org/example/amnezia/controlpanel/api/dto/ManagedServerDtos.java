package org.example.amnezia.controlpanel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ManagedServerDtos {
  private ManagedServerDtos() {}

  public record Create(
      @NotBlank @Size(max = 100) String name,
      @NotBlank String ip,
      @Min(1) @Max(65535) int sshPort,
      @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_-]*") @NotBlank String username,
      @NotBlank @Size(max = 1024) String password) {
    @Override
    public String toString() {
      return "CreateServer[redacted]";
    }
  }

  public record Update(
      @Size(min = 1, max = 100) String name, Boolean enabled, @Size(max = 1024) String password) {
    @Override
    public String toString() {
      return "UpdateServer[redacted]";
    }
  }

  public record PublicServer(UUID id, String name, boolean enabled) {}

  public record AdminServer(
      UUID id,
      String name,
      boolean enabled,
      String ip,
      int sshPort,
      String username,
      String container,
      int vpnPort) {}
}
