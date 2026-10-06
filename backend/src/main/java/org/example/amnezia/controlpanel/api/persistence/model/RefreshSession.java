package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant revokedAt) {
  @Override
  public String toString() {
    return "RefreshSession[id=" + id + "]";
  }
}
