#pragma once

#include <stdint.h>
#include <stddef.h>

#include "SealedText.h"

// Sealing a direct message to the recipient's announced X25519 key. The node
// does this itself, so a standalone device with no phone still gets the same
// protection, and a relay carrying the packet sees only ciphertext.
//
// This wraps whatever the companion app handed over, so an app-level passphrase
// still applies underneath: sealing adds hop privacy and authenticity, it does
// not replace the end-to-end layer.
namespace textseal {

// Encrypts `plain` to `peerX25519Public`. Writes nonce || ciphertext || tag.
// Returns false if the peer key is unusable, the output does not fit, or this
// node has no identity of its own yet.
bool seal(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* plain, size_t plainLen, uint8_t* out, size_t outCapacity,
          size_t* outLen);

// Reverses seal(). Returns false when the tag does not verify, which is the
// only signal that matters: a wrong key, a tampered body, or a message lifted
// from a different conversation all land here.
bool open(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* sealed, size_t sealedLen, uint8_t* out, size_t outCapacity,
          size_t* outLen);

}  // namespace textseal
