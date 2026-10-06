package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.RefreshSession;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class RefreshSessionRowMapper implements RowMapper<RefreshSession> {
  @Override
  public RefreshSession mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new RefreshSession(
        RowValues.uuid(rs.getString("id")),
        RowValues.uuid(rs.getString("user_id")),
        rs.getString("token_hash"),
        RowValues.instant(rs.getString("created_at")),
        RowValues.instant(rs.getString("expires_at")),
        RowValues.instant(rs.getString("revoked_at")));
  }
}
