package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.JwtService;
import org.example.amnezia.controlpanel.api.service.AdminService;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

class ApiSecurityTest extends BaseComponentTest {
  @Autowired
  ApiSecurityTest(
      @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port,
      UserRepository users,
      DeviceRepository devices,
      DeviceService service,
      AdminService adminService,
      JwtService jwt,
      AppProperties properties,
      FakeVpnGateway vpn,
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabaseBeans
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbc,
      PasswordEncoder passwords,
      JsonMapper json,
      org.example.amnezia.controlpanel.api.service.ServerCatalog catalog) {
    this.catalog = catalog;
    this.port = port;
    this.users = users;
    this.devices = devices;
    this.service = service;
    this.adminService = adminService;
    this.jwt = jwt;
    this.properties = properties;
    this.vpn = vpn;
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.json = json;
  }

  @Test
  void shouldRestrictScrapeCredentialToMetricsEndpoint() throws Exception {
    assertThat(request("GET", "/actuator/prometheus", METRICS_TOKEN, null, null).statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/actuator/prometheus", null, null, null).statusCode())
        .isEqualTo(401);
    assertThat(
            request("GET", "/actuator/prometheus", METRICS_TOKEN + "bad", null, null).statusCode())
        .isEqualTo(401);
    assertThat(request("GET", "/api/v1/admin/users", METRICS_TOKEN, null, null).statusCode())
        .isEqualTo(401);
    assertThat(request("GET", "/api/v1/auth/me", METRICS_TOKEN, null, null).statusCode())
        .isEqualTo(401);
  }

  @Test
  void shouldDeliverGeneratedDeviceContractInEveryContentType() throws Exception {
    var created =
        request("POST", "/api/v1/devices", token, createBody("api-laptop", "LAPTOP"), null);
    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(json.readTree(created.body()).get("icon").asString()).isEqualTo("LAPTOP");
    var id = json.readTree(created.body()).get("id").asString();
    var path = "/api/v1/devices/" + id + "/config";
    var plain = request("GET", path + "?format=TEXT", token, null, null);
    assertThat(plain.statusCode()).isEqualTo(200);
    assertThat(plain.headers().firstValue("content-type").orElseThrow()).startsWith("text/plain");
    assertThat(plain.body()).startsWith("[Interface]");
    var wrapped = request("GET", path + "?format=JSON", token, null, null);
    assertThat(wrapped.statusCode()).isEqualTo(200);
    assertThat(json.readTree(wrapped.body()).get("config").asString()).isEqualTo(plain.body());
    assertThat(json.readTree(wrapped.body()).get("format").asString()).isEqualTo("JSON");
    var file = request("GET", path + "/file", token, null, null);
    assertThat(file.statusCode()).isEqualTo(200);
    assertThat(file.body()).isEqualTo(plain.body());
    assertThat(file.headers().firstValue("content-disposition").orElseThrow()).contains(".conf");
    var qr =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path + "/qr"))
                .header("Authorization", "Bearer " + token)
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(qr.statusCode()).isEqualTo(200);
    assertThat(qr.headers().firstValue("content-type")).contains("image/png");
    assertThat(qr.body()).startsWith((byte) 137, (byte) 80, (byte) 78, (byte) 71);
    var link = request("POST", path + "/link", token, "{\"expiresIn\":60}", null);
    assertThat(link.statusCode()).isEqualTo(200);
    assertThat(json.readTree(link.body()).get("url").asString()).contains("/api/v1/config/");
    assertThat(request("GET", path + "?format=INVALID", token, null, null).statusCode())
        .isEqualTo(400);
    assertThat(request("POST", "/api/v1/devices", token, "{\"name\":\"   \"}", null).statusCode())
        .isEqualTo(400);
  }

  @Test
  void shouldPersistDeviceNameAndIconWithoutChangingVpnKeysOrConfiguration() throws Exception {
    var created = request("POST", "/api/v1/devices", token, createBody("Phone", "PHONE"), null);
    assertThat(created.statusCode()).isEqualTo(201);
    var id = UUID.fromString(json.readTree(created.body()).get("id").asString());
    var before = devices.find(id).orElseThrow();
    assertThat(before.icon())
        .isEqualTo(org.example.amnezia.controlpanel.api.persistence.model.Device.Icon.PHONE);
    var originalVpn = vpn.config;
    var path = "/api/v1/devices/" + id;
    var originalClientConfig =
        request("GET", path + "/config?format=TEXT", token, null, null).body();
    var update =
        request("PATCH", path, token, "{\"name\":\"Домашний роутер\",\"icon\":\"ROUTER\"}", null);
    assertThat(update.statusCode()).isEqualTo(200);
    assertThat(json.readTree(update.body()).get("name").asString()).isEqualTo("Домашний роутер");
    assertThat(json.readTree(update.body()).get("icon").asString()).isEqualTo("ROUTER");
    var after = devices.find(id).orElseThrow();
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.userId()).isEqualTo(before.userId());
    assertThat(after.vpnIp()).isEqualTo(before.vpnIp());
    assertThat(after.publicKey().equals(before.publicKey())).isTrue();
    assertThat(after.privateKeyEncrypted().equals(before.privateKeyEncrypted())).isTrue();
    assertThat(after.createdAt()).isEqualTo(before.createdAt());
    assertThat(vpn.config.equals(originalVpn)).isTrue();
    assertThat(
            request("GET", path + "/config?format=TEXT", token, null, null)
                .body()
                .equals(originalClientConfig))
        .isTrue();
    assertThat(json.readTree(request("GET", path, token, null, null).body()).get("icon").asString())
        .isEqualTo("ROUTER");
    var listed =
        json.readTree(request("GET", "/api/v1/devices", token, null, null).body())
            .get("content")
            .get(0);
    assertThat(listed.get("name").asString()).isEqualTo("Домашний роутер");
    assertThat(listed.get("icon").asString()).isEqualTo("ROUTER");
    var adminView = request("GET", "/api/v1/admin/devices/" + id, adminToken, null, null);
    assertThat(json.readTree(adminView.body()).get("icon").asString()).isEqualTo("ROUTER");
    var audit =
        request("GET", "/api/v1/admin/audit?eventType=DEVICE_UPDATED", adminToken, null, null);
    assertThat(audit.statusCode()).isEqualTo(200);
    assertThat(json.readTree(audit.body()).get("content").toString()).contains(id.toString());
  }

  @Test
  void shouldRollBackDeviceMetadataWhenAuditCannotBePersisted() throws Exception {
    var device = service.create(user, "Original");
    var persisted = devices.find(device.id()).orElseThrow();
    var originalVpn = vpn.config;
    jdbc.update(TestQuery.BLOCK_DEVICE_UPDATE_AUDIT.query(), new MapSqlParameterSource());
    try {
      var response =
          request(
              "PATCH",
              "/api/v1/devices/" + device.id(),
              token,
              "{\"name\":\"Should roll back\",\"icon\":\"PHONE\"}",
              null);
      assertThat(response.statusCode()).isEqualTo(409);
      assertThat(devices.find(device.id())).contains(persisted);
      assertThat(vpn.config.equals(originalVpn)).isTrue();
    } finally {
      jdbc.update(TestQuery.UNBLOCK_DEVICE_UPDATE_AUDIT.query(), new MapSqlParameterSource());
    }
  }

  @Test
  void shouldProtectDeviceUpdatesAndAllowAdminMetadataChangesWhileVpnIsUnavailable()
      throws Exception {
    var device = service.create(user, "Original");
    var path = "/api/v1/devices/" + device.id();
    var body = "{\"name\":\"Updated\",\"icon\":\"TABLET\"}";
    assertThat(request("PATCH", path, null, body, null).statusCode()).isEqualTo(401);
    assertThat(request("PATCH", path, jwt.issue(other), body, null).statusCode()).isEqualTo(404);
    assertThat(
            request("PATCH", "/api/v1/devices/" + UUID.randomUUID(), token, body, null)
                .statusCode())
        .isEqualTo(404);
    assertThat(devices.find(device.id()).orElseThrow().name()).isEqualTo("Original");
    var originalVpn = vpn.config;
    vpn.failure = FakeVpnGateway.Failure.SSH;
    var adminUpdate = request("PATCH", path, adminToken, body, null);
    assertThat(adminUpdate.statusCode()).isEqualTo(200);
    assertThat(json.readTree(adminUpdate.body()).get("status").asString()).isEqualTo("UNKNOWN");
    assertThat(devices.find(device.id()).orElseThrow().name()).isEqualTo("Updated");
    assertThat(vpn.config.equals(originalVpn)).isTrue();
    users.status(user, User.Status.DISABLED, Instant.now());
    assertThat(request("PATCH", path, token, body, null).statusCode()).isEqualTo(401);
  }

  @Test
  void shouldRejectInvalidDeviceMetadataWithoutPersistingChanges() throws Exception {
    var device = service.create(user, "Original");
    var path = "/api/v1/devices/" + device.id();
    for (var name : List.of("", "   ", "a".repeat(101))) {
      var body = json.writeValueAsString(java.util.Map.of("name", name, "icon", "LAPTOP"));
      assertThat(request("PATCH", path, token, body, null).statusCode()).isEqualTo(400);
    }
    for (var body :
        List.of(
            "{\"name\":\"Updated\"}",
            "{\"name\":\"Updated\",\"icon\":null}",
            "{\"name\":\"Updated\",\"icon\":\"INVALID\"}",
            "{\"icon\":\"LAPTOP\"}")) {
      var invalid = request("PATCH", path, token, body, null);
      assertThat(invalid.statusCode()).isEqualTo(400);
      assertThat(json.readTree(invalid.body()).has("code")).isTrue();
    }
    assertThat(devices.find(device.id()).orElseThrow().name()).isEqualTo("Original");
    assertThat(devices.find(device.id()).orElseThrow().icon())
        .isEqualTo(org.example.amnezia.controlpanel.api.persistence.model.Device.Icon.LAPTOP);
    assertThat(
            request(
                    "POST",
                    "/api/v1/devices",
                    token,
                    "{\"name\":\"New\",\"icon\":\"INVALID\"}",
                    null)
                .statusCode())
        .isEqualTo(400);
  }

  private final int port;
  private final UserRepository users;
  private final DeviceRepository devices;
  private final DeviceService service;
  private final AdminService adminService;
  private final JwtService jwt;
  private final AppProperties properties;
  private final FakeVpnGateway vpn;
  private final NamedParameterJdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final JsonMapper json;
  private final HttpClient client = HttpClient.newHttpClient();
  private UUID user, admin, other;
  private String token, adminToken;
  private String nickname;

  @org.junit.jupiter.params.ParameterizedTest(name = "{index}")
  @org.junit.jupiter.params.provider.CsvSource({"ab,valid-password", "valid,short"})
  void shouldValidateNicknameAndPassword(String name, String password) throws Exception {
    var response =
        request(
            "POST",
            "/api/v1/auth/login",
            null,
            json.writeValueAsString(new AuthDtos.Credentials(name, password)),
            null);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).doesNotContain(password);
  }

  @Test
  void shouldExposeDatabaseSummaryWhenVpnIsUnavailableAndRestrictItToAdmins() throws Exception {
    vpn.failure = FakeVpnGateway.Failure.SSH;
    var response = request("GET", "/api/v1/admin/dashboard/summary", adminToken, null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    var body = json.readTree(response.body());
    assertThat(body.get("usersTotal").asLong()).isEqualTo(users.count());
    assertThat(body.get("devicesTotal").asLong()).isEqualTo(0);
    assertThat(request("GET", "/api/v1/admin/dashboard/summary", token, null, null).statusCode())
        .isEqualTo(403);
    assertThat(request("GET", "/api/v1/admin/dashboard/summary", null, null, null).statusCode())
        .isEqualTo(401);
  }

  private final org.example.amnezia.controlpanel.api.service.ServerCatalog catalog;
  private UUID serverId;

  @BeforeEach
  void setup() {
    vpn.reset();
    jdbc.update(TestQuery.DELETE_CONFIG_LINKS.query(), new MapSqlParameterSource());
    jdbc.update(TestQuery.DELETE_DEVICES.query(), new MapSqlParameterSource());
    user = insert(User.Role.USER);
    admin = insert(User.Role.ADMIN);
    other = insert(User.Role.USER);
    nickname = users.find(user).orElseThrow().nickname();
    token = jwt.issue(user);
    adminToken = jwt.issue(admin);
    serverId =
        catalog
            .create(
                admin,
                new org.example.amnezia.controlpanel.api.dto.ManagedServerDtos.Create(
                    "API fixture",
                    "2001:db8:" + Integer.toHexString(UUID.randomUUID().hashCode() & 65535) + "::1",
                    22,
                    "root",
                    "fixture-password"))
            .id();
    vpn.servers.put(serverId, vpn);
  }

  private String createBody(String name, String icon) {
    return json.writeValueAsString(
        java.util.Map.of(
            "name", name, "icon", icon, "serverId", serverId, "clientType", "AMNEZIA_WG"));
  }

  @Test
  void shouldLoginAgainWithStaleBearerAndAfterLogout() throws Exception {
    var body = json.writeValueAsString(new AuthDtos.Credentials(nickname, "strong-test-password"));
    var login = request("POST", "/api/v1/auth/login", "expired-or-invalid", body, null);
    assertThat(login.statusCode()).isEqualTo(200);
    var access = json.readTree(login.body()).get("accessToken").asString();
    var cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    assertThat(request("POST", "/api/v1/auth/logout", access, null, cookie).statusCode())
        .isEqualTo(204);
    assertThat(
            request("POST", "/api/v1/auth/login", "expired-or-invalid", body, cookie).statusCode())
        .isEqualTo(200);
  }

  private UUID insert(User.Role role) {
    var id = UUID.randomUUID();
    var now = Instant.now();
    users.insert(
        new User(
            id,
            "internal-" + id,
            "name-" + id,
            passwords.encode("strong-test-password"),
            role,
            User.Status.ACTIVE,
            now,
            now,
            null));
    return id;
  }

  private HttpResponse<String> request(
      String method, String path, String auth, String body, String cookie) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Correlation-ID", "test-correlation")
            .header("Content-Type", "application/json");
    if (auth != null) builder.header("Authorization", "Bearer " + auth);
    if (cookie != null) builder.header("Cookie", cookie);
    return client.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void shouldEnforceAuthenticationRoleOwnershipAndDisabledStatus() throws Exception {
    assertThat(request("GET", "/api/v1/auth/me", null, null, null).statusCode()).isEqualTo(401);
    assertThat(request("GET", "/api/v1/admin/users", token, null, null).statusCode())
        .isEqualTo(403);
    var allowed = request("GET", "/api/v1/admin/users", adminToken, null, null);
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(json.readTree(allowed.body()).has("totalPages")).isTrue();
    var own = service.create(user, "Own");
    var foreign = service.create(other, "Foreign");
    assertThat(request("GET", "/api/v1/devices/" + own.id(), token, null, null).statusCode())
        .isEqualTo(200);
    for (var suffix : List.of("", "/config?format=TEXT", "/config/file", "/config/qr")) {
      assertThat(
              request("GET", "/api/v1/devices/" + foreign.id() + suffix, token, null, null)
                  .statusCode())
          .isEqualTo(404);
    }
    assertThat(request("DELETE", "/api/v1/devices/" + foreign.id(), token, null, null).statusCode())
        .isEqualTo(404);
    assertThat(request("GET", "/api/v1/auth/me", "invalid", null, null).statusCode())
        .isEqualTo(401);
    var expired =
        new JwtService(properties, Clock.offset(Clock.systemUTC(), Duration.ofDays(-1)))
            .issue(user);
    assertThat(request("GET", "/api/v1/auth/me", expired, null, null).statusCode()).isEqualTo(401);
    users.status(user, User.Status.DISABLED, Instant.now());
    assertThat(request("GET", "/api/v1/auth/me", token, null, null).statusCode()).isEqualTo(401);
  }

  @Test
  void shouldMatchApiShapesCookiesAndErrorContract() throws Exception {
    var login =
        request(
            "POST",
            "/api/v1/auth/login",
            null,
            json.writeValueAsString(new AuthDtos.Credentials(nickname, "strong-test-password")),
            null);
    assertThat(login.statusCode()).isEqualTo(200);
    var cookie = login.headers().firstValue("set-cookie").orElseThrow();
    assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
    assertThat(login.body()).doesNotContain("refreshToken", "password", "internalName");
    String cookieValue = cookie.split(";")[0];
    var refresh = request("POST", "/api/v1/auth/refresh", null, null, cookieValue);
    assertThat(refresh.statusCode()).isEqualTo(200);
    assertThat(request("POST", "/api/v1/auth/refresh", null, null, cookieValue).statusCode())
        .isEqualTo(401);
    var currentCookie = refresh.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    assertThat(request("POST", "/api/v1/auth/logout", token, null, currentCookie).statusCode())
        .isEqualTo(204);
    assertThat(request("POST", "/api/v1/auth/refresh", null, null, currentCookie).statusCode())
        .isEqualTo(401);
    var invalid = request("POST", "/api/v1/devices", token, "{\"name\":\"\"}", null);
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(json.readTree(invalid.body()).get("correlationId").asText())
        .isEqualTo("test-correlation");
    assertThat(invalid.headers().firstValue("X-Correlation-ID")).contains("test-correlation");
    assertThat(request("GET", "/api/v1/admin/users?size=101", adminToken, null, null).statusCode())
        .isEqualTo(400);
    assertThat(request("GET", "/api/v1/devices/not-a-uuid", token, null, null).statusCode())
        .isEqualTo(400);
    assertThat(request("GET", "/api/v1/auth/me", token, null, null).body())
        .doesNotContain("internalName", "passwordHash");
  }

  @Test
  void shouldExposeFlattenedAdminDtoAndPaginatedAudit() throws Exception {
    var device = service.create(user, "Phone");
    var details = request("GET", "/api/v1/admin/users/" + user, adminToken, null, null);
    assertThat(details.statusCode()).isEqualTo(200);
    assertThat(json.readTree(details.body()).get("id").asText()).isEqualTo(user.toString());
    assertThat(json.readTree(details.body()).get("devices").size()).isEqualTo(1);
    var response = request("GET", "/api/v1/admin/devices/" + device.id(), adminToken, null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    var root = json.readTree(response.body());
    assertThat(root.get("id").asText()).isEqualTo(device.id().toString());
    assertThat(root.get("owner").get("id").asText()).isEqualTo(user.toString());
    var audit =
        request(
            "GET",
            "/api/v1/admin/audit?size=2&eventType=DEVICE_CREATED&userId=" + user,
            adminToken,
            null,
            null);
    assertThat(audit.statusCode()).isEqualTo(200);
    assertThat(json.readTree(audit.body()).get("content").size()).isEqualTo(1);
    assertThat(request("GET", "/api/v1/admin/dashboard", adminToken, null, null).statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/actuator/health", null, null, null).statusCode()).isEqualTo(200);
    assertThat(request("GET", "/actuator/prometheus", null, null, null).statusCode())
        .isEqualTo(401);
    var metrics = request("GET", "/actuator/prometheus", adminToken, null, null);
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body())
        .contains("vpn_server_up", "vpn_peers_total", "vpn_peer_rx_bytes", "http_server_requests");
  }

  @Test
  void shouldDisableVpnAccessAndKeepStateOnFailure() {
    var device = service.create(user, "Phone");
    vpn.failure = FakeVpnGateway.Failure.APPLY;
    assertThatThrownBy(() -> adminService.status(admin, user, User.Status.DISABLED))
        .isInstanceOf(RuntimeException.class);
    assertThat(users.find(user).orElseThrow().status()).isEqualTo(User.Status.ACTIVE);
    assertThat(devices.find(device.id())).isPresent();
    vpn.failure = FakeVpnGateway.Failure.NONE;
    adminService.status(admin, user, User.Status.DISABLED);
    assertThat(vpn.config).doesNotContain(device.publicKey());
    assertThat(devices.byUser(user)).isEmpty();
    assertThat(users.find(user).orElseThrow().status()).isEqualTo(User.Status.DISABLED);
    adminService.deleteUser(admin, user);
    assertThat(users.find(user)).isEmpty();
  }

  @Test
  void shouldRejectCrossOriginCookieRequests() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/refresh"))
            .header("Origin", "https://untrusted.example")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(403);
  }

  @Test
  void shouldRenameCurrentUserAndRejectCollisionsWithoutChangingOtherAccounts() throws Exception {
    var before = users.find(user).orElseThrow();
    var changed = "renamed-" + user;
    var path = "/api/v1/auth/me/nickname";
    var body = json.writeValueAsString(new AuthDtos.UpdateNickname(changed));
    assertThat(request("PATCH", path, null, body, null).statusCode()).isEqualTo(401);
    assertThat(request("PATCH", path, token, "{\"nickname\":\"  \"}", null).statusCode())
        .isEqualTo(400);
    var result = request("PATCH", path, token, body, null);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(json.readTree(result.body()).get("nickname").asString()).isEqualTo(changed);
    var after = users.find(user).orElseThrow();
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.internalName()).isEqualTo(before.internalName());
    assertThat(after.passwordHash().equals(before.passwordHash())).isTrue();
    var collision = users.find(other).orElseThrow().nickname();
    assertThat(
            request(
                    "PATCH",
                    path,
                    token,
                    json.writeValueAsString(new AuthDtos.UpdateNickname(collision)),
                    null)
                .statusCode())
        .isEqualTo(409);
    assertThat(users.find(user).orElseThrow().nickname()).isEqualTo(changed);
    assertThat(users.find(other).orElseThrow().nickname()).isEqualTo(collision);
    assertThat(
            request(
                    "POST",
                    "/api/v1/auth/login",
                    null,
                    json.writeValueAsString(
                        new AuthDtos.Credentials(before.nickname(), "strong-test-password")),
                    null)
                .statusCode())
        .isEqualTo(401);
    assertThat(
            request(
                    "POST",
                    "/api/v1/auth/login",
                    null,
                    json.writeValueAsString(
                        new AuthDtos.Credentials(changed, "strong-test-password")),
                    null)
                .statusCode())
        .isEqualTo(200);
    users.status(user, User.Status.DISABLED, Instant.now());
    assertThat(request("PATCH", path, token, body, null).statusCode()).isEqualTo(401);
  }

  @Test
  void shouldChangePasswordAndRotateAllRefreshSessions() throws Exception {
    var credentials =
        json.writeValueAsString(new AuthDtos.Credentials(nickname, "strong-test-password"));
    var firstLogin = request("POST", "/api/v1/auth/login", null, credentials, null);
    var secondLogin = request("POST", "/api/v1/auth/login", null, credentials, null);
    var firstCookie = firstLogin.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    var secondCookie = secondLogin.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    var originalHash = users.find(user).orElseThrow().passwordHash();
    var path = "/api/v1/auth/me/password";
    var rejected =
        request(
            "PATCH",
            path,
            token,
            json.writeValueAsString(
                new AuthDtos.ChangePassword("wrong-test-password", "new-strong-password")),
            firstCookie);
    assertThat(rejected.statusCode()).isEqualTo(400);
    assertThat(json.readTree(rejected.body()).get("code").asString())
        .isEqualTo("CURRENT_PASSWORD_INVALID");
    assertThat(users.find(user).orElseThrow().passwordHash().equals(originalHash)).isTrue();
    assertThat(request("GET", "/api/v1/auth/me", token, null, null).statusCode()).isEqualTo(200);
    var otherRefresh = request("POST", "/api/v1/auth/refresh", null, null, secondCookie);
    assertThat(otherRefresh.statusCode()).isEqualTo(200);
    var otherFreshCookie =
        otherRefresh.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    var valid =
        json.writeValueAsString(
            new AuthDtos.ChangePassword("strong-test-password", "new-strong-password"));
    assertThat(request("PATCH", path, null, valid, null).statusCode()).isEqualTo(401);
    assertThat(
            request(
                    "PATCH",
                    path,
                    token,
                    "{\"currentPassword\":\"strong-test-password\",\"newPassword\":\"short\"}",
                    null)
                .statusCode())
        .isEqualTo(400);
    var result = request("PATCH", path, token, valid, firstCookie);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.body()).doesNotContain("password", "refreshToken");
    var cookie = result.headers().firstValue("set-cookie").orElseThrow();
    assertThat(cookie).contains("HttpOnly", "SameSite=Strict");
    assertThat(request("POST", "/api/v1/auth/refresh", null, null, firstCookie).statusCode())
        .isEqualTo(401);
    assertThat(request("POST", "/api/v1/auth/refresh", null, null, otherFreshCookie).statusCode())
        .isEqualTo(401);
    assertThat(
            request("POST", "/api/v1/auth/refresh", null, null, cookie.split(";")[0]).statusCode())
        .isEqualTo(200);
    assertThat(
            passwords.matches("new-strong-password", users.find(user).orElseThrow().passwordHash()))
        .isTrue();
    assertThat(request("POST", "/api/v1/auth/login", null, credentials, null).statusCode())
        .isEqualTo(401);
    assertThat(
            request(
                    "POST",
                    "/api/v1/auth/login",
                    null,
                    json.writeValueAsString(
                        new AuthDtos.Credentials(nickname, "new-strong-password")),
                    null)
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void shouldPersistTvIconOnCreateAndUpdate() throws Exception {
    var response = request("POST", "/api/v1/devices", token, createBody("TV", "TV"), null);
    assertThat(response.statusCode()).isEqualTo(201);
    var id = json.readTree(response.body()).get("id").asString();
    assertThat(json.readTree(response.body()).get("icon").asString()).isEqualTo("TV");
    var result =
        request(
            "PATCH",
            "/api/v1/devices/" + id,
            token,
            "{\"name\":\"Living room TV\",\"icon\":\"TV\"}",
            null);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(devices.find(UUID.fromString(id)).orElseThrow().icon())
        .isEqualTo(org.example.amnezia.controlpanel.api.persistence.model.Device.Icon.TV);
  }

  @Test
  void shouldRollBackProfileChangesAndSessionRevocationWhenAuditFails() throws Exception {
    var credentials =
        json.writeValueAsString(new AuthDtos.Credentials(nickname, "strong-test-password"));
    var login = request("POST", "/api/v1/auth/login", null, credentials, null);
    var cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
    var originalHash = users.find(user).orElseThrow().passwordHash();
    jdbc.update(TestQuery.BLOCK_PROFILE_AUDIT.query(), new MapSqlParameterSource());
    try {
      assertThat(
              request(
                      "PATCH",
                      "/api/v1/auth/me/nickname",
                      token,
                      json.writeValueAsString(new AuthDtos.UpdateNickname("rollback-" + user)),
                      null)
                  .statusCode())
          .isEqualTo(409);
      assertThat(users.find(user).orElseThrow().nickname()).isEqualTo(nickname);
      var body =
          json.writeValueAsString(
              new AuthDtos.ChangePassword("strong-test-password", "new-strong-password"));
      assertThat(request("PATCH", "/api/v1/auth/me/password", token, body, cookie).statusCode())
          .isEqualTo(409);
      assertThat(users.find(user).orElseThrow().passwordHash().equals(originalHash)).isTrue();
      assertThat(request("POST", "/api/v1/auth/refresh", null, null, cookie).statusCode())
          .isEqualTo(200);
    } finally {
      jdbc.update(TestQuery.UNBLOCK_PROFILE_AUDIT.query(), new MapSqlParameterSource());
    }
  }
}
