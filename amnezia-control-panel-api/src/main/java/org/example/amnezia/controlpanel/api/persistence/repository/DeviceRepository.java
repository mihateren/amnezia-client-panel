package org.example.amnezia.controlpanel.api.persistence.repository;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.DEVICE_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NAME;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.PRIVATE_KEY_ENCRYPTED;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.PUBLIC_KEY;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.USER_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.VPN_IP;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabasePins;
import org.example.amnezia.controlpanel.api.persistence.mapper.DeviceRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.query.DeviceQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final DeviceRowMapper rowMapper;

  public DeviceRepository(
      @Qualifier(DatabasePins.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      DeviceRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  public Optional<Device> find(UUID id) {
    return jdbcTemplate
        .query(
            DeviceQuery.FIND_BY_ID.query(),
            new MapSqlParameterSource(DEVICE_ID, id.toString()),
            rowMapper)
        .stream()
        .findFirst();
  }

  public List<Device> byUser(UUID id) {
    return jdbcTemplate.query(
        DeviceQuery.FIND_BY_USER_ID.query(),
        new MapSqlParameterSource(USER_ID, id.toString()),
        rowMapper);
  }

  public List<Device> all() {
    return jdbcTemplate.query(DeviceQuery.FIND_ALL.query(), new MapSqlParameterSource(), rowMapper);
  }

  public long count(UUID userId) {
    return jdbcTemplate.queryForObject(
        DeviceQuery.COUNT_BY_USER_ID.query(),
        new MapSqlParameterSource(USER_ID, userId.toString()),
        Long.class);
  }

  public long count() {
    return jdbcTemplate.queryForObject(
        DeviceQuery.COUNT.query(), new MapSqlParameterSource(), Long.class);
  }

  public void insert(Device device) {
    var params =
        new MapSqlParameterSource(DEVICE_ID, device.id().toString())
            .addValue(USER_ID, device.userId().toString())
            .addValue(NAME, device.name())
            .addValue(VPN_IP, device.vpnIp())
            .addValue(PUBLIC_KEY, device.publicKey())
            .addValue(PRIVATE_KEY_ENCRYPTED, device.privateKeyEncrypted())
            .addValue(CREATED_AT, device.createdAt().toString());
    jdbcTemplate.update(DeviceQuery.INSERT.query(), params);
  }

  public void delete(UUID id) {
    var params = new MapSqlParameterSource(DEVICE_ID, id.toString());
    jdbcTemplate.update(DeviceQuery.DELETE_LINKS.query(), params);
    jdbcTemplate.update(DeviceQuery.DELETE.query(), params);
  }
}
