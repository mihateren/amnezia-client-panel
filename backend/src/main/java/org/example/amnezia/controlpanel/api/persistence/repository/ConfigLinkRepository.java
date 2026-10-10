package org.example.amnezia.controlpanel.api.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.ConfigLink;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface ConfigLinkRepository {
  /** Сохраняет одноразовую ссылку на конфигурацию. */
  void insert(ConfigLink link);

  /** Ищет ссылку по хешу токена. */
  Optional<ConfigLink> find(String hash);

  /** Атомарно погашает неиспользованную и непросроченную ссылку. */
  boolean consume(UUID id, Instant now);
}
