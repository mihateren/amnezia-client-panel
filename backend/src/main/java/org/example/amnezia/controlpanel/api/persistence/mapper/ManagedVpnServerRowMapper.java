package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class ManagedVpnServerRowMapper implements RowMapper<ManagedVpnServer> {
  @Override
  public ManagedVpnServer mapRow(ResultSet r, int row) throws SQLException {
    return new ManagedVpnServer(
        RowValues.uuid(r.getString("id")),
        r.getString("name"),
        r.getString("ip"),
        r.getInt("ssh_port"),
        r.getString("username"),
        r.getString("password_enc"),
        r.getString("host_key"),
        r.getString("container"),
        r.getString("interface_name"),
        r.getString("subnet"),
        r.getInt("vpn_port"),
        r.getBoolean("enabled"),
        RowValues.instant(r.getString("created_at")));
  }
}
