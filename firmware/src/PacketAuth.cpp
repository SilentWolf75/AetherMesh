#include "PacketAuth.h"
#include <SHA256.h>
#include <string.h>

namespace {

constexpr uint32_t CONTROL_PBKDF2_ITERATIONS = 120000;
static const uint8_t CONTROL_KEY_SALT[] = {'A', 'M', 'C', 'T', 'R', 'L', '1', 0};

uint8_t gControlKey[SHA256::HASH_SIZE] = {};
bool gControlKeyValid = false;
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

void deriveControlKey(const char* password, uint8_t* out32) {
    // Match Android ControlKeyDerivation / ChatKeyDerivation (single PBKDF2 block).
    SHA256 sha;
    uint8_t firstInput[sizeof(CONTROL_KEY_SALT) + 4];
    memcpy(firstInput, CONTROL_KEY_SALT, sizeof(CONTROL_KEY_SALT));
    firstInput[sizeof(CONTROL_KEY_SALT) + 0] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 1] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 2] = 0;
    firstInput[sizeof(CONTROL_KEY_SALT) + 3] = 1;
    size_t keyLength = strlen(password);
    sha.resetHMAC(password, keyLength);
    sha.update(firstInput, sizeof(firstInput));
    uint8_t block[SHA256::HASH_SIZE];
    sha.finalizeHMAC(password, keyLength, block, sizeof(block));
    memcpy(out32, block, SHA256::HASH_SIZE);
    for (uint32_t iter = 1; iter < CONTROL_PBKDF2_ITERATIONS; iter++) {
        sha.resetHMAC(password, keyLength);
        sha.update(block, sizeof(block));
        sha.finalizeHMAC(password, keyLength, block, sizeof(block));
        for (uint8_t i = 0; i < SHA256::HASH_SIZE; i++) out32[i] ^= block[i];
    }
    memset(block, 0, sizeof(block));
    memset(firstInput, 0, sizeof(firstInput));
}

bool verifyTag(const uint8_t* canonical, size_t length, const uint8_t* key, size_t keyLength,
               const uint8_t* tag, size_t tagLength) {
    if (tagLength != 16) return false;
    uint8_t expected[SHA256::HASH_SIZE];
    SHA256 sha;
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

void setControlPassword(const char* password) {
    memset(gControlKey, 0, sizeof(gControlKey));
    gControlKeyValid = false;
    if (password == nullptr || password[0] == '\0') return;
    deriveControlKey(password, gControlKey);
    gControlKeyValid = true;
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
