package org.example.amnezia.controlpanel.api.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.MetricPeer;

/** Контракт доступа к данным; транзакциями управляет вызывающий сервис. */
public interface DeviceRepository {
  /** Ищет устройство по идентификатору. */
  Optional<Device> find(UUID id);

  /** Возвращает устройства пользователя. */
  List<Device> byUser(UUID id);

  /** Возвращает все устройства. */
  List<Device> all();

  /** Возвращает данные пиров для сбора метрик. */
  List<MetricPeer> metricPeers();

  /** Считает устройства пользователя. */
  long count(UUID userId);

  /** Считает все устройства. */
  long count();

  /** Сохраняет устройство. */
  void insert(Device device);

  /** Удаляет ссылки на конфигурацию и устройство в транзакции вызывающего сервиса. */
  void delete(UUID id);

  /** Обновляет имя и значок; возвращает признак найденного устройства. */
  boolean updateMetadata(UUID id, String name, Device.Icon icon);
}
