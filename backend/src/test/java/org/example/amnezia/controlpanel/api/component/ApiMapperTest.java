package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.AdminDtos;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.mapper.AdminApiMapper;
import org.example.amnezia.controlpanel.api.mapper.AuditApiMapper;
import org.example.amnezia.controlpanel.api.mapper.AuthApiMapper;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.rest.model.LoginRequest;
import org.example.amnezia.controlpanel.api.rest.model.UserRole;
import org.example.amnezia.controlpanel.api.rest.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApiMapperTest extends BaseComponentTest {
  private final AdminApiMapper admin;
  private final AuditApiMapper audit;
  private final AuthApiMapper auth;

  @Autowired
  ApiMapperTest(AdminApiMapper admin, AuditApiMapper audit, AuthApiMapper auth) {
    this.admin = admin;
    this.audit = audit;
    this.auth = auth;
  }

  @Test
  void shouldMapCredentialsAndFlattenAdminDetailsWithoutLosingNullsOrTimestamps() {
    var credentials = auth.credentials(new LoginRequest("test-nickname", "test-password"));
    assertThat(credentials.nickname()).isEqualTo("test-nickname");
    assertThat(credentials.password()).isEqualTo("test-password");
    assertThat(credentials.toString()).doesNotContain("test-password");
    var id = UUID.randomUUID();
    var now = Instant.parse("2026-01-02T03:04:05.123Z");
    var user =
        new AdminDtos.AdminUser(
            id, "internal", "nickname", User.Role.USER, User.Status.DISABLED, 1, now);
    var device =
        new DeviceResponse(
            UUID.randomUUID(),
            "laptop",
            "10.8.1.2",
            "public",
            DeviceResponse.Status.UNKNOWN,
            null,
            0,
            0,
            now);
    var result = admin.details(new AdminDtos.UserDetails(user, null, List.of(device)));
    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getRole()).isEqualTo(UserRole.USER);
    assertThat(result.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(result.getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
    assertThat(result.getLastActivityAt()).isNull();
    assertThat(result.getDevices().getFirst().getLastHandshakeAt()).isNull();
    assertThat(result.getDevices().getFirst().getRxBytes()).isZero();
    var adminDevice = admin.device(new AdminDtos.AdminDevice(device, user));
    assertThat(adminDevice.getId()).isEqualTo(device.id());
    assertThat(adminDevice.getOwner().getId()).isEqualTo(id);
  }

  @Test
  void shouldMapAuditObjectReservedNameAndNullableActor() {
    var objectId = UUID.randomUUID();
    var event =
        new AdminDtos.AuditResponse(
            UUID.randomUUID(),
            Instant.now(),
            AuditEvent.EventType.DEVICE_CREATED,
            AuditEvent.Result.SUCCESS,
            null,
            new AdminDtos.AuditObject("DEVICE", objectId),
            "correlation");
    var result = audit.event(event);
    assertThat(result.getActor()).isNull();
    assertThat(result.getObject().getId()).isEqualTo(objectId.toString());
    assertThat(result.getEventType().getValue()).isEqualTo("DEVICE_CREATED");
    assertThat(result.getCorrelationId()).isEqualTo("correlation");
  }
}
