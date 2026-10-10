package org.example.amnezia.controlpanel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.amnezia.controlpanel.api.persistence.model.Device;

public final class ApiCommands {
  private ApiCommands() {}

  public record VpnMigration(long generation) {}

  public record CreateInvitation(@NotBlank @Size(max = 100) String internalName) {}

  public record CreateDevice(@NotBlank @Size(max = 100) String name, Device.Icon icon) {
    public CreateDevice {
      icon = icon == null ? Device.Icon.LAPTOP : icon;
    }
  }

  public record UpdateDevice(@NotBlank @Size(max = 100) String name, @NotNull Device.Icon icon) {}
}
