package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_CONFIG_DOWNLOADED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.DEVICE_CONFIG_VIEWED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.ConfigLink;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.ConfigLinkRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.DeviceRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.example.amnezia.controlpanel.api.vpn.VpnGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConfigService {
  private final DeviceService service;
  private final DeviceRepository devices;
  private final UserRepository users;
  private final ConfigLinkRepository links;
  private final VpnGateway vpn;
  private final Secrets secrets;
  private final AppProperties config;
  private final Clock clock;
  private final ReentrantLock lock;
  private final TransactionTemplate tx;
  private final AuditService audit;

  public ConfigService(
      DeviceService service,
      DeviceRepository devices,
      UserRepository users,
      ConfigLinkRepository links,
      VpnGateway vpn,
      Secrets secrets,
      AppProperties config,
      Clock clock,
      ReentrantLock lock,
      TransactionTemplate tx,
      AuditService audit) {
    this.service = service;
    this.devices = devices;
    this.users = users;
    this.links = links;
    this.vpn = vpn;
    this.secrets = secrets;
    this.config = config;
    this.clock = clock;
    this.lock = lock;
    this.tx = tx;
    this.audit = audit;
  }

  public record LinkResponse(String url, Instant expiresAt) {
    @Override
    public String toString() {
      return "LinkResponse[redacted]";
    }
  }

  public String config(UUID actor, UUID id, boolean download) {
    lock.lock();
    try {
      var device = service.owned(actor, id);
      var result = generate(device);
      audit.record(
          download ? DEVICE_CONFIG_DOWNLOADED : DEVICE_CONFIG_VIEWED, SUCCESS, actor, "DEVICE", id);
      return result;
    } finally {
      lock.unlock();
    }
  }

  private String generate(Device device) {
    var owner = users.find(device.userId()).orElseThrow(ApiException::notFound);
    if (owner.status() != User.Status.ACTIVE) throw ApiException.notFound();
    var parsed = AwgConfig.parse(vpn.readConfig());
    if (!parsed.hasPeer(device.publicKey())) throw ApiException.notFound();
    var text =
        new StringBuilder("[Interface]\nPrivateKey = ")
            .append(secrets.decrypt(device.privateKeyEncrypted(), device.id()))
            .append("\nAddress = ")
            .append(device.vpnIp())
            .append("/32\nDNS = ")
            .append(config.vpn().dns())
            .append("\n");
    var parameters = parsed.clientParameters();
    parameters.forEach((name, value) -> text.append(name).append(" = ").append(value).append("\n"));
    var signature = config.vpn().clientSignature();
    if (!parameters.containsKey("I1") && signature != null && !signature.isBlank()) {
      text.append("I1 = ").append(signature).append("\n");
    }
    text.append("\n[Peer]\nPublicKey = ")
        .append(vpn.serverPublicKey())
        .append("\nPresharedKey = ")
        .append(parsed.peerPsk(device.publicKey()))
        .append("\nEndpoint = ")
        .append(config.vpn().endpoint())
        .append("\nAllowedIPs = ")
        .append(config.vpn().allowedIps())
        .append("\nPersistentKeepalive = 25\n");
    return text.toString();
  }

  public byte[] qr(UUID actor, UUID id) {
    var content = config(actor, id, false);
    try {
      var matrix =
          new QRCodeWriter()
              .encode(
                  content,
                  BarcodeFormat.QR_CODE,
                  512,
                  512,
                  Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
      var out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException e) {
      throw ApiException.conflict(
          "CONFIG_TOO_LARGE_FOR_QR", "Configuration is too large for QR; download the file");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("QR generation failed");
    }
  }

  public LinkResponse link(UUID actor, UUID id, Integer expiresIn) {
    long seconds = expiresIn == null ? config.linkTtl().toSeconds() : expiresIn;
    if (seconds < 60 || seconds > 86400) throw ApiException.invalid();
    lock.lock();
    try {
      var device = service.owned(actor, id);
      if (users.find(device.userId()).orElseThrow(ApiException::notFound).status()
          != User.Status.ACTIVE) throw ApiException.notFound();
      var now = clock.instant();
      var raw = Secrets.token();
      var link =
          new ConfigLink(
              UUID.randomUUID(), id, Secrets.hash(raw), now, now.plusSeconds(seconds), null);
      links.insert(link);
      return new LinkResponse(config.publicUrl() + "/api/v1/config/" + raw, link.expiresAt());
    } finally {
      lock.unlock();
    }
  }

  public String importLink(String raw) {
    if (raw.length() > 128) throw ApiException.notFound();
    lock.lock();
    try {
      var link = links.find(Secrets.hash(raw)).orElseThrow(ApiException::notFound);
      if (link.usedAt() != null || !link.expiresAt().isAfter(clock.instant()))
        throw ApiException.notFound();
      var device = devices.find(link.deviceId()).orElseThrow(ApiException::notFound);
      var result = generate(device);
      tx.executeWithoutResult(
          status -> {
            if (!links.consume(link.id(), clock.instant())) throw ApiException.notFound();
            audit.record(DEVICE_CONFIG_DOWNLOADED, SUCCESS, null, "DEVICE", device.id());
          });
      return result;
    } finally {
      lock.unlock();
    }
  }
}
