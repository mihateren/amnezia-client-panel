package org.example.amnezia.controlpanel.api.component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppStartupWithMigrationsComponentTest extends BaseComponentTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void applicationStartsWithSchemaApplied() {
        List<String> tables = jdbcTemplate.getJdbcOperations().queryForList(
                "select name from sqlite_master where type = 'table'", String.class);

        assertThat(tables).contains(
                "users",
                "invitations",
                "devices",
                "config_links",
                "audit_log",
                "DATABASECHANGELOG",
                "DATABASECHANGELOGLOCK"
        );
    }

    @Test
    void initialChangesetIsRecordedInDatabaseChangelog() {
        List<String> changesetIds = jdbcTemplate.getJdbcOperations().queryForList(
                "select id from DATABASECHANGELOG", String.class);

        assertThat(changesetIds).contains("001-initial-schema");
    }
}