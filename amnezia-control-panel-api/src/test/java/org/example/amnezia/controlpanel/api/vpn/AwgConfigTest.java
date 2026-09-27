package org.example.amnezia.controlpanel.api.vpn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AwgConfigTest {
  private static final String KEY = java.util.Base64.getEncoder().encodeToString(new byte[32]);

  private static String config() {
    return "[Interface]\nAddress = 10.8.1.1/24\nPrivateKey = secret-server-key\nJc = 4\nS3 = 12\n";
  }

  @Test
  void shouldPreserveServerAndExternalPeersAndAllocateFromActualAllowedRanges() {
    var first = AwgConfig.parse(config());
    assertThat(first.allocate("10.8.1.0/24")).isEqualTo("10.8.1.2");
    var withPeer = first.add(KEY, KEY, "10.8.1.2");
    var parsed = AwgConfig.parse(withPeer);
    assertThat(parsed.allocate("10.8.1.0/24")).isEqualTo("10.8.1.3");
    assertThat(parsed.remove(Set.of(KEY))).isEqualTo(config() + "\n");
    assertThat(parsed.clientParameters()).containsEntry("S3", "12").doesNotContainKey("PrivateKey");
    var range = withPeer.replace("10.8.1.2/32", "10.8.1.0/28, fd00::2/128");
    assertThat(AwgConfig.parse(range).allocate("10.8.1.0/24")).isEqualTo("10.8.1.16");
  }

  @Test
  void shouldFailClosedForMalformedConfigAndExhaustedSubnet() {
    assertThatThrownBy(() -> AwgConfig.parse("[Peer]\n")).isInstanceOf(RuntimeException.class);
    var full =
        AwgConfig.parse(config()).add(KEY, KEY, "10.8.1.2").replace("10.8.1.2/32", "0.0.0.0/0");
    assertThatThrownBy(() -> AwgConfig.parse(full).allocate("10.8.1.0/24"))
        .hasMessage("No free VPN address");
  }

  @Test
  void shouldCalculateOnlineBoundaryAndNeverTreatMissingHandshakeAsOnline() {
    var now = Instant.parse("2026-01-01T12:00:00Z");
    assertThat(new PeerRuntime(now.minusSeconds(179), 1, 2).online(now, Duration.ofMinutes(3)))
        .isTrue();
    assertThat(new PeerRuntime(now.minusSeconds(180), 1, 2).online(now, Duration.ofMinutes(3)))
        .isFalse();
    assertThat(new PeerRuntime(null, 1, 2).online(now, Duration.ofMinutes(3))).isFalse();
  }

  @Test
  void shouldGenerateDistinctWireguardKeys() throws Exception {
    var a = ClientKeys.generate();
    var b = ClientKeys.generate();
    assertThat(java.util.Base64.getDecoder().decode(a.privateKey())).hasSize(32);
    assertThat(java.util.Base64.getDecoder().decode(a.publicKey())).hasSize(32);
    assertThat(a.publicKey()).isNotEqualTo(b.publicKey());
    assertThat(a.toString()).doesNotContain(a.privateKey());
    var keyFactory = java.security.KeyFactory.getInstance("X25519");
    var privateKey =
        keyFactory.generatePrivate(
            new java.security.spec.XECPrivateKeySpec(
                java.security.spec.NamedParameterSpec.X25519,
                java.util.Base64.getDecoder().decode(a.privateKey())));
    var basePoint =
        keyFactory.generatePublic(
            new java.security.spec.XECPublicKeySpec(
                java.security.spec.NamedParameterSpec.X25519, java.math.BigInteger.valueOf(9)));
    var agreement = javax.crypto.KeyAgreement.getInstance("X25519");
    agreement.init(privateKey);
    agreement.doPhase(basePoint, true);
    assertThat(agreement.generateSecret())
        .isEqualTo(java.util.Base64.getDecoder().decode(a.publicKey()));
  }
}
