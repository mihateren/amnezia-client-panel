package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.ManagedVpnServerRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.persistence.query.ManagedVpnServerQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.ManagedVpnServerRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagedVpnServerRepositoryImpl implements ManagedVpnServerRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final ManagedVpnServerRowMapper mapper;

  public ManagedVpnServerRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      ManagedVpnServerRowMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public List<ManagedVpnServer> all() {
    return jdbc.query(ManagedVpnServerQuery.ALL.query(), new MapSqlParameterSource(), mapper);
  }

  @Override
  public Optional<ManagedVpnServer> find(UUID id) {
    return jdbc
        .query(
            ManagedVpnServerQuery.FIND.query(),
            new MapSqlParameterSource("id", id.toString()),
            mapper)
        .stream()
        .findFirst();
  }

  private MapSqlParameterSource params(ManagedVpnServer s) {
    return new MapSqlParameterSource("id", s.id().toString())
        .addValue("name", s.name())
        .addValue("ip", s.ip())
        .addValue("sshPort", s.sshPort())
        .addValue("username", s.username())
        .addValue("passwordEncrypted", s.passwordEncrypted())
        .addValue("hostKey", s.hostKey())
        .addValue("container", s.container())
        .addValue("interfaceName", s.interfaceName())
        .addValue("subnet", s.subnet())
        .addValue("vpnPort", s.vpnPort())
        .addValue("enabled", s.enabled() ? 1 : 0)
        .addValue("createdAt", s.createdAt().toString());
  }

  @Override
  public void insert(ManagedVpnServer s) {
    jdbc.update(ManagedVpnServerQuery.INSERT.query(), params(s));
  }

  @Override
  public void update(ManagedVpnServer s) {
    jdbc.update(ManagedVpnServerQuery.UPDATE.query(), params(s));
  }

  @Override
  public void adoptLegacy(UUID serverId, UUID deviceId) {
    jdbc.update(
        ManagedVpnServerQuery.ADOPT_LEGACY.query(),
        new MapSqlParameterSource("id", serverId.toString())
            .addValue("deviceId", deviceId.toString()));
  }
}
