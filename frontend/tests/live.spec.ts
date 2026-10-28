import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { inflateSync } from "node:zlib";
import jsQR from "jsqr";
import { PNG } from "pngjs";
import { randomBytes } from "node:crypto";
test("real API and VPS: invitation, refresh, configuration, revocation and cleanup", async ({
  page,
  browser,
  request,
}) => {
  test.skip(
    process.env.RUN_VPS_TESTS !== "1",
    "Explicit opt-in required: this creates and removes a temporary VPN peer.",
  );
  test.setTimeout(240000);
  const settings = Object.fromEntries(
    readFileSync(process.env.PANEL_TEST_ENV_FILE || "../.env", "utf8")
      .split(/\r?\n/)
      .filter((l) => l.includes("="))
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
  );
  const origin = settings.PUBLIC_URL;
  const login = await request.post(`${origin}/api/v1/auth/login`, {
    data: {
      nickname: settings.BOOTSTRAP_ADMIN_NICKNAME,
      password: settings.BOOTSTRAP_ADMIN_PASSWORD,
    },
  });
  expect(login.status()).toBe(200);
  const admin = await login.json();
  const headers = { Authorization: `Bearer ${admin.accessToken}` };
  let userId: string | undefined;
  const nickname = `e2e_${Date.now()}`;
  const password = randomBytes(18).toString("hex");
  const context = await browser.newContext();
  const userPage = await context.newPage();
  try {
    await page.goto(`${origin}/login`);
    await page
      .getByLabel("Никнейм", { exact: true })
      .fill(settings.BOOTSTRAP_ADMIN_NICKNAME);
    await page
      .getByLabel("Пароль", { exact: true })
      .fill(settings.BOOTSTRAP_ADMIN_PASSWORD);
    await page.getByRole("button", { name: "Войти в панель" }).click();
    await expect(
      page.getByRole("heading", { name: "Обзор сети" }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Пригласить пользователя" }).click();
    await page.getByLabel("Внутреннее имя").fill(nickname);
    await page.getByRole("button", { name: "Создать приглашение" }).click();
    const invitation = await page.getByLabel("Ссылка").inputValue();
    await userPage.goto(invitation);
    await userPage.getByLabel("Никнейм", { exact: true }).fill(nickname);
    await userPage.getByLabel("Пароль", { exact: true }).fill(password);
    await userPage.getByLabel("Повторите пароль").fill(password);
    await userPage.getByRole("button", { name: "Создать аккаунт" }).click();
    await expect(
      userPage.getByRole("heading", { name: "Мои устройства" }),
    ).toBeVisible();
    const users = await (
      await request.get(`${origin}/api/v1/admin/users?search=${nickname}`, {
        headers,
      })
    ).json();
    userId = users.content.find(
      (u: { nickname: string }) => u.nickname === nickname,
    ).id;
    await userPage.reload();
    await expect(
      userPage.getByRole("heading", { name: "Мои устройства" }),
    ).toBeVisible();
    await userPage.getByRole("button", { name: "Добавить устройство" }).click();
    await userPage.getByRole("button", { name: "Amnezia VPN Файл .vpn" }).click();
    await userPage.getByLabel("Название").fill("Integration temporary device");
    await userPage
      .getByRole("button", { name: "Создать устройство", exact: true })
      .click();
    await expect(
      userPage.getByRole("dialog").getByRole("heading", {
        name: "Integration temporary device",
        exact: true,
      }),
    ).toBeVisible({ timeout: 30000 });
    const nativeDownload = userPage.waitForEvent("download");
    await userPage
      .getByRole("button", { name: "Скачать .vpn файл", exact: true })
      .click();
    const nativeFile = await nativeDownload;
    expect(nativeFile.suggestedFilename().endsWith(".vpn")).toBe(true);
    const packed = Buffer.from(
      readFileSync((await nativeFile.path())!, "utf8").slice(6),
      "base64url",
    );
    const unpacked = inflateSync(packed.subarray(4));
    expect(packed.readUInt32BE(0) === unpacked.length).toBe(true);
    const profile = JSON.parse(unpacked.toString("utf8"));
    const client = JSON.parse(profile.containers[0].awg.last_config);
    expect(profile.containers[0].awg.protocol_version === "3.1").toBe(true);
    expect(
      [
        "I1",
        "HeaderProtectionKey",
        "MaxHandshakeAttempts",
        "RandomTrailers",
        "DisableCookies",
      ].every((key) => Boolean(client[key])),
    ).toBe(true);
    await expect(userPage.getByRole("button", { name: "Сканировать QR-код" })).toHaveCount(0);
    await userPage.getByRole("button", { name: "Закрыть", exact: true }).click();
    await userPage.getByRole("button", { name: "Добавить устройство" }).click();
    await userPage.getByRole("button", { name: "AmneziaWG Файл .conf и QR-код" }).click();
    await userPage.getByLabel("Название").fill("Integration temporary WG");
    await userPage.getByRole("button", { name: "Создать устройство", exact: true }).click();
    await expect(userPage.getByRole("button", { name: "Скачать .conf файл" })).toBeVisible({timeout:30000});
    const wgDownload=userPage.waitForEvent("download");
    await userPage.getByRole("button", { name: "Скачать .conf файл" }).click();
    const wgFile=await wgDownload;
    expect(wgFile.suggestedFilename().endsWith(".conf")).toBe(true);
    const raw=readFileSync((await wgFile.path())!,"utf8");
    expect(raw.startsWith("[Interface]")).toBe(true);
    await userPage.getByRole("button", { name: "Сканировать QR-код", exact: true }).click();
    const qrImage=userPage.getByRole("img", { name: "QR-код AmneziaWG" });
    await expect(qrImage).toBeVisible({timeout:30000});
    // Decode in memory, never persist a real credential QR screenshot.
    const png=PNG.sync.read(await qrImage.screenshot());
    const code=jsQR(new Uint8ClampedArray(png.data),png.width,png.height);
    expect(Boolean(code)).toBe(true);
    expect(code!.data === raw).toBe(true);
    await userPage.getByRole("button", { name: "Закрыть", exact: true }).click();
    await userPage.getByRole("link", { name: "Открыть профиль" }).click();
    await userPage.getByRole("button", { name: "Выйти", exact: true }).click();
    await userPage.getByLabel("Никнейм", { exact: true }).fill(nickname);
    await userPage.getByLabel("Пароль", { exact: true }).fill(password);
    await userPage.getByRole("button", { name: "Войти в панель" }).click();
    await expect(userPage.getByRole("heading", { name: "Мои устройства" })).toBeVisible();
    const disable = await request.patch(
      `${origin}/api/v1/admin/users/${userId}`,
      { headers, data: { status: "DISABLED" } },
    );
    expect(disable.status()).toBe(200);
    expect((await disable.json()).deviceCount).toBe(0);
    const enable = await request.patch(
      `${origin}/api/v1/admin/users/${userId}`,
      { headers, data: { status: "ACTIVE" } },
    );
    expect(enable.status()).toBe(200);
  } finally {
    if (!userId) {
      const users = await (
        await request.get(`${origin}/api/v1/admin/users?search=${nickname}`, {
          headers,
        })
      ).json();
      userId = users.content?.find(
        (u: { nickname: string }) => u.nickname === nickname,
      )?.id;
    }
    if (userId)
      expect(
        (
          await request.delete(`${origin}/api/v1/admin/users/${userId}`, {
            headers,
          })
        ).status(),
      ).toBe(204);
    await context.close();
  }
});
