#include "PacketAuth.h"
#include <string.h>

#if defined(AETHERMESH_NATIVE_CRYPTO)
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>

/** Host-side stand-in for Arduino Crypto SHA256 HMAC, same call shape as device builds. */
class HmacSha256 {
public:
    static constexpr size_t HASH_SIZE = 32;

    HmacSha256() : ctx_(HMAC_CTX_new()) {}
    ~HmacSha256() {
        if (ctx_) HMAC_CTX_free(ctx_);
    }

    HmacSha256(const HmacSha256&) = delete;
    HmacSha256& operator=(const HmacSha256&) = delete;

    void resetHMAC(const void* key, size_t keyLength) {
        HMAC_Init_ex(ctx_, key, (int)keyLength, EVP_sha256(), nullptr);
    }

    void update(const void* data, size_t length) {
        HMAC_Update(ctx_, static_cast<const unsigned char*>(data), length);
    }

    void finalizeHMAC(const void* /*key*/, size_t /*keyLength*/, void* hash, size_t hashLength) {
        unsigned char full[HASH_SIZE];
        unsigned int outLen = 0;
        HMAC_Final(ctx_, full, &outLen);
        if (hashLength > HASH_SIZE) hashLength = HASH_SIZE;
        memcpy(hash, full, hashLength);
    }

private:
    HMAC_CTX* ctx_;
};

/** Host-side SHA-256 state after one absorbed HMAC pad block (see device PadState). */
class PadState {
public:
    void absorbPad(const void* key, size_t keyLength, uint8_t pad) {
        uint8_t block[64] = {};
        if (keyLength > sizeof(block)) {
            SHA256(static_cast<const unsigned char*>(key), keyLength, block);
            keyLength = SHA256_DIGEST_LENGTH;
        } else {
            memcpy(block, key, keyLength);
        }
        for (size_t i = 0; i < sizeof(block); i++) block[i] ^= pad;
        SHA256_Init(&ctx_);
        SHA256_Update(&ctx_, block, sizeof(block));
        memset(block, 0, sizeof(block));
    }

    void digestFrom(const uint8_t* data, size_t length, uint8_t* out32) const {
        SHA256_CTX copy = ctx_;
        SHA256_Update(&copy, data, length);
        SHA256_Final(out32, &copy);
        memset(&copy, 0, sizeof(copy));
    }

    void clear() { memset(&ctx_, 0, sizeof(ctx_)); }

    ~PadState() { clear(); }

private:
    SHA256_CTX ctx_{};
};

#else
#include <SHA256.h>
using HmacSha256 = SHA256;

/**
 * SHA-256 state after absorbing one HMAC pad block (key XOR ipad/opad). Copying
 * it replaces re-keying, so an HMAC over a short message costs two compressions
 * instead of four. formatHMACKey()/processChunk() are protected in SHA256.
 */
class PadState : public SHA256 {
public:
    void absorbPad(const void* key, size_t keyLength, uint8_t pad) {
        formatHMACKey(state.w, key, keyLength, pad); // resets, then writes the block
        state.length += 64 * 8;
        processChunk();
    }

    void digestFrom(const uint8_t* data, size_t length, uint8_t* out32) const {
        PadState copy(*this);
        copy.update(data, length);
        copy.finalize(out32, HASH_SIZE);
    }
};
#endif

