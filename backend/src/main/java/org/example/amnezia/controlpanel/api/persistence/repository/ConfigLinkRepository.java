package org.example.amnezia.controlpanel.api.persistence.repository;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.DEVICE_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.EXPIRES_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NOW;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.TOKEN_HASH;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabasePins;
import org.example.amnezia.controlpanel.api.persistence.mapper.ConfigLinkRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.ConfigLink;
import org.example.amnezia.controlpanel.api.persistence.query.ConfigLinkQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigLinkRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ConfigLinkRowMapper rowMapper;

  public ConfigLinkRepository(
      @Qualifier(DatabasePins.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      ConfigLinkRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  public void insert(ConfigLink link) {
    var params =
        new MapSqlParameterSource(ID, link.id().toString())
            .addValue(DEVICE_ID, link.deviceId().toString())
            .addValue(TOKEN_HASH, link.tokenHash())
            .addValue(CREATED_AT, link.createdAt().toString())
            .addValue(EXPIRES_AT, link.expiresAt().toString());
    jdbcTemplate.update(ConfigLinkQuery.INSERT.query(), params);
  }

  public Optional<ConfigLink> find(String hash) {
    return jdbcTemplate
        .query(
            ConfigLinkQuery.FIND_BY_TOKEN_HASH.query(),
            new MapSqlParameterSource(TOKEN_HASH, hash),
            rowMapper)
        .stream()
        .findFirst();
  }

  public boolean consume(UUID id, Instant now) {
    return jdbcTemplate.update(
            ConfigLinkQuery.CONSUME.query(),
            new MapSqlParameterSource(ID, id.toString()).addValue(NOW, now.toString()))
        == 1;
  }
}
