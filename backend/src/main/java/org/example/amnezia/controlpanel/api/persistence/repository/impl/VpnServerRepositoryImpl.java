package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import java.util.Optional;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.VpnServerRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;
import org.example.amnezia.controlpanel.api.persistence.query.VpnServerQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnServerRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VpnServerRepositoryImpl implements VpnServerRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final VpnServerRowMapper mapper;

  public VpnServerRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      VpnServerRowMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Optional<VpnServer> find() {
    return jdbc.query(VpnServerQuery.FIND.query(), new MapSqlParameterSource(), mapper).stream()
        .findFirst();
  }

  @Override
  public void save(VpnServer s) {
    jdbc.update(
        VpnServerQuery.SAVE.query(),
        new MapSqlParameterSource("ip", s.ip())
            .addValue("sshPort", s.sshPort())
            .addValue("username", s.username())
            .addValue("privateKeyEncrypted", s.privateKeyEncrypted())
            .addValue("hostKey", s.hostKey())
            .addValue("vpnPort", s.vpnPort()));
  }
}
