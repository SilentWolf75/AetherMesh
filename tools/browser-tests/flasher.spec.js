const { test, expect } = require("@playwright/test");
const { createHash } = require("crypto");
const bytes = Buffer.from([1, 2, 3, 4]);
const hash = createHash("sha256").update(bytes).digest("hex");
const manifest = [
  { name: "Heltec V4", file: "heltec-v4.bin", size: 4, sha256: hash },
  { name: "Heltec V3", file: "heltec-v3.bin", size: 4, sha256: hash },
  { name: "RAK4631", file: "rak4631.uf2", size: 4, sha256: hash },
];
async function setup(page, entries = manifest) {
  await page.addInitScript(() => {
    window.serialRequests = 0;
    window.writtenFirmware = null;
    Object.defineProperty(navigator, "serial", { value: {
      addEventListener() {},
      async requestPort() { window.serialRequests++; return {}; }
    }});
  });
  await page.route("https://unpkg.com/**", route => route.fulfill({
    contentType: "text/javascript",
    body: `export class Transport { async disconnect() {} }
      export class ESPLoader {
        async main() { return "ESP32-S3"; }
        async writeFlash(options) { window.writtenFirmware = Array.from(options.fileArray[0].data, x => x.charCodeAt(0)); }
        async after() {}
      }`
  }));
  await page.route("**/firmware/manifest.json?*", route => route.fulfill({ json: entries }));
  await page.route("**/firmware/*.bin", route => route.fulfill({ body: bytes }));
  await page.route("**/firmware/*.uf2", route => route.fulfill({ body: bytes }));
  await page.goto("/");
  await expect(page.locator("#fw")).not.toContainText("Loading");
}
test("board selection switches between filtered ESP builds and UF2 instructions", async ({ page }) => {
  await setup(page);
  await page.locator('[data-val="heltec-v3"]').click();
  await expect(page.locator("#fw")).toContainText("Heltec V3");
  await expect(page.locator("#fw")).not.toContainText("Heltec V4");
  await page.locator('[data-val="rak4631"]').click();
  await expect(page.locator("#uf2-guide")).toBeVisible();
  await expect(page.locator("#step-3-card")).toBeHidden();
  const download = page.waitForEvent("download");
  await page.locator("#download-uf2-btn").click();
  expect((await download).suggestedFilename()).toBe("rak4631.uf2");
});
test("missing catalog keeps local upload usable and rejects invalid input", async ({ page }) => {
  await setup(page, []);
  await expect(page.locator("#fw")).toContainText("No bundled");
  await page.locator("#file").setInputFiles({ name: "wrong.zip", mimeType: "application/zip", buffer: bytes });
  await expect(page.locator("#status")).toContainText("must be a .bin");
  await page.locator("#flash").click();
  expect(await page.evaluate(() => window.serialRequests)).toBe(0);
});
test("dragged file is the exact firmware flashed and can be removed", async ({ page }) => {
  await setup(page, []);
  const data = await page.evaluateHandle(() => {
    const transfer = new DataTransfer();
    transfer.items.add(new File([new Uint8Array([9, 8, 7])], "local.bin"));
    return transfer;
  });
  await page.locator("#dropzone").dispatchEvent("drop", { dataTransfer: data });
  await expect(page.locator("#file-chip-name")).toHaveText("local.bin");
  await page.locator("#flash").click();
  await expect(page.locator("#status")).toContainText("Done.");
  expect(await page.evaluate(() => window.writtenFirmware)).toEqual([9, 8, 7]);
  await page.locator("#file-chip-remove").click();
  await expect(page.locator("#dropzone")).toBeVisible();
});
for (const [name, entry] of [
  ["digest mismatch", { ...manifest[0], sha256: "0".repeat(64) }],
  ["size mismatch", { ...manifest[0], size: 5 }],
  ["missing integrity metadata", { name: "Heltec V4", file: "heltec-v4.bin" }],
]) {
  test(name + " blocks flashing before opening a port", async ({ page }) => {
    await setup(page, [entry]);
    await page.locator("#flash").click();
    await expect(page.locator("#status")).toContainText("Failed:");
    expect(await page.evaluate(() => window.serialRequests)).toBe(0);
    expect(await page.evaluate(() => window.writtenFirmware)).toBeNull();
  });
}
