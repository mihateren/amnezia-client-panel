package org.example.amnezia.controlpanel.api.persistence.repository;

import java.time.Instant;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface AuditRepository {
  /** Добавляет неизменяемое событие аудита. */
  void append(AuditEvent event);

  /** Возвращает страницу событий с указанными фильтрами. */
  PageResponse<AuditEvent> list(
      int page,
      int size,
      AuditEvent.EventType type,
      UUID userId,
      AuditEvent.Result result,
      Instant from,
      Instant to);
}
