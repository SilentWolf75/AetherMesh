#pragma once

#include <stdint.h>
#include <stddef.h>

#include "IdentityPolicy.h"

// Per-node identity keys: one X25519 pair for agreeing a direct-message key with
// a peer, one Ed25519 pair that signs the announcement carrying both. Both are
// derived from a single 32-byte seed that never leaves this node, so the stored
// secret is 32 bytes and everything else is reproducible from it.
//
// The rules around these keys (trust on first use, epochs, fingerprints) live in
// IdentityPolicy.h and are tested natively; this file is only the key material.
namespace nodeidentity {

// Loads the seed, or creates one from the hardware RNG on first boot. Returns
// false if no seed could be read or written, in which case the node has no
// identity and must not claim one.
bool begin(uint32_t nodeId);

bool isReady();

const uint8_t* x25519Public();   // identity::PUBLIC_KEY_BYTES
const uint8_t* ed25519Public();  // identity::PUBLIC_KEY_BYTES
uint32_t keyEpoch();

// "A1B2-C3D4-E5F6-7890" over this node's signing key, for reading aloud.
bool fingerprint(char* out, size_t outLen);
bool fingerprintOf(const uint8_t* ed25519PublicKey, char* out, size_t outLen);

// Signs the canonical announcement body for this node's current keys.
bool signAnnouncement(uint8_t* signature64);

// Verifies a peer's announcement against the signing key it carries. This
// proves the sender holds that key; whether it is the key we already trust for
// that node id is identity::classify's decision.
bool verifyAnnouncement(uint32_t nodeId, uint32_t keyEpoch, const uint8_t* x25519PublicKey,
                        const uint8_t* ed25519PublicKey, const uint8_t* signature64);

// X25519 shared secret with a peer, for sealing direct messages. Refuses weak
// peer points, which would otherwise produce a shared secret an attacker knows.
bool sharedSecret(const uint8_t* peerX25519Public, uint8_t* out32);

// Generates a fresh seed and advances the epoch, so hearers can tell a
// deliberate rotation from an impostor. Returns false if it could not persist,
// leaving the previous identity in place.
bool rotate(uint32_t nodeId);

}  // namespace nodeidentity
