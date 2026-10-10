package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ACTOR_USER_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CORRELATION_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.EVENT_TYPE;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.FROM;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.LIMIT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.OBJECT_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.OBJECT_TYPE;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.OFFSET;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.RESULT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.TO;

import java.time.Instant;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.persistence.mapper.AuditRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.query.AuditQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("auditRepository")
public class AuditRepositoryImpl implements AuditRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final AuditRowMapper rowMapper;

  public AuditRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      AuditRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  @Override
  public void append(AuditEvent event) {
    var params =
        new MapSqlParameterSource(ID, event.id().toString())
            .addValue(EVENT_TYPE, event.eventType().name())
            .addValue(RESULT, event.result().name())
            .addValue(ACTOR_USER_ID, text(event.actorUserId()))
            .addValue(OBJECT_TYPE, event.objectType())
            .addValue(OBJECT_ID, text(event.objectId()))
            .addValue(CREATED_AT, event.createdAt().toString())
            .addValue(CORRELATION_ID, event.correlationId());
    jdbcTemplate.update(AuditQuery.INSERT.query(), params);
  }

  @Override
  public PageResponse<AuditEvent> list(
      int page,
      int size,
      AuditEvent.EventType type,
      UUID userId,
      AuditEvent.Result result,
      Instant from,
      Instant to) {
    var params =
        new MapSqlParameterSource(EVENT_TYPE, type == null ? null : type.name())
            .addValue(ACTOR_USER_ID, text(userId))
            .addValue(RESULT, result == null ? null : result.name())
            .addValue(FROM, from == null ? null : from.toString())
            .addValue(TO, to == null ? null : to.toString());
    long count = jdbcTemplate.queryForObject(AuditQuery.COUNT.query(), params, Long.class);
    var items =
        jdbcTemplate.query(
            AuditQuery.FIND_ALL.query(),
            params.addValue(LIMIT, size).addValue(OFFSET, (long) page * size),
            rowMapper);
    return PageResponse.of(items, page, size, count);
  }

  private static String text(UUID value) {
    return value == null ? null : value.toString();
  }
}
