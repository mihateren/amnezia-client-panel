package org.example.amnezia.controlpanel.api.vpn;

import static org.example.amnezia.controlpanel.api.ssh.OpenSshExecutor.quote;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.persistence.repository.ManagedVpnServerRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.ssh.PasswordSshExecutor;
import org.example.amnezia.controlpanel.api.ssh.SshExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SshVpnGateway implements VpnGateway {
  private final SshExecutor ssh;
  private final AppProperties.Vpn config;
  private final MeterRegistry metrics;
  private final PasswordSshExecutor passwords;
  private final ManagedVpnServerRepository servers;

  public SshVpnGateway(SshExecutor ssh, AppProperties config, MeterRegistry metrics) {
    this(ssh, config.vpn(), metrics, null, null);
  }

  @Autowired
  public SshVpnGateway(
      SshExecutor ssh,
      AppProperties config,
      MeterRegistry metrics,
      PasswordSshExecutor passwords,
      ManagedVpnServerRepository servers) {
    this(ssh, config.vpn(), metrics, passwords, servers);
  }

  private SshVpnGateway(
      SshExecutor ssh,
      AppProperties.Vpn config,
      MeterRegistry metrics,
      PasswordSshExecutor passwords,
      ManagedVpnServerRepository servers) {
    this.ssh = ssh;
    this.config = config;
    this.metrics = metrics;
    this.passwords = passwords;
    this.servers = servers;
  }

  @Override
  public VpnGateway forServer(ManagedVpnServer server) {
    var bound =
        new AppProperties.Vpn(
            server.interfaceName(),
            "/opt/amnezia/awg/" + server.interfaceName() + ".conf",
            "/opt/amnezia/awg/wireguard_server_public_key.key",
            "/opt/amnezia/awg/wireguard_psk.key",
            server.subnet(),
            server.endpoint(),
            config.dns(),
            config.allowedIps(),
            config.clientSignature(),
            config.handshakeThreshold(),
            config.pollDelay());
    return new SshVpnGateway(
        script -> passwords.execute(server, script, true), bound, metrics, null, null);
  }

  @Override
  public ManagedVpnServer discover(ManagedVpnServer s) {
    var pinned =
        new ManagedVpnServer(
            s.id(),
            s.name(),
            s.ip(),
            s.sshPort(),
            s.username(),
            s.passwordEncrypted(),
            s.hostKey().isBlank() ? passwords.scan(s) : s.hostKey(),
            s.container(),
            s.interfaceName(),
            s.subnet(),
            s.vpnPort(),
            s.enabled(),
            s.createdAt());
    var containers =
        passwords
            .execute(pinned, "docker ps --format '{{.Names}}'\n", false)
            .lines()
            .filter(n -> n.matches("amnezia-awg[0-9]*"))
            .toList();
    if (containers.size() != 1)
      throw ApiException.conflict(
          "AWG_NOT_FOUND", "Нужен один запущенный контейнер AmneziaWG на сервере.");
    var container =
        new ManagedVpnServer(
            pinned.id(),
            pinned.name(),
            pinned.ip(),
            pinned.sshPort(),
            pinned.username(),
            pinned.passwordEncrypted(),
            pinned.hostKey(),
            containers.getFirst(),
            "awg0",
            "10.8.1.0/24",
            51820,
            pinned.enabled(),
            pinned.createdAt());
    String iface = passwords.execute(container, "awg show interfaces\n", true).strip();
    if (!iface.matches("[a-zA-Z0-9_-]{1,15}"))
      throw ApiException.conflict(
          "AWG_NOT_FOUND", "Не найден единственный активный интерфейс AmneziaWG.");
    var parsed =
        AwgConfig.parse(
            passwords.execute(
                container, "cat -- " + quote("/opt/amnezia/awg/" + iface + ".conf") + "\n", true));
    return new ManagedVpnServer(
        container.id(),
        container.name(),
        container.ip(),
        container.sshPort(),
        container.username(),
        container.passwordEncrypted(),
        container.hostKey(),
        container.container(),
        iface,
        parsed.subnet(),
        parsed.listenPort(),
        container.enabled(),
        container.createdAt());
  }

  private String read(String path) {
    return ssh.execute("set -eu\ncat -- " + quote(path) + "\n");
  }

  @Override
  public String readConfig() {
    return read(config.configPath());
  }

  @Override
  public String serverPublicKey() {
    return AwgConfig.key(read(config.publicKeyPath()).trim());
  }

  @Override
  public String presharedKey() {
    return AwgConfig.key(read(config.pskPath()).trim());
  }

  @Override
  public void replace(String expected, String replacement) {
    // Content is sent on SSH stdin, never command arguments or environment variables.
    String script =
        """
        set -eu
        umask 077
        config=%s
        iface=%s
        expected=%s
        actual=$(sha256sum "$config" | cut -d ' ' -f 1)
        test "$actual" = "$expected" || exit 40
        dir=$(dirname "$config")
        work=$(mktemp -d "$dir/.panel-XXXXXXXX")
        trap 'rm -rf "$work"' EXIT
        cp -p "$config" "$work/original.conf"
        printf '%%s' %s | base64 -d > "$work/next.conf"
        awg-quick strip "$work/next.conf" > "$work/next.strip"
        awg-quick strip "$work/original.conf" > "$work/original.strip"
        chmod 600 "$work/next.conf"
        mv "$work/next.conf" "$config"
        if ! awg syncconf "$iface" "$work/next.strip"; then
            cp -p "$work/original.conf" "$work/restore.conf"
            mv "$work/restore.conf" "$config"
            awg syncconf "$iface" "$work/original.strip" || exit 42
            exit 41
        fi
        """
            .formatted(
                quote(config.configPath()),
                quote(config.interfaceName()),
                quote(Secrets.hash(expected)),
                quote(
                    Base64.getEncoder()
                        .encodeToString(replacement.getBytes(StandardCharsets.UTF_8))));
    try {
      ssh.execute(script);
    } catch (RuntimeException e) {
      metrics.counter("vpn_apply_errors").increment();
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("VPN apply failed");
      throw e;
    }
  }

  @Override
  public Map<String, PeerRuntime> runtime() {
    if (servers != null) {
      var combined = new HashMap<String, PeerRuntime>();
      for (var server : servers.all()) combined.putAll(forServer(server).runtime());
      return Map.copyOf(combined);
    }
    // Avoid 'show dump': its interface row contains the server private key.
    var result = new HashMap<String, PeerRuntime>();
    var output =
        ssh.execute(
            "set -eu\nawg show "
                + quote(config.interfaceName())
                + " latest-handshakes\n"
                + "printf 'TRANSFER\\n'\nawg show "
                + quote(config.interfaceName())
                + " transfer\n");
    boolean transfer = false;
    try {
      for (String line : output.lines().toList()) {
        if (line.equals("TRANSFER")) {
          transfer = true;
          continue;
        }
        if (line.isBlank()) continue;
        var fields = line.trim().split("\\s+");
        var key = AwgConfig.key(fields[0]);
        if (!transfer) {
          long timestamp = Long.parseLong(fields[1]);
          result.put(
              key, new PeerRuntime(timestamp == 0 ? null : Instant.ofEpochSecond(timestamp), 0, 0));
        } else {
          var old = result.getOrDefault(key, new PeerRuntime(null, 0, 0));
          long rx = Long.parseLong(fields[1]), tx = Long.parseLong(fields[2]);
          if (rx < 0 || tx < 0) throw ApiException.vpn();
          result.put(key, new PeerRuntime(old.lastHandshake(), rx, tx));
        }
      }
      return Map.copyOf(result);
    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
      throw ApiException.vpn();
    }
  }
}
