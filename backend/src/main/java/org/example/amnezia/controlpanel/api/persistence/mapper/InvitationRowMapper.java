package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.Invitation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class InvitationRowMapper implements RowMapper<Invitation> {
  @Override
  public Invitation mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Invitation(
        RowValues.uuid(rs.getString("id")),
        rs.getString("token_hash"),
        rs.getString("internal_name"),
        RowValues.uuid(rs.getString("created_by")),
        RowValues.instant(rs.getString("created_at")),
        RowValues.instant(rs.getString("used_at")),
        RowValues.uuid(rs.getString("used_by")));
  }
}
