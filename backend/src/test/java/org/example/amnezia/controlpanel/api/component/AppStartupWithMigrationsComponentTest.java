package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AppStartupWithMigrationsComponentTest extends BaseComponentTest {
  @Autowired
  AppStartupWithMigrationsComponentTest(
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabasePins
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void applicationStartsWithSchemaApplied() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "select name from sqlite_master where type = 'table'",
            new MapSqlParameterSource(),
            String.class);

    assertThat(tables)
        .contains(
            "users",
            "invitations",
            "devices",
            "config_links",
            "audit_log",
            "DATABASECHANGELOG",
            "DATABASECHANGELOGLOCK");
  }

  @Test
  void initialChangesetIsRecordedInDatabaseChangelog() {
    List<String> changesetIds =
        jdbcTemplate.queryForList(
            "select id from DATABASECHANGELOG", new MapSqlParameterSource(), String.class);

    assertThat(changesetIds).contains("001-initial-schema");
  }
}
