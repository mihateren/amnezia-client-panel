package org.example.amnezia.controlpanel.api.persistence.query;

public enum DatabaseMetricQuery implements QueryHolder {
  DATABASE_STORAGE("sql/metrics/database-storage.sql"),
  TABLE_STORAGE("sql/metrics/table-storage.sql");

  private final String sql;

  DatabaseMetricQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
