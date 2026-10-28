package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_CREATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_DELETED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_UPDATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.FAILURE;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.ApiCommands;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.example.amnezia.controlpanel.api.vpn.ClientKeys;
import org.example.amnezia.controlpanel.api.vpn.PeerRuntime;
import org.example.amnezia.controlpanel.api.vpn.VpnGateway;
import org.example.amnezia.controlpanel.api.vpn.VpnMutation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeviceService {
  private final ServerCatalog catalog;
  private final VpnMigrationService migration;
  private final DeviceRepository devices;
  private final AuthService auth;
  private final VpnGateway vpn;
  private final VpnMutation mutation;
  private final Secrets secrets;
  private final AppProperties config;
  private final Clock clock;
  private final ReentrantLock lock;
  private final AuditService audit;
  private final TransactionTemplate tx;

  public DeviceService(
      ServerCatalog catalog,
      VpnMigrationService migration,
      DeviceRepository devices,
      AuthService auth,
      VpnGateway vpn,
      VpnMutation mutation,
      Secrets secrets,
      AppProperties config,
      Clock clock,
      ReentrantLock lock,
      AuditService audit,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx) {
    this.catalog = catalog;
    this.migration = migration;
    this.devices = devices;
    this.auth = auth;
    this.vpn = vpn;
    this.mutation = mutation;
    this.secrets = secrets;
    this.config = config;
    this.clock = clock;
    this.lock = lock;
    this.audit = audit;
    this.tx = tx;
  }

  public Device owned(UUID actor, UUID deviceId) {
    var user = auth.active(actor);
    var device = devices.find(deviceId).orElseThrow(ApiException::notFound);
    if (!device.userId().equals(actor) && user.role() != User.Role.ADMIN)
      throw ApiException.notFound();
    return device;
  }

  public DeviceResponse create(UUID actor, String name) {
    return create(actor, name, Device.Icon.LAPTOP);
  }

  public DeviceResponse create(UUID actor, String name, Device.Icon icon) {
    return create(actor, name, icon, null, Device.ClientType.AMNEZIA_WG);
  }

  public DeviceResponse create(UUID actor, ApiCommands.CreateDevice command) {
    return create(actor, command.name(), command.icon(), command.serverId(), command.clientType());
  }

  private DeviceResponse create(
      UUID actor, String name, Device.Icon icon, UUID serverId, Device.ClientType clientType) {
    lock.lock();
    try {
      auth.active(actor);
      var server = serverId == null ? null : catalog.forCreation(serverId);
      var gateway = catalog.gateway(serverId);
      long generation = server == null ? migration.requireAvailable() : 1;
      if (devices.byUser(actor).stream().filter(d -> !migration.outdated(d)).count() >= 5)
        throw ApiException.conflict("DEVICE_LIMIT", "Device limit reached");
      var original = gateway.readConfig();
      var parsed = AwgConfig.parse(original);
      var ip = parsed.allocate(server == null ? config.vpn().subnet() : server.subnet());
      var keys = ClientKeys.generate();
      gateway.serverPublicKey();
      var replacement = parsed.add(keys.publicKey(), gateway.presharedKey(), ip);
      var id = UUID.randomUUID();
      var device =
          new Device(
              id,
              actor,
              name,
              icon,
              ip,
              keys.publicKey(),
              secrets.encrypt(keys.privateKey(), id),
              clock.instant(),
              generation,
              serverId,
              clientType);
      mutation.apply(
          List.of(new VpnMutation.Change(gateway, original, replacement)),
          () -> {
            devices.insert(device);
            audit.record(DEVICE_CREATED, SUCCESS, actor, "DEVICE", id);
          });
      return response(device, Map.of());
    } catch (RuntimeException e) {
      audit.record(DEVICE_CREATED, FAILURE, actor, "USER", actor);
      throw e;
    } finally {
      lock.unlock();
    }
  }

  public void delete(UUID actor, UUID deviceId) {
    lock.lock();
    try {
      var device = owned(actor, deviceId);
      if (device.serverId() == null) migration.requireAvailable();
      if (migration.outdated(device)) {
        tx.executeWithoutResult(
            s -> {
              devices.delete(deviceId);
              audit.record(DEVICE_DELETED, SUCCESS, actor, "DEVICE", deviceId);
            });
        return;
      }
      var gateway = catalog.gateway(device.serverId());
      var original = gateway.readConfig();
      var replacement = AwgConfig.parse(original).remove(Set.of(device.publicKey()));
      mutation.apply(
          List.of(new VpnMutation.Change(gateway, original, replacement)),
          () -> {
            devices.delete(deviceId);
            audit.record(DEVICE_DELETED, SUCCESS, actor, "DEVICE", deviceId);
          });
    } catch (RuntimeException e) {
      // Do not put a foreign requested device ID into an ownership-denial audit record.
      audit.record(DEVICE_DELETED, FAILURE, actor, "USER", actor);
      throw e;
    } finally {
      lock.unlock();
    }
  }

  public DeviceResponse update(UUID actor, UUID deviceId, ApiCommands.UpdateDevice command) {
    lock.lock();
    try {
      var device = owned(actor, deviceId);
      var updated =
          tx.execute(
              status -> {
                if (!devices.updateMetadata(deviceId, command.name(), command.icon()))
                  throw ApiException.notFound();
                audit.record(DEVICE_UPDATED, SUCCESS, actor, "DEVICE", deviceId);
                return new Device(
                    device.id(),
                    device.userId(),
                    command.name(),
                    command.icon(),
                    device.vpnIp(),
                    device.publicKey(),
                    device.privateKeyEncrypted(),
                    device.createdAt(),
                    device.generation(),
                    device.serverId(),
                    device.clientType());
              });
      return response(updated, null);
    } finally {
      lock.unlock();
    }
  }

  public DeviceResponse get(UUID actor, UUID id) {
    var device = owned(actor, id);
    return response(device, null);
  }

  public PageResponse<DeviceResponse> list(UUID actor, int page, int size) {
    return list(actor, page, size, null);
  }

  public PageResponse<DeviceResponse> list(UUID actor, int page, int size, UUID serverId) {
    PageResponse.validate(page, size);
    auth.active(actor);
    long generation = migration.state().generation();
    return PageResponse.slice(
        devices.byUser(actor).stream()
            .filter(d -> serverId == null || serverId.equals(d.serverId()))
            .map(d -> response(d, null, generation))
            .toList(),
        page,
        size);
  }

  public Map<String, PeerRuntime> runtimeOrUnknown() {
    if (migration.state().maintenance()) return null;
    try {
      return vpn.runtime();
    } catch (ApiException e) {
      return null;
    }
  }

  public DeviceResponse response(Device device, Map<String, PeerRuntime> runtime) {
    return response(device, runtime, migration.state().generation());
  }

  private DeviceResponse response(
      Device device, Map<String, PeerRuntime> runtime, long generation) {
    boolean outdated = device.serverId() == null && device.generation() != generation;
    var peer = outdated || runtime == null ? null : runtime.get(device.publicKey());
    var status =
        outdated
            ? DeviceResponse.Status.OUTDATED
            : runtime == null
                ? DeviceResponse.Status.UNKNOWN
                : peer != null && peer.online(clock.instant(), config.vpn().handshakeThreshold())
                    ? DeviceResponse.Status.ONLINE
                    : DeviceResponse.Status.OFFLINE;
    return new DeviceResponse(
        device.id(),
        device.name(),
        device.icon(),
        device.vpnIp(),
        device.publicKey(),
        status,
        peer == null ? null : peer.lastHandshake(),
        peer == null ? 0 : peer.rxBytes(),
        peer == null ? 0 : peer.txBytes(),
        device.createdAt(),
        device.generation(),
        device.serverId(),
        device.serverId() == null ? null : catalog.require(device.serverId()).name(),
        device.clientType());
  }
}
