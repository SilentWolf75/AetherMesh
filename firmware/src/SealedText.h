#pragma once

#include <stdint.h>
#include <string.h>

#include "IdentityPolicy.h"

// Frame layout for a direct message sealed to a peer's X25519 key:
//
//   nonce (12) || ciphertext (n) || tag (16)
//
// The sender and recipient ids are authenticated but not carried here — they
// are already in the packet header, and binding them into the tag means a
// sealed message cannot be lifted out of one conversation and replayed into
// another. Sizes and bounds live here so they can be tested without a radio.
namespace sealedtext {

constexpr size_t NONCE_BYTES = 12;
constexpr size_t TAG_BYTES = 16;
constexpr size_t OVERHEAD_BYTES = NONCE_BYTES + TAG_BYTES;
constexpr size_t AAD_BYTES = 13;  // domain (5) + sender (4) + recipient (4)
constexpr char AAD_DOMAIN[] = "AMDM1";
constexpr size_t AAD_DOMAIN_LEN = 5;

inline size_t sealedSize(size_t plainLen) { return plainLen + OVERHEAD_BYTES; }

inline bool sealedLengthIsSane(size_t sealedLen) { return sealedLen > OVERHEAD_BYTES; }

inline size_t plainSize(size_t sealedLen) {
    return sealedLengthIsSane(sealedLen) ? sealedLen - OVERHEAD_BYTES : 0;
}

// Longest plaintext that still fits the sealed field on the wire.
inline size_t maxPlainFor(size_t sealedCapacity) {
    return sealedCapacity > OVERHEAD_BYTES ? sealedCapacity - OVERHEAD_BYTES : 0;
}

inline bool fits(size_t plainLen, size_t sealedCapacity) {
    return plainLen > 0 && sealedSize(plainLen) <= sealedCapacity;
}

// Authenticated header bytes. Both ends build this identically; a mismatch in
// either id makes the tag fail rather than silently decrypting.
inline bool buildAad(uint8_t* out, size_t outLen, uint32_t senderId, uint32_t recipientId) {
    if (out == nullptr || outLen < AAD_BYTES) return false;
    memcpy(out, AAD_DOMAIN, AAD_DOMAIN_LEN);
    size_t o = AAD_DOMAIN_LEN;
    out[o++] = (uint8_t)(senderId >> 24);
    out[o++] = (uint8_t)(senderId >> 16);
    out[o++] = (uint8_t)(senderId >> 8);
    out[o++] = (uint8_t)(senderId);
    out[o++] = (uint8_t)(recipientId >> 24);
    out[o++] = (uint8_t)(recipientId >> 16);
    out[o++] = (uint8_t)(recipientId >> 8);
    out[o++] = (uint8_t)(recipientId);
    return true;
}

// The two ends derive the same message key from the same shared secret, so the
// key material must not depend on who is sending: the node ids go in sorted.
inline void orderedPair(uint32_t a, uint32_t b, uint32_t& low, uint32_t& high) {
    low = a < b ? a : b;
    high = a < b ? b : a;
}

}  // namespace sealedtext
