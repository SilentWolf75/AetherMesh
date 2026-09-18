// Unit tests for web-flasher/flashplan.js: node tools/test_flashplan.mjs
import assert from "node:assert/strict";
import { parsePartitionTable, findNvs, planUpdate } from "../web-flasher/flashplan.js";

function entry(name, type, subtype, offset, size) {
  const e = new Uint8Array(32);
  e[0] = 0xAA; e[1] = 0x50; e[2] = type; e[3] = subtype;
  new DataView(e.buffer).setUint32(4, offset, true);
  new DataView(e.buffer).setUint32(8, size, true);
  for (let i = 0; i < name.length; i++) e[12 + i] = name.charCodeAt(i);
  return e;
}

// The real Heltec V4 layout from the published beta image.
function table(nvsOffset = 0x9000, nvsSize = 0x5000) {
  const t = new Uint8Array(0xC00).fill(0xFF);
  const rows = [
    entry("nvs", 1, 0x02, nvsOffset, nvsSize),
    entry("otadata", 1, 0x00, 0xE000, 0x2000),
    entry("app0", 0, 0x10, 0x10000, 0x640000),
  ];
  rows.forEach((r, i) => t.set(r, i * 32));
  return t;
}

function mergedImage(length = 0x20000, nvsOffset = 0x9000, nvsSize = 0x5000) {
  const img = new Uint8Array(length).fill(0x11);
  img.set(table(nvsOffset, nvsSize), 0x8000);
  img.fill(0xFF, nvsOffset, nvsOffset + nvsSize); // exactly what merge_bin produces
  return img;
}

const tests = {
  "parses the real layout"() {
    const parts = parsePartitionTable(table());
    assert.equal(parts.length, 3);
    assert.deepEqual(findNvs(parts), { name: "nvs", type: 1, subtype: 2, offset: 0x9000, size: 0x5000 });
  },

  "an update never writes the settings partition"() {
    const img = mergedImage();
    const plan = planUpdate(img, 0, table());
    assert.equal(plan.ok, true);
    for (const s of plan.segments) {
      const overlaps = s.address < 0x9000 + 0x5000 && s.address + (s.end - s.start) > 0x9000;
      assert.equal(overlaps, false, `segment at ${s.address.toString(16)} touches NVS`);
    }
    // Everything else in the image is still written, byte for byte.
    const written = plan.segments.reduce((n, s) => n + (s.end - s.start), 0);
    assert.equal(written, img.length - 0x5000);
    assert.deepEqual(plan.segments.map((s) => s.address), [0x0, 0xE000]);
  },

  "refuses when the board keeps settings somewhere else"() {
    const plan = planUpdate(mergedImage(), 0, table(0xA000, 0x5000));
    assert.equal(plan.ok, false);
    assert.match(plan.reason, /Full erase/);
  },

  "refuses when the board's table could not be read"() {
    assert.equal(planUpdate(mergedImage(), 0, null).ok, false);
    assert.equal(planUpdate(mergedImage(), 0, new Uint8Array(0xC00).fill(0xFF)).ok, false);
  },

  "app-only images above the table are written whole"() {
    const app = new Uint8Array(0x1000).fill(0x22);
    const plan = planUpdate(app, 0x10000, null);
    assert.equal(plan.ok, true);
    assert.deepEqual(plan.segments, [{ address: 0x10000, start: 0, end: 0x1000 }]);
  },

  "a file written into the settings area is refused"() {
    // 0x9000 is above the partition table but IS the settings partition.
    assert.equal(planUpdate(new Uint8Array(16), 0x9000, table()).ok, false);
    assert.equal(planUpdate(new Uint8Array(16), 0x9000, null).ok, false);
  },

  "a raw file at 0x0 without its own table cannot sweep over settings"() {
    const raw = new Uint8Array(0x20000).fill(0x33);
    assert.equal(planUpdate(raw, 0, table()).ok, false);
  },

  "an app image is fine when the board's table shows it clear of settings"() {
    assert.equal(planUpdate(new Uint8Array(0x1000), 0x10000, table()).ok, true);
  },
};

let failed = 0;
for (const [name, fn] of Object.entries(tests)) {
  try { fn(); console.log("ok  ", name); } catch (e) { failed++; console.log("FAIL", name, "-", e.message); }
}
if (failed) { console.log(`${failed} failed`); process.exit(1); }
console.log(`${Object.keys(tests).length} flash plan tests passed`);