namespace {

constexpr uint32_t CONTROL_PBKDF2_ITERATIONS = 120000;
static const uint8_t CONTROL_KEY_SALT[] = {'A', 'M', 'C', 'T', 'R', 'L', '1', 0};

uint8_t gControlKey[HmacSha256::HASH_SIZE] = {};
bool gControlKeyValid = false;
// Password the cached key was derived from. saveSettings() re-sends the
// password on every settings save; comparing here skips the 120k-iteration
// PBKDF2 (seconds of stalled radio on nRF52) when it has not changed. The node
// already keeps nodePassword in RAM, so this copy adds no exposure.
char gControlKeyPassword[64] = {};
uint32_t gControlKeyDerivations = 0;

// In-progress PBKDF2, advanced by serviceControlKey() so boot and password
// changes never stall the radio for the whole derivation.
struct PendingDerivation {
    PadState inner;
    PadState outer;
    uint8_t block[HmacSha256::HASH_SIZE];
    uint8_t out[HmacSha256::HASH_SIZE];
    uint32_t completedIterations;
    bool active;
} gDerivation = {};
bool gRefuseLegacyControl = false;

bool appendBytes(uint8_t*& cursor, size_t& remaining, const void* data, size_t size) {
    if (remaining < size) return false;
    memcpy(cursor, data, size);
    cursor += size;
    remaining -= size;
    return true;
}

bool appendU32(uint8_t*& cursor, size_t& remaining, uint32_t value) {
    uint8_t bytes[4];
    for (uint8_t i = 0; i < 4; i++) bytes[i] = (uint8_t)(value >> (i * 8));
    return appendBytes(cursor, remaining, bytes, sizeof(bytes));
}

bool appendU64(uint8_t*& cursor, size_t& remaining, uint64_t value) {
    uint8_t bytes[8];
    for (uint8_t i = 0; i < 8; i++) bytes[i] = (uint8_t)(value >> (i * 8));
    return appendBytes(cursor, remaining, bytes, sizeof(bytes));
}

bool appendFloat(uint8_t*& cursor, size_t& remaining, float value) {
    uint32_t bits;
    memcpy(&bits, &value, sizeof(bits));
    return appendU32(cursor, remaining, bits);
}

/** HMAC-SHA256(key, data) from pre-keyed pad states; out32 may alias data. */
void hmacFromPads(const PadState& inner, const PadState& outer,
                  const uint8_t* data, size_t length, uint8_t* out32) {
    uint8_t innerDigest[HmacSha256::HASH_SIZE];
    inner.digestFrom(data, length, innerDigest);
    outer.digestFrom(innerDigest, sizeof(innerDigest), out32);
    memset(innerDigest, 0, sizeof(innerDigest));
}

void wipeDerivation() {
    gDerivation.inner.clear();
    gDerivation.outer.clear();
    memset(gDerivation.block, 0, sizeof(gDerivation.block));
    memset(gDerivation.out, 0, sizeof(gDerivation.out));
    gDerivation.completedIterations = 0;
    gDerivation.active = false;
}

void startDerivation(const char* password) {
    // Match Android ControlKeyDerivation / ChatKeyDerivation (single PBKDF2 block).
    // The HMAC pad states depend only on the password, so absorb them once:
    // each iteration is then two SHA-256 compressions instead of four.
    // Re-keying every iteration cost 12.4 s of boot on an ESP32-S3.
    size_t keyLength = strlen(password);
    gDerivation.inner.absorbPad(password, keyLength, 0x36);
    gDerivation.outer.absorbPad(password, keyLength, 0x5C);

    uint8_t firstInput[sizeof(CONTROL_KEY_SALT) + 4];
    memcpy(firstInput, CONTROL_KEY_SALT, sizeof(CONTROL_KEY_SALT));
    firstInput[sizeof(CONTROL_KEY_SALT) + 0] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 1] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 2] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 3] = 1;
    hmacFromPads(gDerivation.inner, gDerivation.outer, firstInput, sizeof(firstInput), gDerivation.block);
    memcpy(gDerivation.out, gDerivation.block, sizeof(gDerivation.out));
    memset(firstInput, 0, sizeof(firstInput));
    gDerivation.completedIterations = 1;
    gDerivation.active = true;
}

