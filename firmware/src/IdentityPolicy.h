#pragma once

#include <stdint.h>
#include <string.h>

#include "MeshMath.h"

// Pure decisions around per-node identity keys. Nothing here touches the curve
// implementation or storage, so every rule is exercised by the native tests;
// NodeIdentity.cpp supplies the key material and the signatures.
namespace identity {

constexpr size_t PUBLIC_KEY_BYTES = 32;
constexpr size_t SIGNATURE_BYTES = 64;
constexpr size_t SEED_BYTES = 32;
// node id (4) + epoch (4) + both public keys, prefixed by a domain tag so a
// signature over an announcement can never be replayed as a signature over
// anything else this firmware signs.
constexpr char ANNOUNCE_DOMAIN[] = "AMID1";
constexpr size_t ANNOUNCE_DOMAIN_LEN = 5;
constexpr size_t ANNOUNCE_BODY_BYTES =
    ANNOUNCE_DOMAIN_LEN + 4 + 4 + PUBLIC_KEY_BYTES + PUBLIC_KEY_BYTES;

// What a hearer should do with an announcement it just verified.
enum class Trust : uint8_t {
    Invalid,   // malformed or signature did not verify — ignore entirely
    FirstUse,  // no key on file: store it, this node is now known
    Match,     // identical to what we stored: nothing to do
    Rotated,   // same node, newer epoch, correctly signed by the new key
    Changed,   // key differs and the epoch did not advance — tell the user
};

inline bool keyIsUsable(const uint8_t* key, size_t len) {
    if (key == nullptr || len != PUBLIC_KEY_BYTES) return false;
    // An all-zero public key is the classic degenerate X25519 input: the shared
    // secret comes out all-zero for every peer, so anyone could read the traffic.
    for (size_t i = 0; i < len; i++) {
        if (key[i] != 0) return true;
    }
    return false;
}

// Canonical bytes covered by the announcement signature. Both the signer and
// every verifier build this the same way; the layout is fixed forever.
inline bool buildAnnounceBody(uint8_t* out, size_t outLen, uint32_t nodeId, uint32_t keyEpoch,
                              const uint8_t* xPublic, const uint8_t* edPublic) {
    if (out == nullptr || outLen < ANNOUNCE_BODY_BYTES) return false;
    if (!keyIsUsable(xPublic, PUBLIC_KEY_BYTES) || !keyIsUsable(edPublic, PUBLIC_KEY_BYTES)) {
        return false;
    }
    size_t o = 0;
    memcpy(out + o, ANNOUNCE_DOMAIN, ANNOUNCE_DOMAIN_LEN);
    o += ANNOUNCE_DOMAIN_LEN;
    out[o++] = (uint8_t)(nodeId >> 24);
    out[o++] = (uint8_t)(nodeId >> 16);
    out[o++] = (uint8_t)(nodeId >> 8);
    out[o++] = (uint8_t)(nodeId);
    out[o++] = (uint8_t)(keyEpoch >> 24);
    out[o++] = (uint8_t)(keyEpoch >> 16);
    out[o++] = (uint8_t)(keyEpoch >> 8);
    out[o++] = (uint8_t)(keyEpoch);
    memcpy(out + o, xPublic, PUBLIC_KEY_BYTES);
    o += PUBLIC_KEY_BYTES;
    memcpy(out + o, edPublic, PUBLIC_KEY_BYTES);
    return true;
}

// Trust on first use. `signatureOk` is the caller's Ed25519 verification of the
// announcement against the key the announcement itself carries, which proves
// possession but says nothing about whether it is the same node as before —
// that is what the stored copy decides here.
inline Trust classify(bool signatureOk, bool haveStored, const uint8_t* storedEd,
                      uint32_t storedEpoch, const uint8_t* announcedX,
                      const uint8_t* announcedEd, uint32_t announcedEpoch) {
    if (!signatureOk) return Trust::Invalid;
    if (!keyIsUsable(announcedX, PUBLIC_KEY_BYTES) ||
        !keyIsUsable(announcedEd, PUBLIC_KEY_BYTES)) {
        return Trust::Invalid;
    }
    if (!haveStored || storedEd == nullptr) return Trust::FirstUse;
    if (memcmp(storedEd, announcedEd, PUBLIC_KEY_BYTES) == 0) {
        // Same key. A replayed old announcement must not roll the epoch back.
        return announcedEpoch >= storedEpoch ? Trust::Match : Trust::Invalid;
    }
    // Different key. A node that legitimately regenerates advances its epoch;
    // anything else is someone claiming an id that is already spoken for.
    return announcedEpoch > storedEpoch ? Trust::Rotated : Trust::Changed;
}

// Whether a decision may overwrite what we have on file. A Changed key never
// does — the user is asked first.
inline bool shouldStore(Trust t) { return t == Trust::FirstUse || t == Trust::Rotated; }

// Whether the user has to be told. Rotation is legitimate but still worth
// surfacing: it is indistinguishable from a reflash by someone else.
inline bool shouldWarnUser(Trust t) { return t == Trust::Changed || t == Trust::Rotated; }

// Whether a direct message may be sealed to this peer without asking.
inline bool mayEncryptTo(Trust t) { return t == Trust::FirstUse || t == Trust::Match; }

// Identity announcements are large and rarely change, so they are the last
// thing that should crowd a slow channel. One announcement is worth many
// beacons; the airtime budget still sets the floor at this spreading factor.
constexpr uint32_t ANNOUNCE_FRAME_BYTES = 160;
constexpr uint32_t ANNOUNCE_BEACON_MULTIPLE = 8;

inline uint32_t announceIntervalSecFor(uint8_t sf, uint32_t beaconIntervalSec) {
    const uint32_t floorSec =
        meshmath::telemetryIntervalSecFor(sf, beaconIntervalSec, ANNOUNCE_FRAME_BYTES);
    const uint32_t spaced = beaconIntervalSec * ANNOUNCE_BEACON_MULTIPLE;
    return spaced > floorSec ? spaced : floorSec;
}

// Keys are useless until both ends hold each other's. Waiting for the slow
// announcement timer would leave a fresh node unable to read sealed mail for
// half an hour, so a node also announces when it meets someone it does not know
// and answers someone else's announcement — which converges in one round trip.
// The gap keeps that from becoming a storm when a busy mesh comes up together.
constexpr uint32_t ANNOUNCE_REPLY_MIN_GAP_MS = 120000;

inline bool shouldAnnounceForStranger(uint32_t nowMs, uint32_t lastAnnounceMs,
                                      bool haveAnnouncedOnce, bool identityReady,
                                      uint32_t minGapMs = ANNOUNCE_REPLY_MIN_GAP_MS) {
    if (!identityReady) return false;
    if (!haveAnnouncedOnce) return true;
    return (uint32_t)(nowMs - lastAnnounceMs) >= minGapMs;
}

// Human-comparable fingerprint of a public key digest: "A1B2-C3D4-E5F6-7890".
// Four groups of four hex digits is enough to read aloud and compare, and is
// the same shape on the node screen and in the app.
constexpr size_t FINGERPRINT_CHARS = 20;  // 16 hex + 3 separators + NUL

inline bool formatFingerprint(char* out, size_t outLen, const uint8_t* digest, size_t digestLen) {
    if (out == nullptr || outLen < FINGERPRINT_CHARS || digest == nullptr || digestLen < 8) {
        return false;
    }
    static const char* kHex = "0123456789ABCDEF";
    size_t o = 0;
    for (size_t i = 0; i < 8; i++) {
        out[o++] = kHex[(digest[i] >> 4) & 0x0F];
        out[o++] = kHex[digest[i] & 0x0F];
        if (i % 2 == 1 && i != 7) out[o++] = '-';
    }
    out[o] = '\0';
    return true;
}

}  // namespace identity
