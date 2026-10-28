package org.example.amnezia.controlpanel.api.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.ManagedServerDtos;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.ManagedVpnServerRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.example.amnezia.controlpanel.api.vpn.VpnGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServerCatalog {
  private final ManagedVpnServerRepository servers;
  private final DeviceRepository devices;
  private final AuthService auth;
  private final Secrets secrets;
  private final VpnGateway vpn;
  private final Clock clock;
  private final ReentrantLock lock;
  private final TransactionTemplate tx;
  private final AuditService audit;

  public ServerCatalog(
      ManagedVpnServerRepository servers,
      DeviceRepository devices,
      AuthService auth,
      Secrets secrets,
      VpnGateway vpn,
      Clock clock,
      ReentrantLock lock,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx,
      AuditService audit) {
    this.servers = servers;
    this.devices = devices;
    this.auth = auth;
    this.secrets = secrets;
    this.vpn = vpn;
    this.clock = clock;
    this.lock = lock;
    this.tx = tx;
    this.audit = audit;
  }

  private void admin(UUID actor) {
    if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
  }

  public ManagedVpnServer require(UUID id) {
    return servers.find(id).orElseThrow(ApiException::notFound);
  }

  public ManagedVpnServer forCreation(UUID id) {
    if (id == null) throw ApiException.conflict("SERVER_REQUIRED", "Выберите VPN-сервер.");
    var server = require(id);
    if (!server.enabled())
      throw ApiException.conflict(
          "VPN_SERVER_DISABLED",
          "Администратор отключил создание конфигураций на этом сервере. Выберите другой сервер.");
    return server;
  }

  public VpnGateway gateway(UUID id) {
    return id == null ? vpn : vpn.forServer(require(id));
  }

  public List<ManagedServerDtos.PublicServer> list(UUID actor) {
    auth.active(actor);
    return servers.all().stream()
        .map(s -> new ManagedServerDtos.PublicServer(s.id(), s.name(), s.enabled()))
        .toList();
  }

  public List<ManagedServerDtos.AdminServer> adminList(UUID actor) {
    admin(actor);
    return servers.all().stream().map(this::view).toList();
  }

  private ManagedServerDtos.AdminServer view(ManagedVpnServer s) {
    return new ManagedServerDtos.AdminServer(
        s.id(),
        s.name(),
        s.enabled(),
        s.ip(),
        s.sshPort(),
        s.username(),
        s.container(),
        s.vpnPort());
  }

  public ManagedServerDtos.AdminServer create(UUID actor, ManagedServerDtos.Create command) {
    admin(actor);
    String ip = VpnServerSettings.canonicalIp(command.ip());
    validatePassword(command.password());
    var id = UUID.randomUUID();
    var candidate =
        new ManagedVpnServer(
            id,
            command.name().strip(),
            ip,
            command.sshPort(),
            command.username(),
            secrets.encrypt(command.password(), id),
            "",
            "",
            "awg0",
            "10.8.1.0/24",
            51820,
            true,
            clock.instant());
    // Probe without holding the global mutation lock; no server data is changed.
    var server = vpn.discover(candidate);
    lock.lock();
    try {
      // The lock excludes concurrent device changes while existing peers are matched.
      var remote = AwgConfig.parse(vpn.forServer(server).readConfig());
      tx.executeWithoutResult(
          status -> {
            servers.insert(server);
            for (var device : devices.all()) {
              if (device.serverId() == null
                  && remote.hasPeer(device.publicKey())
                  && remote.peerHasIp(device.publicKey(), device.vpnIp()))
                servers.adoptLegacy(server.id(), device.id());
            }
            audit.record(
                AuditEvent.EventType.VPN_SERVER_UPDATED,
                AuditEvent.Result.SUCCESS,
                actor,
                "VPN_SERVER",
                id);
          });
      return view(server);
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("VPN_SERVER_EXISTS", "Этот VPN-сервер уже добавлен.");
    } finally {
      lock.unlock();
    }
  }

  public ManagedServerDtos.AdminServer update(
      UUID actor, UUID id, ManagedServerDtos.Update command) {
    admin(actor);
    lock.lock();
    try {
      var old = require(id);
      String password = old.passwordEncrypted();
      if (command.password() != null && !command.password().isEmpty()) {
        validatePassword(command.password());
        password = secrets.encrypt(command.password(), id);
      }
      String name = command.name() == null ? old.name() : command.name().strip();
      if (name.isEmpty()) throw ApiException.invalid();
      var next =
          new ManagedVpnServer(
              id,
              name,
              old.ip(),
              old.sshPort(),
              old.username(),
              password,
              old.hostKey(),
              old.container(),
              old.interfaceName(),
              old.subnet(),
              old.vpnPort(),
              command.enabled() == null ? old.enabled() : command.enabled(),
              old.createdAt());
      if (!password.equals(old.passwordEncrypted())) vpn.forServer(next).readConfig();
      tx.executeWithoutResult(
          s -> {
            servers.update(next);
            audit.record(
                AuditEvent.EventType.VPN_SERVER_UPDATED,
                AuditEvent.Result.SUCCESS,
                actor,
                "VPN_SERVER",
                id);
          });
      return view(next);
    } finally {
      lock.unlock();
    }
  }

  private void validatePassword(String value) {
    if (value == null
        || value.isEmpty()
        || value.length() > 1024
        || value.contains("\n")
        || value.contains("\r")
        || value.contains("\0")) throw ApiException.invalid();
  }
}
