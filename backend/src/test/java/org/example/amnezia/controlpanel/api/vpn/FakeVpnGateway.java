package org.example.amnezia.controlpanel.api.vpn;

import java.util.Base64;
import java.util.Map;
import org.example.amnezia.controlpanel.api.exception.ApiException;

public class FakeVpnGateway implements VpnGateway {
  public enum Failure {
    NONE,
    SSH,
    READ,
    WRITE,
    APPLY,
    INVALID_CONFIG
  }

  public final java.util.concurrent.atomic.AtomicInteger runtimeCalls =
      new java.util.concurrent.atomic.AtomicInteger();
  public volatile Failure failure = Failure.NONE;
  public volatile String config = initial();
  public volatile Map<String, PeerRuntime> peers = Map.of();
  public static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  public static String initial() {
    return "[Interface]\n"
        + "Address = 10.8.1.1/24\n"
        + "PrivateKey = server-secret\n"
        + "Jc = 4\n"
        + "S1 = 12\n"
        + "S2 = 12\n"
        + "S3 = 12\n"
        + "S4 = 12\n"
        + "MaxHandshakeAttempts = 300-500\n"
        + "RandomTrailers = on\n"
        + "DisableCookies = on\n";
  }

  public void reset() {
    failure = Failure.NONE;
    config = initial();
    peers = Map.of();
  }

  @Override
  public String readConfig() {
    if (failure == Failure.SSH || failure == Failure.READ) throw ApiException.vpn();
    return failure == Failure.INVALID_CONFIG ? "bad-config" : config;
  }

  @Override
  public String serverPublicKey() {
    return KEY;
  }

  @Override
  public String presharedKey() {
    return KEY;
  }

  @Override
  public void replace(String expected, String replacement) {
    if (!config.equals(expected)) throw ApiException.conflict("CONFIG_CHANGED", "Config changed");
    if (failure == Failure.SSH || failure == Failure.WRITE || failure == Failure.APPLY)
      throw ApiException.vpn();
    config = replacement;
  }

  @Override
  public Map<String, PeerRuntime> runtime() {
    runtimeCalls.incrementAndGet();
    if (failure == Failure.SSH) throw ApiException.vpn();
    return peers;
  }
}
