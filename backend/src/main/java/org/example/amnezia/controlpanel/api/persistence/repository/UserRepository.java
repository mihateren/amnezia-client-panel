package org.example.amnezia.controlpanel.api.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.User;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface UserRepository {
  /** Ищет пользователя по идентификатору. */
  Optional<User> find(UUID id);

  /** Ищет пользователя по никнейму. */
  Optional<User> findByNickname(String nickname);

  /** Сохраняет пользователя. */
  void insert(User user);

  /** Считает всех пользователей. */
  long count();

  /** Обновляет время активности пользователя. */
  void activity(UUID id, Instant now);

  /** Обновляет статус пользователя. */
  void status(UUID id, User.Status status, Instant now);

  /** Обновляет никнейм пользователя. */
  void nickname(UUID id, String nickname, Instant now);

  /** Обновляет хеш пароля пользователя. */
  void password(UUID id, String hash, Instant now);

  /** Удаляет пользователя. */
  void delete(UUID id);

  /** Возвращает пользователей с фильтрацией и пагинацией. */
  List<User> list(User.Status status, String search, int limit, long offset);

  /** Считает пользователей с указанными фильтрами. */
  long count(User.Status status, String search);
}
