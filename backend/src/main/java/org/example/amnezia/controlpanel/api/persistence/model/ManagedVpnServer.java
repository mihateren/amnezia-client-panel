package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record ManagedVpnServer(
    UUID id,
    String name,
    String ip,
    int sshPort,
    String username,
    String passwordEncrypted,
    String hostKey,
    String container,
    String interfaceName,
    String subnet,
    int vpnPort,
    boolean enabled,
    Instant createdAt) {
  public String endpoint() {
    return (ip.contains(":") ? "[" + ip + "]" : ip) + ":" + vpnPort;
  }

  @Override
  public String toString() {
    return "ManagedVpnServer[id=" + id + "]";
  }
}
