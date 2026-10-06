package org.example.amnezia.controlpanel.api.vpn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Base64;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.ssh.SshExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SshVpnGatewayTest {
  private final SshExecutor ssh = mock(SshExecutor.class);

  private SshVpnGateway gateway() {
    var config = mock(AppProperties.class, RETURNS_DEEP_STUBS);
    when(config.vpn().configPath()).thenReturn("/opt/amnezia/awg/awg0.conf");
    when(config.vpn().interfaceName()).thenReturn("awg0");
    return new SshVpnGateway(ssh, config, new SimpleMeterRegistry());
  }

  @Test
  void shouldSendCompareApplyAndRollbackScriptThroughSshAbstraction() {
    var gateway = gateway();
    when(ssh.execute(anyString())).thenReturn("");
    gateway.replace("original\n", "replacement\n");
    var script = ArgumentCaptor.forClass(String.class);
    verify(ssh).execute(script.capture());
    assertThat(script.getValue())
        .contains(
            "sha256sum",
            Secrets.hash("original\n"),
            "awg-quick strip",
            "awg syncconf",
            "original.strip",
            "mv",
            "umask 077");
    assertThat(script.getValue())
        .contains(
            Base64.getEncoder()
                .encodeToString("replacement\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertThat(script.getValue()).doesNotContain("awg-quick down", "systemctl", "restart");
  }

  @Test
  void shouldReadRuntimeWithoutDumpingServerPrivateKey() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    when(ssh.execute(anyString())).thenReturn(key + "\t1000\nTRANSFER\n" + key + "\t123\t456\n");
    var result = gateway().runtime();
    assertThat(result.get(key)).isEqualTo(new PeerRuntime(Instant.ofEpochSecond(1000), 123, 456));
    var script = ArgumentCaptor.forClass(String.class);
    verify(ssh).execute(script.capture());
    assertThat(script.getValue())
        .contains("latest-handshakes", "transfer")
        .doesNotContain("dump", "private-key");
  }

  @Test
  void shouldRejectCorruptRuntimeOutput() {
    when(ssh.execute(anyString())).thenReturn("invalid-key\t1000\n");
    assertThatThrownBy(() -> gateway().runtime())
        .isInstanceOf(org.example.amnezia.controlpanel.api.exception.ApiException.class);
  }
}
