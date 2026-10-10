package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class VpnServerRowMapper implements RowMapper<VpnServer> {
  @Override
  public VpnServer mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new VpnServer(
        rs.getString("ip"),
        rs.getInt("ssh_port"),
        rs.getString("username"),
        rs.getString("private_key_enc"),
        rs.getString("host_key"),
        rs.getInt("vpn_port"));
  }
}
