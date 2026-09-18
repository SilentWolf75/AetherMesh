// Decides what an ESP32 flash actually writes. Kept free of any browser or
// serial API so it can be tested without hardware.
//
// The published USB image is one merged file starting at 0x0: bootloader,
// partition table, OTA selector and app. Because it is contiguous, it also
// covers the NVS partition — padded with 0xFF — so writing it whole erases
// every setting on the node, including its identity key. After such a flash the
// node generates a new key under the same epoch, and every peer that knew it
// reports a key conflict. An update must therefore write around NVS.

const TABLE_OFFSET = 0x8000;
const TABLE_SIZE = 0xC00;
const ENTRY_SIZE = 32;
const TYPE_DATA = 0x01;
const SUBTYPE_NVS = 0x02;

/** Parses an ESP-IDF partition table. Stops at the first non-entry. */
export function parsePartitionTable(bytes) {
  const entries = [];
  if (!bytes) return entries;
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i + ENTRY_SIZE <= view.length; i += ENTRY_SIZE) {
    if (view[i] !== 0xAA || view[i + 1] !== 0x50) break;
    const dv = new DataView(view.buffer, view.byteOffset + i, ENTRY_SIZE);
    let name = "";
    for (let j = 12; j < 28 && view[i + j] !== 0; j++) name += String.fromCharCode(view[i + j]);
    entries.push({
      name,
      type: view[i + 2],
      subtype: view[i + 3],
      offset: dv.getUint32(4, true),
      size: dv.getUint32(8, true),
    });
  }
  return entries;
}

export function findNvs(entries) {
  return entries.find((e) => e.type === TYPE_DATA && e.subtype === SUBTYPE_NVS) || null;
}

/** The partition table embedded in a merged image, or [] if it has none. */
export function imagePartitions(image) {
  const view = image instanceof Uint8Array ? image : new Uint8Array(image);
  if (view.length < TABLE_OFFSET + TABLE_SIZE) return [];
  return parsePartitionTable(view.subarray(TABLE_OFFSET, TABLE_OFFSET + TABLE_SIZE));
}

/**
 * Plans an update that keeps the node's settings.
 *
 * `image` is the verified file, `address` where it will be written, and
 * `deviceTable` the partition table read back from the board (or null if it
 * could not be read). Returns either `{ ok: true, segments }` — byte ranges of
 * the image with their flash addresses — or `{ ok: false, reason }`.
 *
 * An update is refused rather than guessed at whenever keeping NVS could be
 * wrong: the old settings are only meaningful at the same place, in the same
 * size, as the new firmware expects them.
 */
export function planUpdate(image, address, deviceTable) {
  const length = image.byteLength !== undefined ? image.byteLength : image.length;
  const whole = { ok: true, segments: [{ address, start: 0, end: length }] };
  const deviceNvs = findNvs(parsePartitionTable(deviceTable));
  const imageNvs = address === 0 ? findNvs(imagePartitions(image)) : null;

  if (imageNvs) {
    // A merged image: write everything in it except the settings partition.
    if (!deviceNvs) {
      return { ok: false, reason: "Could not read this board's current partition table, so its settings cannot be kept safely. Use Full erase and install." };
    }
    if (deviceNvs.offset !== imageNvs.offset || deviceNvs.size !== imageNvs.size) {
      return { ok: false, reason: "This firmware stores settings in a different place than the one on the board, so they cannot be carried over. Use Full erase and install." };
    }
    const nvsEnd = imageNvs.offset + imageNvs.size;
    const segments = [];
    if (imageNvs.offset > 0) segments.push({ address: 0, start: 0, end: Math.min(imageNvs.offset, length) });
    if (length > nvsEnd) segments.push({ address: nvsEnd, start: nvsEnd, end: length });
    return { ok: true, segments, keeps: { offset: imageNvs.offset, size: imageNvs.size } };
  }

  // Any other file (an app image, or a raw local file) is written as-is, but
  // only where it cannot land on the settings. "Above the partition table" is
  // not the same thing: NVS itself sits right above the table.
  if (!deviceNvs) {
    // ESP-IDF places NVS below the first app partition, which starts at 0x10000.
    if (address >= 0x10000) return whole;
    return { ok: false, reason: "Could not read this board's partition table to check this file stays clear of its settings. Use Full erase and install." };
  }
  const overlaps = address < deviceNvs.offset + deviceNvs.size && address + length > deviceNvs.offset;
  if (overlaps) {
    return { ok: false, reason: "This file would overwrite the board's settings. Use Full erase and install, or pick a file meant for the app partition." };
  }
  return whole;
}

export const PARTITION_TABLE_OFFSET = TABLE_OFFSET;
export const PARTITION_TABLE_SIZE = TABLE_SIZE;
