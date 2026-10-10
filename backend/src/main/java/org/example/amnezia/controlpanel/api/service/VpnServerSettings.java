package org.example.amnezia.controlpanel.api.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.VpnServerDtos;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnMigrationRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnServerRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VpnServerSettings {
  private static final UUID KEY_CONTEXT = UUID.fromString("273967cf-3cc6-420b-849b-1146d382399d");
  private final VpnServerRepository repository;
  private final VpnMigrationRepository migration;
  private final AppProperties config;
  private final Secrets secrets;
  private final AuthService auth;
  private final AuditService audit;
  private final ReentrantLock lock;
  private final TransactionTemplate tx;

  public VpnServerSettings(
      VpnServerRepository repository,
      VpnMigrationRepository migration,
      AppProperties config,
      Secrets secrets,
      AuthService auth,
      AuditService audit,
      ReentrantLock lock,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx) {
    this.repository = repository;
    this.migration = migration;
    this.config = config;
    this.secrets = secrets;
    this.auth = auth;
    this.audit = audit;
    this.lock = lock;
    this.tx = tx;
  }

  public VpnServer current() {
    return repository
        .find()
        .orElseGet(
            () ->
                new VpnServer(
                    config.ssh().host(),
                    config.ssh().port(),
                    config.ssh().username(),
                    null,
                    null,
                    Integer.parseInt(
                        config
                            .vpn()
                            .endpoint()
                            .substring(config.vpn().endpoint().lastIndexOf(':') + 1))));
  }

  public String endpoint() {
    return repository
        .find()
        .map(s -> (s.ip().contains(":") ? "[" + s.ip() + "]" : s.ip()) + ":" + s.vpnPort())
        .orElse(config.vpn().endpoint());
  }

  public String privateKey(VpnServer server) {
    return server.privateKeyEncrypted() == null
        ? null
        : secrets.decrypt(server.privateKeyEncrypted(), KEY_CONTEXT);
  }

  private void admin(UUID actor) {
    if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
  }

  public VpnServerDtos.Settings get(UUID actor) {
    admin(actor);
    var s = current();
    return new VpnServerDtos.Settings(s.ip(), s.sshPort(), s.username(), s.hostKey(), s.vpnPort());
  }

  public VpnServerDtos.Settings save(UUID actor, VpnServerDtos.Update command) {
    lock.lock();
    try {
      admin(actor);
      var work = migration.find();
      if (!work.maintenance())
        throw ApiException.conflict("MAINTENANCE_REQUIRED", "Сначала начните работы.");
      String ip = canonicalIp(command.ip());
      var old = current();
      boolean changed = !sameIp(old.ip(), ip);
      if (command.sshPort() < 1
          || command.sshPort() > 65535
          || command.vpnPort() < 1
          || command.vpnPort() > 65535
          || command.username() == null
          || !command.username().matches("[a-zA-Z_][a-zA-Z0-9_-]*")) throw ApiException.invalid();
      String encrypted = old.privateKeyEncrypted();
      if (command.privateKey() != null && !command.privateKey().isBlank()) {
        var key = command.privateKey().replace("\r\n", "\n").strip() + "\n";
        if (key.length() > 32768
            || !key.startsWith("-----BEGIN ")
            || !key.contains("PRIVATE KEY-----")
            || !key.contains("-----END ")) throw ApiException.invalid();
        encrypted = secrets.encrypt(key, KEY_CONTEXT);
      } else if (changed)
        throw ApiException.conflict("SSH_KEY_REQUIRED", "Укажите SSH-ключ для нового IP.");
      String hostKey =
          command.hostKey() == null || command.hostKey().isBlank()
              ? old.hostKey()
              : command.hostKey().strip();
      if (changed && (command.hostKey() == null || command.hostKey().isBlank()))
        throw ApiException.conflict(
            "SSH_HOST_KEY_REQUIRED", "Укажите публичный ключ нового SSH-сервера.");
      if (hostKey != null) {
        var parts = hostKey.split("\\s+");
        if (parts.length != 2
            || !parts[0].matches("ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)"))
          throw ApiException.invalid();
        try {
          if (Base64.getDecoder().decode(parts[1]).length < 32) throw ApiException.invalid();
        } catch (IllegalArgumentException e) {
          throw ApiException.invalid();
        }
      }
      var next =
          new VpnServer(
              ip, command.sshPort(), command.username(), encrypted, hostKey, command.vpnPort());
      tx.executeWithoutResult(
          s -> {
            if (work.originalIp() == null)
              migration.update(new VpnMigration(work.generation(), true, old.ip()));
            repository.save(next);
            audit.record(
                AuditEvent.EventType.VPN_SERVER_UPDATED,
                AuditEvent.Result.SUCCESS,
                actor,
                "VPN",
                null);
          });
      return get(actor);
    } finally {
      lock.unlock();
    }
  }

  public static boolean sameIp(String first, String second) {
    if (first == null || second == null) return false;
    if (first.equals(second)) return true;
    try {
      return canonicalIp(first).equals(canonicalIp(second));
    } catch (ApiException e) {
      return false;
    }
  }

  public static String canonicalIp(String value) {
    if (value == null) throw ApiException.invalid();
    value = value.strip();
    if (value.contains(":")) {
      if (!value.matches("[a-fA-F0-9:.]+")) throw ApiException.invalid();
      try {
        return InetAddress.getByName(value).getHostAddress();
      } catch (UnknownHostException e) {
        throw ApiException.invalid();
      }
    }
    var parts = value.split("\\.", -1);
    if (parts.length != 4) throw ApiException.invalid();
    for (int i = 0; i < 4; i++) {
      if (!parts[i].matches("[0-9]{1,3}") || Integer.parseInt(parts[i]) > 255)
        throw ApiException.invalid();
      parts[i] = Integer.toString(Integer.parseInt(parts[i]));
    }
    return String.join(".", parts);
  }
}
