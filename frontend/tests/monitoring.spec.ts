import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";

test("Grafana tabs, Russian explanations, SQLite metrics and route grouping", async ({
  page,
}) => {
  test.skip(
    process.env.RUN_MONITORING_TESTS !== "1",
    "Requires the local Docker monitoring stack.",
  );
  const settings = Object.fromEntries(
    readFileSync("../.env", "utf8")
      .split(/\r?\n/)
      .filter((line) => line.includes("=") && !line.startsWith("#"))
      .map((line) => [
        line.slice(0, line.indexOf("=")),
        line.slice(line.indexOf("=") + 1),
      ]),
  );
  const origin = `http://127.0.0.1:${settings.GRAFANA_PORT || "3000"}`;
  const login = await page.request.post(`${origin}/login`, {
    data: {
      user: settings.GRAFANA_ADMIN_USER || "admin",
      password: settings.GRAFANA_ADMIN_PASSWORD,
    },
  });
  expect(login.status()).toBe(200);
  const response = await page.request.get(
    `${origin}/apis/dashboard.grafana.app/v2beta1/namespaces/default/dashboards/amnezia-control-panel`,
  );
  expect(response.status()).toBe(200);
  const dashboard = (await response.json()).spec;
  expect(dashboard.layout.kind).toBe("TabsLayout");
  expect(
    dashboard.layout.spec.tabs.map(
      (tab: { spec: { title: string } }) => tab.spec.title,
    ),
  ).toEqual(["Приложение", "VPN"]);
  expect(dashboard.variables[0].spec.name).toBe("http_route");
  expect(dashboard.variables[0].spec.includeAll).toBe(true);
  const panels = Object.values(dashboard.elements) as {
    spec: {
      id: number;
      title: string;
      description: string;
      data: {
        spec: {
          queries: {
            spec: { query: { spec: { expr: string; legendFormat: string } } };
          }[];
        };
      };
    };
  }[];
  for (const panel of panels) {
    expect(/[А-Яа-я]/.test(panel.spec.description)).toBe(true);
    for (const query of panel.spec.data.spec.queries) {
      const expr = query.spec.query.spec.expr
        .replaceAll("$__rate_interval", "1m")
        .replaceAll("$http_route", ".*");
      const result = await page.request.get(
        `http://127.0.0.1:${settings.PROMETHEUS_PORT || "9090"}/api/v1/query`,
        { params: { query: expr } },
      );
      expect(result.status()).toBe(200);
      expect((await result.json()).status).toBe("success");
      if (panel.spec.id >= 16)
        expect((await result.json()).data.result.length).toBeGreaterThan(0);
      if ([7, 8].includes(panel.spec.id))
        expect(expr).toContain("sum by (uri, method, status)");
    }
  }
  const httpQueries: string[] = [];
  page.on("request", (request) => {
    if (
      request.url().includes("/api/ds/query") &&
      request.method() === "POST"
    ) {
      for (const query of request.postDataJSON()?.queries || []) {
        if (query.expr?.includes("http_server_requests_seconds"))
          httpQueries.push(query.expr);
      }
    }
  });
  await page.goto(
    `${origin}/d/amnezia-control-panel?var-http_route=${encodeURIComponent("/api/v1/auth/login")}`,
  );
  await page.getByText("Приложение", { exact: true }).first().click();
  await expect(
    page.getByText("Доступность приложения", { exact: true }),
  ).toHaveCount(0);
  await page.getByText("Приложение и HTTP", { exact: true }).click();
  await expect(
    page.getByText("Маршрут HTTP", { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByText("● Работает", { exact: true })).toBeVisible();
  await expect.poll(() => httpQueries.length).toBeGreaterThan(0);
  expect(
    httpQueries.every(
      (expr) => expr.includes("/api/v1/auth/login") && !expr.includes("\\/"),
    ),
  ).toBe(true);
  httpQueries.length = 0;
  await page.goto(
    `${origin}/d/amnezia-control-panel?var-http_route=${encodeURIComponent("$__all")}`,
  );
  await page.getByText("Приложение и HTTP", { exact: true }).click();
  await expect.poll(() => httpQueries.length).toBeGreaterThan(0);
  expect(httpQueries.every((expr) => expr.includes('uri=~".*"'))).toBe(true);
  await page.getByText("База данных · SQLite", { exact: true }).click();
  await expect(
    page.getByText("Пул SQLite — соединения", { exact: true }),
  ).toBeVisible();
  await page.getByText("VPN", { exact: true }).first().click();
  await expect(
    page.getByText("Получено от устройств — байты RX", { exact: true }),
  ).toHaveCount(0);
  await page.getByText("Устройства и трафик", { exact: true }).click();
  await expect(
    page.getByText("Получено от устройств — байты RX", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Пул SQLite — соединения", { exact: true }),
  ).toHaveCount(0);
});
