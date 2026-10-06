package org.example.amnezia.controlpanel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.User;

public final class AuthDtos {
  private AuthDtos() {}

  public record Credentials(
      @NotBlank @Size(min = 3, max = 64) String nickname,
      @NotBlank @Size(min = 8, max = 256) String password) {
    @Override
    public String toString() {
      return "Credentials[redacted]";
    }
  }

  public record CurrentUser(UUID id, String nickname, User.Role role, User.Status status) {
    public static CurrentUser from(User user) {
      return new CurrentUser(user.id(), user.nickname(), user.role(), user.status());
    }
  }

  public record AuthResponse(String accessToken, CurrentUser user) {
    @Override
    public String toString() {
      return "AuthResponse[redacted]";
    }
  }

  public record TokenResponse(String accessToken) {
    @Override
    public String toString() {
      return "TokenResponse[redacted]";
    }
  }
}
