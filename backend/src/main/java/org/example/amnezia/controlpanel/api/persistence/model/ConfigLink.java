package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record ConfigLink(
    UUID id,
    UUID deviceId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant usedAt) {
  @Override
  public String toString() {
    return "ConfigLink[id=" + id + "]";
  }
}
