/**
 * Golden-vector test for remote-config v3 (AMCFG3 + PBKDF2) and v2 HMAC.
 * Must match ControlAuthTest.kt and tools/test_control_auth_vectors.py.
 *
 * Uses OpenSSL (libcrypto) so the native env does not need Arduino Crypto.
 */
#include <unity.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <string.h>
#include <stdint.h>

static const char* PASSWORD = "admin-key";
static const uint8_t SALT[] = {'A', 'M', 'C', 'T', 'R', 'L', '1', 0};
static const uint32_t ITERATIONS = 120000;

static const char* V2_CANONICAL_HEX =
    "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000";
static const char* V2_TAG_HEX = "165a8fa5f809a08d3063ea46c78c64e4";

static const char* V3_CANONICAL_HEX =
    "414d4346473301000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000";
static const char* V3_TAG_HEX = "0cd1d291935a725a4ea210f3ac3dddbf";

static int hexNibble(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static size_t hexDecode(const char* hex, uint8_t* out, size_t capacity) {
    size_t n = 0;
    for (size_t i = 0; hex[i] && hex[i + 1]; i += 2) {
        int hi = hexNibble(hex[i]);
        int lo = hexNibble(hex[i + 1]);
        if (hi < 0 || lo < 0) break;
        if (n >= capacity) return 0;
        out[n++] = (uint8_t)((hi << 4) | lo);
    }
    return n;
}

static void bytesToHex(const uint8_t* data, size_t len, char* out) {
    static const char* digits = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[i * 2] = digits[(data[i] >> 4) & 0xF];
        out[i * 2 + 1] = digits[data[i] & 0xF];
    }
    out[len * 2] = 0;
}

/** Single-block PBKDF2-HMAC-SHA256 matching PacketAuth / ControlKeyDerivation. */
static int deriveControlKey(const char* password, uint8_t out[32]) {
    size_t pwLen = strlen(password);
    uint8_t first[sizeof(SALT) + 4];
    memcpy(first, SALT, sizeof(SALT));
    first[sizeof(SALT) + 0] = 0;
    first[sizeof(SALT) + 1] = 0;
    first[sizeof(SALT) + 2] = 0;
    first[sizeof(SALT) + 3] = 1;

    unsigned int blockLen = 32;
    uint8_t block[32];
    if (!HMAC(EVP_sha256(), password, (int)pwLen, first, sizeof(first), block, &blockLen)) {
        return 0;
    }
    memcpy(out, block, 32);
    for (uint32_t iter = 1; iter < ITERATIONS; iter++) {
        if (!HMAC(EVP_sha256(), password, (int)pwLen, block, 32, block, &blockLen)) {
            return 0;
        }
        for (int i = 0; i < 32; i++) out[i] ^= block[i];
    }
    return 1;
}

static void hmacSha256Trunc16(const uint8_t* key, size_t keyLen,
                              const uint8_t* data, size_t dataLen,
                              uint8_t out16[16]) {
    uint8_t full[32];
    unsigned int fullLen = 32;
    TEST_ASSERT_NOT_NULL(HMAC(EVP_sha256(), key, (int)keyLen, data, dataLen, full, &fullLen));
    memcpy(out16, full, 16);
}

void test_v2_tag_matches_published_vector() {
    uint8_t canon[128];
    size_t canonLen = hexDecode(V2_CANONICAL_HEX, canon, sizeof(canon));
    TEST_ASSERT_EQUAL(101, (int)canonLen);

    uint8_t tag[16];
    hmacSha256Trunc16((const uint8_t*)PASSWORD, strlen(PASSWORD), canon, canonLen, tag);
    char hex[33];
    bytesToHex(tag, 16, hex);
    TEST_ASSERT_EQUAL_STRING(V2_TAG_HEX, hex);
}

void test_v3_tag_matches_published_vector() {
    uint8_t key[32];
    TEST_ASSERT_TRUE(deriveControlKey(PASSWORD, key));

    uint8_t canon[128];
    size_t canonLen = hexDecode(V3_CANONICAL_HEX, canon, sizeof(canon));
    TEST_ASSERT_EQUAL(101, (int)canonLen);
    TEST_ASSERT_EQUAL_UINT8('A', canon[0]);
    TEST_ASSERT_EQUAL_UINT8('3', canon[5]);

    uint8_t tag[16];
    hmacSha256Trunc16(key, 32, canon, canonLen, tag);
    char hex[33];
    bytesToHex(tag, 16, hex);
    TEST_ASSERT_EQUAL_STRING(V3_TAG_HEX, hex);
}

void setUp(void) {}
void tearDown(void) {}

int main(int argc, char** argv) {
    UNITY_BEGIN();
    RUN_TEST(test_v2_tag_matches_published_vector);
    RUN_TEST(test_v3_tag_matches_published_vector);
    return UNITY_END();
}
