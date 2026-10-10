package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway;
import org.example.amnezia.controlpanel.api.vpn.VpnMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.vpn.client-signature=<r 64>", "app.vpn.poll-delay=3600000"})
class DeviceLifecycleTest extends BaseComponentTest {
  @Autowired
  DeviceLifecycleTest(
      FakeVpnGateway vpn,
      DeviceService service,
      DeviceRepository devices,
      UserRepository users,
      VpnMonitor monitor,
      MeterRegistry metrics,
      org.example.amnezia.controlpanel.api.service.ConfigService configs,
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabaseBeans
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
    this.vpn = vpn;
    this.service = service;
    this.devices = devices;
    this.users = users;
    this.monitor = monitor;
    this.metrics = metrics;
    this.configs = configs;
    this.jdbc = jdbc;
  }

  private final FakeVpnGateway vpn;
  private final DeviceService service;
  private final DeviceRepository devices;
  private final UserRepository users;
  private final VpnMonitor monitor;
  private final MeterRegistry metrics;
  private final org.example.amnezia.controlpanel.api.service.ConfigService configs;
  private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
  private UUID user;

  @BeforeEach
  void setup() {
    vpn.reset();
    jdbc.update(TestQuery.DELETE_CONFIG_LINKS.query(), new MapSqlParameterSource());
    jdbc.update(TestQuery.DELETE_DEVICES.query(), new MapSqlParameterSource());
    var now = Instant.now();
    user = UUID.randomUUID();
    users.insert(
        new User(
            user,
            "internal-" + user,
            "name-" + user,
            "irrelevant",
            User.Role.USER,
            User.Status.ACTIVE,
            now,
            now,
            null));
  }

  @Test
  void shouldListReadAndRenameDevicesWithoutRequestingVpnRuntime() {
    var created = service.create(user, "Phone");
    vpn.failure = FakeVpnGateway.Failure.SSH;
    int calls = vpn.runtimeCalls.get();
    var list = service.list(user, 0, 12);
    assertThat(list.content()).extracting(d -> d.id()).containsExactly(created.id());
    var details = service.get(user, created.id());
    assertThat(details.status())
        .isEqualTo(org.example.amnezia.controlpanel.api.dto.DeviceResponse.Status.UNKNOWN);
    var updated =
        service.update(
            user,
            created.id(),
            new org.example.amnezia.controlpanel.api.dto.ApiCommands.UpdateDevice(
                "Renamed",
                org.example.amnezia.controlpanel.api.persistence.model.Device.Icon.PHONE));
    assertThat(updated.name()).isEqualTo("Renamed");
    assertThat(devices.find(created.id()).orElseThrow().name()).isEqualTo("Renamed");
    assertThat(vpn.runtimeCalls.get()).isEqualTo(calls);
  }

  @Test
  void shouldCreateAndDeleteOnlyAfterVpnApply() {
    var created = service.create(user, "Phone");
    assertThat(vpn.config).contains(created.publicKey());
    assertThat(devices.find(created.id())).isPresent();
    assertThat(devices.find(created.id()).orElseThrow().privateKeyEncrypted()).startsWith("v1:");
    service.delete(user, created.id());
    assertThat(devices.find(created.id())).isEmpty();
    assertThat(vpn.config).doesNotContain(created.publicKey());
  }

