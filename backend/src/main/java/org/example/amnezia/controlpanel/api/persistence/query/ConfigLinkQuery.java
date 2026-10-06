package org.example.amnezia.controlpanel.api.persistence.query;

public enum ConfigLinkQuery implements QueryHolder {
  INSERT("sql/config-link/insert.sql"),
  FIND_BY_TOKEN_HASH("sql/config-link/find-by-token-hash.sql"),
  CONSUME("sql/config-link/consume.sql");
  private final String sql;

  ConfigLinkQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
