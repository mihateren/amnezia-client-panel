package org.example.amnezia.controlpanel.api.dto;

import java.time.Instant;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.Device;

public record DeviceResponse(
    UUID id,
    String name,
    Device.Icon icon,
    String vpnIp,
    String publicKey,
    Status status,
    Instant lastHandshakeAt,
    long rxBytes,
    long txBytes,
    Instant createdAt,
    long generation,
    UUID serverId,
    String serverName,
    Device.ClientType clientType) {
  public DeviceResponse(
      UUID id,
      String name,
      Device.Icon icon,
      String vpnIp,
      String publicKey,
      Status status,
      Instant lastHandshakeAt,
      long rxBytes,
      long txBytes,
      Instant createdAt,
      long generation) {
    this(
        id,
        name,
        icon,
        vpnIp,
        publicKey,
        status,
        lastHandshakeAt,
        rxBytes,
        txBytes,
        createdAt,
        generation,
        null,
        null,
        Device.ClientType.AMNEZIA_WG);
  }

  public DeviceResponse(
      UUID id,
      String name,
      Device.Icon icon,
      String vpnIp,
      String publicKey,
      Status status,
      Instant lastHandshakeAt,
      long rxBytes,
      long txBytes,
      Instant createdAt) {
    this(id, name, icon, vpnIp, publicKey, status, lastHandshakeAt, rxBytes, txBytes, createdAt, 1);
  }

  public DeviceResponse(
      UUID id,
      String name,
      String vpnIp,
      String publicKey,
      Status status,
      Instant lastHandshakeAt,
      long rxBytes,
      long txBytes,
      Instant createdAt) {
    this(
        id,
        name,
        Device.Icon.LAPTOP,
        vpnIp,
        publicKey,
        status,
        lastHandshakeAt,
        rxBytes,
        txBytes,
        createdAt,
        1);
  }

  public enum Status {
    ONLINE,
    OFFLINE,
    OUTDATED,
    UNKNOWN
  }
}
