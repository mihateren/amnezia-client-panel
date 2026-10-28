package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class DeviceRowMapper implements RowMapper<Device> {
  @Override
  public Device mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Device(
        RowValues.uuid(rs.getString("id")),
        RowValues.uuid(rs.getString("user_id")),
        rs.getString("name"),
        Device.Icon.valueOf(rs.getString("icon")),
        rs.getString("vpn_ip"),
        rs.getString("client_public_key"),
        rs.getString("client_private_key_enc"),
        RowValues.instant(rs.getString("created_at")),
        rs.getLong("generation"),
        rs.getString("server_id") == null ? null : RowValues.uuid(rs.getString("server_id")),
        Device.ClientType.valueOf(rs.getString("client_type")));
  }
}
