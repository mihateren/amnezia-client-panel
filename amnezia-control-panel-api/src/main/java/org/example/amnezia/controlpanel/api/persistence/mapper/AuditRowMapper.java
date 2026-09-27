package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class AuditRowMapper implements RowMapper<AuditEvent> {
  @Override
  public AuditEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new AuditEvent(
        RowValues.uuid(rs.getString("id")),
        AuditEvent.EventType.valueOf(rs.getString("event_type")),
        AuditEvent.Result.valueOf(rs.getString("result")),
        RowValues.uuid(rs.getString("actor_user_id")),
        rs.getString("object_type"),
        RowValues.uuid(rs.getString("object_id")),
        RowValues.instant(rs.getString("created_at")),
        rs.getString("correlation_id"));
  }
}
