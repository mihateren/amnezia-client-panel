package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.ConfigLink;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ConfigLinkRowMapper implements RowMapper<ConfigLink> {
  @Override
  public ConfigLink mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new ConfigLink(
        RowValues.uuid(rs.getString("id")),
        RowValues.uuid(rs.getString("device_id")),
        rs.getString("token_hash"),
        RowValues.instant(rs.getString("created_at")),
        RowValues.instant(rs.getString("expires_at")),
        RowValues.instant(rs.getString("used_at")));
  }
}
