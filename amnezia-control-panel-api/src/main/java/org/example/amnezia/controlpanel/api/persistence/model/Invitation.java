package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record Invitation(
    UUID id,
    String tokenHash,
    String internalName,
    UUID createdBy,
    Instant createdAt,
    Instant usedAt,
    UUID usedBy) {
  @Override
  public String toString() {
    return "Invitation[id=" + id + "]";
  }
}
