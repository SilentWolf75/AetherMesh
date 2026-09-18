const { test, expect } = require("@playwright/test");
const { createHash } = require("crypto");
const bytes = Buffer.from([1, 2, 3, 4]);
const hash = createHash("sha256").update(bytes).digest("hex");
const manifest = [
  { name: "Heltec V4", file: "heltec-v4.bin", size: 4, sha256: hash },
  { name: "Heltec V3", file: "heltec-v3.bin", size: 4, sha256: hash },
  { name: "RAK4631", file: "rak4631.uf2", size: 4, sha256: hash },
];

// The Pages deploy mirrors each channel's published build next to the flasher
// (firmware/release/ and firmware/beta/), because browsers cannot fetch GitHub
// Release assets cross-origin. Mock those same-origin paths so every test runs
// the real download-and-verify path: a test that only passes because no catalog
// loaded proves nothing about verification.
async function setup(page, entries = manifest, channels = null) {
  const published = channels || { release: { tag: "v9.9.9", entries } };
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
  for (const channel of ["release", "beta"]) {
    await page.route(`**/firmware/${channel}/**`, route => {
      const name = new URL(route.request().url()).pathname.split("/").pop();
      const ch = published[channel];
      if (!ch) return route.fulfill({ status: 404, body: "" });
      if (name === "manifest.json") return route.fulfill({ json: ch.entries });
      if (name === "release.json") return route.fulfill({ json: { tag: ch.tag } });
      return route.fulfill({ body: bytes });
    });
  }
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
  await expect(page.locator("#fw")).toContainText("Nothing published");
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

for (const [target, file] of [
  ["lilygo-t-echo", "aethermesh-t-echo-abcdef0.uf2"],
  ["seeed-t1000-e", "aethermesh-t1000-e-abcdef0.uf2"],
]) {
  test(target + " downloads only its verified UF2", async ({ page }) => {
    await setup(page, [
      { name: "Unrelated", board: "rak4631", file, size: 4, sha256: hash },
      { name: target, board: target, file: "correct-" + file, size: 4, sha256: hash },
    ]);
    await page.locator('[data-val="' + target + '"]').click();
    await expect(page.locator("#uf2-guide")).toBeVisible();
    const download = page.waitForEvent("download");
    await page.locator("#download-uf2-btn").click();
    expect((await download).suggestedFilename()).toBe("correct-" + file);
    expect(await page.evaluate(() => window.serialRequests)).toBe(0);
  });
}

test("release channel never offers a pre-release", async ({ page }) => {
  // Only a beta is mirrored. Asking for Release must come back empty rather
  // than quietly handing over the beta.
  await setup(page, [], { beta: { tag: "v9.9.9-beta.1", entries: manifest } });
  await expect(page.locator("#fw")).toContainText("Nothing published");
  await expect(page.locator("#channel-note")).toContainText("No release published yet");
});

test("beta channel lists only the pre-release", async ({ page }) => {
  const betaOnly = [{ name: "Heltec V4 beta", file: "heltec-v4-beta.bin", size: 4, sha256: hash }];
  await setup(page, [], {
    release: { tag: "v9.9.9", entries: manifest },
    beta: { tag: "v9.9.10-beta.1", entries: betaOnly },
  });
  await page.locator("#channel").selectOption("beta");
  await expect(page.locator("#fw")).toContainText("Heltec V4 beta");
  await expect(page.locator("#channel-note")).toContainText("v9.9.10-beta.1");
});

test("UF2 boards can choose a channel and download that channel's build", async ({ page }) => {
  // nRF52 boards hide the serial firmware step, so the channel control must
  // live outside it, or those boards silently get whatever channel loaded first.
  await setup(page, [], {
    release: { tag: "v9.9.9", entries: [
      { name: "RAK release", board: "rak4631", file: "rak-release.uf2", size: 4, sha256: hash }] },
    beta: { tag: "v9.9.10-beta.1", entries: [
      { name: "RAK beta", board: "rak4631", file: "rak-beta.uf2", size: 4, sha256: hash }] },
  });
  await page.locator('[data-val="rak4631"]').click();
  await expect(page.locator("#uf2-guide")).toBeVisible();
  await expect(page.locator("#channel")).toBeVisible();
  await page.locator("#channel").selectOption("beta");
  await expect(page.locator("#channel-note")).toContainText("v9.9.10-beta.1");
  const download = page.waitForEvent("download");
  await page.locator("#download-uf2-btn").click();
  expect((await download).suggestedFilename()).toBe("rak-beta.uf2");
});
