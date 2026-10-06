import { describe, it, expect } from "vitest";
import { inflateSync } from "node:zlib";
import { amneziaLink, amneziaQrChunks, nativeProfile } from "./amnezia";
import QRCode from "qrcode";
import jsQR from "jsqr";
import { PNG } from "pngjs";
const text =
  "[Interface]\nPrivateKey = client-test-key\nAddress = 10.8.1.4/32\nDNS = 1.1.1.1, 1.0.0.1\nHeaderProtectionKey = header-test-key\nRandomTrailers = on\nDisableCookies = on\nMaxHandshakeAttempts = 300-500\n[Peer]\nPublicKey = server-test-key\nPresharedKey = test-psk\nEndpoint = 153.56.134.194:33763\nAllowedIPs = 0.0.0.0/0, ::/0\nPersistentKeepalive = 25\n";
describe("AmneziaVPN native export", () => {
  it("decodes QR images and reassembles them using Amnezia's QDataStream framing", async () => {
    const link = await amneziaLink(text, "Телефон", "client-public-test");
    // Force a series and a trailing short frame; the client assembles compressed bytes.
    const original = Buffer.concat([
      Buffer.from(link.slice(6), "base64url"),
      Buffer.alloc(1300, 7),
    ]);
    const chunks = amneziaQrChunks("vpn://" + original.toString("base64url"));
    expect(chunks.length).toBeGreaterThan(1);
    const parts: Buffer[] = [];
    for (const [index, payload] of chunks.entries()) {
      const png = PNG.sync.read(
        await QRCode.toBuffer(payload, {
          scale: 5,
          margin: 4,
          errorCorrectionLevel: "L",
        }),
      );
      const decoded = jsQR(
        new Uint8ClampedArray(png.data),
        png.width,
        png.height,
      );
      expect(decoded?.data).toBe(payload);
      const frame = Buffer.from(decoded!.data, "base64url");
      expect(frame.readInt16BE(0)).toBe(1984);
      expect(frame[2]).toBe(chunks.length);
      expect(frame[3]).toBe(index);
      expect(frame.readUInt32BE(4)).toBe(frame.length - 8);
      parts.push(frame.subarray(8));
    }
    expect(Buffer.concat(parts)).toEqual(original);
    const reassembled = Buffer.concat(
      amneziaQrChunks(link).map((frame) =>
        Buffer.from(frame, "base64url").subarray(8),
      ),
    );
    const json = inflateSync(reassembled.subarray(4));
    expect(JSON.parse(json.toString()).containers[0].awg.protocol_version).toBe(
      "3.1",
    );
  });
  it("keeps transport protection fields as JSON fields and uses native numeric port", () => {
    const profile = nativeProfile(text, "Телефон", "client-public-test");
    const awg = profile.containers[0].awg;
    const client = JSON.parse(awg.last_config);
    expect(awg.protocol_version).toBe("3.1");
    expect(client).toMatchObject({
      port: 33763,
      client_pub_key: "client-public-test",
      HeaderProtectionKey: "header-test-key",
      RandomTrailers: "on",
      DisableCookies: "on",
      MaxHandshakeAttempts: "300-500",
      psk_key: "test-psk",
    });
    expect(profile).not.toHaveProperty("password");
    expect(profile).not.toHaveProperty("userName");
    expect(profile.dns1).toBe("1.1.1.1");
  });
  it("encodes a Qt-compatible compressed UTF-8 envelope", async () => {
    const link = await amneziaLink(text, "Телефон", "client-public-test");
    const buffer = Buffer.from(link.slice(6), "base64url");
    const json = inflateSync(buffer.subarray(4));
    expect(buffer.readUInt32BE(0)).toBe(json.length);
    expect(JSON.parse(json.toString("utf8"))).toEqual(
      nativeProfile(text, "Телефон", "client-public-test"),
    );
  });
  it("rejects missing endpoint and handles bracketed IPv6", () => {
    expect(() => nativeProfile("[Interface]", "Test", "pub")).toThrow(
      "неполная",
    );
    const profile = nativeProfile(
      text.replace("153.56.134.194:33763", "[2001:db8::1]:33763"),
      "Test",
      "pub",
    );
    expect(profile.hostName).toBe("2001:db8::1");
  });
});
