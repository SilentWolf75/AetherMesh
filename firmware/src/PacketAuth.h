#ifndef PACKET_AUTH_H
#define PACKET_AUTH_H

#include <stddef.h>
#include <stdint.h>
#include "mesh.pb.h"

namespace packetauth {

size_t buildConfigCanonical(const aethermesh_MeshPacket& packet,
                            uint8_t* output, size_t capacity,
                            const uint8_t* domain, size_t domainLength);

/**
 * Derive (or clear) the cached PBKDF2 control key, blocking until done. Safe to
 * call on every settings save: an unchanged password reuses the cached key.
 */
void setControlPassword(const char* password);

/**
 * Non-blocking form: invalidate the old key and start deriving the new one.
 * Advance it with serviceControlKey(). An unchanged password is a no-op.
 */
void beginControlPassword(const char* password);

/** Run up to maxIterations of a pending derivation; true once none is pending. */
bool serviceControlKey(uint32_t maxIterations);

/** True while a derivation started by beginControlPassword() is unfinished. */
bool controlKeyPending();

/** Number of PBKDF2 derivations performed (diagnostics and tests). */
uint32_t controlKeyDerivationCount();

/** When true, protocol_version < 3 remote config is rejected (no v2 HMAC, no plaintext). */
void setRefuseLegacyControl(bool refuse);
bool refuseLegacyControl();

/**
 * Verify authenticated remote config.
 * protocol_version >= 3: uses cached PBKDF2 key (setControlPassword); passwordIgnored.
 *   Returns false while controlKeyPending(); callers should hold such packets.
 * protocol_version == 2: raw-password HMAC unless refuseLegacyControl(); needs password.
 * protocol_version < 2: always false (plaintext path is separate and also gated).
 */
bool verifyConfig(const aethermesh_MeshPacket& packet, const char* passwordForLegacyV2);

} // namespace packetauth

#endif
