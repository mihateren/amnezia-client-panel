package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.dto.VpnServerDtos;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.model.VpnMigration;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnMigrationRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.VpnServerRepository;
import org.example.amnezia.controlpanel.api.security.JwtService;
import org.example.amnezia.controlpanel.api.service.AdminService;
import org.example.amnezia.controlpanel.api.service.ConfigService;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.example.amnezia.controlpanel.api.service.VpnMigrationService;
import org.example.amnezia.controlpanel.api.service.VpnServerSettings;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class VpnMigrationTest extends BaseComponentTest {
  private final VpnServerSettings servers;
  private final VpnServerRepository serverRepository;
  private final VpnMigrationService migration;
  private final VpnMigrationRepository states;
  private final DeviceService devices;
  private final DeviceRepository repository;
  private final ConfigService configs;
  private final AdminService admins;
  private final UserRepository users;
  private final FakeVpnGateway vpn;
  private final NamedParameterJdbcTemplate jdbc;
  private final JwtService jwt;
  private final int port;
  private UUID user;
  private UUID admin;

  @Autowired
  VpnMigrationTest(
      VpnServerSettings servers,
      VpnServerRepository serverRepository,
      VpnMigrationService migration,
      VpnMigrationRepository states,
      DeviceService devices,
      DeviceRepository repository,
      ConfigService configs,
      AdminService admins,
      UserRepository users,
      FakeVpnGateway vpn,
      @Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN) NamedParameterJdbcTemplate jdbc,
      JwtService jwt,
      @Value("${local.server.port}") int port) {
    this.servers = servers;
    this.serverRepository = serverRepository;
    this.migration = migration;
    this.states = states;
    this.devices = devices;
    this.repository = repository;
    this.configs = configs;
    this.admins = admins;
    this.users = users;
    this.vpn = vpn;
    this.jdbc = jdbc;
    this.jwt = jwt;
    this.port = port;
  }

  @BeforeEach
  void setup() {
    cleanup();
    user = createUser(User.Role.USER);
    admin = createUser(User.Role.ADMIN);
  }

  @AfterEach
  void cleanup() {
    vpn.reset();
    jdbc.update(TestQuery.DELETE_CONFIG_LINKS.query(), new MapSqlParameterSource());
    jdbc.update(TestQuery.DELETE_DEVICES.query(), new MapSqlParameterSource());
    states.update(new VpnMigration(1, false));
    jdbc.update(TestQuery.DELETE_VPN_SERVER_SETTINGS.query(), new MapSqlParameterSource());
  }

  private static final String TEST_PRIVATE_KEY =
      "-----BEGIN OPENSSH PRIVATE KEY-----\nfixture-only\n-----END OPENSSH PRIVATE KEY-----\n";
  private static final String TEST_HOST_KEY =
      "ssh-ed25519 " + Base64.getEncoder().encodeToString(new byte[32]);

  private void saveServer(String ip) {
    servers.save(
        admin, new VpnServerDtos.Update(ip, 22, "root", TEST_PRIVATE_KEY, TEST_HOST_KEY, 51820));
  }

  @Test
  void shouldKeepGenerationAndConfigsWhenWorksEndOnSameIp() {
    var device = devices.create(user, "existing");
    var original = configs.config(user, device.id(), false);
    migration.change(admin, 1, true);
    migration.change(admin, 1, false);
    assertThat(states.find().generation()).isEqualTo(1);
    assertThat(migration.status(user).outdatedDevices()).isZero();
    assertThat(original.equals(configs.config(user, device.id(), false))).isTrue();
    assertThat(servers.get(admin).toString()).doesNotContain(TEST_PRIVATE_KEY);
  }

  @Test
  void shouldEncryptSettingsBlockAllDeletesAndPreserveVersionOnSameSavedIp() {
    assertThatThrownBy(() -> saveServer("192.0.2.10")).hasMessageContaining("начните");
    var old = devices.create(user, "old");
    migration.change(admin, 1, true);
    saveServer("192.0.2.10");
    var stored = serverRepository.find().orElseThrow();
    assertThat(stored.privateKeyEncrypted()).startsWith("v1:").doesNotContain("fixture-only");
    assertThat(servers.privateKey(stored)).isEqualTo(TEST_PRIVATE_KEY);
    assertThat(servers.endpoint()).isEqualTo("192.0.2.10:51820");
    vpn.reset();
    migration.change(admin, 1, false);
    var current = devices.create(user, "current");
    migration.change(admin, 2, true);
    assertThatThrownBy(() -> devices.delete(user, old.id())).hasMessageContaining("перенос");
    assertThatThrownBy(() -> devices.delete(user, current.id())).hasMessageContaining("перенос");
    servers.save(admin, new VpnServerDtos.Update("192.000.002.010", 22, "root", "", "", 51820));
    assertThat(serverRepository.find().orElseThrow().privateKeyEncrypted())
        .isEqualTo(stored.privateKeyEncrypted());
    saveServer("192.0.2.11");
    saveServer("192.0.2.10");
    migration.change(admin, 2, false);
    assertThat(states.find().generation()).isEqualTo(2);
    assertThat(migration.status(user).outdatedDevices()).isEqualTo(1);
    assertThat(configs.config(user, current.id(), false)).contains("Endpoint = 192.0.2.10:51820");
    devices.delete(user, old.id());
    assertThat(migration.status(user).outdatedDevices()).isZero();
  }

  @Test
  void shouldRejectUntrustedSettingsAndCompareCanonicalIpv6() {
    migration.change(admin, 1, true);
    assertThatThrownBy(
            () ->
                servers.save(
                    user,
                    new VpnServerDtos.Update(
                        "192.0.2.10", 22, "root", TEST_PRIVATE_KEY, TEST_HOST_KEY, 51820)))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> saveServer("example.com")).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                servers.save(
                    admin,
                    new VpnServerDtos.Update("192.0.2.10", 22, "root", "", TEST_HOST_KEY, 51820)))
        .hasMessageContaining("SSH-ключ");
    assertThatThrownBy(
            () ->
                servers.save(
                    admin,
                    new VpnServerDtos.Update(
                        "192.0.2.10", 22, "root", TEST_PRIVATE_KEY, "", 51820)))
        .hasMessageContaining("публичный");
    assertThat(VpnServerSettings.sameIp("2001:db8::1", "2001:0db8:0:0:0:0:0:1")).isTrue();
  }

  private UUID createUser(User.Role role) {
    var id = UUID.randomUUID();
    var now = Instant.now();
    users.insert(
        new User(
            id,
            "internal-" + id,
            "nick-" + id,
            "unused",
            role,
            User.Status.ACTIVE,
            now,
            now,
            null));
    return id;
  }

  @Test
  void shouldKeepBannerUntilEveryOldDeviceIsDeletedAcrossRepeatedMigrations() {
    var old = new ArrayList<DeviceResponse>();
    for (int i = 0; i < 5; i++) old.add(devices.create(user, "old-" + i));
    var link = configs.link(user, old.getFirst().id(), 3600).url();
    var token = link.substring(link.lastIndexOf('/') + 1);
    migration.change(admin, 1, true);
    assertThat(states.find().maintenance()).isTrue();
    assertThatThrownBy(() -> devices.create(user, "blocked")).hasMessageContaining("перенос");
    assertThatThrownBy(() -> configs.config(user, old.getFirst().id(), false))
        .hasMessageContaining("перенос");
    assertThatThrownBy(() -> configs.link(user, old.getFirst().id(), 3600))
        .hasMessageContaining("перенос");
    assertThatThrownBy(() -> configs.importLink(token)).hasMessageContaining("перенос");
    assertThatThrownBy(() -> devices.delete(user, old.getFirst().id()))
        .hasMessageContaining("перенос");
    vpn.reset(); // Admin points the gateway at a newly configured server.
    saveServer("192.0.2.10");
    migration.change(admin, 1, false);
    assertThat(migration.status(user).outdatedDevices()).isEqualTo(5);
    assertThat(devices.get(user, old.getFirst().id()).status())
        .isEqualTo(DeviceResponse.Status.OUTDATED);
    assertThatThrownBy(() -> configs.config(user, old.getFirst().id(), true))
        .hasMessageContaining("устарела");
    assertThatThrownBy(() -> configs.qr(user, old.getFirst().id()))
        .hasMessageContaining("устарела");
    assertThatThrownBy(() -> configs.link(user, old.getFirst().id(), 3600))
        .hasMessageContaining("устарела");
    assertThatThrownBy(() -> configs.importLink(token)).hasMessageContaining("устарела");
    var first = devices.create(user, "new");
    assertThat(first.vpnIp()).isEqualTo(old.getFirst().vpnIp());
    assertThat(first.generation()).isEqualTo(2);
    assertThat(migration.status(user).outdatedDevices()).isEqualTo(5);
    for (int i = 0; i < 4; i++) devices.create(user, "new-" + i);
    assertThatThrownBy(() -> devices.create(user, "over-limit")).hasMessageContaining("limit");
    vpn.failure = FakeVpnGateway.Failure.SSH;
    for (int i = 0; i < 5; i++) {
      devices.delete(user, old.get(i).id());
      assertThat(migration.status(user).outdatedDevices()).isEqualTo(4 - i);
    }
    vpn.reset();
    migration.change(admin, 2, true);
    saveServer("192.0.2.11");
    migration.change(admin, 2, false);
    assertThat(migration.status(user).outdatedDevices()).isEqualTo(5);
    var fresh = createUser(User.Role.USER);
    assertThat(migration.status(fresh).outdatedDevices()).isZero();
  }

  @Test
  void shouldRetainMaintenanceOnFailureAndRejectRepeatedOrUnauthorizedTransitions() {
    assertThatThrownBy(() -> migration.change(user, 1, true)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> migration.change(admin, 1, false)).hasMessageContaining("изменилось");
    migration.change(admin, 1, true);
    assertThatThrownBy(() -> migration.change(admin, 1, true)).hasMessageContaining("изменилось");
    vpn.failure = FakeVpnGateway.Failure.SSH;
    assertThatThrownBy(() -> migration.change(admin, 1, false))
        .isInstanceOf(RuntimeException.class);
    assertThat(states.find().maintenance()).isTrue();
    vpn.reset();
    saveServer("192.0.2.10");
    migration.change(admin, 1, false);
    assertThatThrownBy(() -> migration.change(admin, 1, false)).hasMessageContaining("изменилось");
    assertThatThrownBy(() -> migration.change(admin, 1, true)).hasMessageContaining("изменилось");
    assertThat(states.find()).isEqualTo(new VpnMigration(2, false));
  }

  @Test
  void shouldDeleteUserWithOnlyOldDevicesWithoutContactingEitherServer() {
    var device = devices.create(user, "old");
    migration.change(admin, 1, true);
    vpn.reset();
    saveServer("192.0.2.10");
    migration.change(admin, 1, false);
    vpn.failure = FakeVpnGateway.Failure.SSH;
    admins.deleteUser(admin, user);
    assertThat(users.find(user)).isEmpty();
    assertThat(repository.find(device.id())).isEmpty();
  }

  @Test
  void shouldExposeMigrationThroughAuthenticatedApiAndEnforceAdminRole() throws Exception {
    var client = HttpClient.newHttpClient();
    var base = "http://localhost:" + port + "/api/v1";
    var userToken = jwt.issue(user);
    var adminToken = jwt.issue(admin);
    var status =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/vpn/migration"))
                .header("Authorization", "Bearer " + userToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(status.statusCode()).isEqualTo(200);
    assertThat(status.body()).contains("\"outdatedDevices\":0", "\"maintenance\":false");
    var denied =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/vpn/migration/start"))
                .header("Authorization", "Bearer " + userToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"generation\":1}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(denied.statusCode()).isEqualTo(403);
    var started =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "/admin/vpn/migration/start"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"generation\":1}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(started.statusCode()).isEqualTo(200);
    assertThat(started.body()).contains("\"maintenance\":true");
    var settingsUri = URI.create(base + "/admin/vpn/server");
    var forbidden =
        client.send(
            HttpRequest.newBuilder(settingsUri)
                .header("Authorization", "Bearer " + userToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(forbidden.statusCode()).isEqualTo(403);
    String body =
        "{\"ip\":\"192.0.2.10\",\"sshPort\":22,\"username\":\"root\",\"vpnPort\":51820,\"hostKey\":\""
            + TEST_HOST_KEY
            + "\",\"privateKey\":\""
            + TEST_PRIVATE_KEY.replace("\n", "\\n")
            + "\"}";
    var saved =
        client.send(
            HttpRequest.newBuilder(settingsUri)
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(saved.body())
        .contains("192.0.2.10")
        .doesNotContain("privateKey", "fixture-only", "PRIVATE KEY");
    var read =
        client.send(
            HttpRequest.newBuilder(settingsUri)
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(read.body()).doesNotContain("privateKey", "fixture-only", "PRIVATE KEY");
  }
}
