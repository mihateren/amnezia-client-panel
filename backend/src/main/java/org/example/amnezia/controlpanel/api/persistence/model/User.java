package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String internalName,
    String nickname,
    String passwordHash,
    Role role,
    Status status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastActivityAt) {
  public enum Role {
    USER,
    ADMIN
  }

  public enum Status {
    ACTIVE,
    DISABLED
  }

  @Override
  public String toString() {
    return "User[id=" + id + "]";
  }
}
