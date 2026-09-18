#ifndef AETHERMESH_AUTH_PROOF_H
#define AETHERMESH_AUTH_PROOF_H

#include <stddef.h>
#include <stdint.h>

/**
 * Unlocking the node over Bluetooth without sending its password.
 *
 * The node hands the phone a fresh random challenge; the phone answers with
 * HMAC-SHA256(key = password, message = "AMAUTH1" || challenge). Each
 * challenge is good for one attempt on one connection, so a captured answer
 * cannot be replayed. The Android and iOS apps implement the same function
 * and share its test vectors.
 */
namespace authproof {

constexpr size_t CHALLENGE_BYTES = 16;
constexpr size_t PROOF_BYTES = 32;

void compute(const char* password, const uint8_t* challenge, size_t challengeLen,
             uint8_t out[PROOF_BYTES]);

/** Constant-time check of a proof against the password and challenge. */
bool verify(const char* password, const uint8_t* challenge, size_t challengeLen,
            const uint8_t* proof, size_t proofLen);

}  // namespace authproof

#endif  // AETHERMESH_AUTH_PROOF_H
