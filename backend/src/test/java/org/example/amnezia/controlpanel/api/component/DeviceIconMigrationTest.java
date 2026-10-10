package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.example.amnezia.controlpanel.api.persistence.mapper.DeviceRowMapper;
import org.example.amnezia.controlpanel.api.persistence.mapper.MetricPeerRowMapper;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.repository.impl.DeviceRepositoryImpl;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DeviceIconMigrationTest {
  @TempDir Path temporary;

  @Test
  void shouldUpgradeExistingDeviceWithoutChangingIdentityOrKeys() throws Exception {
    var datasource = new DriverManagerDataSource("jdbc:sqlite:" + temporary.resolve("legacy.db"));
    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(datasource);
    liquibase.setChangeLog("classpath:db-migrations/pre-device-icon.xml");
    liquibase.afterPropertiesSet();
    var jdbc = new NamedParameterJdbcTemplate(datasource);
    var device =
        new Device(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Existing phone",
            "10.8.1.2",
            "test-public-key",
            "test-encrypted-key",
            Instant.parse("2026-01-02T03:04:05Z"));
    jdbc.update(
        TestQuery.INSERT_LEGACY_DEVICE.query(),
        new MapSqlParameterSource("id", device.id().toString())
            .addValue("userId", device.userId().toString())
            .addValue("name", device.name())
            .addValue("vpnIp", device.vpnIp())
            .addValue("publicKey", device.publicKey())
            .addValue("privateKeyEncrypted", device.privateKeyEncrypted())
            .addValue("createdAt", device.createdAt().toString()));
    liquibase.setChangeLog("classpath:db-migrations/pre-device-tv.xml");
    liquibase.afterPropertiesSet();
    var repositoryBefore =
        new DeviceRepositoryImpl(jdbc, new DeviceRowMapper(), new MetricPeerRowMapper());
    assertThat(
            jdbc.queryForObject(
                TestQuery.FIND_LEGACY_ICON.query(),
                new MapSqlParameterSource("deviceId", device.id().toString()),
                String.class))
        .isEqualTo("LAPTOP");
    repositoryBefore.updateMetadata(device.id(), device.name(), Device.Icon.PHONE);
    liquibase.setChangeLog("classpath:db-migrations/changelog.xml");
    liquibase.afterPropertiesSet();
    var repository =
        new DeviceRepositoryImpl(jdbc, new DeviceRowMapper(), new MetricPeerRowMapper());
    var preserved = repository.find(device.id()).orElseThrow();
    assertThat(preserved.generation()).isEqualTo(1);
    assertThat(preserved.id()).isEqualTo(device.id());
    assertThat(preserved.userId()).isEqualTo(device.userId());
    assertThat(preserved.name()).isEqualTo(device.name());
    assertThat(preserved.vpnIp()).isEqualTo(device.vpnIp());
    assertThat(preserved.createdAt()).isEqualTo(device.createdAt());
    assertThat(preserved.publicKey().equals(device.publicKey())).isTrue();
    assertThat(preserved.privateKeyEncrypted().equals(device.privateKeyEncrypted())).isTrue();
    assertThat(repository.find(device.id()).orElseThrow().icon()).isEqualTo(Device.Icon.PHONE);
    repository.updateMetadata(device.id(), device.name(), Device.Icon.TV);
    liquibase.afterPropertiesSet();
    assertThat(repository.find(device.id()).orElseThrow().icon()).isEqualTo(Device.Icon.TV);
  }
}
