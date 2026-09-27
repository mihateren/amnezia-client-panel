package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class UserRowMapper implements RowMapper<User> {
  @Override
  public User mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new User(
        RowValues.uuid(rs.getString("id")),
        rs.getString("internal_name"),
        rs.getString("nickname"),
        rs.getString("password_hash"),
        User.Role.valueOf(rs.getString("role")),
        User.Status.valueOf(rs.getString("status")),
        RowValues.instant(rs.getString("created_at")),
        RowValues.instant(rs.getString("updated_at")),
        RowValues.instant(rs.getString("last_activity_at")));
  }
}
