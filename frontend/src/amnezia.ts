/** Native AmneziaVPN guest profile, matching the official configKeys/importController.
 * The vpn:// envelope uses Qt qCompress: 4-byte BE UTF-8 length + zlib + base64url.
 * Secrets are held in memory only. Never log the profile or encoded link.
 */
const awgFields = [
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
  "MaxHandshakeAttempts",
  "RandomTrailers",
  "DisableCookies",
];
export function nativeProfile(text: string, name: string, publicKey: string) {
  const sections: Record<string, string>[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.split("#", 1)[0].trim();
    if (line.startsWith("[")) {
      sections.push({ section: line });
      continue;
    }
    const index = line.indexOf("=");
    if (index > 0 && sections.length)
      sections.at(-1)![line.slice(0, index).trim()] = line
        .slice(index + 1)
        .trim();
  }
  const iface = sections.find((s) => s.section === "[Interface]");
  const peer = sections.find((s) => s.section === "[Peer]");
  if (
    !iface?.PrivateKey ||
    !iface.Address ||
    !peer?.PublicKey ||
    !peer.Endpoint ||
    !peer.AllowedIPs
  )
    throw new Error("Конфигурация устройства неполная. Скачайте её повторно.");
  const match = peer.Endpoint.match(/^(\[[^\]]+\]|[^:]+):(\d+)$/);
  if (!match || Number(match[2]) < 1 || Number(match[2]) > 65535)
    throw new Error("Некорректный адрес VPN-сервера.");
  const host = match[1].replace(/^\[|\]$/g, "");
  const port = Number(match[2]);
  const parameters = Object.fromEntries(
    awgFields.filter((k) => iface[k] !== undefined).map((k) => [k, iface[k]]),
  );
  const version = [
    "HeaderProtectionKey",
    "ContentPaddingAddition",
    "RekeyAfterTime",
    "RekeyTimeout",
    "RejectAfterTime",
    "KeepaliveTimeout",
    "MaxHandshakeAttempts",
    "RandomTrailers",
    "DisableCookies",
  ].some((k) => iface[k] !== undefined)
    ? "3.1"
    : iface.S3 !== undefined || iface.S4 !== undefined
      ? "2"
      : "1.5";
  const client = {
    config: text,
    hostName: host,
    port,
    client_ip: iface.Address,
    client_priv_key: iface.PrivateKey,
    client_pub_key: publicKey,
    clientId: publicKey,
    server_pub_key: peer.PublicKey,
    psk_key: peer.PresharedKey || "",
    allowed_ips: peer.AllowedIPs.split(",").map((v) => v.trim()),
    persistent_keep_alive: peer.PersistentKeepalive || "25",
    ...(iface.MTU ? { mtu: iface.MTU } : {}),
    ...parameters,
  };
  const dns = (iface.DNS || "")
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);
  return {
    containers: [
      {
        container: "amnezia-awg",
        awg: {
          isThirdPartyConfig: true,
          protocol_version: version,
          port: String(port),
          transport_proto: "udp",
          ...parameters,
          last_config: JSON.stringify(client),
        },
      },
    ],
    defaultContainer: "amnezia-awg",
    description: name,
    hostName: host,
    ...(dns[0] ? { dns1: dns[0] } : {}),
    ...(dns[1] ? { dns2: dns[1] } : {}),
  };
}
export async function amneziaLink(
  text: string,
  name: string,
  publicKey: string,
) {
  const bytes = new TextEncoder().encode(
    JSON.stringify(nativeProfile(text, name, publicKey)),
  );
  const compressed = await new Response(
    new Blob([bytes]).stream().pipeThrough(new CompressionStream("deflate")),
  ).arrayBuffer();
  const packed = new Uint8Array(4 + compressed.byteLength);
  new DataView(packed.buffer).setUint32(0, bytes.byteLength, false);
  packed.set(new Uint8Array(compressed), 4);
  return (
    "vpn://" +
    btoa(Array.from(packed, (b) => String.fromCharCode(b)).join(""))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "")
  );
}

/** Amnezia's QR series uses a QDataStream header, not the vpn:// text.
 * qint16 magic, quint8 count/id, quint32 byte-array length (all BE), then payload.
 * See the official client/core/utils/qrCodeUtils.cpp and importController.cpp.
 */
export function amneziaQrChunks(link: string): string[] {
  if (!link.startsWith("vpn://"))
    throw new Error("Некорректный ключ AmneziaVPN.");
  const raw = atob(link.slice(6).replace(/-/g, "+").replace(/_/g, "/"));
  const data = Uint8Array.from(raw, (c) => c.charCodeAt(0));
  // Smaller frames than the client's 850-byte maximum remain compatible and
  // leave wider modules on phone screens, making camera decoding reliable.
  const chunkSize = 400;
  const count = Math.ceil(data.length / chunkSize);
  if (!count || count > 255)
    throw new Error("Конфигурация слишком большая для QR-кода. Скачайте файл.");
  return Array.from({ length: count }, (_, index) => {
    const part = data.subarray(index * chunkSize, (index + 1) * chunkSize);
    const frame = new Uint8Array(8 + part.length);
    const header = new DataView(frame.buffer);
    header.setInt16(0, 1984, false);
    frame[2] = count;
    frame[3] = index;
    header.setUint32(4, part.length, false);
    frame.set(part, 8);
    return btoa(Array.from(frame, (b) => String.fromCharCode(b)).join(""))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  });
}
