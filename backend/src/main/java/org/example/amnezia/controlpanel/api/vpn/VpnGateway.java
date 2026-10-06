package org.example.amnezia.controlpanel.api.vpn;

import java.util.Map;

public interface VpnGateway {
  String readConfig();

  String serverPublicKey();

  String presharedKey();

  /** Compare the current file to expected, atomically replace, apply, and restore on failure. */
  void replace(String expected, String replacement);

  Map<String, PeerRuntime> runtime();
}
