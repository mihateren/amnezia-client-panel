package org.example.amnezia.controlpanel.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
    UUID id,
    String name,
    String vpnIp,
    String publicKey,
    Status status,
    Instant lastHandshakeAt,
    long rxBytes,
    long txBytes,
    Instant createdAt) {
  public enum Status {
    ONLINE,
    OFFLINE,
    UNKNOWN
  }
}
