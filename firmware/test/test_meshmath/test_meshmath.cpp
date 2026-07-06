#include <unity.h>
#include "../../src/MeshMath.h"

using namespace meshmath;

void test_hopcost_strong_link_is_min() {
    // SNR at/above +10 dB -> cheapest hop cost of 1
    TEST_ASSERT_EQUAL_UINT8(1, hopCost(10.0f));
    TEST_ASSERT_EQUAL_UINT8(1, hopCost(20.0f)); // clamped
}

void test_hopcost_weak_link_is_high() {
    // SNR at/below -20 dB -> clamped, highest cost (~25)
    TEST_ASSERT_EQUAL_UINT8(25, hopCost(-20.0f));
    TEST_ASSERT_EQUAL_UINT8(25, hopCost(-40.0f)); // clamped
}

void test_hopcost_monotonic() {
    // Worse SNR must never cost less than better SNR
    uint8_t prev = 0;
    for (float snr = 10.0f; snr >= -20.0f; snr -= 1.0f) {
        uint8_t c = hopCost(snr);
        TEST_ASSERT_GREATER_OR_EQUAL_UINT8(prev, c);
        prev = c;
    }
}

void test_backoff_strong_link_shortest() {
    TEST_ASSERT_EQUAL_UINT32(500, rebroadcastDelayMs(10.0f));
    TEST_ASSERT_EQUAL_UINT32(500, rebroadcastDelayMs(25.0f)); // clamped
}

void test_backoff_weak_link_longest() {
    TEST_ASSERT_EQUAL_UINT32(2000, rebroadcastDelayMs(-20.0f));
    TEST_ASSERT_EQUAL_UINT32(2000, rebroadcastDelayMs(-50.0f)); // clamped
}

void test_backoff_monotonic() {
    uint32_t prev = 0;
    for (float snr = 10.0f; snr >= -20.0f; snr -= 1.0f) {
        uint32_t d = rebroadcastDelayMs(snr);
        TEST_ASSERT_GREATER_OR_EQUAL_UINT32(prev, d);
        prev = d;
    }
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_hopcost_strong_link_is_min);
    RUN_TEST(test_hopcost_weak_link_is_high);
    RUN_TEST(test_hopcost_monotonic);
    RUN_TEST(test_backoff_strong_link_shortest);
    RUN_TEST(test_backoff_weak_link_longest);
    RUN_TEST(test_backoff_monotonic);
    return UNITY_END();
}
