package org.example.amnezia.controlpanel.api.persistence.query;

public enum InvitationQuery implements QueryHolder {
  INSERT("sql/invitation/insert.sql"),
  FIND_UNUSED("sql/invitation/find-unused.sql"),
  CONSUME("sql/invitation/consume.sql"),
  DELETE_UNUSED_BY_CREATOR("sql/invitation/delete-unused-by-creator.sql");
  private final String sql;

  InvitationQuery(String path) {
    sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
