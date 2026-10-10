package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class VpnMigrationRowMapper implements RowMapper<VpnMigration> {
  @Override
  public VpnMigration mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new VpnMigration(
        rs.getLong("generation"), rs.getBoolean("maintenance"), rs.getString("original_ip"));
  }
}
