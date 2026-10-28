package org.example.amnezia.controlpanel.api.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import org.example.amnezia.controlpanel.api.persistence.model.Device;
import org.example.amnezia.controlpanel.api.vpn.AwgConfig;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Amnezia guest profile: vpn:// + base64url(Qt qCompress(JSON)). Contains no SSH credentials. */
@Component
public class NativeVpnConfig {
  private final JsonMapper json;

  public NativeVpnConfig(JsonMapper json) {
    this.json = json;
  }

  public String encode(String text, Device device) {
    var fields = new LinkedHashMap<String, String>();
    text.lines()
        .forEach(
            line -> {
              int separator = line.indexOf('=');
              if (separator > 0)
                fields.put(
                    line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            });
    var endpoint = fields.get("Endpoint");
    int colon = endpoint.lastIndexOf(':');
    String host = endpoint.substring(0, colon).replace("[", "").replace("]", "");
    int port = Integer.parseInt(endpoint.substring(colon + 1));
    var parameters = AwgConfig.parse(text).clientParameters();
    String version =
        List.of(
                    "HeaderProtectionKey",
                    "ContentPaddingAddition",
                    "RekeyAfterTime",
                    "RekeyTimeout",
                    "RejectAfterTime",
                    "KeepaliveTimeout",
                    "MaxHandshakeAttempts",
                    "RandomTrailers",
                    "DisableCookies")
                .stream()
                .anyMatch(parameters::containsKey)
            ? "3.1"
            : parameters.containsKey("S3") || parameters.containsKey("S4") ? "2" : "1.5";
    var client = new LinkedHashMap<String, Object>();
    client.put("config", text);
    client.put("hostName", host);
    client.put("port", port);
    client.put("client_ip", fields.get("Address"));
    client.put("client_priv_key", fields.get("PrivateKey"));
    client.put("client_pub_key", device.publicKey());
    client.put("clientId", device.publicKey());
    client.put("server_pub_key", fields.get("PublicKey"));
    client.put("psk_key", fields.getOrDefault("PresharedKey", ""));
    client.put(
        "allowed_ips",
        Arrays.stream(fields.get("AllowedIPs").split(",")).map(String::trim).toList());
    client.put("persistent_keep_alive", fields.getOrDefault("PersistentKeepalive", "25"));
    client.putAll(parameters);
    var awg = new LinkedHashMap<String, Object>();
    awg.put("isThirdPartyConfig", true);
    awg.put("protocol_version", version);
    awg.put("port", Integer.toString(port));
    awg.put("transport_proto", "udp");
    awg.putAll(parameters);
    awg.put("last_config", json.writeValueAsString(client));
    var profile = new LinkedHashMap<String, Object>();
    profile.put("containers", List.of(Map.of("container", "amnezia-awg", "awg", awg)));
    profile.put("defaultContainer", "amnezia-awg");
    profile.put("description", device.name());
    profile.put("hostName", host);
    var dns = fields.getOrDefault("DNS", "").split(",");
    if (!dns[0].isBlank()) profile.put("dns1", dns[0].trim());
    if (dns.length > 1) profile.put("dns2", dns[1].trim());
    byte[] bytes = json.writeValueAsBytes(profile);
    try {
      var packed = new ByteArrayOutputStream();
      packed.write(ByteBuffer.allocate(4).putInt(bytes.length).array());
      try (var compressed = new DeflaterOutputStream(packed)) {
        compressed.write(bytes);
      }
      return "vpn://"
          + Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException("Cannot encode VPN profile");
    }
  }
}
