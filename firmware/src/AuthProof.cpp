#include "AuthProof.h"

#include <string.h>

#if defined(AETHERMESH_NATIVE_CRYPTO)
// Host tests: OpenSSL stands in for the Arduino Crypto library, as in PacketAuth.
#include <openssl/evp.h>
#include <openssl/hmac.h>
#else
#include <SHA256.h>
#endif

namespace authproof {

static const uint8_t kLabel[] = {'A', 'M', 'A', 'U', 'T', 'H', '1'};

void compute(const char* password, const uint8_t* challenge, size_t challengeLen,
             uint8_t out[PROOF_BYTES]) {
    const size_t keyLen = password ? strlen(password) : 0;
#if defined(AETHERMESH_NATIVE_CRYPTO)
    uint8_t message[sizeof(kLabel) + CHALLENGE_BYTES];
    if (challengeLen > CHALLENGE_BYTES) challengeLen = CHALLENGE_BYTES;
    memcpy(message, kLabel, sizeof(kLabel));
    memcpy(message + sizeof(kLabel), challenge, challengeLen);
    unsigned int outLen = 0;
    HMAC(EVP_sha256(), password, (int)keyLen, message, sizeof(kLabel) + challengeLen, out, &outLen);
#else
    SHA256 sha;
    sha.resetHMAC(password, keyLen);
    sha.update(kLabel, sizeof(kLabel));
    sha.update(challenge, challengeLen);
    sha.finalizeHMAC(password, keyLen, out, PROOF_BYTES);
#endif
}

bool verify(const char* password, const uint8_t* challenge, size_t challengeLen,
            const uint8_t* proof, size_t proofLen) {
    if (password == nullptr || password[0] == '\0') return false;
    if (challengeLen != CHALLENGE_BYTES || proofLen != PROOF_BYTES) return false;
    uint8_t expected[PROOF_BYTES];
    compute(password, challenge, challengeLen, expected);
    uint8_t diff = 0;
    for (size_t i = 0; i < PROOF_BYTES; i++) diff |= (uint8_t)(expected[i] ^ proof[i]);
    memset(expected, 0, sizeof(expected));
    return diff == 0;
}

}  // namespace authproof
