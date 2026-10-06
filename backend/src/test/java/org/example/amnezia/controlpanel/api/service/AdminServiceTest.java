package org.example.amnezia.controlpanel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AdminUser;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
  private final UserRepository users = mock(UserRepository.class);
  private final DeviceRepository devices = mock(DeviceRepository.class);
  private final AuthService auth = mock(AuthService.class);
  private final DeviceService deviceService = mock(DeviceService.class);
  private final AdminService service =
      new AdminService(
          users,
          devices,
          null,
          null,
          deviceService,
          auth,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
  private final UUID admin = UUID.randomUUID();

  @Test
  void shouldPreserveOwnerDetailsStatusFilteringOrderAndPagination() {
    authorize();
    var owner = user(UUID.randomUUID(), "Имя администратора", "nickname");
    var other = user(UUID.randomUUID(), "Другой пользователь", "other");
    var first = device(owner.id(), "first");
    var offline = device(other.id(), "offline");
    var second = device(owner.id(), "second");
    var third = device(owner.id(), "third");
    when(users.find(owner.id())).thenReturn(Optional.of(owner));
    when(users.find(other.id())).thenReturn(Optional.of(other));
    when(devices.count(owner.id())).thenReturn(3L);
    when(devices.count(other.id())).thenReturn(1L);
    when(devices.all()).thenReturn(List.of(first, offline, second, third));
    respond(first, DeviceResponse.Status.ONLINE);
    respond(offline, DeviceResponse.Status.OFFLINE);
    respond(second, DeviceResponse.Status.ONLINE);
    respond(third, DeviceResponse.Status.ONLINE);

    var page = service.devices(admin, 1, 2, null, DeviceResponse.Status.ONLINE);

    assertThat(page.content()).extracting(item -> item.device().id()).containsExactly(third.id());
    assertThat(page.content().getFirst().owner()).isEqualTo(view(owner, 3));
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(2);
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(service.devices(admin, 0, 2, null, DeviceResponse.Status.ONLINE).content())
        .extracting(item -> item.device().id())
        .containsExactly(first.id(), second.id());
    verify(users, times(2)).find(owner.id());
    verify(devices, times(2)).count(owner.id());
  }

  @Test
  void shouldReadUpdatedOwnerAndDeviceCountOnTheNextRequest() {
    authorize();
    var owner = user(UUID.randomUUID(), "Исходное имя", "nickname");
    var renamed = user(owner.id(), "Новое имя", "new-nickname");
    var first = device(owner.id(), "first");
    var second = device(owner.id(), "second");
    when(devices.byUser(owner.id())).thenReturn(List.of(first, second));
    when(users.find(owner.id())).thenReturn(Optional.of(owner));
    when(devices.count(owner.id())).thenReturn(2L);
    respond(first, DeviceResponse.Status.OFFLINE);
    respond(second, DeviceResponse.Status.ONLINE);

    assertThat(service.devices(admin, 0, 10, owner.id(), null).content())
        .extracting(item -> item.owner())
        .containsExactly(view(owner, 2), view(owner, 2));
    when(users.find(owner.id())).thenReturn(Optional.of(renamed));
    when(devices.count(owner.id())).thenReturn(3L);
    assertThat(service.devices(admin, 0, 10, owner.id(), null).content())
        .extracting(item -> item.owner())
        .containsExactly(view(renamed, 3), view(renamed, 3));
    verify(users, times(2)).find(owner.id());
    verify(devices, times(2)).count(owner.id());
  }

  private void authorize() {
    when(auth.active(admin))
        .thenReturn(
            new User(
                admin,
                "Admin",
                "admin",
                "hash",
                User.Role.ADMIN,
                User.Status.ACTIVE,
                NOW,
                NOW,
                null));
    when(deviceService.runtimeOrUnknown()).thenReturn(Map.of());
  }

  private void respond(Device device, DeviceResponse.Status status) {
    when(deviceService.response(device, Map.of()))
        .thenReturn(
            new DeviceResponse(
                device.id(),
                device.name(),
                device.vpnIp(),
                device.publicKey(),
                status,
                null,
                0,
                0,
                NOW));
  }

  private static User user(UUID id, String name, String nickname) {
    return new User(id, name, nickname, "hash", User.Role.USER, User.Status.ACTIVE, NOW, NOW, null);
  }

  private static Device device(UUID owner, String name) {
    return new Device(UUID.randomUUID(), owner, name, "10.8.1.2", name, "encrypted", NOW);
  }

  private static AdminUser view(User owner, long count) {
    return new AdminUser(
        owner.id(),
        owner.internalName(),
        owner.nickname(),
        owner.role(),
        owner.status(),
        count,
        owner.createdAt());
  }
}
