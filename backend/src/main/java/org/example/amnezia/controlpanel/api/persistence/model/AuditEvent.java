package org.example.amnezia.controlpanel.api.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID id,
    EventType eventType,
    Result result,
    UUID actorUserId,
    String objectType,
    UUID objectId,
    Instant createdAt,
    String correlationId) {
  public enum Result {
    SUCCESS,
    FAILURE
  }

  public enum EventType {
    USER_LOGIN,
    USER_LOGOUT,
    USER_NICKNAME_CHANGED,
    USER_PASSWORD_CHANGED,
    INVITATION_CREATED,
    INVITATION_USED,
    DEVICE_CREATED,
    DEVICE_UPDATED,
    DEVICE_CONFIG_VIEWED,
    DEVICE_CONFIG_DOWNLOADED,
    DEVICE_DELETED,
    USER_CREATED,
    USER_DISABLED,
    USER_DELETED,
    VPN_SERVER_UPDATED,
    VPN_MIGRATION_STARTED,
    VPN_MIGRATION_COMPLETED,
    ADMIN_LOGIN
  }
}
