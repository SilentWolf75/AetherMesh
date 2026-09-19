const { test, expect } = require("@playwright/test");
const { createHash } = require("crypto");
const bytes = Buffer.from([1, 2, 3, 4]);
const hash = createHash("sha256").update(bytes).digest("hex");
const manifest = [
  { name: "Heltec V4", file: "heltec-v4.bin", size: 4, sha256: hash },
  { name: "Heltec V3", file: "heltec-v3.bin", size: 4, sha256: hash },
  { name: "RAK4631", file: "rak4631.uf2", size: 4, sha256: hash },
];

// An ESP-IDF partition table with NVS at 0x9000 (the real Heltec V4 layout).
function partitionTable(nvsOffset = 0x9000, nvsSize = 0x5000) {
  const t = Buffer.alloc(0xC00, 0xFF);
  const rows = [["nvs", 1, 0x02, nvsOffset, nvsSize], ["otadata", 1, 0x00, 0xE000, 0x2000], ["app0", 0, 0x10, 0x10000, 0x640000]];
  rows.forEach(([name, type, sub, off, size], i) => {
    const e = Buffer.alloc(32, 0);
    e[0] = 0xAA; e[1] = 0x50; e[2] = type; e[3] = sub;
    e.writeUInt32LE(off, 4); e.writeUInt32LE(size, 8); e.write(name, 12, "ascii");
    e.copy(t, i * 32);
  });
  return t;
}

// A merged image laid out the way the release packager builds it: the NVS
// range is present and blank, which is exactly why writing it whole erases
// a node's settings.
const merged = (() => {
  const img = Buffer.alloc(0x20000, 0x11);
  partitionTable().copy(img, 0x8000);
  img.fill(0xFF, 0x9000, 0xE000);
  return img;
})();
const mergedHash = createHash("sha256").update(merged).digest("hex");

