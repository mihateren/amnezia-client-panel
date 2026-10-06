package org.example.amnezia.controlpanel.api.persistence.repository;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_BY;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.INTERNAL_NAME;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NOW;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.TOKEN_HASH;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.USER_ID;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabasePins;
import org.example.amnezia.controlpanel.api.persistence.mapper.InvitationRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.Invitation;
import org.example.amnezia.controlpanel.api.persistence.query.InvitationQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final InvitationRowMapper rowMapper;

  public InvitationRepository(
      @Qualifier(DatabasePins.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      InvitationRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  public void insert(Invitation invitation) {
    var params =
        new MapSqlParameterSource(ID, invitation.id().toString())
            .addValue(TOKEN_HASH, invitation.tokenHash())
            .addValue(INTERNAL_NAME, invitation.internalName())
            .addValue(CREATED_BY, invitation.createdBy().toString())
            .addValue(CREATED_AT, invitation.createdAt().toString());
    jdbcTemplate.update(InvitationQuery.INSERT.query(), params);
  }

  public Optional<Invitation> findUnused(String hash) {
    return jdbcTemplate
        .query(
            InvitationQuery.FIND_UNUSED.query(),
            new MapSqlParameterSource(TOKEN_HASH, hash),
            rowMapper)
        .stream()
        .findFirst();
  }

  public boolean consume(UUID id, UUID userId, Instant now) {
    return jdbcTemplate.update(
            InvitationQuery.CONSUME.query(),
            new MapSqlParameterSource(ID, id.toString())
                .addValue(USER_ID, userId.toString())
                .addValue(NOW, now.toString()))
        == 1;
  }

  public void deleteUnusedByCreator(UUID creator) {
    jdbcTemplate.update(
        InvitationQuery.DELETE_UNUSED_BY_CREATOR.query(),
        new MapSqlParameterSource(CREATED_BY, creator.toString()));
  }
}
