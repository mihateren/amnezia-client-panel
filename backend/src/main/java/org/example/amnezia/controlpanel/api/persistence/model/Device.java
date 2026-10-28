package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record Device(
    UUID id,
    UUID userId,
    String name,
    Icon icon,
    String vpnIp,
    String publicKey,
    String privateKeyEncrypted,
    Instant createdAt,
    long generation,
    UUID serverId,
    ClientType clientType) {
  public enum ClientType {
    AMNEZIA_VPN,
    AMNEZIA_WG
  }

  public Device(
      UUID id,
      UUID userId,
      String name,
      Icon icon,
      String vpnIp,
      String publicKey,
      String privateKeyEncrypted,
      Instant createdAt,
      long generation) {
    this(
        id,
        userId,
        name,
        icon,
        vpnIp,
        publicKey,
        privateKeyEncrypted,
        createdAt,
        generation,
        null,
        ClientType.AMNEZIA_WG);
  }

  public Device(
      UUID id,
      UUID userId,
      String name,
      Icon icon,
      String vpnIp,
      String publicKey,
      String privateKeyEncrypted,
      Instant createdAt) {
    this(id, userId, name, icon, vpnIp, publicKey, privateKeyEncrypted, createdAt, 1);
  }

  public enum Icon {
    DESKTOP,
    LAPTOP,
    PHONE,
    TABLET,
    TV,
    ROUTER
  }

  public Device(
      UUID id,
      UUID userId,
      String name,
      String vpnIp,
      String publicKey,
      String privateKeyEncrypted,
      Instant createdAt) {
    this(id, userId, name, Icon.LAPTOP, vpnIp, publicKey, privateKeyEncrypted, createdAt);
  }

  @Override
  public String toString() {
    return "Device[id=" + id + "]";
  }
}
