package org.example.amnezia.controlpanel.api.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;
import org.junit.jupiter.api.Test;

class SshCredentialFilesTest {
  @Test
  void shouldWritePrivateCredentialsAndRemoveFilesAfterUse() throws Exception {
    var defaults =
        new AppProperties.Ssh(
            "127.0.0.1", 22, "root", "original-key", "original-hosts", Duration.ofSeconds(20), "");
    var server =
        new VpnServer("2001:db8::1", 2222, "root", "encrypted", "ssh-ed25519 fixture", 51820);
    Path key;
    Path hosts;
    try (var files = SshCredentialFiles.create(defaults, server, "test-only-private-key")) {
      key = Path.of(files.privateKeyPath());
      hosts = Path.of(files.knownHostsPath());
      assertThat(Files.readString(key).equals("test-only-private-key")).isTrue();
      assertThat(Files.readString(hosts)).isEqualTo("[2001:db8::1]:2222 ssh-ed25519 fixture\n");
      if (Files.getFileAttributeView(key, PosixFileAttributeView.class) != null) {
        assertThat(Files.getPosixFilePermissions(key))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
      }
    }
    assertThat(Files.exists(key)).isFalse();
    assertThat(Files.exists(hosts)).isFalse();
    assertThat(Files.exists(key.getParent())).isFalse();
  }

  @Test
  void shouldRetainMountedCredentialsWhenNoOverrideWasSaved() throws Exception {
    var defaults =
        new AppProperties.Ssh(
            "127.0.0.1", 22, "root", "original-key", "original-hosts", Duration.ofSeconds(20), "");
    try (var files =
        SshCredentialFiles.create(
            defaults, new VpnServer("127.0.0.1", 22, "root", null, null, 51820), null)) {
      assertThat(files.privateKeyPath()).isEqualTo("original-key");
      assertThat(files.knownHostsPath()).isEqualTo("original-hosts");
    }
  }
}
