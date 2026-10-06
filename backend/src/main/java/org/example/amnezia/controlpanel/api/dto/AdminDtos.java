package org.example.amnezia.controlpanel.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.User;

public final class AdminDtos {
  private AdminDtos() {}

  public record AdminUser(
      UUID id,
      String internalName,
      String nickname,
      User.Role role,
      User.Status status,
      long deviceCount,
      Instant createdAt) {}

  public record UserDetails(AdminUser user, Instant lastActivityAt, List<DeviceResponse> devices) {}

  public record AdminDevice(DeviceResponse device, AdminUser owner) {}

  public record AuditActor(UUID userId, String nickname) {}

  public record AuditObject(String type, UUID id) {}

  public record AuditResponse(
      UUID id,
      Instant timestamp,
      AuditEvent.EventType eventType,
      AuditEvent.Result result,
      AuditActor actor,
      AuditObject object,
      String correlationId) {}

  public record VpnState(boolean serverUp, int peersTotal, int peersActive, Instant checkedAt) {}

  public record Infrastructure(String application, String database, String ssh, String vpn) {}

  public record Dashboard(
      long usersTotal,
      long devicesTotal,
      long devicesActive,
      VpnState vpn,
      Infrastructure infrastructure) {}
}