// The Pages deploy mirrors each channel's published builds next to the flasher
// (firmware/<channel>/index.json plus one folder per version), because browsers
// cannot fetch GitHub Release assets cross-origin. Mock those same-origin paths
// so every test runs the real download-and-verify path: a test that passes only
// because nothing loaded proves nothing about verification.
async function setup(page, entries = manifest, channels = null, device = {}) {
  const published = channels || { release: [{ tag: "v9.9.9", entries }] };
  await page.addInitScript((deviceTable) => {
    window.serialRequests = 0;
    window.opened = [];
    window.writes = null;
    window.eraseAll = null;
    window.deviceTable = deviceTable;
    Object.defineProperty(navigator, "serial", { value: {
      addEventListener() {},
      async requestPort() {
        window.serialRequests++;
        return {
          async open(options) { window.opened.push(options.baudRate); },
          async setSignals() {},
          async close() {},
        };
      }
    }});
  }, device.table ? Array.from(device.table) : Array.from(partitionTable()));
  await page.route("https://unpkg.com/**", route => route.fulfill({
    contentType: "text/javascript",
    body: `export class Transport { async disconnect() {} }
      export class ESPLoader {
        async main() { return "ESP32-S3"; }
        async readFlash(address, size) { return new Uint8Array(window.deviceTable); }
        async writeFlash(options) {
          window.eraseAll = options.eraseAll;
          window.writes = options.fileArray.map(f => ({ address: f.address, length: f.data.length,
            data: Array.from(f.data.slice(0, 8), x => x.charCodeAt(0)) }));
        }
        async after() {}
      }`
  }));
  await page.route("**/firmware/**", route => {
    const parts = new URL(route.request().url()).pathname.split("/");
    const at = parts.indexOf("firmware");
    const channel = parts[at + 1];
    const versions = published[channel];
    if (!versions) return route.fulfill({ status: 404, body: "" });
    if (parts[at + 2] === "index.json") {
      return route.fulfill({ json: versions.map(v => ({ tag: v.tag, name: v.tag, published: "2026-09-18T00:00:00Z", notes: v.notes || "" })) });
    }
    const version = versions.find(v => v.tag === parts[at + 2]);
    if (!version) return route.fulfill({ status: 404, body: "" });
    const name = parts[at + 3];
    if (name === "manifest.json") return route.fulfill({ json: version.entries });
    return route.fulfill({ body: (version.files && version.files[name]) || bytes });
  });
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
  await setup(page, [], {});
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
  const writes = await page.evaluate(() => window.writes);
  expect(writes).toEqual([{ address: 0, length: 3, data: [9, 8, 7] }]);
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
    expect(await page.evaluate(() => window.writes)).toBeNull();
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

test("stable channel never offers a pre-release", async ({ page }) => {
  // Only a beta is mirrored. Asking for Stable must come back empty rather
  // than quietly handing over the beta.
  await setup(page, [], { beta: [{ tag: "v9.9.9-beta.1", entries: manifest }] });
  await expect(page.locator("#fw")).toContainText("Nothing published");
  await expect(page.locator("#channel-note")).toContainText("No stable version published yet");
});

test("an empty Stable channel offers Beta instead of a dead end", async ({ page }) => {
  const betaOnly = [{ name: "Heltec V4 beta", file: "heltec-v4-beta.bin", size: 4, sha256: hash }];
  await setup(page, [], { beta: [{ tag: "v9.9.10-beta.1", entries: betaOnly }] });
  await expect(page.locator("#channel")).toHaveValue("release");
  await page.locator("#use-beta-btn").click();
  await expect(page.locator("#channel")).toHaveValue("beta");
  await expect(page.locator("#fw")).toContainText("Heltec V4 beta");
  await expect(page.locator("#channel-note")).toContainText("v9.9.10-beta.1");
});

test("beta channel lists only the pre-release", async ({ page }) => {
  const betaOnly = [{ name: "Heltec V4 beta", file: "heltec-v4-beta.bin", size: 4, sha256: hash }];
  await setup(page, [], {
    release: [{ tag: "v9.9.9", entries: manifest }],
    beta: [{ tag: "v9.9.10-beta.1", entries: betaOnly }],
  });
  await page.locator("#channel").selectOption("beta");
  await expect(page.locator("#fw")).toContainText("Heltec V4 beta");
  await expect(page.locator("#channel-note")).toContainText("v9.9.10-beta.1");
});

test("UF2 boards can choose a channel and download that channel's build", async ({ page }) => {
  // nRF52 boards hide the serial firmware step, so the channel control must
  // live outside it, or those boards silently get whatever channel loaded first.
  await setup(page, [], {
    release: [{ tag: "v9.9.9", entries: [
      { name: "RAK release", board: "rak4631", file: "rak-release.uf2", size: 4, sha256: hash }] }],
    beta: [{ tag: "v9.9.10-beta.1", entries: [
      { name: "RAK beta", board: "rak4631", file: "rak-beta.uf2", size: 4, sha256: hash }] }],
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

test("the bootloader button reboots a UF2 board with a 1200 baud touch", async ({ page }) => {
  await setup(page);
  await page.locator('[data-val="rak4631"]').click();
  await page.locator("#enter-bootloader-btn").click();
  await expect(page.locator("#status")).toContainText("USB drive");
  expect(await page.evaluate(() => window.opened)).toEqual([1200]);
});

test("update keeps the node's settings partition", async ({ page }) => {
  // Writing the merged image whole would blank NVS: settings, channels and
  // the node's identity key. Update must write around it.
  await setup(page, [{ name: "Heltec V4", file: "heltec-v4.bin", size: merged.length, sha256: mergedHash }],
    { release: [{ tag: "v9.9.9", entries: [{ name: "Heltec V4", file: "heltec-v4.bin", size: merged.length, sha256: mergedHash }],
      files: { "heltec-v4.bin": merged } }] });
  await page.locator("#flash").click();
  await expect(page.locator("#status")).toContainText("Done.");
  const writes = await page.evaluate(() => window.writes);
  expect(writes.map(w => w.address)).toEqual([0x0, 0xE000]);
  for (const w of writes) {
    expect(w.address < 0xE000 && w.address + w.length > 0x9000).toBe(false);
  }
  expect(writes.reduce((n, w) => n + w.length, 0)).toBe(merged.length - 0x5000);
  expect(await page.evaluate(() => window.eraseAll)).toBe(false);
});

test("update refuses when the board keeps settings elsewhere", async ({ page }) => {
  const entry = { name: "Heltec V4", file: "heltec-v4.bin", size: merged.length, sha256: mergedHash };
  await setup(page, [entry], { release: [{ tag: "v9.9.9", entries: [entry], files: { "heltec-v4.bin": merged } }] },
    { table: partitionTable(0xA000, 0x5000) });
  await page.locator("#flash").click();
  await expect(page.locator("#status")).toContainText("Full erase");
  expect(await page.evaluate(() => window.writes)).toBeNull();
});

test("full erase writes the whole image after erasing the chip", async ({ page }) => {
  const entry = { name: "Heltec V4", file: "heltec-v4.bin", size: merged.length, sha256: mergedHash };
  await setup(page, [entry], { release: [{ tag: "v9.9.9", entries: [entry], files: { "heltec-v4.bin": merged } }] });
  await page.locator("#mode-full").check();
  await page.locator("#flash").click();
  await expect(page.locator("#status")).toContainText("Done.");
  expect(await page.evaluate(() => window.writes.map(w => [w.address, w.length]))).toEqual([[0, merged.length]]);
  expect(await page.evaluate(() => window.eraseAll)).toBe(true);
});

test("an older version can be chosen to roll back", async ({ page }) => {
  await setup(page, [], { release: [
    // Named for the board so the default Heltec V4 target lists them.
    { tag: "v9.9.9", entries: [{ name: "Heltec V4 newest", board: "heltec-v4", file: "heltec-v4-new.bin", size: 4, sha256: hash }] },
    { tag: "v9.9.8", entries: [{ name: "Heltec V4 older", board: "heltec-v4", file: "heltec-v4-old.bin", size: 4, sha256: hash }] },
  ] });
  await expect(page.locator("#fw")).toContainText("Heltec V4 newest");
  await page.locator("#version").selectOption("v9.9.8");
  await expect(page.locator("#fw")).toContainText("Heltec V4 older");
  await expect(page.locator("#channel-note")).toContainText("older version");
});

test("release notes are shown as text, never as markup", async ({ page }) => {
  await setup(page, [], { release: [{ tag: "v9.9.9", entries: manifest,
    notes: "Fixes <img src=x onerror=\"window.pwned=1\"> receipts" }] });
  await expect(page.locator("#release-notes")).toBeVisible();
  await page.locator("#release-notes summary").click();
  await expect(page.locator("#release-notes-text")).toContainText("<img src=x");
  expect(await page.evaluate(() => window.pwned)).toBeUndefined();
});

test("log lines stay text when a tab is re-shown", async ({ page }) => {
  // The serial monitor shows whatever the node prints, and the firmware prints
  // chat received from other nodes. Switching tabs used to rebuild the view
  // with innerHTML, so a remote node's message could run script in this page.
  await setup(page, []);
  const hostile = '<img src=x onerror="window.pwned=1">.bin';
  const data = await page.evaluateHandle((name) => {
    const transfer = new DataTransfer();
    transfer.items.add(new File([new Uint8Array([9, 8, 7])], name));
    return transfer;
  }, hostile);
  await page.locator("#dropzone").dispatchEvent("drop", { dataTransfer: data });
  await page.locator("#flash").click();
  await expect(page.locator("#status")).toContainText("Done.");
  await page.locator("#tab-monitor-btn").click();
  await page.locator("#tab-logs-btn").click();
  await expect(page.locator("#log")).toContainText("<img src=x");
  expect(await page.evaluate(() => window.pwned)).toBeUndefined();
});
