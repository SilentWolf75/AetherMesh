#include "PacketAuth.h"
#include <SHA256.h>
#include <string.h>

namespace {

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

} // namespace

namespace packetauth {

size_t buildConfigCanonical(const aethermesh_MeshPacket& packet,
                            uint8_t* output, size_t capacity) {
    if (packet.which_payload != aethermesh_MeshPacket_config_tag) return 0;
    uint8_t* cursor = output;
    size_t remaining = capacity;
    static const uint8_t domain[] = {'A', 'M', 'C', 'F', 'G', '2'};
    const aethermesh_NodeConfig& config = packet.payload.config;
    size_t nameLength = strnlen(config.node_name, sizeof(config.node_name));
    if (nameLength > 16) nameLength = 16;
    uint8_t nameSize = (uint8_t)nameLength;
    uint8_t powerSave = config.power_save_mode ? 1 : 0;
    uint8_t fixedPosition = config.fixed_position ? 1 : 0;
    uint8_t applyNameOnly = config.apply_name_only ? 1 : 0;

    if (!appendBytes(cursor, remaining, domain, sizeof(domain)) ||
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
        !appendBytes(cursor, remaining, &applyNameOnly, 1)) {
        return 0;
    }
    return capacity - remaining;
}

bool verifyConfig(const aethermesh_MeshPacket& packet, const char* password) {
    if (packet.protocol_version < 2 || packet.auth_counter == 0 ||
        packet.session_id == 0 || packet.auth_tag.size != 16 ||
        password == nullptr || password[0] == '\0') return false;
    uint8_t canonical[128];
    size_t length = buildConfigCanonical(packet, canonical, sizeof(canonical));
    if (length == 0) return false;
    uint8_t expected[SHA256::HASH_SIZE];
    SHA256 sha;
    size_t keyLength = strlen(password);
    sha.resetHMAC(password, keyLength);
    sha.update(canonical, length);
    sha.finalizeHMAC(password, keyLength, expected, sizeof(expected));
    uint8_t difference = 0;
    for (uint8_t i = 0; i < 16; i++) difference |= expected[i] ^ packet.auth_tag.bytes[i];
    memset(expected, 0, sizeof(expected));
    memset(canonical, 0, sizeof(canonical));
    return difference == 0;
}

} // namespace packetauth
