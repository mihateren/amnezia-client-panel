package org.example.amnezia.controlpanel.api.service;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.VpnMigrationResponse;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnMigrationRepository;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.example.amnezia.controlpanel.api.vpn.VpnGateway;
import org.example.amnezia.controlpanel.api.vpn.VpnMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VpnMigrationService {
  private final VpnMigrationRepository repository;
  private final DeviceRepository devices;
  private final AuthService auth;
  private final VpnServerSettings servers;
  private final VpnGateway vpn;
  private final VpnMonitor monitor;
  private final AuditService audit;
  private final ReentrantLock lock;
  private final TransactionTemplate tx;

  public VpnMigrationService(
      VpnMigrationRepository repository,
      DeviceRepository devices,
      AuthService auth,
      VpnServerSettings servers,
      VpnGateway vpn,
      VpnMonitor monitor,
      AuditService audit,
      ReentrantLock lock,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx) {
    this.repository = repository;
    this.devices = devices;
    this.auth = auth;
    this.servers = servers;
    this.vpn = vpn;
    this.monitor = monitor;
    this.audit = audit;
    this.lock = lock;
    this.tx = tx;
  }

  public VpnMigration state() {
    return repository.find();
  }

  public boolean outdated(Device device) {
    return device.serverId() == null && device.generation() != state().generation();
  }

  public long requireAvailable() {
    var state = state();
    if (state.maintenance())
      throw ApiException.conflict(
          "VPN_MAINTENANCE", "Проводится перенос VPN. Повторите после завершения работ.");
    return state.generation();
  }

  public void requireConfig(Device device) {
    if (device.serverId() != null) return;
    long generation = requireAvailable();
    if (device.generation() != generation)
      throw ApiException.conflict(
          "CONFIG_OUTDATED",
          "Конфигурация устарела после переезда. Создайте новую и удалите старую.");
  }

  public VpnMigrationResponse status(UUID actor) {
    lock.lock();
    try {
      auth.active(actor);
      var state = state();
      long outdated = devices.byUser(actor).stream().filter(this::outdated).count();
      return new VpnMigrationResponse(
          state.generation(),
          state.maintenance(),
          outdated,
          !state.maintenance() && monitor.unavailable());
    } finally {
      lock.unlock();
    }
  }

  public VpnMigrationResponse change(UUID actor, long expectedGeneration, boolean begin) {
    lock.lock();
    try {
      if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
      var state = state();
      if (state.generation() != expectedGeneration || state.maintenance() == begin)
        throw ApiException.conflict(
            "MIGRATION_STATE_CHANGED", "Состояние переезда изменилось. Обновите страницу.");
      if (!begin) {
        AwgConfig.parse(vpn.readConfig());
        AwgConfig.key(vpn.serverPublicKey());
        AwgConfig.key(vpn.presharedKey());
        vpn.runtime();
      }
      String currentIp = servers.current().ip();
      boolean ipChanged =
          !begin
              && state.originalIp() != null
              && !VpnServerSettings.sameIp(state.originalIp(), currentIp);
      tx.executeWithoutResult(
          s -> {
            repository.update(
                new VpnMigration(
                    ipChanged ? Math.incrementExact(state.generation()) : state.generation(),
                    begin,
                    begin ? currentIp : null));
            audit.record(
                begin ? EventType.VPN_MIGRATION_STARTED : EventType.VPN_MIGRATION_COMPLETED,
                Result.SUCCESS,
                actor,
                "VPN",
                null);
          });
      return status(actor);
    } finally {
      lock.unlock();
    }
  }
}
