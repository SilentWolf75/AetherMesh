#include <unity.h>
#include <string.h>
#include "AuthProof.h"

void setUp() {}
void tearDown() {}

static void fromHex(const char* hex, uint8_t* out, size_t n) {
    for (size_t i = 0; i < n; i++) {
        unsigned v = 0;
        sscanf(hex + 2 * i, "%2x", &v);
        out[i] = (uint8_t)v;
    }
}

// Same vectors as the Android and iOS tests.
void test_matches_the_shared_vectors() {
    uint8_t challenge[16];
    for (int i = 0; i < 16; i++) challenge[i] = (uint8_t)i;
    uint8_t expected[32], got[32];
    fromHex("2ab4d919d4aa92c4649a3541a25e6e73908a358a4faf2ca51a7e2577aa969932", expected, 32);
    authproof::compute("admin", challenge, 16, got);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, got, 32);

    memset(challenge, 0xFF, sizeof(challenge));
    fromHex("83ba1a19d57fa4755405d91ac287085ac065ffd75e674ff4eecb86e151de273f", expected, 32);
    authproof::compute("p\xC3\xA4ssw\xC3\xB6rd-with-a-long-tail-0123456789", challenge, 16, got);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, got, 32);
}

void test_verify_accepts_only_the_right_answer() {
    uint8_t challenge[16] = {1, 2, 3};
    uint8_t proof[32];
    authproof::compute("secret", challenge, 16, proof);
    TEST_ASSERT_TRUE(authproof::verify("secret", challenge, 16, proof, 32));
    TEST_ASSERT_FALSE(authproof::verify("Secret", challenge, 16, proof, 32));
    TEST_ASSERT_FALSE(authproof::verify("", challenge, 16, proof, 32));
    TEST_ASSERT_FALSE(authproof::verify("secret", challenge, 16, proof, 31));
    challenge[0] ^= 1;
    TEST_ASSERT_FALSE(authproof::verify("secret", challenge, 16, proof, 32));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_matches_the_shared_vectors);
    RUN_TEST(test_verify_accepts_only_the_right_answer);
    return UNITY_END();
}
