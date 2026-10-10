package org.example.amnezia.controlpanel.api.persistence.repository;

import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;

public interface VpnMigrationRepository {
  VpnMigration find();

  void update(VpnMigration state);
}
