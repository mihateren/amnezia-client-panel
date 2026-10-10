package org.example.amnezia.controlpanel.api.persistence.model;

public record VpnMigration(long generation, boolean maintenance, String originalIp) {
  public VpnMigration(long generation, boolean maintenance) {
    this(generation, maintenance, null);
  }
}
