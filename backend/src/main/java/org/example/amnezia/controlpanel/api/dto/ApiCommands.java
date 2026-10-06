package org.example.amnezia.controlpanel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ApiCommands {
  private ApiCommands() {}

  public record CreateInvitation(@NotBlank @Size(max = 100) String internalName) {}

  public record CreateDevice(@NotBlank @Size(max = 100) String name) {}
}
