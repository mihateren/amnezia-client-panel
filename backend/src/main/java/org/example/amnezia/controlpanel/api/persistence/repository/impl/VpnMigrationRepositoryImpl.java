package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.VpnMigrationRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;
import org.example.amnezia.controlpanel.api.persistence.query.VpnMigrationQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnMigrationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VpnMigrationRepositoryImpl implements VpnMigrationRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final VpnMigrationRowMapper mapper;

  public VpnMigrationRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      VpnMigrationRowMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public VpnMigration find() {
    return jdbc.queryForObject(VpnMigrationQuery.FIND.query(), new MapSqlParameterSource(), mapper);
  }

  @Override
  public void update(VpnMigration state) {
    jdbc.update(
        VpnMigrationQuery.UPDATE.query(),
        new MapSqlParameterSource("generation", state.generation())
            .addValue("maintenance", state.maintenance() ? 1 : 0)
            .addValue("originalIp", state.originalIp()));
  }
}
