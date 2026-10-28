package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import org.example.amnezia.controlpanel.api.dto.ApiCommands;
import org.example.amnezia.controlpanel.api.dto.ManagedServerDtos;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.service.AdminService;
import org.example.amnezia.controlpanel.api.service.ConfigService;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.example.amnezia.controlpanel.api.service.ServerCatalog;
import org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class MultipleVpnServersTest extends BaseComponentTest {
  private final ServerCatalog catalog;
  private final DeviceService service;
  private final ConfigService configs;
  private final UserRepository users;
  private final DeviceRepository devices;
  private final FakeVpnGateway vpn;
  private final AdminService adminService;
  private final JsonMapper json;

  @Autowired
  MultipleVpnServersTest(
      ServerCatalog catalog,
      DeviceService service,
      ConfigService configs,
      UserRepository users,
      DeviceRepository devices,
      FakeVpnGateway vpn,
      AdminService adminService,
      JsonMapper json) {
    this.catalog = catalog;
    this.service = service;
    this.configs = configs;
    this.users = users;
    this.devices = devices;
    this.vpn = vpn;
    this.adminService = adminService;
    this.json = json;
  }

  private UUID user(User.Role role) {
    var id = UUID.randomUUID();
    var now = Instant.now();
    users.insert(
        new User(
            id,
            "internal-" + id,
            "name-" + id,
            "irrelevant",
            role,
            User.Status.ACTIVE,
            now,
            now,
            null));
    return id;
  }

  private ManagedServerDtos.AdminServer server(UUID admin, String name) {
    var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String ip =
        "2001:db8:"
            + suffix.substring(0, 4)
            + ":"
            + suffix.substring(4, 8)
            + ":"
            + suffix.substring(8, 12)
            + ":"
            + suffix.substring(12)
            + "::1";
    return catalog.create(
        admin, new ManagedServerDtos.Create(name, ip, 22, "root", "test-server-password"));
  }

  private ApiCommands.CreateDevice command(UUID server, Device.ClientType type) {
    return new ApiCommands.CreateDevice("Телефон", Device.Icon.PHONE, server, type);
  }

  @Test
  void shouldIsolateServersAndExportOnlyTheSelectedFormat() throws Exception {
    var admin = user(User.Role.ADMIN);
    var owner = user(User.Role.USER);
    var first = server(admin, "Рекомендуемый");
    var second = server(admin, "Медленный");
    var wg = service.create(owner, command(first.id(), Device.ClientType.AMNEZIA_WG));
    var nativeDevice = service.create(owner, command(second.id(), Device.ClientType.AMNEZIA_VPN));
    assertThat(wg.vpnIp()).isEqualTo(nativeDevice.vpnIp());
    assertThat(service.list(owner, 0, 20, first.id()).content())
        .extracting(d -> d.id())
        .containsExactly(wg.id());
    assertThat(vpn.servers.get(first.id()).config.contains(nativeDevice.publicKey())).isFalse();
    assertThat(catalog.require(first.id()).passwordEncrypted()).startsWith("v1:");
    assertThat(json.writeValueAsString(catalog.list(owner)))
        .doesNotContain("password", "username", "hostKey", "test-server-password");
    assertThat(json.writeValueAsString(catalog.adminList(admin)))
        .doesNotContain("password", "hostKey", "test-server-password");
    var raw = configs.config(owner, wg.id(), true);
    assertThat(raw.startsWith("[Interface]")).isTrue();
    assertThat(configs.extension(owner, wg.id())).isEqualTo(".conf");
    var bitmap =
        new BinaryBitmap(
            new HybridBinarizer(
                new BufferedImageLuminanceSource(
                    ImageIO.read(new ByteArrayInputStream(configs.qr(owner, wg.id()))))));
    assertThat(new MultiFormatReader().decode(bitmap).getText().equals(raw)).isTrue();
    var encoded = configs.config(owner, nativeDevice.id(), true);
    assertThat(encoded.startsWith("vpn://")).isTrue();
    assertThat(configs.extension(owner, nativeDevice.id())).isEqualTo(".vpn");
    byte[] packed = Base64.getUrlDecoder().decode(encoded.substring(6));
    byte[] expanded =
        new InflaterInputStream(new ByteArrayInputStream(packed, 4, packed.length - 4))
            .readAllBytes();
    assertThat(expanded.length).isEqualTo(ByteBuffer.wrap(packed).getInt());
    var profile = json.readTree(expanded);
    assertThat(profile.get("description").asString()).isEqualTo("Телефон");
    var awg = profile.get("containers").get(0).get("awg");
    assertThat(awg.get("protocol_version").asString()).isEqualTo("3.1");
    assertThat(
            json.readTree(awg.get("last_config").asString())
                .get("config")
                .asString()
                .startsWith("[Interface]"))
        .isTrue();
    assertThatThrownBy(() -> configs.qr(owner, nativeDevice.id()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(".vpn");
    catalog.update(admin, first.id(), new ManagedServerDtos.Update("Быстрый", false, null));
    assertThat(service.get(owner, wg.id()).serverName()).isEqualTo("Быстрый");
    assertThatThrownBy(
            () -> service.create(owner, command(first.id(), Device.ClientType.AMNEZIA_WG)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("отключил");
    assertThat(configs.config(owner, wg.id(), true).equals(raw)).isTrue();
    service.update(owner, wg.id(), new ApiCommands.UpdateDevice("Планшет", Device.Icon.TABLET));
    assertThat(service.get(owner, wg.id()).serverId()).isEqualTo(first.id());
    assertThat(service.get(owner, wg.id()).clientType()).isEqualTo(Device.ClientType.AMNEZIA_WG);
    service.create(owner, command(second.id(), Device.ClientType.AMNEZIA_WG));
    service.delete(owner, wg.id());
    assertThat(devices.find(wg.id())).isEmpty();
    assertThatThrownBy(
            () ->
                catalog.update(
                    owner, second.id(), new ManagedServerDtos.Update("Hacked", false, null)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void shouldRevokeAcrossServersAndRestoreAlreadyAppliedChangesOnFailure() {
    var admin = user(User.Role.ADMIN);
    var owner = user(User.Role.USER);
    var first = server(admin, "One");
    var second = server(admin, "Two");
    var a = service.create(owner, command(first.id(), Device.ClientType.AMNEZIA_WG));
    var b = service.create(owner, command(second.id(), Device.ClientType.AMNEZIA_VPN));
    String originalA = vpn.servers.get(first.id()).config,
        originalB = vpn.servers.get(second.id()).config;
    vpn.servers.get(second.id()).failure = FakeVpnGateway.Failure.APPLY;
    assertThatThrownBy(() -> adminService.status(admin, owner, User.Status.DISABLED))
        .isInstanceOf(ApiException.class);
    assertThat(vpn.servers.get(first.id()).config.equals(originalA)).isTrue();
    assertThat(vpn.servers.get(second.id()).config.equals(originalB)).isTrue();
    assertThat(users.find(owner).orElseThrow().status()).isEqualTo(User.Status.ACTIVE);
    assertThat(devices.byUser(owner)).hasSize(2);
    vpn.servers.get(second.id()).failure = FakeVpnGateway.Failure.NONE;
    adminService.status(admin, owner, User.Status.DISABLED);
    assertThat(devices.byUser(owner)).isEmpty();
    assertThat(vpn.servers.get(first.id()).config.contains(a.publicKey())).isFalse();
    assertThat(vpn.servers.get(second.id()).config.contains(b.publicKey())).isFalse();
  }
}
