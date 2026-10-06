import { expect, test } from "@playwright/test";
const user = {
  id: "11111111-1111-4111-8111-111111111111",
  nickname: "alex",
  role: "ADMIN",
  status: "ACTIVE",
};
const device = {
  id: "22222222-2222-4222-8222-222222222222",
  name: "MacBook Pro",
  vpnIp: "10.8.1.2",
  publicKey: "public-key",
  status: "ONLINE",
  createdAt: "2026-10-05T10:00:00Z",
  lastHandshakeAt: "2026-10-05T12:00:00Z",
  rxBytes: 1230000,
  txBytes: 3400000,
};
const paged = (content: unknown[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
});
async function mock(page: import("@playwright/test").Page, role = "ADMIN") {
  let devices = [device];
  let authenticated = false;
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace("/api/v1", "");
    let data: unknown = {};
    if (path === "/auth/refresh") {
      if (!authenticated)
        return route.fulfill({
          status: 401,
          json: { code: "UNAUTHORIZED", message: "Unauthorized" },
        });
      data = { accessToken: "test" };
    } else if (path === "/auth/login") {
      authenticated = true;
      data = { accessToken: "test", user: { ...user, role } };
    } else if (path === "/auth/me") data = { ...user, role };
    else if (path === "/auth/logout") {
      authenticated = false;
      return route.fulfill({ status: 204 });
    } else if (path === "/admin/dashboard")
      data = {
        usersTotal: 8,
        devicesTotal: 12,
        devicesActive: 5,
        vpn: {
          serverUp: true,
          peersTotal: 13,
          peersActive: 5,
          checkedAt: "2026-10-05T12:00:00Z",
        },
        infrastructure: {
          application: "UP",
          database: "UP",
          ssh: "UP",
          vpn: "UP",
        },
      };
    else if (path === "/admin/audit")
      data = paged([
        {
          id: "event",
          timestamp: "2026-10-05T12:00:00Z",
          eventType: "DEVICE_CREATED",
          result: "SUCCESS",
          actor: { nickname: "alex" },
          object: { type: "DEVICE" },
          correlationId: "test-id",
        },
      ]);
    else if (path === "/admin/users")
      data = paged([
        {
          ...user,
          internalName: "Александр",
          deviceCount: 1,
          createdAt: device.createdAt,
        },
      ]);
    else if (path === "/devices" && req.method() === "POST") {
      devices = [
        ...devices,
        { ...device, id: "new", name: req.postDataJSON().name },
      ];
      data = devices.at(-1);
    } else if (path === "/devices" || path === "/admin/devices")
      data = paged(devices.map((d) => ({ ...d, owner: user })));
    else if (path.endsWith("/config/link"))
      data = {
        url: "https://panel.example/api/v1/config/secret",
        expiresAt: "2026-10-05T13:00:00Z",
      };
    else if (path.endsWith("/config"))
      return route.fulfill({
        contentType: "text/plain",
        body: "[Interface]\nPrivateKey = test-private\nAddress = 10.8.1.2/32\n[Peer]\nPublicKey = test-server\nEndpoint = 127.0.0.1:33763\nAllowedIPs = 0.0.0.0/0",
      });
    else if (path.startsWith("/devices/") && req.method() === "DELETE") {
      devices = devices.filter((d) => d.id !== path.split("/")[2]);
      return route.fulfill({ status: 204 });
    } else if (
      path.startsWith("/devices/") ||
      path.startsWith("/admin/devices/")
    )
      data = devices.find((d) => d.id === path.split("/").at(-1)) || device;
    else if (path === "/admin/invitations")
      data = {
        invitationUrl: "https://panel.example/api/v1/invitations/abc123",
        id: "invite",
        internalName: "Новый",
        createdAt: device.createdAt,
      };
    else if (path.startsWith("/invitations/") && req.method() === "GET")
      data = { valid: true };
    else if (path.endsWith("/register")) {
      authenticated = true;
      data = { accessToken: "test", user: { ...user, role: "USER" } };
    }
    return route.fulfill({ json: data });
  });
}
async function login(page: import("@playwright/test").Page) {
  await page.goto("/login");
  await page.getByLabel("Никнейм", { exact: true }).fill("alex");
  await page.getByLabel("Пароль", { exact: true }).fill("test-password");
  await page.getByRole("button", { name: "Войти в панель" }).click();
}
test("admin dashboard, invitation and users search", async ({ page }) => {
  await mock(page);
  await login(page);
  await expect(page.getByRole("heading", { name: "Обзор сети" })).toBeVisible();
  await page.screenshot({ path: "test-results/dashboard.png", fullPage: true });
  await page.getByRole("button", { name: "Пригласить пользователя" }).click();
  await page.getByLabel("Внутреннее имя").fill("Новый");
  await page.getByRole("button", { name: "Создать приглашение" }).click();
  await expect(page.getByLabel("Ссылка")).toHaveValue(
    "https://panel.example/register#abc123",
  );
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("link", { name: "Пользователи", exact: true }).click();
  await page.getByRole("textbox", { name: "Поиск пользователей" }).fill("alex");
  await expect(
    page.getByRole("button", { name: "Открыть alex" }),
  ).toBeVisible();
});
test("user device lifecycle and access control", async ({ page }) => {
  await mock(page, "USER");
  await login(page);
  await expect(
    page.getByRole("heading", { name: "Мои устройства" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Пользователи", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Добавить устройство" }).click();
  await page.getByLabel("Название").fill("Телефон");
  await page
    .getByRole("button", { name: "Создать устройство", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  const downloaded = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Скачать .conf файл", exact: true })
    .click();
  expect((await downloaded).suggestedFilename().endsWith(".conf")).toBe(true);
  await page
    .getByRole("button", { name: "Сканировать QR-код", exact: true })
    .click();
  await expect(
    page.getByRole("img", { name: /QR-код AmneziaVPN/ }).first(),
  ).toBeVisible();
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await page
    .getByRole("button", { name: "Скопировать ключ доступа", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Ключ скопирован");
  expect(
    await page.evaluate(async () =>
      (await navigator.clipboard.readText()).startsWith("vpn://"),
    ),
  ).toBe(true);
  await expect(page.getByRole("button", { name: "Скачать JSON" })).toHaveCount(
    0,
  );
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "Удалить Телефон" }).click();
  await page.getByRole("button", { name: "Подтвердить" }).click();
  await expect(
    page.getByRole("button", { name: "Телефон", exact: true }),
  ).toHaveCount(0);
  await page.goto("/users");
  await expect(
    page.getByRole("heading", { name: "Мои устройства" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
});
test("invitation registration and mobile layout", async ({ page }) => {
  await mock(page, "USER");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/register#valid-invitation");
  await page.getByLabel("Никнейм", { exact: true }).fill("newuser");
  await page.getByLabel("Пароль", { exact: true }).fill("new-password");
  await page.getByLabel("Повторите пароль").fill("new-password");
  await page.getByRole("button", { name: "Создать аккаунт" }).click();
  await expect(
    page.getByRole("heading", { name: "Мои устройства" }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await page.screenshot({ path: "test-results/mobile.png", fullPage: true });
});
test("server failure can be retried", async ({ page }) => {
  await mock(page);
  await page.route("**/api/v1/auth/login", (r) =>
    r.fulfill({
      status: 401,
      json: {
        code: "INVALID_CREDENTIALS",
        message: "Неверный логин или пароль",
        correlationId: "test-error",
      },
    }),
  );
  await login(page);
  await expect(page.getByRole("alert")).toContainText("test-error");
  await expect(
    page.getByRole("button", { name: "Войти в панель" }),
  ).toBeEnabled();
});
