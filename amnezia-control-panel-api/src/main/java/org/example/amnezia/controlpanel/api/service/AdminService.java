package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_DELETED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_DELETED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_DISABLED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AdminDevice;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AdminUser;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.Dashboard;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.Infrastructure;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.UserDetails;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.VpnState;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.InvitationRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.RefreshSessionRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.example.amnezia.controlpanel.api.vpn.VpnGateway;
import org.example.amnezia.controlpanel.api.vpn.VpnMonitor;
import org.example.amnezia.controlpanel.api.vpn.VpnMutation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminService {
  private final UserRepository users;
  private final DeviceRepository devices;
  private final RefreshSessionRepository sessions;
  private final InvitationRepository invitations;
  private final DeviceService deviceService;
  private final AuthService auth;
  private final AuditService audit;
  private final VpnGateway vpn;
  private final VpnMutation mutation;
  private final VpnMonitor monitor;
  private final ReentrantLock lock;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AdminService(
      UserRepository users,
      DeviceRepository devices,
      RefreshSessionRepository sessions,
      InvitationRepository invitations,
      DeviceService deviceService,
      AuthService auth,
      AuditService audit,
      VpnGateway vpn,
      VpnMutation mutation,
      VpnMonitor monitor,
      ReentrantLock lock,
      TransactionTemplate tx,
      Clock clock) {
    this.users = users;
    this.devices = devices;
    this.sessions = sessions;
    this.invitations = invitations;
    this.deviceService = deviceService;
    this.auth = auth;
    this.audit = audit;
    this.vpn = vpn;
    this.mutation = mutation;
    this.monitor = monitor;
    this.lock = lock;
    this.tx = tx;
    this.clock = clock;
  }

  private void admin(UUID actor) {
    if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
  }

  private User user(UUID id) {
    return users.find(id).orElseThrow(ApiException::notFound);
  }

  private AdminUser view(User user) {
    return new AdminUser(
        user.id(),
        user.internalName(),
        user.nickname(),
        user.role(),
        user.status(),
        devices.count(user.id()),
        user.createdAt());
  }

  public PageResponse<AdminUser> users(
      UUID actor, int page, int size, User.Status status, String search) {
    admin(actor);
    PageResponse.validate(page, size);
    if (search != null && search.length() > 100) throw ApiException.invalid();
    return PageResponse.of(
        users.list(status, search, size, (long) page * size).stream().map(this::view).toList(),
        page,
        size,
        users.count(status, search));
  }

  public UserDetails detail(UUID actor, UUID id) {
    admin(actor);
    var user = user(id);
    var runtime = deviceService.runtimeOrUnknown();
    return new UserDetails(
        view(user),
        user.lastActivityAt(),
        devices.byUser(id).stream().map(d -> deviceService.response(d, runtime)).toList());
  }

  public UserDetails status(UUID actor, UUID id, User.Status status) {
    lock.lock();
    try {
      admin(actor);
      var target = user(id);
      if (target.role() == User.Role.ADMIN && status == User.Status.DISABLED)
        throw ApiException.conflict("ADMIN_PROTECTED", "Administrator cannot be disabled");
      if (status == User.Status.DISABLED) removeAccess(actor, id, false);
      else tx.executeWithoutResult(s -> users.status(id, User.Status.ACTIVE, clock.instant()));
      return detail(actor, id);
    } finally {
      lock.unlock();
    }
  }

  public void deleteUser(UUID actor, UUID id) {
    lock.lock();
    try {
      admin(actor);
      if (user(id).role() == User.Role.ADMIN)
        throw ApiException.conflict("ADMIN_PROTECTED", "Administrator cannot be deleted");
      removeAccess(actor, id, true);
    } finally {
      lock.unlock();
    }
  }

  private void removeAccess(UUID actor, UUID id, boolean delete) {
    var owned = devices.byUser(id);
    Runnable persist =
        () -> {
          for (var device : owned) {
            devices.delete(device.id());
            audit.record(DEVICE_DELETED, SUCCESS, actor, "DEVICE", device.id());
          }
          invitations.deleteUnusedByCreator(id);
          if (delete) {
            sessions.deleteUser(id);
            users.delete(id);
          } else {
            sessions.revokeUser(id, clock.instant());
            users.status(id, User.Status.DISABLED, clock.instant());
          }
          audit.record(delete ? USER_DELETED : USER_DISABLED, SUCCESS, actor, "USER", id);
        };
    if (owned.isEmpty()) tx.executeWithoutResult(s -> persist.run());
    else {
      var original = vpn.readConfig();
      var replacement =
          AwgConfig.parse(original)
              .remove(owned.stream().map(Device::publicKey).collect(Collectors.toSet()));
      mutation.apply(original, replacement, persist);
    }
  }

  public PageResponse<AdminDevice> devices(
      UUID actor, int page, int size, UUID userId, DeviceResponse.Status status) {
    admin(actor);
    PageResponse.validate(page, size);
    var runtime = deviceService.runtimeOrUnknown();
    var candidates = userId == null ? devices.all() : devices.byUser(userId);
    var matched =
        candidates.stream()
            .map(d -> new AdminDevice(deviceService.response(d, runtime), view(user(d.userId()))))
            .filter(d -> status == null || d.device().status() == status)
            .toList();
    return PageResponse.slice(matched, page, size);
  }

  public AdminDevice device(UUID actor, UUID id) {
    admin(actor);
    var device = deviceService.owned(actor, id);
    return new AdminDevice(
        deviceService.response(device, deviceService.runtimeOrUnknown()),
        view(user(device.userId())));
  }

  public VpnState vpn(UUID actor) {
    admin(actor);
    return monitor.refresh();
  }

  public Dashboard dashboard(UUID actor) {
    admin(actor);
    var state = monitor.refresh();
    var runtime = monitor.runtime();
    long active =
        devices.all().stream()
            .filter(
                d -> deviceService.response(d, runtime).status() == DeviceResponse.Status.ONLINE)
            .count();
    return new Dashboard(
        users.count(),
        devices.count(),
        active,
        state,
        new Infrastructure(
            "UP", "UP", state.serverUp() ? "UP" : "DOWN", state.serverUp() ? "UP" : "DOWN"));
  }
}
