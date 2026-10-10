package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.EXPIRES_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NOW;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.TOKEN_HASH;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.USER_ID;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.RefreshSessionRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.RefreshSession;
import org.example.amnezia.controlpanel.api.persistence.query.RefreshSessionQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.RefreshSessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("refreshSessionRepository")
public class RefreshSessionRepositoryImpl implements RefreshSessionRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final RefreshSessionRowMapper rowMapper;

  public RefreshSessionRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      RefreshSessionRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  @Override
  public void insert(RefreshSession session) {
    var params =
        new MapSqlParameterSource(ID, session.id().toString())
            .addValue(USER_ID, session.userId().toString())
            .addValue(TOKEN_HASH, session.tokenHash())
            .addValue(CREATED_AT, session.createdAt().toString())
            .addValue(EXPIRES_AT, session.expiresAt().toString());
    jdbcTemplate.update(RefreshSessionQuery.INSERT.query(), params);
  }

  @Override
  public Optional<RefreshSession> find(String hash) {
    return jdbcTemplate
        .query(
            RefreshSessionQuery.FIND_BY_TOKEN_HASH.query(),
            new MapSqlParameterSource(TOKEN_HASH, hash),
            rowMapper)
        .stream()
        .findFirst();
  }

  @Override
  public boolean revoke(String hash, Instant now) {
    return jdbcTemplate.update(
            RefreshSessionQuery.REVOKE.query(),
            new MapSqlParameterSource(TOKEN_HASH, hash).addValue(NOW, now.toString()))
        == 1;
  }

  @Override
  public void revokeUser(UUID userId, Instant now) {
    jdbcTemplate.update(
        RefreshSessionQuery.REVOKE_USER.query(),
        new MapSqlParameterSource(USER_ID, userId.toString()).addValue(NOW, now.toString()));
  }

  @Override
  public void deleteUser(UUID userId) {
    jdbcTemplate.update(
        RefreshSessionQuery.DELETE_USER.query(),
        new MapSqlParameterSource(USER_ID, userId.toString()));
  }
}
