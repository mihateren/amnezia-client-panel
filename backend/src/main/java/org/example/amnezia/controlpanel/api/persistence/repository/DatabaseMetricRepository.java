package org.example.amnezia.controlpanel.api.persistence.repository;

import org.example.amnezia.controlpanel.api.config.db.DatabasePins;
import org.example.amnezia.controlpanel.api.persistence.mapper.TableStorageRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.DatabaseStorage;
import org.example.amnezia.controlpanel.api.persistence.query.DatabaseMetricQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseMetricRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final TableStorageRowMapper rowMapper;

  public DatabaseMetricRepository(
      @Qualifier(DatabasePins.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      TableStorageRowMapper rowMapper) {
    this.jdbc = jdbc;
    this.rowMapper = rowMapper;
  }

  public DatabaseStorage storage() {
    var parameters = new MapSqlParameterSource();
    var total =
        jdbc.queryForObject(DatabaseMetricQuery.DATABASE_STORAGE.query(), parameters, Long.class);
    var tables = jdbc.query(DatabaseMetricQuery.TABLE_STORAGE.query(), parameters, rowMapper);
    return new DatabaseStorage(total, tables);
  }
}
