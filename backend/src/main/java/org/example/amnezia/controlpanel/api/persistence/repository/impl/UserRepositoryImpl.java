package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.INTERNAL_NAME;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.LIMIT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NICKNAME;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NOW;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.OFFSET;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.PASSWORD_HASH;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ROLE;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.SEARCH;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.STATUS;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.UPDATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.USER_ID;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.UserRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.query.UserQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("userRepository")
public class UserRepositoryImpl implements UserRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final UserRowMapper rowMapper;

  public UserRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      UserRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  @Override
  public Optional<User> find(UUID id) {
    return jdbcTemplate
        .query(
            UserQuery.FIND_BY_ID.query(),
            new MapSqlParameterSource(USER_ID, id.toString()),
            rowMapper)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<User> findByNickname(String nickname) {
    return jdbcTemplate
        .query(
            UserQuery.FIND_BY_NICKNAME.query(),
            new MapSqlParameterSource(NICKNAME, nickname),
            rowMapper)
        .stream()
        .findFirst();
  }

  @Override
  public void insert(User user) {
    var params =
        new MapSqlParameterSource()
            .addValue(USER_ID, user.id().toString())
            .addValue(INTERNAL_NAME, user.internalName())
            .addValue(NICKNAME, user.nickname())
            .addValue(PASSWORD_HASH, user.passwordHash())
            .addValue(ROLE, user.role().name())
            .addValue(STATUS, user.status().name())
            .addValue(CREATED_AT, user.createdAt().toString())
            .addValue(UPDATED_AT, user.updatedAt().toString());
    jdbcTemplate.update(UserQuery.INSERT.query(), params);
  }

  @Override
  public long count() {
    return jdbcTemplate.queryForObject(
        UserQuery.COUNT.query(), new MapSqlParameterSource(), Long.class);
  }

  @Override
  public void activity(UUID id, Instant now) {
    jdbcTemplate.update(
        UserQuery.UPDATE_ACTIVITY.query(),
        new MapSqlParameterSource(USER_ID, id.toString()).addValue(NOW, now.toString()));
  }

  @Override
  public void status(UUID id, User.Status status, Instant now) {
    jdbcTemplate.update(
        UserQuery.UPDATE_STATUS.query(),
        new MapSqlParameterSource(USER_ID, id.toString())
            .addValue(STATUS, status.name())
            .addValue(NOW, now.toString()));
  }

  @Override
  public void nickname(UUID id, String nickname, Instant now) {
    jdbcTemplate.update(
        UserQuery.UPDATE_NICKNAME.query(),
        new MapSqlParameterSource(USER_ID, id.toString())
            .addValue(NICKNAME, nickname)
            .addValue(NOW, now.toString()));
  }

  @Override
  public void password(UUID id, String hash, Instant now) {
    jdbcTemplate.update(
        UserQuery.UPDATE_PASSWORD.query(),
        new MapSqlParameterSource(USER_ID, id.toString())
            .addValue(PASSWORD_HASH, hash)
            .addValue(NOW, now.toString()));
  }

  @Override
  public void delete(UUID id) {
    jdbcTemplate.update(
        UserQuery.DELETE.query(), new MapSqlParameterSource(USER_ID, id.toString()));
  }

  @Override
  public List<User> list(User.Status status, String search, int limit, long offset) {
    return jdbcTemplate.query(
        UserQuery.FIND_ALL.query(),
        filters(status, search).addValue(LIMIT, limit).addValue(OFFSET, offset),
        rowMapper);
  }

  @Override
  public long count(User.Status status, String search) {
    return jdbcTemplate.queryForObject(
        UserQuery.COUNT_FILTERED.query(), filters(status, search), Long.class);
  }

  private MapSqlParameterSource filters(User.Status status, String search) {
    return new MapSqlParameterSource(STATUS, status == null ? null : status.name())
        .addValue(SEARCH, pattern(search));
  }

  private static String pattern(String value) {
    return "%"
        + (value == null ? "" : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
        + "%";
  }
}
