const { defineConfig } = require("@playwright/test");
module.exports = defineConfig({
  testDir: ".", testMatch: "*.spec.js", fullyParallel: true,
  use: { baseURL: "http://127.0.0.1:4179", headless: true },
  webServer: { command: "node server.js", url: "http://127.0.0.1:4179", reuseExistingServer: false },
});
