package org.example.amnezia.controlpanel.api.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class Secrets {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final SecretKeySpec encryptionKey;

  public Secrets(AppProperties config) {
    encryptionKey = new SecretKeySpec(key(config.encryptionKey()), "AES");
  }

  public static byte[] key(String encoded) {
    try {
      var bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length != 32) throw new IllegalArgumentException();
      return bytes;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Secret must be a base64 encoded 32-byte key");
    }
  }

  public static String token() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public String encrypt(String plaintext, UUID deviceId) {
    try {
      var nonce = new byte[12];
      RANDOM.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(deviceId.toString().getBytes(StandardCharsets.UTF_8));
      return "v1:"
          + Base64.getEncoder().encodeToString(nonce)
          + ":"
          + Base64.getEncoder()
              .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Key encryption failed");
    }
  }

  public String decrypt(String encoded, UUID deviceId) {
    try {
      var fields = encoded.split(":");
      if (fields.length != 3 || !fields[0].equals("v1")) throw new IllegalArgumentException();
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          encryptionKey,
          new GCMParameterSpec(128, Base64.getDecoder().decode(fields[1])));
      cipher.updateAAD(deviceId.toString().getBytes(StandardCharsets.UTF_8));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(fields[2])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Key decryption failed");
    }
  }
}
