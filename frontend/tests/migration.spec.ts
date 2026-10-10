import { expect, test, type Page } from "@playwright/test";

async function migrationApp(page: Page, role = "USER") {
  let generation = 2;
  let maintenance = false;
  let devices = [1, 2].map((n) => ({
    id: `old-${n}`,
    name: `Старый ${n}`,
    icon: "PHONE",
    generation: 1,
    status: "OUTDATED",
    vpnIp: `10.8.1.${n + 1}`,
    publicKey: "key",
    createdAt: "2026-10-09T00:00:00Z",
  }));
  let failComplete = false;
  let server = {
    ip: "192.0.2.10",
    sshPort: 22,
    username: "root",
    vpnPort: 51820,
    hostKey: "",
  };
  let originalIp = server.ip;
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace("/api/v1", "");
    const status = () => ({
      generation,
      maintenance,
      outdatedDevices: devices.filter((d) => d.generation < generation).length,
    });
    let data: unknown = {};
    if (path === "/auth/refresh") data = { accessToken: "test" };
    else if (path === "/auth/me")
      data = { id: "user", nickname: "alex", role, status: "ACTIVE" };
    else if (path === "/vpn/migration") data = status();
    else if (path === "/admin/vpn/migration/start") {
      originalIp = server.ip;
      maintenance = true;
      data = status();
    } else if (path === "/admin/vpn/migration/complete") {
      if (failComplete)
        return route.fulfill({
          status: 502,
          json: { code: "VPN_UNAVAILABLE", message: "Новый сервер недоступен" },
        });
      maintenance = false;
      if (server.ip !== originalIp) generation++;
      data = status();
    } else if (path === "/admin/vpn/server") {
      if (req.method() === "PUT") {
        const { privateKey, ...settings } = req.postDataJSON();
        server = settings;
      }
      data = server;
    } else if (path === "/admin/vpn")
      data = {
        serverUp: true,
        peersTotal: 2,
        peersActive: 0,
        checkedAt: "2026-10-09T00:00:00Z",
      };
    else if (path === "/devices" && req.method() === "POST") {
      data = {
        ...devices[0],
        ...req.postDataJSON(),
        id: "new",
        generation,
        status: "OFFLINE",
      };
      devices.push(data as (typeof devices)[number]);
    } else if (path === "/devices")
      data = {
        content: devices,
        totalElements: devices.length,
        totalPages: 1,
        page: 0,
        size: 12,
      };
    else if (path.startsWith("/devices/") && req.method() === "DELETE") {
      devices = devices.filter((d) => d.id !== path.split("/")[2]);
      return route.fulfill({ status: 204 });
    } else if (path.startsWith("/devices/"))
      data = devices.find((d) => d.id === path.split("/")[2]);
    return route.fulfill({ json: data });
  });
  return {
    generation: () => generation,
    failCompletion: () => {
      failComplete = true;
    },
    recover: () => {
      failComplete = false;
    },
  };
}

