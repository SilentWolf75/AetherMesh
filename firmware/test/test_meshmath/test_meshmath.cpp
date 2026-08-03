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
    // SF-scaled bases: snappy at SF7–9, airtime-clear at SF11/12.
    TEST_ASSERT_EQUAL_UINT32(2200, ackRetryBaseMs(7));
    TEST_ASSERT_EQUAL_UINT32(2800, ackRetryBaseMs(9));
    TEST_ASSERT_EQUAL_UINT32(4500, ackRetryBaseMs(11));
    TEST_ASSERT_EQUAL_UINT32(6000, ackRetryBaseMs(12));
    TEST_ASSERT_TRUE(ackRetryBaseMs(12) > ackRetryBaseMs(11));
    TEST_ASSERT_TRUE(ackRetryBaseMs(11) > ackRetryBaseMs(7));
    // Must clear first-ACK airtime floor used by early probes.
    TEST_ASSERT_TRUE(ackRetryBaseMs(11) >= earlyProbeMinDelayMs(11));
    TEST_ASSERT_TRUE(ackRetryBaseMs(12) >= earlyProbeMinDelayMs(12));

    TEST_ASSERT_EQUAL_UINT32(2200, ackRetryDelayMs(7, 0, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(4500, ackRetryDelayMs(11, 0, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(9500, ackRetryDelayMs(11, 1, 5, 0)); // 4500*2 + 500
    TEST_ASSERT_EQUAL_UINT32(21123, ackRetryDelayMs(11, 2, 40, 123)); // 4500*4 + 3000 + 123
    TEST_ASSERT_EQUAL_UINT32(27000, ackRetryDelayMs(12, 9, 30, 0)); // 6000*4 + 3000
    TEST_ASSERT_EQUAL_UINT32(2700, ackRetryDelayMs(7, 0, 0, 9999)); // jitter capped at 500
}

void test_store_forward_wake_helpers() {
    TEST_ASSERT_TRUE(shouldWakeStoredPending(true, false, true));
    TEST_ASSERT_FALSE(shouldWakeStoredPending(true, true, true));  // already woke
    TEST_ASSERT_FALSE(shouldWakeStoredPending(false, false, true)); // not stored
    TEST_ASSERT_FALSE(shouldWakeStoredPending(true, false, false)); // no route
    TEST_ASSERT_EQUAL_UINT32(200, storeForwardWakeDelayMs(7, 0));
    TEST_ASSERT_EQUAL_UINT32(500, storeForwardWakeDelayMs(11, 0));
    TEST_ASSERT_EQUAL_UINT32(800, storeForwardWakeDelayMs(12, 0));
    TEST_ASSERT_EQUAL_UINT32(900, storeForwardWakeDelayMs(11, 9999)); // base+cap
    TEST_ASSERT_TRUE(storeForwardWakeDelayMs(12, 0) > storeForwardWakeDelayMs(7, 0));
}

void test_delivery_soft_age_nudge() {
    TEST_ASSERT_EQUAL_UINT32(900, nudgedSoftAgeTimestamp(1000, 900, 200000)); // fresh: keep timestamp
    // Soft-stale backup pulled forward to now - softAge/2.
    TEST_ASSERT_EQUAL_UINT32(1000000u - 100000u,
                             nudgedSoftAgeTimestamp(1000000u, 1000000u - 300000u, 200000u));
    TEST_ASSERT_EQUAL_UINT32(5000, nudgedSoftAgeTimestamp(5000, 0, 0)); // softAge=0 → now
}

void test_repair_phase_guardrails() {
    TEST_ASSERT_TRUE(mayEarlyBackupProbe(false, false, false));
    TEST_ASSERT_FALSE(mayEarlyBackupProbe(true, false, false));
    TEST_ASSERT_FALSE(mayEarlyBackupProbe(false, true, false));
    TEST_ASSERT_FALSE(mayEarlyBackupProbe(false, false, true)); // stored
    TEST_ASSERT_TRUE(mayEarlyLimitedFlood(false, false));
    TEST_ASSERT_FALSE(mayEarlyLimitedFlood(true, false));
    TEST_ASSERT_FALSE(mayEarlyLimitedFlood(false, true));
}

void test_radio_busy_retry_delay_is_bounded() {
    TEST_ASSERT_EQUAL_UINT32(120, radioBusyRetryDelayMs(0));
    TEST_ASSERT_EQUAL_UINT32(200, radioBusyRetryDelayMs(80));
    TEST_ASSERT_EQUAL_UINT32(300, radioBusyRetryDelayMs(180));
    TEST_ASSERT_EQUAL_UINT32(300, radioBusyRetryDelayMs(999));
}

void test_channel_ack_delay_scales_with_sf_and_clamps_jitter() {
    // Slot 0 node lands at base + jitter only.
    TEST_ASSERT_EQUAL_UINT32(0, channelAckSlotIndex(0));
    TEST_ASSERT_EQUAL_UINT32(150, channelAckDelayMs(7, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(250, channelAckDelayMs(7, 0, 9999)); // base+cap
    TEST_ASSERT_EQUAL_UINT32(600, channelAckDelayMs(11, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(780, channelAckDelayMs(11, 0, 9999));
    TEST_ASSERT_EQUAL_UINT32(1000, channelAckDelayMs(12, 0, 0));
    TEST_ASSERT_EQUAL_UINT32(1200, channelAckDelayMs(12, 0, 9999));
    TEST_ASSERT_EQUAL_UINT32(780, channelAckDelayMs(11, 0, 180));
    // Slot width covers small-ACK airtime without recreating 20s+ grids.
    TEST_ASSERT_TRUE(channelAckSlotWidthMs(11) >= 700);
    TEST_ASSERT_TRUE(channelAckSlotWidthMs(11) <= 900);
    TEST_ASSERT_TRUE(channelAckSlotWidthMs(12) >= 1200);
    TEST_ASSERT_TRUE(channelAckSlotWidthMs(12) <= 1500);
    // Distinct node ids that map to distinct slots stay spaced by slot width.
    uint32_t idA = 1;
    uint32_t idB = 3;
    while (channelAckSlotIndex(idA) == channelAckSlotIndex(idB)) {
        idB++;
    }
    uint32_t delayA = channelAckDelayMs(11, idA, 0);
    uint32_t delayB = channelAckDelayMs(11, idB, 0);
    uint32_t gap = delayA > delayB ? delayA - delayB : delayB - delayA;
    TEST_ASSERT_TRUE(gap >= channelAckSlotWidthMs(11));
    TEST_ASSERT_TRUE(channelAckAltSlotIndex(0xC504A6B0u) < CHANNEL_ACK_SLOT_COUNT);
    TEST_ASSERT_EQUAL_UINT32(4, CHANNEL_ACK_SLOT_COUNT);
}

void test_channel_ack_busy_retry_scales_with_sf() {
    TEST_ASSERT_EQUAL_UINT32(180, channelAckBusyRetryDelayMs(8, 0));
    TEST_ASSERT_EQUAL_UINT32(550, channelAckBusyRetryDelayMs(11, 0));
    TEST_ASSERT_EQUAL_UINT32(950, channelAckBusyRetryDelayMs(11, 9999)); // base+cap
    TEST_ASSERT_EQUAL_UINT32(900, channelAckBusyRetryDelayMs(12, 0));
    TEST_ASSERT_EQUAL_UINT32(1300, channelAckBusyRetryDelayMs(12, 9999));
}

void test_channel_insurance_delay_clears_ack_window() {
    // Insurance must start after max slotted channelAckDelayMs for the same SF.
    TEST_ASSERT_TRUE(channelInsuranceDelayMs(11, 0) > channelAckMaxDelayMs(11));
    TEST_ASSERT_TRUE(channelInsuranceDelayMs(12, 0) > channelAckMaxDelayMs(12));
    // SF11: 600 + 3*700 + 180 = 2880 (under 4s cap)
    TEST_ASSERT_EQUAL_UINT32(600 + 3 * 700 + 180, channelAckMaxDelayMs(11));
    // SF12 raw would be 1000 + 3*1200 + 200 = 4800 → capped at 4000
    TEST_ASSERT_EQUAL_UINT32(CHANNEL_ACK_MAX_DELAY_CAP_MS, channelAckMaxDelayMs(12));
    // SF11 raw 2880+2200=5080 → capped (no recovery wave; insurance only clears primary ACKs)
    TEST_ASSERT_EQUAL_UINT32(CHANNEL_INSURANCE_DELAY_CAP_MS, channelInsuranceDelayMs(11, 0));
    TEST_ASSERT_EQUAL_UINT32(CHANNEL_INSURANCE_DELAY_CAP_MS, channelInsuranceDelayMs(11, 9999));
    // SF12 insurance base 4000+3200=7200 → capped
    TEST_ASSERT_EQUAL_UINT32(CHANNEL_INSURANCE_DELAY_CAP_MS, channelInsuranceDelayMs(12, 0));
    TEST_ASSERT_EQUAL_UINT32(CHANNEL_INSURANCE_DELAY_CAP_MS, channelInsuranceDelayMs(12, 9999));
    // Recovery delay helpers remain for busy-retry alt-slot math / tests only.
    uint32_t recovery0 = channelAckRecoveryDelayMs(11, 0, 0);
    TEST_ASSERT_TRUE(recovery0 > channelInsuranceDelayMs(11, 9999));
    TEST_ASSERT_TRUE(recovery0 <= CHANNEL_ACK_RECOVERY_DELAY_CAP_MS);
    TEST_ASSERT_TRUE(channelAckRecoveryDelayMs(12, 0, 9999) <= CHANNEL_ACK_RECOVERY_DELAY_CAP_MS);
    // Hard caps keep SF11/12 from pinning radios for tens of seconds.
    TEST_ASSERT_TRUE(channelAckMaxDelayMs(11) <= CHANNEL_ACK_MAX_DELAY_CAP_MS);
    TEST_ASSERT_TRUE(channelInsuranceDelayMs(11, 9999) <= CHANNEL_INSURANCE_DELAY_CAP_MS);
}

void test_deadline_order_handles_millis_wrap() {
    TEST_ASSERT_TRUE(deadlineBefore(1100, 1200, 1000));
    TEST_ASSERT_FALSE(deadlineBefore(1200, 1100, 1000));
    TEST_ASSERT_TRUE(deadlineBefore(25, 75, UINT32_MAX - 25));
}

void test_route_metric_smoothing_and_backup_freshness() {
    TEST_ASSERT_EQUAL_UINT8(10, smoothedRouteMetric(10, 10));
    TEST_ASSERT_EQUAL_UINT8(12, smoothedRouteMetric(10, 18));
    TEST_ASSERT_TRUE(backupRouteIsUsable(1000, 900, 100));
    TEST_ASSERT_FALSE(backupRouteIsUsable(1001, 900, 100));
    TEST_ASSERT_TRUE(backupRouteIsUsable(25, UINT32_MAX - 25, 100));
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

void test_directed_next_hop_relay_gate() {
    TEST_ASSERT_TRUE(shouldRelayAsNextHop(0, 0xABCDu));          // flood/legacy
    TEST_ASSERT_TRUE(shouldRelayAsNextHop(0xABCDu, 0xABCDu));     // we are next hop
    TEST_ASSERT_FALSE(shouldRelayAsNextHop(0x1234u, 0xABCDu));    // someone else
}

void test_flood_unicast_policy() {
    TEST_ASSERT_TRUE(shouldFloodUnicast(false, false));  // cold table
    TEST_ASSERT_TRUE(shouldFloodUnicast(true, true));    // recent failure
    TEST_ASSERT_FALSE(shouldFloodUnicast(true, false));  // healthy route
}

void test_multi_hop_learned_metric_is_conservative() {
    TEST_ASSERT_EQUAL_UINT8(2, multiHopLearnedMetric(1));
    TEST_ASSERT_EQUAL_UINT8(20, multiHopLearnedMetric(10));
    TEST_ASSERT_EQUAL_UINT8(255, multiHopLearnedMetric(200));
}

void test_flood_dest_cooldown() {
    TEST_ASSERT_FALSE(floodDestCooldownActive(1000, 0, 6000));
    TEST_ASSERT_TRUE(floodDestCooldownActive(1000, 500, 6000));
    TEST_ASSERT_FALSE(floodDestCooldownActive(7000, 500, 6000));
    TEST_ASSERT_TRUE(floodDestCooldownActive(100, UINT32_MAX - 100, 6000));
}

void test_reply_path_splice_gate() {
    TEST_ASSERT_TRUE(shouldInstallReplyPath(0x100u, 0x200u));
    TEST_ASSERT_FALSE(shouldInstallReplyPath(0, 0x200u));
    TEST_ASSERT_FALSE(shouldInstallReplyPath(0xFFFFFFFFu, 0x200u));
    TEST_ASSERT_FALSE(shouldInstallReplyPath(0x200u, 0x200u)); // self
}

void test_path_repair_rediscover_policy() {
    TEST_ASSERT_TRUE(shouldRediscoverAfterRouteFail(false, false));  // cold
    TEST_ASSERT_TRUE(shouldRediscoverAfterRouteFail(true, true));    // failed
    TEST_ASSERT_FALSE(shouldRediscoverAfterRouteFail(true, false));  // healthy
}

void test_restamp_next_hop_clears_on_flood() {
    TEST_ASSERT_EQUAL_UINT32(0xABCDu, restampNextHopId(0xABCDu, true));
    TEST_ASSERT_EQUAL_UINT32(0u, restampNextHopId(0xABCDu, false));
    TEST_ASSERT_EQUAL_UINT32(0u, restampNextHopId(0u, true));
}

void test_aged_route_metric_decays_after_soft_age() {
    TEST_ASSERT_EQUAL_UINT8(10, agedRouteMetric(10, 1000, 200000));
    TEST_ASSERT_EQUAL_UINT8(10, agedRouteMetric(10, 200000, 200000));
    TEST_ASSERT_EQUAL_UINT8(12, agedRouteMetric(10, 400000, 200000)); // +2
    TEST_ASSERT_EQUAL_UINT8(14, agedRouteMetric(10, 600000, 200000)); // +4
    TEST_ASSERT_EQUAL_UINT8(255, agedRouteMetric(250, 200000 * 20, 200000));
}

void test_route_selection_prefers_fresher_near_equal() {
    // Soft-stale primary (age > softAge) loses to nearly-equal fresh candidate.
    TEST_ASSERT_TRUE(shouldReplaceRoute(0x10, 10, 250000, 0x20, 11, 600000, 200000));
    // Fresh primary still rejects clearly worse candidate.
    TEST_ASSERT_FALSE(shouldReplaceRoute(0x10, 10, 1000, 0x20, 20, 600000, 200000));
}

void test_soft_demote_stale_primary_to_fresher_backup() {
    TEST_ASSERT_TRUE(shouldDemoteStalePrimary(300000, 12, 50000, 14, 200000, true));
    TEST_ASSERT_FALSE(shouldDemoteStalePrimary(100000, 12, 50000, 14, 200000, true));
    TEST_ASSERT_FALSE(shouldDemoteStalePrimary(300000, 12, 250000, 10, 200000, true)); // backup not fresher
    TEST_ASSERT_FALSE(shouldDemoteStalePrimary(300000, 10, 50000, 40, 200000, true)); // backup much worse
    TEST_ASSERT_FALSE(shouldDemoteStalePrimary(300000, 12, 50000, 14, 200000, false));
}

void test_early_backup_and_flood_timers() {
    uint32_t probe7 = earlyBackupProbeDelayMs(7, 0, 0, 200000, 0);
    TEST_ASSERT_TRUE(probe7 >= earlyProbeMinDelayMs(7));
    TEST_ASSERT_TRUE(probe7 <= earlyProbeMaxDelayMs(7));
    uint32_t flood7 = earlyFloodDelayMs(7, 0, 0, 200000, 0);
    TEST_ASSERT_TRUE(flood7 > probe7);

    // SF11/12 floors clear first-ACK airtime (must not probe at ~1.5s).
    TEST_ASSERT_TRUE(earlyProbeMinDelayMs(11) >= 2500);
    TEST_ASSERT_TRUE(earlyProbeMinDelayMs(12) >= 4000);
    uint32_t probe11 = earlyBackupProbeDelayMs(11, 0, 0, 200000, 0);
    uint32_t probe12 = earlyBackupProbeDelayMs(12, 0, 0, 200000, 0);
    TEST_ASSERT_TRUE(probe11 >= earlyProbeMinDelayMs(11));
    TEST_ASSERT_TRUE(probe12 >= earlyProbeMinDelayMs(12));
    TEST_ASSERT_TRUE(probe12 >= probe11);
    TEST_ASSERT_TRUE(probe11 > probe7);

    // Stale routes probe sooner, but never below the SF airtime floor.
    uint32_t stale11 = earlyBackupProbeDelayMs(11, 0, 300000, 200000, 0);
    TEST_ASSERT_TRUE(stale11 >= earlyProbeMinDelayMs(11));
    TEST_ASSERT_TRUE(stale11 <= probe11);

    TEST_ASSERT_TRUE(shouldEarlyBackupProbe(2000, 1500, false, true));
    TEST_ASSERT_FALSE(shouldEarlyBackupProbe(1000, 1500, false, true));
    TEST_ASSERT_FALSE(shouldEarlyBackupProbe(2000, 1500, true, true));
    TEST_ASSERT_TRUE(shouldEarlyLimitedFlood(4000, 3000, false, true));
    TEST_ASSERT_FALSE(shouldEarlyLimitedFlood(4000, 3000, false, false));
    TEST_ASSERT_TRUE(shouldAbandonDirectedOnCad(2, 2));
    TEST_ASSERT_FALSE(shouldAbandonDirectedOnCad(1, 2));
}

void test_rediscovery_pacing_and_piggyback() {
    TEST_ASSERT_TRUE(shouldEmitRouteRequest(true, false));
    TEST_ASSERT_FALSE(shouldEmitRouteRequest(true, true));  // flood piggyback
    TEST_ASSERT_FALSE(shouldEmitRouteRequest(false, false));
    TEST_ASSERT_FALSE(rediscoveryGlobalPacingActive(1000, 0, 3000));
    TEST_ASSERT_TRUE(rediscoveryGlobalPacingActive(2000, 1000, 3000));
    TEST_ASSERT_FALSE(rediscoveryGlobalPacingActive(5000, 1000, 3000));
    TEST_ASSERT_EQUAL_UINT32(8000, routeDiscoveryCooldownMs(7));
    TEST_ASSERT_EQUAL_UINT32(12000, routeDiscoveryCooldownMs(11));
    TEST_ASSERT_EQUAL_UINT32(16000, routeDiscoveryCooldownMs(12));
    TEST_ASSERT_TRUE(routeDiscoveryGlobalGapMs(12) > routeDiscoveryGlobalGapMs(7));
}

void test_return_path_flood_policy() {
    TEST_ASSERT_FALSE(shouldFloodReturnPath(true));
    TEST_ASSERT_TRUE(shouldFloodReturnPath(false));
    // ACK / config result / RREP / traceroute response keep reverse next hop.
    TEST_ASSERT_TRUE(shouldPreferReturnPathNextHop(true, false, false, false));
    TEST_ASSERT_TRUE(shouldPreferReturnPathNextHop(false, true, false, false));
    TEST_ASSERT_TRUE(shouldPreferReturnPathNextHop(false, false, true, false));
    TEST_ASSERT_TRUE(shouldPreferReturnPathNextHop(false, false, false, true));
    // Forward config request / data must not use return-path exemption.
    TEST_ASSERT_FALSE(shouldPreferReturnPathNextHop(false, false, false, false));
    TEST_ASSERT_TRUE(hasUsableDirectedHop(0xABCDu));
    TEST_ASSERT_FALSE(hasUsableDirectedHop(0));
}

void test_retarget_directed_pending_policy() {
    TEST_ASSERT_TRUE(shouldRetargetDirectedPending(0x10, 0x20, false));
    TEST_ASSERT_FALSE(shouldRetargetDirectedPending(0x10, 0x10, false));
    TEST_ASSERT_FALSE(shouldRetargetDirectedPending(0x10, 0x20, true)); // flooded
    TEST_ASSERT_FALSE(shouldRetargetDirectedPending(0, 0x20, false));
    TEST_ASSERT_FALSE(shouldRetargetDirectedPending(0x10, 0, false));
}

void test_congestion_score_and_deferral() {
    // Clear: empty queues, no recent airtime.
    TEST_ASSERT_EQUAL_UINT8(0, congestionScore(0, 8, 0, 4, 0, 10000));
    // Elevated from queue half-full.
    TEST_ASSERT_TRUE(congestionScore(4, 8, 0, 4, 0, 10000) >= 1);
    // Busy from >=50% airtime in window.
    TEST_ASSERT_TRUE(congestionScore(0, 8, 0, 4, 5000, 10000) >= 2);
    // Congested from heavy airtime.
    TEST_ASSERT_EQUAL_UINT8(3, congestionScore(0, 8, 0, 4, 8000, 10000));
    TEST_ASSERT_FALSE(shouldDeferCongestedFlood(1));
    TEST_ASSERT_TRUE(shouldDeferCongestedFlood(2));
    TEST_ASSERT_TRUE(shouldDeferCongestedStoredWake(2));
    TEST_ASSERT_FALSE(shouldDeferCongestedStoredWake(1));
    // Floods/wakes defer more than directed first-hop retries.
    TEST_ASSERT_EQUAL_UINT32(0, congestionDeferMs(0, true, 11, 0));
    TEST_ASSERT_EQUAL_UINT32(0, congestionDeferMs(1, false, 11, 0)); // directed @ elevated
    TEST_ASSERT_TRUE(congestionDeferMs(2, true, 11, 0) >
                     congestionDeferMs(2, false, 11, 0));
    TEST_ASSERT_TRUE(congestionDeferMs(3, true, 12, 0) >
                     congestionDeferMs(3, true, 7, 0));
    TEST_ASSERT_EQUAL_UINT32(1600 + 100, congestionDeferMs(1, true, 11, 100));
}

void test_link_quality_adjusted_metric() {
    TEST_ASSERT_EQUAL_UINT8(10, linkQualityAdjustedMetric(10, 5));  // good link
    TEST_ASSERT_EQUAL_UINT8(10, linkQualityAdjustedMetric(10, 8));  // boundary
    TEST_ASSERT_EQUAL_UINT8(12, linkQualityAdjustedMetric(10, 12)); // +2
    TEST_ASSERT_EQUAL_UINT8(18, linkQualityAdjustedMetric(10, 25)); // +8
    TEST_ASSERT_EQUAL_UINT8(255, linkQualityAdjustedMetric(250, 25));
}

void test_relay_loss_recovery_policy() {
    TEST_ASSERT_TRUE(shouldRetargetAfterRelayLoss(false, false));
    TEST_ASSERT_FALSE(shouldRetargetAfterRelayLoss(true, false));  // already flooded
    TEST_ASSERT_FALSE(shouldRetargetAfterRelayLoss(false, true));  // stored → wake path
    TEST_ASSERT_TRUE(shouldScheduleRediscoveryOnInvalidate(false, true));
    TEST_ASSERT_FALSE(shouldScheduleRediscoveryOnInvalidate(true, true)); // backup promote
    TEST_ASSERT_FALSE(shouldScheduleRediscoveryOnInvalidate(false, false));
    TEST_ASSERT_EQUAL_UINT32(80, relayLossRetargetDelayMs(7, 0));
    TEST_ASSERT_EQUAL_UINT32(220, relayLossRetargetDelayMs(11, 0));
    TEST_ASSERT_EQUAL_UINT32(350, relayLossRetargetDelayMs(12, 0));
    TEST_ASSERT_EQUAL_UINT32(420, relayLossRetargetDelayMs(11, 9999)); // base+cap
    TEST_ASSERT_TRUE(relayLossRetargetDelayMs(12, 0) < earlyProbeMinDelayMs(12));
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
    RUN_TEST(test_store_forward_wake_helpers);
    RUN_TEST(test_delivery_soft_age_nudge);
    RUN_TEST(test_repair_phase_guardrails);
    RUN_TEST(test_radio_busy_retry_delay_is_bounded);
    RUN_TEST(test_channel_ack_delay_scales_with_sf_and_clamps_jitter);
    RUN_TEST(test_channel_ack_busy_retry_scales_with_sf);
    RUN_TEST(test_channel_insurance_delay_clears_ack_window);
    RUN_TEST(test_deadline_order_handles_millis_wrap);
    RUN_TEST(test_route_metric_smoothing_and_backup_freshness);
    RUN_TEST(test_blur_zero_radius_passthrough);
    RUN_TEST(test_blur_no_fix_passthrough);
    RUN_TEST(test_blur_within_radius_per_axis);
    RUN_TEST(test_blur_deterministic);
    RUN_TEST(test_blur_nearby_points_share_cell);
    RUN_TEST(test_directed_next_hop_relay_gate);
    RUN_TEST(test_flood_unicast_policy);
    RUN_TEST(test_multi_hop_learned_metric_is_conservative);
    RUN_TEST(test_flood_dest_cooldown);
    RUN_TEST(test_reply_path_splice_gate);
    RUN_TEST(test_path_repair_rediscover_policy);
    RUN_TEST(test_restamp_next_hop_clears_on_flood);
    RUN_TEST(test_aged_route_metric_decays_after_soft_age);
    RUN_TEST(test_route_selection_prefers_fresher_near_equal);
    RUN_TEST(test_soft_demote_stale_primary_to_fresher_backup);
    RUN_TEST(test_early_backup_and_flood_timers);
    RUN_TEST(test_rediscovery_pacing_and_piggyback);
    RUN_TEST(test_return_path_flood_policy);
    RUN_TEST(test_retarget_directed_pending_policy);
    RUN_TEST(test_congestion_score_and_deferral);
    RUN_TEST(test_link_quality_adjusted_metric);
    RUN_TEST(test_relay_loss_recovery_policy);
    return UNITY_END();
}
