package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.TableStorage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TableStorageRowMapper implements RowMapper<TableStorage> {
  @Override
  public TableStorage mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new TableStorage(
        rs.getString("table_name"), rs.getLong("allocated_bytes"), rs.getLong("payload_bytes"));
  }
}
