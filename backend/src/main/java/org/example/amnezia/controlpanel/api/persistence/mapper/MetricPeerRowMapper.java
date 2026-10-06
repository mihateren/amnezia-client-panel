package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.MetricPeer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class MetricPeerRowMapper implements RowMapper<MetricPeer> {
  @Override
  public MetricPeer mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new MetricPeer(
        RowValues.uuid(rs.getString("id")),
        rs.getString("name"),
        rs.getString("user_name"),
        rs.getString("client_public_key"));
  }
}
