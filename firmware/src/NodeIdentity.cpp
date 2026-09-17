#include "NodeIdentity.h"

#include <Arduino.h>
#include <string.h>

#include <Crypto.h>
#include <Curve25519.h>
#include <Ed25519.h>
#include <SHA256.h>

#ifdef ESP32
#include <Preferences.h>
#include <esp_system.h>
#else
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
using namespace Adafruit_LittleFS_Namespace;
#endif

namespace nodeidentity {
namespace {

// Stored form: the seed plus the epoch. Everything else is derived, so a backup
// of these 36 bytes is a backup of the node's identity.
struct StoredIdentity {
    uint8_t seed[identity::SEED_BYTES];
    uint32_t epoch;
};

StoredIdentity gStored = {};
uint8_t gXPublic[identity::PUBLIC_KEY_BYTES] = {0};
uint8_t gXPrivate[identity::PUBLIC_KEY_BYTES] = {0};
uint8_t gEdPublic[identity::PUBLIC_KEY_BYTES] = {0};
uint8_t gEdPrivate[identity::PUBLIC_KEY_BYTES] = {0};
uint32_t gNodeId = 0;
bool gReady = false;

void hardwareRandom(uint8_t* out, size_t len) {
#ifdef ESP32
    esp_fill_random(out, len);
#else
    // nRF52 hardware RNG. Bias correction is on so the bytes are uniform.
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

// Separate labels so the two private keys are independent even though one seed
// produces both: learning one must not reveal the other.
void derivePrivate(const char* label, uint8_t* out32) {
    SHA256 hash;
    hash.reset();
    hash.update(label, strlen(label));
    hash.update(gStored.seed, sizeof(gStored.seed));
    hash.finalize(out32, identity::PUBLIC_KEY_BYTES);
}

void deriveKeys() {
    derivePrivate("AMID1-ed25519", gEdPrivate);
    Ed25519::derivePublicKey(gEdPublic, gEdPrivate);

    derivePrivate("AMID1-x25519", gXPrivate);
    // dh1 clamps the private key in place and writes the matching public key.
    Curve25519::dh1(gXPublic, gXPrivate);
}

bool loadStored(StoredIdentity& out) {
#ifdef ESP32
    Preferences store;
    if (!store.begin("mesh-identity", true)) return false;
    size_t read = store.getBytes("identity", &out, sizeof(out));
    store.end();
    return read == sizeof(out);
#else
    InternalFS.begin();
    const char* paths[] = {"/identity.bin", "/identity.bak"};
    for (const char* path : paths) {
        if (!InternalFS.exists(path)) continue;
        File file(InternalFS);
        if (!file.open(path, FILE_O_READ)) continue;
        bool ok = file.size() == sizeof(out) &&
                  file.read((uint8_t*)&out, sizeof(out)) == (int)sizeof(out);
        file.close();
        if (ok) return true;
    }
    return false;
#endif
}

bool saveStored(const StoredIdentity& in) {
#ifdef ESP32
    Preferences store;
    if (!store.begin("mesh-identity", false)) return false;
    bool saved = store.putBytes("identity", &in, sizeof(in)) == sizeof(in);
    store.end();
    return saved;
#else
    // Write-then-rename with a backup: losing the seed to a half-written file
    // would change this node's identity and alarm every peer that knows it.
    InternalFS.remove("/identity.tmp");
    File file(InternalFS);
    if (!file.open("/identity.tmp", FILE_O_WRITE)) return false;
    bool written = file.write((const uint8_t*)&in, sizeof(in)) == (int)sizeof(in);
    file.close();
    if (!written) return false;
    if (InternalFS.exists("/identity.bin")) {
        InternalFS.remove("/identity.bak");
        if (!InternalFS.rename("/identity.bin", "/identity.bak")) return false;
    }
    if (!InternalFS.rename("/identity.tmp", "/identity.bin")) {
        if (InternalFS.exists("/identity.bak")) InternalFS.rename("/identity.bak", "/identity.bin");
        return false;
    }
    return true;
#endif
}

bool seedLooksUnset(const StoredIdentity& s) {
    for (size_t i = 0; i < sizeof(s.seed); i++) {
        if (s.seed[i] != 0) return false;
    }
    return true;
}

}  // namespace

bool begin(uint32_t nodeId) {
    gNodeId = nodeId;
    gReady = false;

    StoredIdentity stored = {};
    if (!loadStored(stored) || seedLooksUnset(stored)) {
        hardwareRandom(stored.seed, sizeof(stored.seed));
        stored.epoch = 1;
        if (seedLooksUnset(stored)) {
            Serial.println("Identity: hardware RNG returned nothing; node has no identity.");
            return false;
        }
        if (!saveStored(stored)) {
            Serial.println("Identity: could not store a new seed; node has no identity.");
            return false;
        }
        Serial.println("Identity: generated a new node key.");
    }

    gStored = stored;
    deriveKeys();
    if (!identity::keyIsUsable(gXPublic, identity::PUBLIC_KEY_BYTES) ||
        !identity::keyIsUsable(gEdPublic, identity::PUBLIC_KEY_BYTES)) {
        Serial.println("Identity: derived an unusable key; node has no identity.");
        return false;
    }
    gReady = true;

    char text[identity::FINGERPRINT_CHARS];
    if (fingerprint(text, sizeof(text))) {
        Serial.printf("Identity: fingerprint %s (epoch %u)\n", text, gStored.epoch);
    }
    return true;
}

bool isReady() { return gReady; }

const uint8_t* x25519Public() { return gXPublic; }
const uint8_t* ed25519Public() { return gEdPublic; }
uint32_t keyEpoch() { return gStored.epoch; }

bool fingerprintOf(const uint8_t* ed25519PublicKey, char* out, size_t outLen) {
    if (!identity::keyIsUsable(ed25519PublicKey, identity::PUBLIC_KEY_BYTES)) return false;
    uint8_t digest[32];
    SHA256 hash;
    hash.reset();
    hash.update("AMID1-fp", 8);
    hash.update(ed25519PublicKey, identity::PUBLIC_KEY_BYTES);
    hash.finalize(digest, sizeof(digest));
    return identity::formatFingerprint(out, outLen, digest, sizeof(digest));
}

bool fingerprint(char* out, size_t outLen) {
    if (!gReady) return false;
    return fingerprintOf(gEdPublic, out, outLen);
}

bool signAnnouncement(uint8_t* signature64) {
    if (!gReady || signature64 == nullptr) return false;
    uint8_t body[identity::ANNOUNCE_BODY_BYTES];
    if (!identity::buildAnnounceBody(body, sizeof(body), gNodeId, gStored.epoch, gXPublic,
                                     gEdPublic)) {
        return false;
    }
    Ed25519::sign(signature64, gEdPrivate, gEdPublic, body, sizeof(body));
    return true;
}

bool verifyAnnouncement(uint32_t nodeId, uint32_t keyEpoch, const uint8_t* x25519PublicKey,
                        const uint8_t* ed25519PublicKey, const uint8_t* signature64) {
    if (signature64 == nullptr) return false;
    uint8_t body[identity::ANNOUNCE_BODY_BYTES];
    if (!identity::buildAnnounceBody(body, sizeof(body), nodeId, keyEpoch, x25519PublicKey,
                                     ed25519PublicKey)) {
        return false;
    }
    return Ed25519::verify(signature64, ed25519PublicKey, body, sizeof(body));
}

bool sharedSecret(const uint8_t* peerX25519Public, uint8_t* out32) {
    if (!gReady || out32 == nullptr) return false;
    if (!identity::keyIsUsable(peerX25519Public, identity::PUBLIC_KEY_BYTES)) return false;
    // dh2 consumes the peer point in place and rejects the small-order points
    // that would hand every listener the same shared secret.
    uint8_t shared[identity::PUBLIC_KEY_BYTES];
    uint8_t priv[identity::PUBLIC_KEY_BYTES];
    memcpy(shared, peerX25519Public, sizeof(shared));
    memcpy(priv, gXPrivate, sizeof(priv));
    bool ok = Curve25519::dh2(shared, priv);
    if (ok) memcpy(out32, shared, identity::PUBLIC_KEY_BYTES);
    memset(shared, 0, sizeof(shared));
    memset(priv, 0, sizeof(priv));
    return ok;
}

bool rotate(uint32_t nodeId) {
    StoredIdentity next = {};
    hardwareRandom(next.seed, sizeof(next.seed));
    if (seedLooksUnset(next)) return false;
    next.epoch = gStored.epoch + 1;
    if (!saveStored(next)) return false;
    gStored = next;
    gNodeId = nodeId;
    deriveKeys();
    gReady = identity::keyIsUsable(gXPublic, identity::PUBLIC_KEY_BYTES) &&
             identity::keyIsUsable(gEdPublic, identity::PUBLIC_KEY_BYTES);
    return gReady;
}

}  // namespace nodeidentity
