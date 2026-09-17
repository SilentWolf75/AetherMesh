#include "TextSeal.h"

#include <Arduino.h>
#include <string.h>

#include <ChaChaPoly.h>
#include <SHA256.h>

#include "NodeIdentity.h"

#ifdef ESP32
#include <esp_system.h>
#endif

namespace textseal {
namespace {

void randomBytes(uint8_t* out, size_t len) {
#ifdef ESP32
    esp_fill_random(out, len);
#else
    NRF_RNG->CONFIG = RNG_CONFIG_DERCEN_Msk;
    NRF_RNG->TASKS_START = 1;
    for (size_t i = 0; i < len; i++) {
        NRF_RNG->EVENTS_VALRDY = 0;
        while (NRF_RNG->EVENTS_VALRDY == 0) {
        }
        out[i] = (uint8_t)NRF_RNG->VALUE;
    }
    NRF_RNG->TASKS_STOP = 1;
#endif
}

// Message key from the X25519 shared secret. The raw secret is never used as a
// key directly: it is hashed with a label and the two node ids in a fixed order,
// so both ends derive the same key and a secret shared with one peer cannot be
// repurposed for another conversation.
bool deriveKey(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
               uint8_t* key32) {
    uint8_t shared[identity::PUBLIC_KEY_BYTES];
    if (!nodeidentity::sharedSecret(peerX25519Public, shared)) return false;

    uint32_t low = 0;
    uint32_t high = 0;
    sealedtext::orderedPair(senderId, recipientId, low, high);
    uint8_t pair[8];
    pair[0] = (uint8_t)(low >> 24);
    pair[1] = (uint8_t)(low >> 16);
    pair[2] = (uint8_t)(low >> 8);
    pair[3] = (uint8_t)(low);
    pair[4] = (uint8_t)(high >> 24);
    pair[5] = (uint8_t)(high >> 16);
    pair[6] = (uint8_t)(high >> 8);
    pair[7] = (uint8_t)(high);

    SHA256 hash;
    hash.reset();
    hash.update("AMDM1-key", 9);
    hash.update(shared, sizeof(shared));
    hash.update(pair, sizeof(pair));
    hash.finalize(key32, 32);
    memset(shared, 0, sizeof(shared));
    memset(pair, 0, sizeof(pair));
    return true;
}

}  // namespace

bool seal(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* plain, size_t plainLen, uint8_t* out, size_t outCapacity,
          size_t* outLen) {
    if (plain == nullptr || out == nullptr || outLen == nullptr) return false;
    if (!sealedtext::fits(plainLen, outCapacity)) return false;

    uint8_t key[32];
    if (!deriveKey(peerX25519Public, senderId, recipientId, key)) return false;

    uint8_t aad[sealedtext::AAD_BYTES];
    sealedtext::buildAad(aad, sizeof(aad), senderId, recipientId);

    // A repeated nonce under the same key would leak the plaintext difference,
    // so it comes from the hardware RNG and travels with the message.
    uint8_t* nonce = out;
    randomBytes(nonce, sealedtext::NONCE_BYTES);

    ChaChaPoly cipher;
    bool ok = cipher.setKey(key, sizeof(key)) && cipher.setIV(nonce, sealedtext::NONCE_BYTES);
    if (ok) {
        cipher.addAuthData(aad, sizeof(aad));
        cipher.encrypt(out + sealedtext::NONCE_BYTES, plain, plainLen);
        cipher.computeTag(out + sealedtext::NONCE_BYTES + plainLen, sealedtext::TAG_BYTES);
        *outLen = sealedtext::sealedSize(plainLen);
    }
    cipher.clear();
    memset(key, 0, sizeof(key));
    return ok;
}

bool open(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* sealed, size_t sealedLen, uint8_t* out, size_t outCapacity,
          size_t* outLen) {
    if (sealed == nullptr || out == nullptr || outLen == nullptr) return false;
    if (!sealedtext::sealedLengthIsSane(sealedLen)) return false;
    const size_t plainLen = sealedtext::plainSize(sealedLen);
    if (plainLen == 0 || plainLen > outCapacity) return false;

    uint8_t key[32];
    if (!deriveKey(peerX25519Public, senderId, recipientId, key)) return false;

    uint8_t aad[sealedtext::AAD_BYTES];
    sealedtext::buildAad(aad, sizeof(aad), senderId, recipientId);

    ChaChaPoly cipher;
    bool ok = cipher.setKey(key, sizeof(key)) &&
              cipher.setIV(sealed, sealedtext::NONCE_BYTES);
    if (ok) {
        cipher.addAuthData(aad, sizeof(aad));
        cipher.decrypt(out, sealed + sealedtext::NONCE_BYTES, plainLen);
        ok = cipher.checkTag(sealed + sealedtext::NONCE_BYTES + plainLen, sealedtext::TAG_BYTES);
        if (ok) {
            *outLen = plainLen;
        } else {
            // Never hand back unauthenticated plaintext.
            memset(out, 0, plainLen);
        }
    }
    cipher.clear();
    memset(key, 0, sizeof(key));
    return ok;
}

}  // namespace textseal
