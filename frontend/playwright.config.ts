import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  testMatch: "*.spec.ts",
  use: {
    baseURL: process.env.PANEL_TEST_URL || "http://127.0.0.1:5173",
    browserName: "chromium",
    channel: "msedge",
  },
  reporter: "list",
  workers: 1,
});
