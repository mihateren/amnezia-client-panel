package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_CREATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_DELETED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.FAILURE;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
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
import org.springframework.stereotype.Service;

@Service
public class DeviceService {
  private final DeviceRepository devices;
  private final AuthService auth;
  private final VpnGateway vpn;
  private final VpnMutation mutation;
  private final Secrets secrets;
  private final AppProperties config;
  private final Clock clock;
  private final ReentrantLock lock;
  private final AuditService audit;

  public DeviceService(
      DeviceRepository devices,
      AuthService auth,
      VpnGateway vpn,
      VpnMutation mutation,
      Secrets secrets,
      AppProperties config,
      Clock clock,
      ReentrantLock lock,
      AuditService audit) {
    this.devices = devices;
    this.auth = auth;
    this.vpn = vpn;
    this.mutation = mutation;
    this.secrets = secrets;
    this.config = config;
    this.clock = clock;
    this.lock = lock;
    this.audit = audit;
  }

  public Device owned(UUID actor, UUID deviceId) {
    var user = auth.active(actor);
    var device = devices.find(deviceId).orElseThrow(ApiException::notFound);
    if (!device.userId().equals(actor) && user.role() != User.Role.ADMIN)
      throw ApiException.notFound();
    return device;
  }

  public DeviceResponse create(UUID actor, String name) {
    lock.lock();
    try {
      auth.active(actor);
      if (devices.count(actor) >= 5)
        throw ApiException.conflict("DEVICE_LIMIT", "Device limit reached");
      var original = vpn.readConfig();
      var parsed = AwgConfig.parse(original);
      var ip = parsed.allocate(config.vpn().subnet());
      var keys = ClientKeys.generate();
      vpn.serverPublicKey();
      var replacement = parsed.add(keys.publicKey(), vpn.presharedKey(), ip);
      var id = UUID.randomUUID();
      var device =
          new Device(
              id,
              actor,
              name,
              ip,
              keys.publicKey(),
              secrets.encrypt(keys.privateKey(), id),
              clock.instant());
      mutation.apply(
          original,
          replacement,
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
      var original = vpn.readConfig();
      var replacement = AwgConfig.parse(original).remove(Set.of(device.publicKey()));
      mutation.apply(
          original,
          replacement,
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

  public DeviceResponse get(UUID actor, UUID id) {
    var device = owned(actor, id);
    return response(device, runtimeOrUnknown());
  }

  public PageResponse<DeviceResponse> list(UUID actor, int page, int size) {
    PageResponse.validate(page, size);
    auth.active(actor);
    var runtime = runtimeOrUnknown();
    return PageResponse.slice(
        devices.byUser(actor).stream().map(d -> response(d, runtime)).toList(), page, size);
  }

  public Map<String, PeerRuntime> runtimeOrUnknown() {
    try {
      return vpn.runtime();
    } catch (ApiException e) {
      return null;
    }
  }

  public DeviceResponse response(Device device, Map<String, PeerRuntime> runtime) {
    var peer = runtime == null ? null : runtime.get(device.publicKey());
    var status =
        runtime == null
            ? DeviceResponse.Status.UNKNOWN
            : peer != null && peer.online(clock.instant(), config.vpn().handshakeThreshold())
                ? DeviceResponse.Status.ONLINE
                : DeviceResponse.Status.OFFLINE;
    return new DeviceResponse(
        device.id(),
        device.name(),
        device.vpnIp(),
        device.publicKey(),
        status,
        peer == null ? null : peer.lastHandshake(),
        peer == null ? 0 : peer.rxBytes(),
        peer == null ? 0 : peer.txBytes(),
        device.createdAt());
  }
}
