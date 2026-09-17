// Host-test stand-ins for the on-device crypto. The router tests care about
// routing decisions around sealed messages — delivered or not, acknowledged or
// not — never about the strength of the primitives, which are exercised on the
// device and by the Curve25519/Ed25519/ChaChaPoly library's own suites.
//
// Nothing here is cryptography. It is deliberately trivial and deliberately
// reversible so a test can assert what the router does with a good frame and
// with a corrupted one.
#include <stdint.h>
#include <string.h>

#include "IdentityPolicy.h"
#include "NodeIdentity.h"
#include "TextSeal.h"

namespace {
uint8_t gX[identity::PUBLIC_KEY_BYTES];
uint8_t gEd[identity::PUBLIC_KEY_BYTES];
bool gReady = false;
uint32_t gEpoch = 1;

uint8_t keyByte(const uint8_t* peer, uint32_t a, uint32_t b) {
    uint32_t low = 0;
    uint32_t high = 0;
    sealedtext::orderedPair(a, b, low, high);
    return (uint8_t)(peer[0] ^ (low & 0xFF) ^ ((high >> 8) & 0xFF) ^ 0x5A);
}
}  // namespace

namespace nodeidentity {

bool begin(uint32_t nodeId) {
    for (size_t i = 0; i < identity::PUBLIC_KEY_BYTES; i++) {
        gX[i] = (uint8_t)(nodeId + i + 1);
        gEd[i] = (uint8_t)(nodeId + i + 129);
    }
    gReady = true;
    return true;
}

bool isReady() { return gReady; }
const uint8_t* x25519Public() { return gX; }
const uint8_t* ed25519Public() { return gEd; }
uint32_t keyEpoch() { return gEpoch; }

bool fingerprintOf(const uint8_t* ed25519PublicKey, char* out, size_t outLen) {
    if (!identity::keyIsUsable(ed25519PublicKey, identity::PUBLIC_KEY_BYTES)) return false;
    return identity::formatFingerprint(out, outLen, ed25519PublicKey,
                                       identity::PUBLIC_KEY_BYTES);
}

bool fingerprint(char* out, size_t outLen) { return fingerprintOf(gEd, out, outLen); }

bool signAnnouncement(uint8_t* signature64) {
    if (!gReady || signature64 == nullptr) return false;
    memset(signature64, 0, identity::SIGNATURE_BYTES);
    memcpy(signature64, gEd, identity::PUBLIC_KEY_BYTES);
    return true;
}

bool verifyAnnouncement(uint32_t, uint32_t, const uint8_t* x25519PublicKey,
                        const uint8_t* ed25519PublicKey, const uint8_t* signature64) {
    if (signature64 == nullptr) return false;
    if (!identity::keyIsUsable(x25519PublicKey, identity::PUBLIC_KEY_BYTES)) return false;
    if (!identity::keyIsUsable(ed25519PublicKey, identity::PUBLIC_KEY_BYTES)) return false;
    // "Signed by the key it carries", the same property the real one proves.
    return memcmp(signature64, ed25519PublicKey, identity::PUBLIC_KEY_BYTES) == 0;
}

bool sharedSecret(const uint8_t* peerX25519Public, uint8_t* out32) {
    if (!gReady || !identity::keyIsUsable(peerX25519Public, identity::PUBLIC_KEY_BYTES)) {
        return false;
    }
    for (size_t i = 0; i < identity::PUBLIC_KEY_BYTES; i++) {
        out32[i] = (uint8_t)(peerX25519Public[i] ^ gX[i]);
    }
    return true;
}

bool rotate(uint32_t nodeId) {
    gEpoch++;
    return begin(nodeId);
}

}  // namespace nodeidentity

namespace textseal {

bool seal(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* plain, size_t plainLen, uint8_t* out, size_t outCapacity,
          size_t* outLen) {
    if (!nodeidentity::isReady() || plain == nullptr || out == nullptr || outLen == nullptr) {
        return false;
    }
    if (!identity::keyIsUsable(peerX25519Public, identity::PUBLIC_KEY_BYTES)) return false;
    if (!sealedtext::fits(plainLen, outCapacity)) return false;

    const uint8_t k = keyByte(peerX25519Public, senderId, recipientId);
    memset(out, 0xA5, sealedtext::NONCE_BYTES);
    uint8_t check = 0;
    for (size_t i = 0; i < plainLen; i++) {
        out[sealedtext::NONCE_BYTES + i] = (uint8_t)(plain[i] ^ k);
        check = (uint8_t)(check + plain[i]);
    }
    uint8_t* tag = out + sealedtext::NONCE_BYTES + plainLen;
    memset(tag, 0, sealedtext::TAG_BYTES);
    tag[0] = check;
    tag[1] = k;
    *outLen = sealedtext::sealedSize(plainLen);
    return true;
}

bool open(const uint8_t* peerX25519Public, uint32_t senderId, uint32_t recipientId,
          const uint8_t* sealed, size_t sealedLen, uint8_t* out, size_t outCapacity,
          size_t* outLen) {
    if (!nodeidentity::isReady() || sealed == nullptr || out == nullptr || outLen == nullptr) {
        return false;
    }
    if (!identity::keyIsUsable(peerX25519Public, identity::PUBLIC_KEY_BYTES)) return false;
    if (!sealedtext::sealedLengthIsSane(sealedLen)) return false;
    const size_t plainLen = sealedtext::plainSize(sealedLen);
    if (plainLen == 0 || plainLen > outCapacity) return false;

    const uint8_t k = keyByte(peerX25519Public, senderId, recipientId);
    uint8_t check = 0;
    for (size_t i = 0; i < plainLen; i++) {
        out[i] = (uint8_t)(sealed[sealedtext::NONCE_BYTES + i] ^ k);
        check = (uint8_t)(check + out[i]);
    }
    const uint8_t* tag = sealed + sealedtext::NONCE_BYTES + plainLen;
    if (tag[0] != check || tag[1] != k) {
        memset(out, 0, plainLen);
        return false;
    }
    *outLen = plainLen;
    return true;
}

}  // namespace textseal
