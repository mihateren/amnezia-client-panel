package org.example.amnezia.controlpanel.api.persistence.repository;

import org.example.amnezia.controlpanel.api.persistence.model.DatabaseStorage;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface DatabaseMetricRepository {
  /** Возвращает объём базы и статистику хранения таблиц. */
  DatabaseStorage storage();
}
