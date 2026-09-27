package org.example.amnezia.controlpanel.api.persistence.query;

public enum RefreshSessionQuery implements QueryHolder {
  INSERT("sql/refresh-session/insert.sql"),
  FIND_BY_TOKEN_HASH("sql/refresh-session/find-by-token-hash.sql"),
  REVOKE("sql/refresh-session/revoke.sql"),
  REVOKE_USER("sql/refresh-session/revoke-user.sql"),
  DELETE_USER("sql/refresh-session/delete-user.sql");
  private final String sql;

  RefreshSessionQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
