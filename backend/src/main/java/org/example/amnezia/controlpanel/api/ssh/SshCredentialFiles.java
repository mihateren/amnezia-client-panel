package org.example.amnezia.controlpanel.api.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;

/** Short-lived credentials; never passed as command arguments or logged. */
final class SshCredentialFiles implements AutoCloseable {
  private Path directory;
  private Path key;
  private Path hosts;
  private String privateKeyPath;
  private String knownHostsPath;

  static SshCredentialFiles create(AppProperties.Ssh defaults, VpnServer server, String privateKey)
      throws IOException {
    var files = new SshCredentialFiles();
    files.privateKeyPath = defaults.privateKey();
    files.knownHostsPath = defaults.knownHosts();
    try {
      if (privateKey != null || server.hostKey() != null) {
        files.directory = Files.createTempDirectory("panel-ssh-");
        protect(files.directory, true);
      }
      if (privateKey != null) {
        files.key = Files.createFile(files.directory.resolve("key"));
        protect(files.key, false);
        Files.writeString(files.key, privateKey);
        files.privateKeyPath = files.key.toString();
      }
      if (server.hostKey() != null) {
        files.hosts = Files.createFile(files.directory.resolve("known_hosts"));
        protect(files.hosts, false);
        String host =
            server.sshPort() == 22 ? server.ip() : "[" + server.ip() + "]:" + server.sshPort();
        Files.writeString(files.hosts, host + " " + server.hostKey() + "\n");
        files.knownHostsPath = files.hosts.toString();
      }
      return files;
    } catch (IOException | RuntimeException e) {
      files.close();
      throw e;
    }
  }

  private static void protect(Path path, boolean directory) throws IOException {
    if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
      Files.setPosixFilePermissions(
          path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
    } else {
      var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (acl == null) throw new IOException("Secure file permissions unavailable");
      acl.setAcl(
          List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(Files.getOwner(path))
                  .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                  .build()));
    }
  }

  String privateKeyPath() {
    return privateKeyPath;
  }

  String knownHostsPath() {
    return knownHostsPath;
  }

  @Override
  public void close() throws IOException {
    try {
      if (key != null) Files.deleteIfExists(key);
    } finally {
      try {
        if (hosts != null) Files.deleteIfExists(hosts);
      } finally {
        if (directory != null) Files.deleteIfExists(directory);
      }
    }
  }
}
