package org.example.amnezia.controlpanel.api.persistence.model;

public record VpnServer(
    String ip,
    int sshPort,
    String username,
    String privateKeyEncrypted,
    String hostKey,
    int vpnPort) {
  @Override
  public String toString() {
    return "VpnServer[redacted]";
  }
}
