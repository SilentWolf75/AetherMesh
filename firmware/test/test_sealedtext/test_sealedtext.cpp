#include <unity.h>

#include "../../src/SealedText.h"

using namespace sealedtext;

void test_overhead_is_nonce_plus_tag() {
    TEST_ASSERT_EQUAL_UINT32(28, OVERHEAD_BYTES);
    TEST_ASSERT_EQUAL_UINT32(1 + OVERHEAD_BYTES, sealedSize(1));
    TEST_ASSERT_EQUAL_UINT32(164 + OVERHEAD_BYTES, sealedSize(164));
}

void test_round_trip_sizes_agree() {
    for (size_t plain = 1; plain <= 164; plain++) {
        TEST_ASSERT_EQUAL_UINT32(plain, plainSize(sealedSize(plain)));
    }
}

void test_truncated_frames_are_rejected() {
    // A frame with no room for both the nonce and the tag cannot be authentic.
    TEST_ASSERT_FALSE(sealedLengthIsSane(0));
    TEST_ASSERT_FALSE(sealedLengthIsSane(OVERHEAD_BYTES));
    TEST_ASSERT_TRUE(sealedLengthIsSane(OVERHEAD_BYTES + 1));
    TEST_ASSERT_EQUAL_UINT32(0, plainSize(OVERHEAD_BYTES));
    TEST_ASSERT_EQUAL_UINT32(0, plainSize(3));
}

void test_capacity_math_matches_the_wire_field() {
    // TextMessage.sealed is 192 bytes and content is 168, so the longest
    // message the app can hand over still fits once sealed.
    const size_t wireCapacity = 192;
    TEST_ASSERT_EQUAL_UINT32(164, maxPlainFor(wireCapacity));
    TEST_ASSERT_TRUE(fits(164, wireCapacity));
    TEST_ASSERT_FALSE(fits(165, wireCapacity));
    TEST_ASSERT_FALSE(fits(0, wireCapacity));  // nothing to seal
    TEST_ASSERT_EQUAL_UINT32(0, maxPlainFor(OVERHEAD_BYTES));
}

void test_aad_binds_both_ends_and_the_domain() {
    uint8_t a[AAD_BYTES] = {0};
    uint8_t b[AAD_BYTES] = {0};
    TEST_ASSERT_TRUE(buildAad(a, sizeof(a), 0x11223344u, 0x55667788u));
    TEST_ASSERT_TRUE(buildAad(b, sizeof(b), 0x11223344u, 0x55667788u));
    TEST_ASSERT_EQUAL_UINT8_ARRAY(a, b, AAD_BYTES);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(AAD_DOMAIN, a, AAD_DOMAIN_LEN);

    // Swapping the direction changes the authenticated header, so a sealed
    // message cannot be replayed back at its sender as if they wrote it.
    uint8_t reversed[AAD_BYTES] = {0};
    TEST_ASSERT_TRUE(buildAad(reversed, sizeof(reversed), 0x55667788u, 0x11223344u));
    TEST_ASSERT_FALSE(memcmp(a, reversed, AAD_BYTES) == 0);

    TEST_ASSERT_FALSE(buildAad(a, AAD_BYTES - 1, 1, 2));
    TEST_ASSERT_FALSE(buildAad(nullptr, AAD_BYTES, 1, 2));
}

void test_key_pair_order_is_direction_independent() {
    uint32_t low = 0;
    uint32_t high = 0;
    orderedPair(0xAAAAu, 0xBBBBu, low, high);
    TEST_ASSERT_EQUAL_UINT32(0xAAAAu, low);
    TEST_ASSERT_EQUAL_UINT32(0xBBBBu, high);
    // Reversed arguments must produce the same pair, or the two ends would
    // derive different keys and nothing would ever decrypt.
    uint32_t low2 = 0;
    uint32_t high2 = 0;
    orderedPair(0xBBBBu, 0xAAAAu, low2, high2);
    TEST_ASSERT_EQUAL_UINT32(low, low2);
    TEST_ASSERT_EQUAL_UINT32(high, high2);
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_overhead_is_nonce_plus_tag);
    RUN_TEST(test_round_trip_sizes_agree);
    RUN_TEST(test_truncated_frames_are_rejected);
    RUN_TEST(test_capacity_math_matches_the_wire_field);
    RUN_TEST(test_aad_binds_both_ends_and_the_domain);
    RUN_TEST(test_key_pair_order_is_direction_independent);
    return UNITY_END();
}
