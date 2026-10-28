package org.example.amnezia.controlpanel.api.persistence.query;

public enum ManagedVpnServerQuery implements QueryHolder {
  ALL("all"),
  FIND("find"),
  INSERT("insert"),
  UPDATE("update"),
  ADOPT_LEGACY("adopt-legacy");
  private final String sql;

  ManagedVpnServerQuery(String file) {
    sql = SqlResourceLoader.load("sql/managed-vpn-server/" + file + ".sql");
  }

  @Override
  public String query() {
    return sql;
  }
}
