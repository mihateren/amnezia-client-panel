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
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.ssh.SshExecutor;
import org.springframework.stereotype.Component;

@Component
public class SshVpnGateway implements VpnGateway {
  private final SshExecutor ssh;
  private final AppProperties.Vpn config;
  private final MeterRegistry metrics;

  public SshVpnGateway(SshExecutor ssh, AppProperties config, MeterRegistry metrics) {
    this.ssh = ssh;
    this.config = config.vpn();
    this.metrics = metrics;
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
