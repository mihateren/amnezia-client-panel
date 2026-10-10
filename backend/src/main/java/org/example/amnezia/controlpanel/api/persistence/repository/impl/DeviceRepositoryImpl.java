package org.example.amnezia.controlpanel.api.persistence.repository.impl;

import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.CREATED_AT;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.DEVICE_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.ICON;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.NAME;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.PRIVATE_KEY_ENCRYPTED;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.PUBLIC_KEY;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.USER_ID;
import static org.example.amnezia.controlpanel.api.persistence.query.QueryParameter.VPN_IP;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.mapper.DeviceRowMapper;
import org.example.amnezia.controlpanel.api.persistence.mapper.MetricPeerRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.MetricPeer;
import org.example.amnezia.controlpanel.api.persistence.query.DeviceQuery;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository("deviceRepository")
public class DeviceRepositoryImpl implements DeviceRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final DeviceRowMapper rowMapper;
  private final MetricPeerRowMapper metricPeerRowMapper;

  public DeviceRepositoryImpl(
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbcTemplate,
      DeviceRowMapper rowMapper,
      MetricPeerRowMapper metricPeerRowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
    this.metricPeerRowMapper = metricPeerRowMapper;
  }

  @Override
  public Optional<Device> find(UUID id) {
    return jdbcTemplate
        .query(
            DeviceQuery.FIND_BY_ID.query(),
            new MapSqlParameterSource(DEVICE_ID, id.toString()),
            rowMapper)
        .stream()
        .findFirst();
  }

  @Override
  public List<Device> byUser(UUID id) {
    return jdbcTemplate.query(
        DeviceQuery.FIND_BY_USER_ID.query(),
        new MapSqlParameterSource(USER_ID, id.toString()),
        rowMapper);
  }

  @Override
  public List<Device> all() {
    return jdbcTemplate.query(DeviceQuery.FIND_ALL.query(), new MapSqlParameterSource(), rowMapper);
  }

  @Override
  public List<MetricPeer> metricPeers() {
    return jdbcTemplate.query(
        DeviceQuery.FIND_METRIC_PEERS.query(), new MapSqlParameterSource(), metricPeerRowMapper);
  }

  @Override
  public long count(UUID userId) {
    return jdbcTemplate.queryForObject(
        DeviceQuery.COUNT_BY_USER_ID.query(),
        new MapSqlParameterSource(USER_ID, userId.toString()),
        Long.class);
  }

  @Override
  public long count() {
    return jdbcTemplate.queryForObject(
        DeviceQuery.COUNT.query(), new MapSqlParameterSource(), Long.class);
  }

  @Override
  public void insert(Device device) {
    var params =
        new MapSqlParameterSource(DEVICE_ID, device.id().toString())
            .addValue(USER_ID, device.userId().toString())
            .addValue(NAME, device.name())
            .addValue(ICON, device.icon().name())
            .addValue(VPN_IP, device.vpnIp())
            .addValue(PUBLIC_KEY, device.publicKey())
            .addValue(PRIVATE_KEY_ENCRYPTED, device.privateKeyEncrypted())
            .addValue(CREATED_AT, device.createdAt().toString())
            .addValue("generation", device.generation());
    jdbcTemplate.update(DeviceQuery.INSERT.query(), params);
  }

  @Override
  public void delete(UUID id) {
    var params = new MapSqlParameterSource(DEVICE_ID, id.toString());
    jdbcTemplate.update(DeviceQuery.DELETE_LINKS.query(), params);
    jdbcTemplate.update(DeviceQuery.DELETE.query(), params);
  }

  @Override
  public boolean updateMetadata(UUID id, String name, Device.Icon icon) {
    return jdbcTemplate.update(
            DeviceQuery.UPDATE_METADATA.query(),
            new MapSqlParameterSource(DEVICE_ID, id.toString())
                .addValue(NAME, name)
                .addValue(ICON, icon.name()))
        == 1;
  }
}
