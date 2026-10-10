package org.example.amnezia.controlpanel.api.dto;

public final class VpnServerDtos {
  private VpnServerDtos() {}

  public record Settings(String ip, int sshPort, String username, String hostKey, int vpnPort) {}

  public record Update(
      String ip, int sshPort, String username, String privateKey, String hostKey, int vpnPort) {
    @Override
    public String toString() {
      return "VpnServerUpdate[redacted]";
    }
  }
}
