package org.example.amnezia.controlpanel.api.vpn;

import java.util.Map;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;

public interface VpnGateway {
  default VpnGateway forServer(ManagedVpnServer server) {
    throw new UnsupportedOperationException("Server selection is not supported");
  }

  default ManagedVpnServer discover(ManagedVpnServer candidate) {
    throw new UnsupportedOperationException("Server discovery is not supported");
  }

  String readConfig();

  String serverPublicKey();

  String presharedKey();

  /** Compare the current file to expected, atomically replace, apply, and restore on failure. */
  void replace(String expected, String replacement);

  Map<String, PeerRuntime> runtime();
}
