package org.example.amnezia.controlpanel.api.persistence.query;

public enum UserQuery implements QueryHolder {
  FIND_BY_ID("sql/user/find-by-id.sql"),
  UPDATE_NICKNAME("sql/user/update-nickname.sql"),
  UPDATE_PASSWORD("sql/user/update-password.sql"),
  FIND_BY_NICKNAME("sql/user/find-by-nickname.sql"),
  INSERT("sql/user/insert.sql"),
  COUNT("sql/user/count.sql"),
  UPDATE_ACTIVITY("sql/user/update-activity.sql"),
  UPDATE_STATUS("sql/user/update-status.sql"),
  DELETE("sql/user/delete.sql"),
  FIND_ALL("sql/user/find-all.sql"),
  COUNT_FILTERED("sql/user/count-filtered.sql");
  private final String sql;

  UserQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
