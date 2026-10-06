package org.example.amnezia.controlpanel.api.persistence.query;

public enum DeviceQuery implements QueryHolder {
  FIND_BY_ID("sql/device/find-by-id.sql"),
  FIND_BY_USER_ID("sql/device/find-by-user-id.sql"),
  FIND_ALL("sql/device/find-all.sql"),
  FIND_METRIC_PEERS("sql/device/find-metric-peers.sql"),
  COUNT_BY_USER_ID("sql/device/count-by-user-id.sql"),
  COUNT("sql/device/count.sql"),
  INSERT("sql/device/insert.sql"),
  DELETE_LINKS("sql/device/delete-links.sql"),
  DELETE("sql/device/delete.sql");
  private final String sql;

  DeviceQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
