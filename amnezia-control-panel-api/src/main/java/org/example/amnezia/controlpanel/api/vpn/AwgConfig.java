package org.example.amnezia.controlpanel.api.vpn;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.amnezia.controlpanel.api.exception.ApiException;

/** Lossless section editing; unmodified server directives and external peers remain intact. */
public final class AwgConfig {
  private final String original;
  private final List<Section> sections;

  private record Section(String name, int start, int end, Map<String, List<String>> fields) {}

  private AwgConfig(String original, List<Section> sections) {
    this.original = original;
    this.sections = sections;
  }

  public static AwgConfig parse(String text) {
    if (text == null || text.length() > 4 * 1024 * 1024) throw ApiException.vpn();
    var matcher =
        java.util.regex.Pattern.compile(
                "(?m)^[ \\t]*\\[(Interface|Peer)][ \\t]*(?:#.*)?(?:\\r?\\n|$)")
            .matcher(text);
    var starts = new ArrayList<Integer>();
    var ends = new ArrayList<Integer>();
    var names = new ArrayList<String>();
    while (matcher.find()) {
      starts.add(matcher.start());
      ends.add(matcher.end());
      names.add(matcher.group(1));
    }
    if (names.isEmpty()
        || !names.getFirst().equals("Interface")
        || names.stream().filter("Interface"::equals).count() != 1) throw ApiException.vpn();
    var sections = new ArrayList<Section>();
    var publicKeys = new HashSet<String>();
    for (int i = 0; i < names.size(); i++) {
      int end = i + 1 < names.size() ? starts.get(i + 1) : text.length();
      var fields = new LinkedHashMap<String, List<String>>();
      for (String line : text.substring(ends.get(i), end).lines().toList()) {
        line = line.split("#", 2)[0].trim();
        if (line.isEmpty()) continue;
        int eq = line.indexOf('=');
        if (eq < 1) throw ApiException.vpn();
        var name = line.substring(0, eq).trim();
        fields.computeIfAbsent(name, k -> new ArrayList<>()).add(line.substring(eq + 1).trim());
      }
      var section = new Section(names.get(i), starts.get(i), end, fields);
      if (section.name().equals("Peer")) {
        var key = key(single(section, "PublicKey"));
        if (!publicKeys.add(key) || !fields.containsKey("AllowedIPs")) throw ApiException.vpn();
      }
      sections.add(section);
    }
    if (!sections.getFirst().fields().containsKey("Address")) throw ApiException.vpn();
    return new AwgConfig(text, List.copyOf(sections));
  }

  public static String key(String value) {
    try {
      if (Base64.getDecoder().decode(value).length != 32) throw new IllegalArgumentException();
      return value;
    } catch (IllegalArgumentException e) {
      throw ApiException.vpn();
    }
  }

  private static String single(Section section, String field) {
    var values = section.fields().get(field);
    if (values == null || values.size() != 1) throw ApiException.vpn();
    return values.getFirst();
  }

  public String add(String publicKey, String psk, String ip) {
    key(publicKey);
    key(psk);
    ipv4(ip);
    if (hasPeer(publicKey)) throw ApiException.conflict("PEER_EXISTS", "Peer already exists");
    return original.stripTrailing()
        + "\n\n[Peer]\nPublicKey = "
        + publicKey
        + "\nPresharedKey = "
        + psk
        + "\nAllowedIPs = "
        + ip
        + "/32\n";
  }

  public boolean hasPeer(String publicKey) {
    return sections.stream()
        .filter(s -> s.name().equals("Peer"))
        .anyMatch(s -> single(s, "PublicKey").equals(publicKey));
  }

  public String remove(Set<String> publicKeys) {
    var text = new StringBuilder(original);
    for (var section : sections.reversed()) {
      if (section.name().equals("Peer") && publicKeys.contains(single(section, "PublicKey")))
        text.delete(section.start(), section.end());
    }
    return text.toString();
  }

  public String peerPsk(String publicKey) {
    var peer =
        sections.stream()
            .filter(s -> s.name().equals("Peer") && single(s, "PublicKey").equals(publicKey))
            .findFirst()
            .orElseThrow(ApiException::notFound);
    return key(single(peer, "PresharedKey"));
  }

  public Map<String, String> clientParameters() {
    Set<String> supported =
        Set.of(
            "Jc",
            "Jmin",
            "Jmax",
            "S1",
            "S2",
            "S3",
            "S4",
            "H1",
            "H2",
            "H3",
            "H4",
            "I1",
            "I2",
            "I3",
            "I4",
            "I5",
            "HeaderProtectionKey",
            "ContentPaddingAddition",
            "RekeyAfterTime",
            "RekeyTimeout",
            "RejectAfterTime",
            "KeepaliveTimeout",
            "RekeyAttemptTime");
    var result = new LinkedHashMap<String, String>();
    var iface = sections.getFirst();
    iface
        .fields()
        .forEach(
            (name, values) -> {
              if (supported.contains(name)) result.put(name, single(iface, name));
            });
    return Collections.unmodifiableMap(result);
  }

  public String allocate(String subnet) {
    var network = cidr(subnet);
    if (network.prefix() < 16 || network.prefix() > 30) throw ApiException.vpn();
    var excluded = new ArrayList<Network>();
    for (var section : sections) {
      String field = section.name().equals("Interface") ? "Address" : "AllowedIPs";
      for (var value : section.fields().getOrDefault(field, List.of())) {
        for (var part : value.split(",")) {
          part = part.trim();
          if (part.contains(":")) continue;
          excluded.add(
              section.name().equals("Interface")
                  ? new Network(ipv4(part.split("/")[0]), 32)
                  : cidr(part));
        }
      }
    }
    boolean serverInSubnet =
        excluded.stream().anyMatch(n -> n.prefix() == 32 && network.contains(n.address()));
    if (!serverInSubnet) throw ApiException.vpn();
    for (long ip = network.start() + 1; ip < network.end(); ip++) {
      final long candidate = ip;
      if (excluded.stream().noneMatch(n -> n.contains(candidate))) return format(ip);
    }
    throw ApiException.conflict("VPN_IP_EXHAUSTED", "No free VPN address");
  }

  private record Network(long address, int prefix) {
    long mask() {
      return prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
    }

    long start() {
      return address & mask();
    }

    long end() {
      return start() | (~mask() & 0xffffffffL);
    }

    boolean contains(long value) {
      return value >= start() && value <= end();
    }
  }

  private static Network cidr(String value) {
    try {
      var parts = value.split("/");
      int prefix = parts.length == 1 ? 32 : Integer.parseInt(parts[1]);
      if (parts.length > 2 || prefix < 0 || prefix > 32) throw ApiException.vpn();
      return new Network(ipv4(parts[0]), prefix);
    } catch (IllegalArgumentException e) {
      throw ApiException.vpn();
    }
  }

  private static long ipv4(String value) {
    try {
      var parts = value.split("\\.", -1);
      if (parts.length != 4) throw ApiException.vpn();
      long address = 0;
      for (var part : parts) {
        if (!part.matches("[0-9]{1,3}")) throw ApiException.vpn();
        int octet = Integer.parseInt(part);
        if (octet > 255) throw ApiException.vpn();
        address = (address << 8) | octet;
      }
      return address;
    } catch (IllegalArgumentException e) {
      throw ApiException.vpn();
    }
  }

  private static String format(long ip) {
    return ((ip >> 24) & 255)
        + "."
        + ((ip >> 16) & 255)
        + "."
        + ((ip >> 8) & 255)
        + "."
        + (ip & 255);
  }

  @Override
  public String toString() {
    return "AwgConfig[redacted]";
  }
}
