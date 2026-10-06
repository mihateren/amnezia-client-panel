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
    INVITATION_CREATED,
    INVITATION_USED,
    DEVICE_CREATED,
    DEVICE_CONFIG_VIEWED,
    DEVICE_CONFIG_DOWNLOADED,
    DEVICE_DELETED,
    USER_CREATED,
    USER_DISABLED,
    USER_DELETED,
    ADMIN_LOGIN
  }
}
