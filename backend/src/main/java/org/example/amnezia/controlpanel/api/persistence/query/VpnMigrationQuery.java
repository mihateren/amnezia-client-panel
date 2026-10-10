package org.example.amnezia.controlpanel.api.persistence.query;

public enum VpnMigrationQuery implements QueryHolder {
  FIND("sql/vpn-migration/find.sql"),
  UPDATE("sql/vpn-migration/update.sql");

  private final String sql;

  VpnMigrationQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
