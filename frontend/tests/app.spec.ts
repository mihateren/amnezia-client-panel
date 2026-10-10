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
  icon: "LAPTOP",
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
  let currentUser = { ...user, role };
  let currentPassword = "test-password";
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
      data = { accessToken: "test", user: currentUser };
    } else if (path === "/auth/me/nickname" && req.method() === "PATCH") {
      currentUser = { ...currentUser, nickname: req.postDataJSON().nickname };
      data = currentUser;
    } else if (path === "/auth/me/password" && req.method() === "PATCH") {
      if (req.postDataJSON().currentPassword !== currentPassword) {
        return route.fulfill({
          status: 400,
          json: {
            code: "CURRENT_PASSWORD_INVALID",
            message: "Текущий пароль неверен",
          },
        });
      }
      currentPassword = req.postDataJSON().newPassword;
      data = { accessToken: "rotated", user: currentUser };
    } else if (path === "/auth/me") data = currentUser;
    else if (path === "/auth/logout") {
      authenticated = false;
      return route.fulfill({ status: 204 });
    } else if (path === "/admin/dashboard/summary")
      data = { usersTotal: 8, devicesTotal: 12 };
    else if (path === "/admin/dashboard")
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
        {
          ...device,
          id: "new",
          name: req.postDataJSON().name,
          icon: req.postDataJSON().icon,
        },
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
    } else if (path.startsWith("/devices/") && req.method() === "PATCH") {
      devices = devices.map((d) =>
        d.id === path.split("/")[2] ? { ...d, ...req.postDataJSON() } : d,
      );
      data = devices.find((d) => d.id === path.split("/")[2]);
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
  await page.screenshot({
    animations: "disabled",
    path: "test-results/dashboard.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Тёмное", exact: true }).click();
  await page.screenshot({
    animations: "disabled",
    path: "test-results/dashboard-dark.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Светлое", exact: true }).click();
  await page.getByRole("button", { name: "Пригласить пользователя" }).click();
  await page.getByLabel("Имя для администратора").fill("Новый");
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
  await page.route("**/api/v1/admin/devices?**", (route) =>
    route.fulfill({
      json: paged(
        ["PHONE", "LAPTOP", "LAPTOP", "TV", "ROUTER"].map((icon, i) => ({
          ...device,
          id: `${device.id}-${i}`,
          name: [
            "Телефон",
            "Ноутбук",
            "Рабочий ноутбук",
            "Телевизор",
            "Роутер",
          ][i],
          icon,
          owner: { ...user, internalName: "Александр" },
        })),
      ),
    }),
  );
  await page.getByRole("link", { name: "Все устройства", exact: true }).click();
  await expect(page.locator(".device-card")).toHaveCount(5);
  await page.getByRole("button", { name: "Тёмное", exact: true }).click();
  await page.screenshot({
    animations: "disabled",
    path: "test-results/devices-dark.png",
    fullPage: true,
  });
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
  await page.getByRole("link", { name: "Открыть профиль" }).click();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Вход" })).toBeVisible();
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
  await page.screenshot({
    animations: "disabled",
    path: "test-results/mobile.png",
    fullPage: true,
  });
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

test("device metadata persists and hides connection internals", async ({
  page,
}) => {
  await mock(page, "USER");
  await login(page);
  await page.getByRole("button", { name: "Изменить MacBook Pro" }).click();
  await page.getByLabel("Название").fill("Мой телевизор");
  await page.getByRole("button", { name: "Телевизор", exact: true }).click();
  await page.getByRole("button", { name: "Сохранить", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Мой телевизор", exact: true }),
  ).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Изменить Мой телевизор" }).click();
  await expect(page.getByLabel("Название")).toHaveValue("Мой телевизор");
  await expect(
    page.getByRole("button", { name: "Телевизор", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "Подключить", exact: true }).click();
  await expect(
    page
      .getByRole("dialog")
      .getByRole("button", { name: "Изменить устройство" }),
  ).toHaveCount(0);
  await expect(page.getByText("10.8.1.2", { exact: true })).toHaveCount(0);
  await expect(page.getByText("public-key", { exact: true })).toHaveCount(0);
  await expect(page.getByText("В сети", { exact: true })).toHaveCount(0);
  await expect(page.locator(".topbar, .avatar")).toHaveCount(0);
});

test("sidebar offers two persistent themes", async ({ page }) => {
  await mock(page, "USER");
  await login(page);
  await expect(page).toHaveTitle("Amnezia Control Panel");
  await expect(page.locator(".sidebar .brand")).toContainText(
    "Amnezia Control Panel",
  );
  await page.getByRole("link", { name: "Открыть профиль" }).click();
  await expect(
    page.getByRole("heading", { name: "Профиль", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".sidebar-theme")).toBeVisible();
  await expect(page.locator(".account-panel .theme-picker")).toHaveCount(0);
  await page.getByRole("button", { name: "Тёмное", exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.screenshot({
    animations: "disabled",
    path: "test-results/profile-dark.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Светлое", exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(
    page.getByRole("button", { name: "Светлое", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({
    animations: "disabled",
    path: "test-results/profile-light.png",
    fullPage: true,
  });
  await expect(
    page.getByRole("button", { name: "Как на устройстве", exact: true }),
  ).toHaveCount(0);
  await expect(page.locator(".sidebar-theme button")).toHaveCount(2);
  await page.emulateMedia({ colorScheme: "dark" });
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.emulateMedia({ colorScheme: "light" });
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
});

test("guide shows supplied screenshots and official Google Play link", async ({
  page,
}) => {
  await mock(page, "USER");
  await login(page);
  await page
    .getByRole("link", { name: "Как подключиться", exact: true })
    .click();
  await expect(
    page.getByRole("link", { name: /Скачать в Google Play/ }),
  ).toHaveAttribute(
    "href",
    "https://play.google.com/store/apps/details?id=org.amnezia.vpn",
  );
  await expect(
    page.getByRole("link", { name: "«Мои устройства»" }),
  ).toHaveAttribute("href", "/my-devices");
  for (const [name, file] of [
    ["Файл", "file"],
    ["QR-код", "qr"],
    ["Ключ доступа", "key"],
  ]) {
    await page.getByRole("button", { name, exact: true }).click();
    const image = page.locator(".guide-shot img");
    await image.scrollIntoViewIfNeeded();
    await expect(image).toHaveAttribute("src", `/guide/${file}.png`);
    await expect
      .poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth))
      .toBeGreaterThan(0);
  }
  await page.getByRole("button", { name: "Файл", exact: true }).click();
  await page.screenshot({
    animations: "disabled",
    path: "test-results/guide.png",
    fullPage: true,
  });
});

test("mobile connection close button remains visible while scrolling", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 568 });
  await mock(page, "USER");
  await login(page);
  await page.getByRole("button", { name: "Подключить", exact: true }).click();
  await page
    .getByRole("button", { name: "Сканировать QR-код", exact: true })
    .click();
  await expect(
    page.getByRole("img", { name: /QR-код AmneziaVPN/ }),
  ).toBeVisible();
  await page.getByRole("dialog").evaluate((el) => {
    el.scrollTop = el.scrollHeight;
  });
  const close = page.getByRole("button", { name: "Закрыть", exact: true });
  const bounds = await close.boundingBox();
  expect(bounds).not.toBeNull();
  expect(bounds!.y).toBeGreaterThanOrEqual(0);
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(568);
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await page.screenshot({
    animations: "disabled",
    path: "test-results/mobile-connect.png",
  });
  await close.click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("button", { name: "Открыть меню", exact: true }).click();
  await expect(page.locator(".sidebar-theme")).toBeVisible();
  await page.getByRole("button", { name: "Тёмное", exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.screenshot({
    animations: "disabled",
    path: "test-results/mobile-menu-dark.png",
  });
});

test("audit user dropdown loads every page and sends chosen user filter", async ({
  page,
}) => {
  await mock(page);
  const secondId = "33333333-3333-4333-8333-333333333333";
  await page.route("**/api/v1/admin/users?**", async (route) => {
    const n = Number(new URL(route.request().url()).searchParams.get("page"));
    const selected =
      n === 0
        ? { ...user, internalName: "Александр" }
        : {
            ...user,
            id: secondId,
            nickname: "maria",
            internalName: "Мария",
            status: "DISABLED",
          };
    return route.fulfill({
      json: { ...paged([selected]), page: n, totalPages: 2, totalElements: 2 },
    });
  });
  await login(page);
  await page.getByRole("link", { name: "Журнал событий", exact: true }).click();
  const picker = page.getByRole("combobox", {
    name: "Пользователь",
    exact: true,
  });
  await expect(picker).toBeEnabled();
  await expect(picker.locator("option")).toHaveCount(3);
  await expect(
    picker.locator("option", { hasText: "maria — Мария (отключён)" }),
  ).toHaveCount(1);
  const filtered = page.waitForRequest((req) => {
    const url = new URL(req.url());
    return (
      url.pathname.endsWith("/admin/audit") &&
      url.searchParams.get("userId") === secondId
    );
  });
  await picker.selectOption(secondId);
  const url = new URL((await filtered).url());
  expect(url.searchParams.get("page")).toBe("0");
  const cleared = page.waitForRequest((req) => {
    const url = new URL(req.url());
    return (
      url.pathname.endsWith("/admin/audit") && !url.searchParams.has("userId")
    );
  });
  await picker.selectOption("");
  await cleared;
  await expect(
    page.getByRole("textbox", { name: "ID пользователя" }),
  ).toHaveCount(0);
});

test("account nickname updates sidebar and persists after reload", async ({
  page,
}) => {
  await mock(page, "USER");
  await login(page);
  await page.getByRole("link", { name: "Открыть профиль" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .locator(".account-setting-row")
    .filter({ has: page.getByText("Никнейм", { exact: true }) })
    .click({ position: { x: 8, y: 8 } });
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Изменить никнейм", exact: true })
    .click({ position: { x: 8, y: 8 } });
  await page
    .getByRole("textbox", { name: "Никнейм", exact: true })
    .fill("new-alex");
  await page
    .getByRole("button", { name: "Сохранить никнейм", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Никнейм сохранён");
  await expect(
    page.getByRole("link", { name: "Открыть профиль" }),
  ).toContainText("new-alex");
  await page.reload();
  await expect(
    page.getByRole("link", { name: "Открыть профиль" }),
  ).toContainText("new-alex");
  await expect(page.getByRole("textbox", { name: "Никнейм" })).toHaveCount(0);
});

test("account password validates confirmation, preserves session on rejection and clears secrets after success", async ({
  page,
}) => {
  await mock(page, "USER");
  await login(page);
  await page.getByRole("link", { name: "Открыть профиль" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .locator(".account-setting-row")
    .filter({ has: page.getByText("Пароль", { exact: true }) })
    .click({ position: { x: 8, y: 8 } });
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Изменить пароль", exact: true })
    .click({ position: { x: 8, y: 8 } });
  const current = page.getByLabel("Текущий пароль", { exact: true });
  const next = page.getByLabel("Новый пароль", { exact: true });
  const repeat = page.getByLabel("Повторите новый пароль", { exact: true });
  await current.fill("wrong-password");
  await next.fill("new-password");
  await repeat.fill("other-password");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Изменить пароль", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("не совпадают");
  await repeat.fill("new-password");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Изменить пароль", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Текущий пароль введён неверно",
  );
  await expect(
    page.getByRole("heading", { name: "Профиль", exact: true }),
  ).toBeVisible();
  await current.fill("test-password");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Изменить пароль", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Пароль изменён");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Изменить пароль", exact: true })
    .click();
  await expect(current).toHaveValue("");
  await expect(next).toHaveValue("");
  await expect(repeat).toHaveValue("");
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Профиль", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    animations: "disabled",
    path: "test-results/account.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await page.screenshot({
    animations: "disabled",
    path: "test-results/account-mobile.png",
    fullPage: true,
  });
});

test("empty lists hide table headers and pagination", async ({ page }) => {
  await mock(page);
  await page.route("**/api/v1/admin/users?**", (route) =>
    route.fulfill({ json: paged([]) }),
  );
  await page.route("**/api/v1/admin/audit?**", (route) =>
    route.fulfill({ json: paged([]) }),
  );
  await login(page);
  for (const path of ["/users", "/audit"]) {
    await page.goto(path);
    await expect(
      page.getByText("Ничего не найдено", { exact: true }),
    ).toBeVisible();
    await expect(page.locator("table")).toHaveCount(0);
    await expect(page.locator(".pagination")).toHaveCount(0);
    if (path === "/users") {
      const input = await page
        .getByRole("textbox", { name: "Поиск пользователей" })
        .boundingBox();
      const select = await page
        .getByRole("combobox", { name: "Статус пользователя" })
        .boundingBox();
      expect(input).not.toBeNull();
      expect(select).not.toBeNull();
      expect(Math.abs(input!.height - select!.height)).toBeLessThan(1);
      expect(Math.abs(input!.y - select!.y)).toBeLessThan(1);
    }
  }
});
test("device owner search accepts names without sending invalid IDs", async ({
  page,
}) => {
  await mock(page);
  const ids: string[] = [];
  await page.route("**/api/v1/admin/devices?**", (route) => {
    ids.push(new URL(route.request().url()).searchParams.get("userId") || "");
    return route.fulfill({ json: paged([]) });
  });
  await login(page);
  await page.goto("/devices");
  await page.getByPlaceholder("Имя или никнейм").fill("Неизвестный");
  await expect(
    page.getByText("Пользователь не найден. Попробуйте другое имя."),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page.getByPlaceholder("Имя или никнейм").fill("Александр");
  await page.getByLabel("Пользователь", { exact: true }).selectOption(user.id);
  await expect.poll(() => ids.at(-1)).toBe(user.id);
  expect(ids.every((id) => id === "" || id === user.id)).toBe(true);
});
test("dashboard shows counts and events while VPN is delayed", async ({
  page,
}) => {
  await mock(page);
  let release!: () => void;
  const wait = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/admin/dashboard", async (route) => {
    await wait;
    await route.fulfill({
      json: {
        usersTotal: 1,
        devicesTotal: 1,
        vpn: { serverUp: true, peersTotal: 1, peersActive: 1 },
        infrastructure: {},
      },
    });
  });
  await login(page);
  await expect(
    page.getByRole("status", { name: "Загрузка состояния VPN" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Последние события" }),
  ).toBeVisible();
  await expect(
    page.getByText("Устройство добавлено", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".stats strong")).toHaveText(["8", "12"]);
  await expect(
    page
      .locator(".infra-row")
      .filter({ hasText: "Приложение" })
      .locator(".badge"),
  ).toBeVisible();
  await expect(page.getByLabel("Загрузка: SSH-подключение")).toBeVisible();
  release();
  await expect(
    page.getByRole("status", { name: "Загрузка состояния VPN" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Инфраструктура" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Последние события" }),
  ).toBeVisible();
  await expect(page.locator(".dashboard-skeleton")).toHaveCount(0);
});

for (const [path, endpoint, label, result] of [
  [
    "/vpn",
    "/admin/vpn",
    "Загрузка VPN-сервера",
    { serverUp: true, peersTotal: 2, peersActive: 1 },
  ],
  ["/my-devices", "/devices", "Загрузка устройств", paged([device])],
  ["/devices", "/admin/devices", "Загрузка устройств", paged([device])],
] as const) {
  test(`loading skeleton on ${path} preserves content during refresh`, async ({
    page,
  }) => {
    await mock(page);
    await login(page);
    let release!: () => void;
    let pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route(`**/api/v1${endpoint}*`, async (route) => {
      await pending;
      await route.fulfill({ json: result });
    });
    await page.goto(path);
    await expect(page.getByRole("status", { name: label })).toBeVisible();
    await expect(
      page.getByText("Загружаем данные…", { exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByText("Устройств пока нет", { exact: true }),
    ).toHaveCount(0);
    release();
    await expect(page.getByRole("status", { name: label })).toHaveCount(0);
    const content =
      path === "/vpn"
        ? page.getByRole("heading", { name: "AmneziaWG" })
        : page.getByRole("button", { name: device.name, exact: true });
    await expect(content).toBeVisible();
    if (path === "/vpn") {
      pending = new Promise<void>((resolve) => {
        release = resolve;
      });
      await page.getByRole("button", { name: "Обновить" }).click();
      await expect(content).toBeVisible();
      await expect(page.getByRole("status", { name: label })).toHaveCount(0);
      release();
      await expect(
        page.getByRole("button", { name: "Обновить" }),
      ).toBeEnabled();
    }
  });
}

test("device page loads once when migration status arrives", async ({
  page,
}) => {
  await mock(page, "USER");
  let release!: () => void;
  const ready = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/vpn/migration", async (route) => {
    await ready;
    await route.fulfill({
      json: { generation: 1, maintenance: false, outdatedDevices: 0 },
    });
  });
  let deviceRequests = 0;
  page.on("request", (req) => {
    if (
      new URL(req.url()).pathname === "/api/v1/devices" &&
      req.method() === "GET"
    )
      deviceRequests++;
  });
  await login(page);
  await expect(
    page.getByRole("button", { name: "MacBook Pro", exact: true }),
  ).toBeVisible();
  expect(deviceRequests).toBe(1);
  release();
  await expect(
    page.getByRole("button", { name: "Добавить устройство" }),
  ).toBeEnabled();
  await page.waitForLoadState("networkidle");
  expect(deviceRequests).toBe(1);
});

test("admin device details show directional traffic and handle unavailable stats", async ({
  page,
}) => {
  await mock(page);
  let status = "ONLINE";
  await page.route("**/api/v1/admin/devices/" + device.id, (route) =>
    route.fulfill({ json: { ...device, owner: user, status } }),
  );
  await login(page);
  await page.goto("/devices");
  await page.getByRole("button", { name: "Открыть", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("В сети", { exact: true })).toBeVisible();
  await expect(dialog.getByText("10.8.1.2")).toBeVisible();
  await expect(
    dialog.locator(".device-traffic-grid > div").first(),
  ).toContainText("3,2 МБ");
  await expect(
    dialog.locator(".device-traffic-grid > div").last(),
  ).toContainText("1,2 МБ");
  await page.screenshot({ path: "test-results/admin-device-details.png" });
  await page.keyboard.press("Escape");
  status = "UNKNOWN";
  await page.getByRole("button", { name: "Открыть", exact: true }).click();
  await expect(
    dialog.getByText("Не удалось получить статистику с VPN-сервера."),
  ).toBeVisible();
  await expect(dialog.locator(".device-traffic-grid strong")).toHaveText([
    "—",
    "—",
  ]);
  await page.keyboard.press("Escape");
  status = "OUTDATED";
  await page.getByRole("button", { name: "Открыть", exact: true }).click();
  await expect(
    dialog.getByText("Статистика старого сервера недоступна."),
  ).toBeVisible();
  await expect(dialog.locator(".device-traffic-grid strong")).toHaveText([
    "—",
    "—",
  ]);
});

test("dashboard keeps ready blocks visible while events are loading", async ({
  page,
}) => {
  await mock(page);
  let release!: () => void;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/admin/audit?size=5", async (route) => {
    await pending;
    await route.fulfill({ json: paged([]) });
  });
  await login(page);
  await expect(
    page.getByRole("status", { name: "Загрузка последних событий" }),
  ).toBeVisible();
  await expect(page.locator(".stats strong")).toHaveText(["8", "12"]);
  await expect(
    page.getByRole("heading", { name: "AmneziaWG", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".dashboard-skeleton")).toHaveCount(0);
  release();
  await expect(
    page.getByRole("status", { name: "Загрузка последних событий" }),
  ).toHaveCount(0);
});
