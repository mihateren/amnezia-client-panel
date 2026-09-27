package org.example.amnezia.controlpanel.api.persistence.query;

public enum AuditQuery implements QueryHolder {
  INSERT("sql/audit/insert.sql"),
  FIND_ALL("sql/audit/find-all.sql"),
  COUNT("sql/audit/count.sql");
  private final String sql;

  AuditQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