bool verifyTag(const uint8_t* canonical, size_t length, const uint8_t* key, size_t keyLength,
               const uint8_t* tag, size_t tagLength) {
    if (tagLength != 16) return false;
    uint8_t expected[HmacSha256::HASH_SIZE];
    HmacSha256 sha;
    sha.resetHMAC(key, keyLength);
    sha.update(canonical, length);
    sha.finalizeHMAC(key, keyLength, expected, sizeof(expected));
    uint8_t difference = 0;
    for (uint8_t i = 0; i < 16; i++) difference |= expected[i] ^ tag[i];
    memset(expected, 0, sizeof(expected));
    return difference == 0;
}

} // namespace

namespace packetauth {

void beginControlPassword(const char* password) {
    const bool clearing = password == nullptr || password[0] == '\0';
    const size_t length = clearing ? 0 : strnlen(password, sizeof(gControlKeyPassword));
    const bool cacheable = !clearing && length < sizeof(gControlKeyPassword);
    // Same password as the ready or in-progress key: nothing to redo.
    if ((gControlKeyValid || gDerivation.active) && cacheable &&
        strcmp(gControlKeyPassword, password) == 0) {
        return;
    }

    // Any change invalidates the old key immediately, before the new one exists.
    memset(gControlKey, 0, sizeof(gControlKey));
    memset(gControlKeyPassword, 0, sizeof(gControlKeyPassword));
    gControlKeyValid = false;
    wipeDerivation();
    if (clearing) return;
    startDerivation(password);
    if (cacheable) memcpy(gControlKeyPassword, password, length + 1);
}

bool serviceControlKey(uint32_t maxIterations) {
    if (!gDerivation.active) return true;
    uint32_t remaining = CONTROL_PBKDF2_ITERATIONS - gDerivation.completedIterations;
    uint32_t steps = remaining < maxIterations ? remaining : maxIterations;
    for (uint32_t step = 0; step < steps; step++) {
        hmacFromPads(gDerivation.inner, gDerivation.outer,
                     gDerivation.block, sizeof(gDerivation.block), gDerivation.block);
        for (uint8_t i = 0; i < HmacSha256::HASH_SIZE; i++) gDerivation.out[i] ^= gDerivation.block[i];
    }
    gDerivation.completedIterations += steps;
    if (gDerivation.completedIterations < CONTROL_PBKDF2_ITERATIONS) return false;

    memcpy(gControlKey, gDerivation.out, sizeof(gControlKey));
    gControlKeyValid = true;
    gControlKeyDerivations++;
    wipeDerivation();
    return true;
}

bool controlKeyPending() {
    return gDerivation.active;
}

void setControlPassword(const char* password) {
    beginControlPassword(password);
    serviceControlKey(CONTROL_PBKDF2_ITERATIONS);
}

uint32_t controlKeyDerivationCount() {
    return gControlKeyDerivations;
}

void setRefuseLegacyControl(bool refuse) {
    gRefuseLegacyControl = refuse;
}

bool refuseLegacyControl() {
    return gRefuseLegacyControl;
}

size_t buildConfigCanonical(const aethermesh_MeshPacket& packet,
                            uint8_t* output, size_t capacity,
                            const uint8_t* domain, size_t domainLength) {
    if (packet.which_payload != aethermesh_MeshPacket_config_tag) return 0;
    uint8_t* cursor = output;
    size_t remaining = capacity;
    const aethermesh_NodeConfig& config = packet.payload.config;
    size_t nameLength = strnlen(config.node_name, sizeof(config.node_name));
    if (nameLength > 16) nameLength = 16;
    uint8_t nameSize = (uint8_t)nameLength;
    size_t shortLength = strnlen(config.node_short_name, sizeof(config.node_short_name));
    if (shortLength > 4) shortLength = 4;
    uint8_t shortSize = (uint8_t)shortLength;
    uint8_t powerSave = config.power_save_mode ? 1 : 0;
    uint8_t fixedPosition = config.fixed_position ? 1 : 0;
    uint8_t applyNameOnly = config.apply_name_only ? 1 : 0;
    uint8_t requestReport = config.request_report ? 1 : 0;

    if (!appendBytes(cursor, remaining, domain, domainLength) ||
        !appendU32(cursor, remaining, packet.sender_id) ||
        !appendU32(cursor, remaining, packet.recipient_id) ||
        !appendU64(cursor, remaining, packet.session_id) ||
        !appendU32(cursor, remaining, packet.auth_counter) ||
        !appendBytes(cursor, remaining, &nameSize, 1) ||
        !appendBytes(cursor, remaining, config.node_name, nameLength) ||
        !appendU32(cursor, remaining, config.lora_sf) ||
        !appendFloat(cursor, remaining, config.lora_bw) ||
        !appendU32(cursor, remaining, (uint32_t)config.lora_tx_power) ||
        !appendU32(cursor, remaining, config.region) ||
        !appendU32(cursor, remaining, config.node_role) ||
        !appendU32(cursor, remaining, config.telemetry_interval) ||
        !appendU32(cursor, remaining, config.screen_timeout_secs) ||
        !appendBytes(cursor, remaining, &powerSave, 1) ||
        !appendU32(cursor, remaining, config.position_precision) ||
        !appendU32(cursor, remaining, config.gps_mode) ||
        !appendBytes(cursor, remaining, &fixedPosition, 1) ||
        !appendFloat(cursor, remaining, config.fixed_latitude) ||
        !appendFloat(cursor, remaining, config.fixed_longitude) ||
        !appendU32(cursor, remaining, (uint32_t)config.fixed_altitude) ||
        !appendBytes(cursor, remaining, &applyNameOnly, 1) ||
        !appendU32(cursor, remaining, config.mesh_hop_limit) ||
        !appendU32(cursor, remaining, config.rebroadcast_txdelay_x100) ||
        !appendBytes(cursor, remaining, &requestReport, 1) ||
        !appendU32(cursor, remaining, config.apply_mask) ||
        !appendBytes(cursor, remaining, &shortSize, 1) ||
        !appendBytes(cursor, remaining, config.node_short_name, shortLength) ||
        !appendU32(cursor, remaining, config.gps_duty_interval_secs)) {
        return 0;
    }
    return capacity - remaining;
}

bool verifyConfig(const aethermesh_MeshPacket& packet, const char* passwordForLegacyV2) {
    if (packet.auth_counter == 0 || packet.session_id == 0 || packet.auth_tag.size != 16) {
        return false;
    }

    if (gRefuseLegacyControl && packet.protocol_version < 3) {
        return false;
    }

    uint8_t canonical[256];
    if (packet.protocol_version >= 3) {
        if (!gControlKeyValid) return false;
        static const uint8_t domainV3[] = {'A', 'M', 'C', 'F', 'G', '3'};
        size_t length = buildConfigCanonical(packet, canonical, sizeof(canonical), domainV3, sizeof(domainV3));
        if (length == 0) return false;
        // One HMAC only — PBKDF2 was done in setControlPassword().
        bool ok = verifyTag(canonical, length, gControlKey, sizeof(gControlKey),
                            packet.auth_tag.bytes, packet.auth_tag.size);
        memset(canonical, 0, sizeof(canonical));
        return ok;
    }

    if (packet.protocol_version < 2) return false;
    if (passwordForLegacyV2 == nullptr || passwordForLegacyV2[0] == '\0') return false;
    static const uint8_t domainV2[] = {'A', 'M', 'C', 'F', 'G', '2'};
    size_t length = buildConfigCanonical(packet, canonical, sizeof(canonical), domainV2, sizeof(domainV2));
    if (length == 0) return false;
    size_t keyLength = strlen(passwordForLegacyV2);
    bool ok = verifyTag(canonical, length, (const uint8_t*)passwordForLegacyV2, keyLength,
                        packet.auth_tag.bytes, packet.auth_tag.size);
    memset(canonical, 0, sizeof(canonical));
    return ok;
}

} // namespace packetauth
