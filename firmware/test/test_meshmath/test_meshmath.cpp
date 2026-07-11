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

void test_route_selection_prefers_better_metric() {
    TEST_ASSERT_TRUE(shouldReplaceRoute(0x10, 20, 1000, 0x20, 10, 600000));
}

void test_route_selection_refreshes_same_next_hop() {
    TEST_ASSERT_TRUE(shouldReplaceRoute(0x10, 10, 1000, 0x10, 20, 600000));
}

void test_route_selection_rejects_worse_fresh_path() {
    TEST_ASSERT_FALSE(shouldReplaceRoute(0x10, 10, 1000, 0x20, 20, 600000));
}

void test_route_selection_replaces_aging_path() {
    TEST_ASSERT_TRUE(shouldReplaceRoute(0x10, 10, 300001, 0x20, 20, 600000));
}

void test_proxy_route_freshness_is_bounded() {
    TEST_ASSERT_TRUE(proxyRouteIsFresh(60000, 60000));
    TEST_ASSERT_FALSE(proxyRouteIsFresh(60001, 60000));
}

void test_seen_entry_expires_and_handles_millis_wrap() {
    TEST_ASSERT_TRUE(seenEntryIsFresh(2000, 1000, 1000));
    TEST_ASSERT_FALSE(seenEntryIsFresh(2001, 1000, 1000));
    TEST_ASSERT_TRUE(seenEntryIsFresh(25, UINT32_MAX - 25, 100));
}

void test_packet_sequence_seed_is_deterministic_nonzero_and_well_mixed() {
    uint32_t first = initialPacketSequence(0x12345678u, 0xABCDEF01u);
    TEST_ASSERT_NOT_EQUAL(0u, first);
    TEST_ASSERT_EQUAL_UINT32(first, initialPacketSequence(0x12345678u, 0xABCDEF01u));
    TEST_ASSERT_NOT_EQUAL(first, initialPacketSequence(0x12345679u, 0xABCDEF01u));
    TEST_ASSERT_NOT_EQUAL(first, initialPacketSequence(0x12345678u, 0xABCDEF02u));
}

void test_ack_retry_delay_backs_off_and_caps_route_penalty() {
    TEST_ASSERT_EQUAL_UINT32(3000, ackRetryDelayMs(0, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(6500, ackRetryDelayMs(1, 5, 0));
    TEST_ASSERT_EQUAL_UINT32(15123, ackRetryDelayMs(2, 40, 123));
    TEST_ASSERT_EQUAL_UINT32(15000, ackRetryDelayMs(9, 30, 0));
}

void test_deadline_order_handles_millis_wrap() {
    TEST_ASSERT_TRUE(deadlineBefore(1100, 1200, 1000));
    TEST_ASSERT_FALSE(deadlineBefore(1200, 1100, 1000));
    TEST_ASSERT_TRUE(deadlineBefore(25, 75, UINT32_MAX - 25));
}

void test_blur_zero_radius_passthrough() {
    float lat, lon;
    blurPosition(38.812345f, -94.912345f, 0, lat, lon);
    TEST_ASSERT_EQUAL_FLOAT(38.812345f, lat);
    TEST_ASSERT_EQUAL_FLOAT(-94.912345f, lon);
}

void test_blur_no_fix_passthrough() {
    // (0,0) means "no GPS fix" in our telemetry; must not be moved to a cell center
    float lat, lon;
    blurPosition(0.0f, 0.0f, 1000, lat, lon);
    TEST_ASSERT_EQUAL_FLOAT(0.0f, lat);
    TEST_ASSERT_EQUAL_FLOAT(0.0f, lon);
}

void test_blur_within_radius_per_axis() {
    // Reported cell center must be within +/-radius of the true position on each axis
    const uint32_t radii[] = {100, 500, 1000, 5000};
    const float positions[][2] = {
        {38.8123f, -94.9123f}, {-33.8688f, 151.2093f}, {64.1466f, -21.9426f}
    };
    for (uint32_t r = 0; r < 4; r++) {
        for (int p = 0; p < 3; p++) {
            float lat, lon;
            blurPosition(positions[p][0], positions[p][1], radii[r], lat, lon);
            double latErrM = fabs((double)lat - positions[p][0]) * 111320.0;
            double lonErrM = fabs((double)lon - positions[p][1]) * 111320.0 *
                             cos(positions[p][0] * 3.14159265358979 / 180.0);
            // ~2% headroom: float32 rounding + the lon cell being sized at the
            // snapped (not true) latitude at high-latitude/large-radius combos
            TEST_ASSERT_TRUE(latErrM <= radii[r] * 1.02 + 2.0);
            TEST_ASSERT_TRUE(lonErrM <= radii[r] * 1.02 + 2.0);
        }
    }
}

void test_blur_deterministic() {
    // Same input -> same output every time (no jitter to average away)
    float lat1, lon1, lat2, lon2;
    blurPosition(38.8123f, -94.9123f, 800, lat1, lon1);
    blurPosition(38.8123f, -94.9123f, 800, lat2, lon2);
    TEST_ASSERT_EQUAL_FLOAT(lat1, lat2);
    TEST_ASSERT_EQUAL_FLOAT(lon1, lon2);
}

void test_blur_nearby_points_share_cell() {
    // Two points ~50m apart with a 1km radius should usually snap to the same
    // cell center — verifies real snapping (not just rounding the last digit)
    float latA, lonA, latB, lonB;
    blurPosition(38.81230f, -94.91230f, 1000, latA, lonA);
    blurPosition(38.81260f, -94.91260f, 1000, latB, lonB);
    TEST_ASSERT_EQUAL_FLOAT(latA, latB);
    TEST_ASSERT_EQUAL_FLOAT(lonA, lonB);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_hopcost_strong_link_is_min);
    RUN_TEST(test_hopcost_weak_link_is_high);
    RUN_TEST(test_hopcost_monotonic);
    RUN_TEST(test_backoff_strong_link_shortest);
    RUN_TEST(test_backoff_weak_link_longest);
    RUN_TEST(test_backoff_monotonic);
    RUN_TEST(test_route_selection_prefers_better_metric);
    RUN_TEST(test_route_selection_refreshes_same_next_hop);
    RUN_TEST(test_route_selection_rejects_worse_fresh_path);
    RUN_TEST(test_route_selection_replaces_aging_path);
    RUN_TEST(test_proxy_route_freshness_is_bounded);
    RUN_TEST(test_seen_entry_expires_and_handles_millis_wrap);
    RUN_TEST(test_packet_sequence_seed_is_deterministic_nonzero_and_well_mixed);
    RUN_TEST(test_ack_retry_delay_backs_off_and_caps_route_penalty);
    RUN_TEST(test_deadline_order_handles_millis_wrap);
    RUN_TEST(test_blur_zero_radius_passthrough);
    RUN_TEST(test_blur_no_fix_passthrough);
    RUN_TEST(test_blur_within_radius_per_axis);
    RUN_TEST(test_blur_deterministic);
    RUN_TEST(test_blur_nearby_points_share_cell);
    return UNITY_END();
}