  @Test
  void shouldExposeAdminNamesAndRemoveOldPeerLabelsOnRenameAndDeletion() {
    var device = service.create(user, "Телефон");
    monitor.refresh();
    var tags = new String[] {"peer_id", device.id().toString()};
    var meter = metrics.get("vpn_peer_rx_bytes").tags(tags).gauge();
    assertThat(meter.getId().getTag("peer_name")).isEqualTo("Телефон");
    assertThat(meter.getId().getTag("user_name")).isEqualTo("internal-" + user);
    assertThat(meter.getId().getTag("public_key")).isNull();
    jdbc.update(
        TestQuery.RENAME_DEVICE.query(),
        new MapSqlParameterSource("name", "Ноутбук").addValue("id", device.id().toString()));
    jdbc.update(
        TestQuery.RENAME_USER.query(),
        new MapSqlParameterSource("name", "Иван").addValue("id", user.toString()));
    monitor.refresh();
    var updated = metrics.get("vpn_peer_rx_bytes").tags(tags).gauge();
    assertThat(updated.getId().getTag("peer_name")).isEqualTo("Ноутбук");
    assertThat(updated.getId().getTag("user_name")).isEqualTo("Иван");
    assertThat(metrics.find("vpn_peer_rx_bytes").tag("peer_name", "Телефон").meters()).isEmpty();
    service.delete(user, device.id());
    monitor.refresh();
    assertThat(metrics.find("vpn_peer_rx_bytes").tags(tags).meters()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = FakeVpnGateway.Failure.class,
      names = {"SSH", "READ", "WRITE", "APPLY", "INVALID_CONFIG"})
  void shouldNotInsertWhenVpnFails(FakeVpnGateway.Failure failure) {
    vpn.failure = failure;
    assertThatThrownBy(() -> service.create(user, "Phone")).isInstanceOf(RuntimeException.class);
    assertThat(devices.count(user)).isZero();
  }

  @Test
  void shouldKeepDeviceWhenDeleteApplyFails() {
    var device = service.create(user, "Phone");
    vpn.failure = FakeVpnGateway.Failure.APPLY;
    assertThatThrownBy(() -> service.delete(user, device.id()))
        .isInstanceOf(RuntimeException.class);
    assertThat(devices.find(device.id())).isPresent();
    assertThat(vpn.config).contains(device.publicKey());
  }

  @Test
  void shouldSerializeConcurrentCreationAndEnforceLimit() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = new ArrayList<Future<org.example.amnezia.controlpanel.api.dto.DeviceResponse>>();
      for (int i = 0; i < 5; i++)
        tasks.add(executor.submit(() -> service.create(user, "Concurrent")));
      var ips = new HashSet<String>();
      for (var task : tasks) ips.add(task.get(10, TimeUnit.SECONDS).vpnIp());
      assertThat(ips).hasSize(5);
    }
    assertThat(devices.count(user)).isEqualTo(5);
    assertThatThrownBy(() -> service.create(user, "Sixth")).hasMessage("Device limit reached");
    for (var device : devices.byUser(user)) assertThat(vpn.config).contains(device.publicKey());
  }

  @Test
  void shouldDeliverConfigQrAndConsumeLinkOnce() throws Exception {
    var device = service.create(user, "Phone");
    var text = configs.config(user, device.id(), false);
    assertThat(text)
        .contains(
            "[Interface]",
            "PrivateKey = ",
            "S3 = 12",
            "[Peer]",
            "PersistentKeepalive = 25",
            "MaxHandshakeAttempts = 300-500",
            "RandomTrailers = on",
            "DisableCookies = on",
            "I1 = <r 64>")
        .doesNotContain("server-secret");
    var image =
        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(configs.qr(user, device.id())));
    var bitmap =
        new com.google.zxing.BinaryBitmap(
            new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));
    assertThat(new com.google.zxing.MultiFormatReader().decode(bitmap).getText()).isEqualTo(text);
    var link = configs.link(user, device.id(), 60);
    var token = link.url().substring(link.url().lastIndexOf('/') + 1);
    assertThat(configs.importLink(token)).isEqualTo(text);
    assertThatThrownBy(() -> configs.importLink(token)).isInstanceOf(RuntimeException.class);
    var expired = configs.link(user, device.id(), 60);
    jdbc.update(
        TestQuery.EXPIRE_CONFIG_LINKS.query(),
        new MapSqlParameterSource("expiresAt", Instant.EPOCH.toString())
            .addValue("deviceId", device.id().toString()));
    assertThatThrownBy(
            () -> configs.importLink(expired.url().substring(expired.url().lastIndexOf('/') + 1)))
        .isInstanceOf(RuntimeException.class);
  }
}
