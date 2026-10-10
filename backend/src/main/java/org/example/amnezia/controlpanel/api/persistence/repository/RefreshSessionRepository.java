package org.example.amnezia.controlpanel.api.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.RefreshSession;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface RefreshSessionRepository {
  /** Сохраняет сессию обновления токена. */
  void insert(RefreshSession session);

  /** Ищет сессию по хешу токена. */
  Optional<RefreshSession> find(String hash);

  /** Атомарно отзывает действующую сессию. */
  boolean revoke(String hash, Instant now);

  /** Отзывает сессии пользователя. */
  void revokeUser(UUID userId, Instant now);

  /** Удаляет сессии пользователя. */
  void deleteUser(UUID userId);
}
