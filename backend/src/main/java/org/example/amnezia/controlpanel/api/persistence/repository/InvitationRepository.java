package org.example.amnezia.controlpanel.api.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.Invitation;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface InvitationRepository {
  /** Сохраняет приглашение. */
  void insert(Invitation invitation);

  /** Ищет неиспользованное приглашение по хешу токена. */
  Optional<Invitation> findUnused(String hash);

  /** Атомарно связывает неиспользованное приглашение с пользователем. */
  boolean consume(UUID id, UUID userId, Instant now);

  /** Удаляет неиспользованные приглашения указанного автора. */
  void deleteUnusedByCreator(UUID creator);
}
