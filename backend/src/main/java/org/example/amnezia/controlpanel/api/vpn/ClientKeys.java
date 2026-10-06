package org.example.amnezia.controlpanel.api.vpn;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.Arrays;
import java.util.Base64;

public record ClientKeys(String privateKey, String publicKey) {
  public static ClientKeys generate() {
    try {
      var pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
      var scalar = ((XECPrivateKey) pair.getPrivate()).getScalar().orElseThrow();
      scalar[0] &= (byte) 248;
      scalar[31] &= (byte) 127;
      scalar[31] |= (byte) 64;
      var bigEndian = ((XECPublicKey) pair.getPublic()).getU().toByteArray();
      var littleEndian = new byte[32];
      for (int i = 0; i < Math.min(32, bigEndian.length); i++)
        littleEndian[i] = bigEndian[bigEndian.length - 1 - i];
      var result =
          new ClientKeys(
              Base64.getEncoder().encodeToString(scalar),
              Base64.getEncoder().encodeToString(littleEndian));
      Arrays.fill(scalar, (byte) 0);
      return result;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("X25519 unavailable");
    }
  }

  @Override
  public String toString() {
    return "ClientKeys[redacted]";
  }
}
