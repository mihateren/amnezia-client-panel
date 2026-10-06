import { afterEach, describe, expect, it, vi } from "vitest";
import { invitationUrl, request, setToken } from "./api";
afterEach(() => {
  vi.unstubAllGlobals();
  setToken(null);
});
describe("API client", () => {
  it("deduplicates refresh during concurrent expired requests", async () => {
    let refreshes = 0;
    setToken("expired");
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, options: RequestInit) => {
        if (url.endsWith("/refresh")) {
          refreshes++;
          await new Promise((r) => setTimeout(r, 5));
          return Response.json({ accessToken: "fresh" });
        }
        return (options.headers as Record<string, string>).Authorization ===
          "Bearer fresh"
          ? Response.json({ ok: true })
          : new Response("", { status: 401 });
      }),
    );
    const result = await Promise.all([
      request("/devices"),
      request("/admin/users"),
    ]);
    expect(result).toEqual([{ ok: true }, { ok: true }]);
    expect(refreshes).toBe(1);
  });
  it("preserves API error correlation id and never retries mutations on server failure", async () => {
    const fetch = vi.fn(async () =>
      Response.json(
        {
          code: "VPN_FAILED",
          message: "Недоступен сервер",
          correlationId: "abc",
        },
        { status: 502 },
      ),
    );
    vi.stubGlobal("fetch", fetch);
    await expect(
      request("/devices", { method: "POST", body: "{}" }),
    ).rejects.toMatchObject({
      status: 502,
      code: "VPN_FAILED",
      correlationId: "abc",
    });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("keeps invitation secrets in a browser fragment", () => {
    expect(invitationUrl("https://example.test/api/v1/invitations/abc")).toBe(
      "https://example.test/register#abc",
    );
  });
});