test("banner survives creation, navigation and reload until the last old config is deleted", async ({
  page,
}) => {
  await migrationApp(page);
  await page.goto("/my-devices");
  const banner = page.getByRole("status", { name: "Переезд VPN" });
  await expect(banner).toContainText("Старых конфигураций осталось: 2");
  await page.screenshot({
    path: "test-results/vpn-migration-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(banner).toBeVisible();
  await page.screenshot({
    path: "test-results/vpn-migration-mobile.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 1280, height: 720 });
  await page.getByRole("button", { name: "Старый 1", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "Устарел после переезда",
  );
  await expect(
    page.getByRole("button", { name: "Скачать .conf файл" }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "Добавить устройство" }).click();
  await page.getByLabel("Название", { exact: true }).fill("Новый телефон");
  await page
    .getByRole("button", { name: "Создать устройство", exact: true })
    .click();
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await expect(banner).toContainText("Старых конфигураций осталось: 2");
  await page.getByRole("link", { name: "Инструкции", exact: true }).click();
  await expect(banner).toBeVisible();
  await page.reload();
  await expect(banner).toContainText("Старых конфигураций осталось: 2");
  await expect(
    page.getByRole("link", { name: "Обновить мои конфигурации" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "Мои устройства", exact: true }).click();
  for (let n = 1; n <= 2; n++) {
    await page
      .getByRole("button", { name: `Удалить Старый ${n}`, exact: true })
      .click();
    await page
      .getByRole("button", { name: "Подтвердить", exact: true })
      .click();
    if (n === 1)
      await expect(banner).toContainText("Старых конфигураций осталось: 1");
  }
  await expect(banner).toHaveCount(0);
  await page.reload();
  await expect(banner).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Новый телефон", exact: true }),
  ).toBeVisible();
});

test("admin migration disables config creation and retains maintenance on failed verification", async ({
  page,
}) => {
  const app = await migrationApp(page, "ADMIN");
  await page.goto("/vpn");
  await expect(page.getByRole("heading", { name: "AmneziaWG" })).toBeVisible();
  await page.screenshot({ path: "test-results/vpn-controls.png" });
  await expect(
    page.getByRole("button", { name: "Сохранить сервер" }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Добавить новый сервер", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Сохранить сервер" }),
  ).toBeDisabled();
  await page.keyboard.press("Escape");
  await page
    .getByRole("button", { name: "Начать работы", exact: true })
    .click();
  await page.getByRole("button", { name: "Подтвердить", exact: true }).click();
  await expect(page.getByRole("status", { name: "Переезд VPN" })).toContainText(
    "Проводится перенос VPN",
  );
  await page.getByRole("link", { name: "Мои устройства", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Добавить устройство" }),
  ).toBeDisabled();
  await page.goto("/vpn");
  await page
    .getByRole("button", { name: "Добавить новый сервер", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Сохранить сервер" }),
  ).toBeEnabled();
  await page.screenshot({
    path: "test-results/vpn-server-form.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.screenshot({ path: "test-results/vpn-form-mobile.png" });
  await page.setViewportSize({ width: 1280, height: 720 });
  await page.getByLabel("IP сервера", { exact: true }).fill("192.0.2.11");
  await page
    .getByLabel("Приватный SSH-ключ", { exact: true })
    .fill("fixture-private-key");
  await page
    .getByLabel("Публичный ключ SSH-сервера", { exact: true })
    .fill("ssh-ed25519 fixture-public-key");
  await page.getByRole("button", { name: "Сохранить сервер" }).click();
  await expect(
    page.getByLabel("Приватный SSH-ключ", { exact: true }),
  ).toHaveValue("");
  await page.reload();
  await page
    .getByRole("button", { name: "Добавить новый сервер", exact: true })
    .click();
  await expect(page.getByLabel("IP сервера", { exact: true })).toHaveValue(
    "192.0.2.11",
  );
  await page.keyboard.press("Escape");
  app.failCompletion();
  await page
    .getByRole("button", { name: "Закончить работы", exact: true })
    .click();
  await page.getByRole("button", { name: "Подтвердить", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Новый сервер недоступен",
  );
  await expect(page.getByRole("status", { name: "Переезд VPN" })).toContainText(
    "Проводится перенос VPN",
  );
  app.recover();
  await page.getByRole("button", { name: "Подтвердить", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Начать работы", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("status", { name: "Переезд VPN" })).toContainText(
    "VPN переехал на новый сервер",
  );
});

test("finishing maintenance without changing IP preserves generation", async ({
  page,
}) => {
  const app = await migrationApp(page, "ADMIN");
  await page.goto("/vpn");
  await page
    .getByRole("button", { name: "Начать работы", exact: true })
    .click();
  await page.getByRole("button", { name: "Подтвердить", exact: true }).click();
  await page.getByRole("link", { name: "Мои устройства", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Удалить Старый 1", exact: true }),
  ).toBeDisabled();
  await page.goto("/vpn");
  await page
    .getByRole("button", { name: "Закончить работы", exact: true })
    .click();
  await page.getByRole("button", { name: "Подтвердить", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Начать работы", exact: true }),
  ).toBeVisible();
  expect(app.generation()).toBe(2);
});

test("VPN problem banner coexists with migration, clears on recovery and hides during maintenance", async ({
  page,
}) => {
  await migrationApp(page);
  let unavailable = true;
  let maintenance = false;
  await page.route("**/api/v1/vpn/migration", (route) =>
    route.fulfill({
      json: {
        generation: 2,
        maintenance,
        outdatedDevices: 2,
        vpnUnavailable: unavailable,
      },
    }),
  );
  await page.goto("/my-devices");
  const problem = page.getByRole("status", { name: "Проблемы с VPN" });
  await expect(problem).toContainText("Пожалуйста, напишите администратору.");
  await expect(page.getByRole("status", { name: "Переезд VPN" })).toBeVisible();
  await page.screenshot({
    path: "test-results/vpn-problem-banner.png",
    fullPage: true,
  });
  unavailable = false;
  await page.getByRole("link", { name: "Инструкции", exact: true }).click();
  await expect(problem).toHaveCount(0);
  unavailable = true;
  maintenance = true;
  await page.getByRole("link", { name: "Мои устройства", exact: true }).click();
  await expect(page.getByRole("status", { name: "Переезд VPN" })).toContainText(
    "Проводится перенос VPN",
  );
  await expect(problem).toHaveCount(0);
});
