package org.example.amnezia.controlpanel.api.ssh;

import static org.example.amnezia.controlpanel.api.ssh.OpenSshExecutor.quote;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Passwords travel through a private temporary file, never arguments, environment or logs. */
@Component
public class PasswordSshExecutor {
  private final Secrets secrets;
  private final AppProperties config;
  private final MeterRegistry metrics;

  public PasswordSshExecutor(Secrets secrets, AppProperties config, MeterRegistry metrics) {
    this.secrets = secrets;
    this.config = config;
    this.metrics = metrics;
  }

  public String scan(ManagedVpnServer s) {
    String result =
        run(List.of("ssh-keyscan", "-T", "8", "-p", Integer.toString(s.sshPort()), s.ip()), "");
    var lines = result.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).toList();
    if (lines.isEmpty()) throw ApiException.ssh();
    return String.join("\n", lines) + "\n";
  }

  public String execute(ManagedVpnServer s, String script, boolean inContainer) {
    Path directory = null;
    try {
      directory =
          Files.createTempDirectory(
              "panel-password-",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      Path password = directory.resolve("password"), hosts = directory.resolve("known_hosts");
      Files.createFile(
          password,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.writeString(
          password, secrets.decrypt(s.passwordEncrypted(), s.id()), StandardCharsets.UTF_8);
      Files.writeString(hosts, s.hostKey(), StandardCharsets.UTF_8);
      var args =
          new ArrayList<>(
              List.of(
                  "sshpass",
                  "-f",
                  password.toString(),
                  "ssh",
                  "-T",
                  "-o",
                  "StrictHostKeyChecking=yes",
                  "-o",
                  "UserKnownHostsFile=" + hosts,
                  "-o",
                  "GlobalKnownHostsFile=/dev/null",
                  "-o",
                  "PreferredAuthentications=password,keyboard-interactive",
                  "-o",
                  "PubkeyAuthentication=no",
                  "-o",
                  "NumberOfPasswordPrompts=1",
                  "-o",
                  "ConnectTimeout=10",
                  "-o",
                  "ServerAliveInterval=5",
                  "-o",
                  "ServerAliveCountMax=2",
                  "-p",
                  Integer.toString(s.sshPort()),
                  s.username() + "@" + s.ip()));
      args.add(inContainer ? "docker exec -i " + quote(s.container()) + " sh -s" : "sh -s");
      return run(args, script);
    } catch (IOException e) {
      throw ApiException.ssh();
    } finally {
      if (directory != null) {
        try {
          Files.deleteIfExists(directory.resolve("password"));
          Files.deleteIfExists(directory.resolve("known_hosts"));
          Files.deleteIfExists(directory);
        } catch (IOException ignored) {
          /* Private ephemeral tmpfs; never log credential paths or contents. */
        }
      }
    }
  }

  private String run(List<String> command, String input) {
    Process process = null;
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      var running = process;
      var output =
          executor.submit(
              () -> {
                try (var stream = running.getInputStream()) {
                  byte[] bytes = stream.readNBytes(4 * 1024 * 1024 + 1);
                  if (bytes.length > 4 * 1024 * 1024) {
                    terminate(running);
                    throw new IOException("Output limit");
                  }
                  return new String(bytes, StandardCharsets.UTF_8);
                }
              });
      var writer =
          executor.submit(
              () -> {
                try (var stream = running.getOutputStream()) {
                  stream.write(input.getBytes(StandardCharsets.UTF_8));
                }
                return null;
              });
      if (!process.waitFor(config.ssh().timeout().toMillis(), TimeUnit.MILLISECONDS)) {
        terminate(process);
        throw new IOException("Timeout");
      }
      if (process.exitValue() == 5)
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "SSH_AUTH_FAILED",
            "Не удалось войти на VPN-сервер. Проверьте логин и пароль.");
      if (process.exitValue() == 6 || process.exitValue() == 7)
        throw new ApiException(
            HttpStatus.BAD_GATEWAY,
            "SSH_HOST_KEY_CHANGED",
            "Ключ VPN-сервера изменился. Проверьте сервер перед повторным подключением.");
      if (process.exitValue() != 0) throw new IOException("Remote operation failed");
      writer.get(1, TimeUnit.SECONDS);
      return output.get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (process != null) terminate(process);
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      metrics.counter("ssh_errors").increment();
      if (e instanceof ApiException api) throw api;
      throw ApiException.ssh();
    } finally {
      if (process != null) terminate(process);
      executor.shutdownNow();
    }
  }

  private static void terminate(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }
}
