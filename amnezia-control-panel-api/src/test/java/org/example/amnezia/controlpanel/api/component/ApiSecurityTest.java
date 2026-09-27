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
              org.example.amnezia.controlpanel.api.config.db.DatabasePins
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbc,
      PasswordEncoder passwords,
      JsonMapper json) {
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
    var created = request("POST", "/api/v1/devices", token, "{\"name\":\"api-laptop\"}", null);
    assertThat(created.statusCode()).isEqualTo(201);
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

  @BeforeEach
  void setup() {
    vpn.reset();
    jdbc.update("DELETE FROM config_links", new MapSqlParameterSource());
    jdbc.update("DELETE FROM devices", new MapSqlParameterSource());
    user = insert(User.Role.USER);
    admin = insert(User.Role.ADMIN);
    other = insert(User.Role.USER);
    nickname = users.find(user).orElseThrow().nickname();
    token = jwt.issue(user);
    adminToken = jwt.issue(admin);
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
}
