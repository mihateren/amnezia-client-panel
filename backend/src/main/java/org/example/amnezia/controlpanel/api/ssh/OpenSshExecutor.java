package org.example.amnezia.controlpanel.api.ssh;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;
import org.example.amnezia.controlpanel.api.service.VpnServerSettings;
import org.springframework.stereotype.Component;

@Component
public class OpenSshExecutor implements SshExecutor {
  private final VpnServerSettings servers;
  private final AppProperties config;
  private final MeterRegistry metrics;

  public OpenSshExecutor(AppProperties config, MeterRegistry metrics, VpnServerSettings servers) {
    this.servers = servers;
    this.config = config;
    this.metrics = metrics;
  }

  @Override
  public String execute(String script) {
    var server = servers.current();
    try (var credentials =
        SshCredentialFiles.create(config.ssh(), server, servers.privateKey(server))) {
      return execute(script, server, credentials);
    } catch (IOException e) {
      metrics.counter("ssh_errors").increment();
      throw ApiException.ssh();
    }
  }

  private String execute(String script, VpnServer server, SshCredentialFiles credentials) {
    var ssh = config.ssh();
    var args =
        new ArrayList<>(
            List.of(
                "ssh",
                "-T",
                "-o",
                "BatchMode=yes",
                "-o",
                "IdentitiesOnly=yes",
                "-o",
                "StrictHostKeyChecking=yes",
                "-o",
                "PasswordAuthentication=no",
                "-o",
                "KbdInteractiveAuthentication=no",
                "-o",
                "ConnectTimeout=10",
                "-o",
                "ServerAliveInterval=5",
                "-o",
                "ServerAliveCountMax=2",
                "-o",
                "UserKnownHostsFile=" + credentials.knownHostsPath(),
                "-i",
                credentials.privateKeyPath(),
                "-p",
                Integer.toString(server.sshPort()),
                server.username() + "@" + server.ip()));
    args.add(
        ssh.container().isEmpty()
            ? "sh -s"
            : "docker exec -i " + quote(ssh.container()) + " sh -s");
    Process process = null;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      process = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      var running = process;
      var output =
          executor.submit(
              () -> {
                try (var in = running.getInputStream()) {
                  byte[] bytes = in.readNBytes(4 * 1024 * 1024 + 1);
                  if (bytes.length > 4 * 1024 * 1024) {
                    running.destroyForcibly();
                    throw new IOException("Output limit");
                  }
                  return new String(bytes, StandardCharsets.UTF_8);
                }
              });
      var input =
          executor.submit(
              () -> {
                try (var out = running.getOutputStream()) {
                  out.write(script.getBytes(StandardCharsets.UTF_8));
                }
                return null;
              });
      if (!process.waitFor(ssh.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        throw new IOException("SSH timeout");
      }
      input.get(1, TimeUnit.SECONDS);
      if (process.exitValue() != 0) throw new IOException("SSH operation failed");
      return output.get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (process != null) process.destroyForcibly();
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      metrics.counter("ssh_errors").increment();
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("SSH operation failed");
      throw ApiException.ssh();
    }
  }

  public static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}
