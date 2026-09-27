package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record Device(
    UUID id,
    UUID userId,
    String name,
    String vpnIp,
    String publicKey,
    String privateKeyEncrypted,
    Instant createdAt) {
  @Override
  public String toString() {
    return "Device[id=" + id + "]";
  }
}
