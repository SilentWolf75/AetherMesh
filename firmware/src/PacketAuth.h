#ifndef PACKET_AUTH_H
#define PACKET_AUTH_H

#include <Arduino.h>
#include "mesh.pb.h"

namespace packetauth {

size_t buildConfigCanonical(const aethermesh_MeshPacket& packet,
                            uint8_t* output, size_t capacity,
                            const uint8_t* domain, size_t domainLength);

/** Derive (or clear) the cached PBKDF2 control key. Call on boot and password change. */
void setControlPassword(const char* password);

/** When true, protocol_version < 3 remote config is rejected (no v2 HMAC, no plaintext). */
void setRefuseLegacyControl(bool refuse);
bool refuseLegacyControl();

/**
 * Verify authenticated remote config.
 * protocol_version >= 3: uses cached PBKDF2 key (setControlPassword); passwordIgnored.
 * protocol_version == 2: raw-password HMAC unless refuseLegacyControl(); needs password.
 * protocol_version < 2: always false (plaintext path is separate and also gated).
 */
bool verifyConfig(const aethermesh_MeshPacket& packet, const char* passwordForLegacyV2);

} // namespace packetauth

#endif
