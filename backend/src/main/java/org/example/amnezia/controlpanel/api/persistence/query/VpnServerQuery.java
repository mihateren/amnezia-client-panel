package org.example.amnezia.controlpanel.api.persistence.query;

public enum VpnServerQuery implements QueryHolder {
  FIND("sql/vpn-server/find.sql"),
  SAVE("sql/vpn-server/save.sql");
  private final String sql;

  VpnServerQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
