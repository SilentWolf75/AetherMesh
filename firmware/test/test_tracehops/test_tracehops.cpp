/**
 * Compact traceroute hop encoding (TraceHops.h). The Android app decodes the
 * same 5-byte layout, so these byte-level expectations are the contract.
 */
#include <unity.h>

#include "../../src/TraceHops.h"

using namespace tracehops;

void setUp(void) {}
void tearDown(void) {}

void test_round_trips_node_and_snr() {
    uint8_t bytes[MAX_BYTES] = {};
    uint16_t length = 0;
    TEST_ASSERT_TRUE(append(bytes, length, sizeof(bytes), 0xC504A6B0u, 6.75f));
    TEST_ASSERT_TRUE(append(bytes, length, sizeof(bytes), 0x14D3228Cu, -12.25f));
    TEST_ASSERT_EQUAL_UINT16(10, length);
    TEST_ASSERT_EQUAL_UINT32(2, hopCount(length));
    TEST_ASSERT_EQUAL_HEX32(0xC504A6B0u, nodeAt(bytes, 0));
    TEST_ASSERT_EQUAL_INT8(27, snrQuarterDbAt(bytes, 0));
    TEST_ASSERT_EQUAL_HEX32(0x14D3228Cu, nodeAt(bytes, 1));
    TEST_ASSERT_EQUAL_INT8(-49, snrQuarterDbAt(bytes, 1));
}

void test_layout_is_little_endian_id_then_snr() {
    uint8_t bytes[HOP_BYTES] = {};
    uint8_t length = 0;
    TEST_ASSERT_TRUE(append(bytes, length, sizeof(bytes), 0x11223344u, -1.0f));
    const uint8_t expected[HOP_BYTES] = {0x44, 0x33, 0x22, 0x11, 0xFC};
    TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, bytes, HOP_BYTES);
}

void test_snr_is_clamped_to_int8() {
    uint8_t bytes[MAX_BYTES] = {};
    uint16_t length = 0;
    append(bytes, length, sizeof(bytes), 1, 100.0f);
    append(bytes, length, sizeof(bytes), 2, -100.0f);
    TEST_ASSERT_EQUAL_INT8(127, snrQuarterDbAt(bytes, 0));
    TEST_ASSERT_EQUAL_INT8(-128, snrQuarterDbAt(bytes, 1));
}

void test_append_stops_at_sixteen_hops() {
    uint8_t bytes[MAX_BYTES + 20] = {};
    uint16_t length = 0;
    for (uint32_t hop = 0; hop < MAX_HOPS; hop++) {
        TEST_ASSERT_TRUE(append(bytes, length, sizeof(bytes), hop + 1, 0.0f));
    }
    // Even with spare buffer, the path never exceeds 16 hops.
    TEST_ASSERT_FALSE(append(bytes, length, sizeof(bytes), 99, 0.0f));
    TEST_ASSERT_EQUAL_UINT16(MAX_BYTES, length);
    TEST_ASSERT_EQUAL_HEX32(16, nodeAt(bytes, 15));
}

void test_append_respects_smaller_capacity() {
    uint8_t bytes[MAX_BYTES] = {};
    uint16_t length = 0;
    TEST_ASSERT_TRUE(append(bytes, length, 7, 1, 0.0f));
    TEST_ASSERT_FALSE(append(bytes, length, 7, 2, 0.0f));
    TEST_ASSERT_EQUAL_UINT16(HOP_BYTES, length);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_round_trips_node_and_snr);
    RUN_TEST(test_layout_is_little_endian_id_then_snr);
    RUN_TEST(test_snr_is_clamped_to_int8);
    RUN_TEST(test_append_stops_at_sixteen_hops);
    RUN_TEST(test_append_respects_smaller_capacity);
    return UNITY_END();
}
