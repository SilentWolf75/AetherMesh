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

void test_channel_store_forward_helpers() {
    TEST_ASSERT_TRUE(channelStoreEntryFresh(1000, 900, 500));
    TEST_ASSERT_FALSE(channelStoreEntryFresh(1000, 400, 500));
    TEST_ASSERT_TRUE(neighborWasOffline(5000, 1000, 2000));
    TEST_ASSERT_FALSE(neighborWasOffline(2500, 1000, 2000));
    TEST_ASSERT_TRUE(shouldChannelCatchup(true, false, true, true));
    TEST_ASSERT_FALSE(shouldChannelCatchup(false, false, true, true)); // Client
    TEST_ASSERT_FALSE(shouldChannelCatchup(true, true, true, true));  // quiet
    TEST_ASSERT_FALSE(shouldChannelCatchup(true, false, false, true));
    TEST_ASSERT_FALSE(shouldChannelCatchup(true, false, true, false)); // no route
    TEST_ASSERT_TRUE(shouldStoreChannelBroadcast(true, true, true, false));
    TEST_ASSERT_FALSE(shouldStoreChannelBroadcast(false, true, true, false));
    TEST_ASSERT_FALSE(shouldStoreChannelBroadcast(true, false, true, false));
    TEST_ASSERT_FALSE(shouldStoreChannelBroadcast(true, true, true, true)); // range test
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
    // A slot holds one whole ACK plus jitter, so adjacent hearers never overlap.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        TEST_ASSERT_TRUE(channelAckSlotWidthMs(sf) >=
                         loraAirtimeMs(sf, CHANNEL_ACK_FRAME_BYTES) + channelAckJitterCapMs(sf));
    }
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

void test_channel_flood_slots_never_overlap() {
    // Field bug: every hearer relayed a channel broadcast after the same
    // 500-2000 ms SNR backoff. At SF12 one copy is ~3.2 s on air, so the relays
    // transmitted over each other and were deaf to each other's ACKs, and the
    // originator credited only one hearer.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        for (uint32_t bytes = 40; bytes <= 200; bytes += 53) {
            const uint32_t width = channelFloodSlotWidthMs(sf, bytes);
            // A slot holds one whole relay copy plus the SNR lead-in.
            TEST_ASSERT_TRUE(width >= loraAirtimeMs(sf, bytes) + CHANNEL_FLOOD_LEAD_CAP_MS);
            // Consecutive slots are separated by more than one copy on air.
            for (uint32_t slot = 0; slot + 1 < CHANNEL_FLOOD_SLOT_COUNT; slot++) {
                const uint32_t earliestNext = (slot + 1) * width;
                const uint32_t latestInSlot = slot * width + CHANNEL_FLOOD_LEAD_CAP_MS;
                TEST_ASSERT_TRUE(earliestNext >= latestInSlot + loraAirtimeMs(sf, bytes));
            }
            // The window covers the last slot's copy finishing.
            TEST_ASSERT_TRUE(channelFloodWindowMs(sf, bytes) >=
                             (CHANNEL_FLOOD_SLOT_COUNT - 1u) * width + loraAirtimeMs(sf, bytes));
        }
    }
    // Zero bytes = "no relay wave"; keeps the old 3-argument ACK timing.
    TEST_ASSERT_EQUAL_UINT32(0, channelFloodWindowMs(12, 0));
    TEST_ASSERT_EQUAL_UINT32(channelAckDelayMs(12, 0x1234u, 0),
                             channelAckDelayMs(12, 0x1234u, 0, 0));
}

void test_channel_flood_slot_assignment_spreads_and_reshuffles() {
    // Sequential node ids (ESP MAC suffixes) must not pile into one slot.
    uint32_t counts[CHANNEL_FLOOD_SLOT_COUNT] = {0};
    for (uint32_t id = 0x14D32280u; id < 0x14D32280u + 60u; id++) {
        counts[channelFloodSlotIndex(id, 7u)]++;
    }
    for (uint32_t slot = 0; slot < CHANNEL_FLOOD_SLOT_COUNT; slot++) {
        TEST_ASSERT_TRUE(counts[slot] >= 10);
    }
    // Two nodes sharing a slot for one packet separate on a later one.
    uint32_t sharedPackets = 0;
    for (uint32_t packetId = 1; packetId <= 40; packetId++) {
        if (channelFloodSlotIndex(0xAABBCCDDu, packetId) ==
            channelFloodSlotIndex(0x11223344u, packetId)) {
            sharedPackets++;
        }
    }
    TEST_ASSERT_TRUE(sharedPackets < 40);
}

void test_channel_ack_waits_out_the_relay_wave() {
    // An ACK sent while another hearer is relaying the same message is lost.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        for (uint32_t bytes = 40; bytes <= 200; bytes += 53) {
            for (uint32_t id = 0; id < 8; id++) {
                TEST_ASSERT_TRUE(channelAckDelayMs(sf, 0x1000u + id, 0, bytes) >=
                                 channelFloodWindowMs(sf, bytes));
            }
            TEST_ASSERT_TRUE(channelAckMaxDelayMs(sf, bytes) > channelAckMaxDelayMs(sf, 0));
        }
    }
}

void test_route_aging_follows_beacon_cadence() {
    const uint32_t baseSoft = 200000u;   // ROUTE_SOFT_AGE_MS
    const uint32_t baseTimeout = 600000u; // ROUTE_TIMEOUT_MS
    // Fast cadence keeps the historical fixed windows.
    TEST_ASSERT_EQUAL_UINT32(baseSoft, neighborSoftAgeMsFor(60, baseSoft));
    TEST_ASSERT_EQUAL_UINT32(baseTimeout,
                             routeTimeoutMsFor(60, baseTimeout, baseSoft));
    // Stretched SF12 beacons must not age neighbors out between beacons.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        const uint32_t beacon = telemetryIntervalSecFor(sf, 60);
        const uint32_t soft = neighborSoftAgeMsFor(beacon, baseSoft);
        const uint32_t timeout = routeTimeoutMsFor(beacon, baseTimeout, soft);
        TEST_ASSERT_TRUE(soft >= beacon * 2000u);    // survives a missed beacon
        TEST_ASSERT_TRUE(timeout > soft);            // route outlives soft-stale
        TEST_ASSERT_TRUE(timeout >= beacon * 2500u);
    }
}

void test_telemetry_interval_respects_airtime_budget() {
    // Fast SFs keep the configured cadence.
    TEST_ASSERT_EQUAL_UINT32(60, telemetryIntervalSecFor(7, 60));
    TEST_ASSERT_EQUAL_UINT32(60, telemetryIntervalSecFor(9, 60));
    // SF11/12 stretch: a 120-byte beacon is seconds of airtime, and four nodes
    // beaconing every 60 s at SF12 filled ~30% of the channel, starving receipts.
    TEST_ASSERT_TRUE(telemetryIntervalSecFor(11, 60) > 100);
    TEST_ASSERT_TRUE(telemetryIntervalSecFor(12, 60) > 200);
    // Never below the configured interval, and one node stays inside its budget.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        uint32_t sec = telemetryIntervalSecFor(sf, 60);
        TEST_ASSERT_TRUE(sec >= 60);
        TEST_ASSERT_TRUE(loraAirtimeMs(sf, TELEMETRY_FRAME_BYTES) * 100u <=
                         sec * 1000u * TELEMETRY_DUTY_PERCENT);
    }
    // A user asking for a slower beacon is always honored.
    TEST_ASSERT_EQUAL_UINT32(3600, telemetryIntervalSecFor(12, 3600));
}

void test_channel_receipts_stop_when_slots_run_out() {
    // Receipts are unconditional in the app now, so the radio bounds them:
    // one slot per hearer, or nobody answers and the relay is the receipt.
    for (uint32_t neighbors = 0; neighbors <= CHANNEL_ACK_SLOT_COUNT; neighbors++) {
        TEST_ASSERT_TRUE(shouldSendChannelReceipt(neighbors));
    }
    TEST_ASSERT_FALSE(shouldSendChannelReceipt(CHANNEL_ACK_SLOT_COUNT + 1));
    TEST_ASSERT_FALSE(shouldSendChannelReceipt(50));
    // Every hearer that does answer owns a distinct slot start.
    for (uint32_t a = 0; a < CHANNEL_ACK_SLOT_COUNT; a++) {
        for (uint32_t b = a + 1; b < CHANNEL_ACK_SLOT_COUNT; b++) {
            TEST_ASSERT_TRUE(b * channelAckSlotWidthMs(12) >=
                             a * channelAckSlotWidthMs(12) + loraAirtimeMs(12, CHANNEL_ACK_FRAME_BYTES));
        }
    }
}

void test_lora_airtime_matches_semtech_reference() {
    // Reference values from the Semtech time-on-air formula, computed
    // independently in Python (125 kHz, CR 4/5, explicit header, CRC, LDRO at SF11/12).
    TEST_ASSERT_UINT32_WITHIN(1, 143, loraAirtimeMs(7, 64));
    TEST_ASSERT_UINT32_WITHIN(1, 423, loraAirtimeMs(9, 64));
    TEST_ASSERT_UINT32_WITHIN(1, 764, loraAirtimeMs(10, 64));
    TEST_ASSERT_UINT32_WITHIN(1, 1692, loraAirtimeMs(11, 64));
    TEST_ASSERT_UINT32_WITHIN(1, 3056, loraAirtimeMs(12, 64));
    TEST_ASSERT_UINT32_WITHIN(1, 3219, loraAirtimeMs(12, 70));
    TEST_ASSERT_UINT32_WITHIN(1, 3412, loraAirtimeMs(11, 160));
    TEST_ASSERT_UINT32_WITHIN(1, 6169, loraAirtimeMs(12, 160));
}

void test_channel_insurance_delay_clears_ack_window() {
    // Field bug (SF12 "Max range"): insurance at +5 s landed on hearers' ACKs.
    // Hearers start their ACK timer when the message ENDS on air; the originator
    // queues insurance when it STARTS sending. The insurance copy must begin only
    // after the latest ACK has finished, for every SF and message size.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        for (uint32_t bytes = 20; bytes <= 255; bytes += 47) {
            const uint32_t lastAckEnds = loraAirtimeMs(sf, bytes) +
                                         channelAckMaxDelayMs(sf, bytes) +
                                         loraAirtimeMs(sf, CHANNEL_ACK_FRAME_BYTES);
            TEST_ASSERT_TRUE(channelInsuranceDelayMs(sf, 0, bytes) > lastAckEnds);
            TEST_ASSERT_TRUE(channelInsuranceDelayMs(sf, 9999, bytes) <= CHANNEL_TIMING_SANITY_CAP_MS);
        }
    }
    // The measured field timeline: 3.2 s message, ACK slots, then insurance.
    TEST_ASSERT_TRUE(channelInsuranceDelayMs(12, 0, 70) > 3219u + channelAckMaxDelayMs(12) + 3056u);
    // Recovery ACK still lands after the insurance copy has finished.
    uint32_t recovery0 = channelAckRecoveryDelayMs(11, 0, 0);
    TEST_ASSERT_TRUE(recovery0 > channelInsuranceDelayMs(11, 9999, 128) + loraAirtimeMs(11, 128));
    TEST_ASSERT_TRUE(channelAckRecoveryDelayMs(12, 0, 9999) <= CHANNEL_ACK_RECOVERY_DELAY_CAP_MS);
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

void test_text_pending_ack_defer_scales_with_sf() {
    TEST_ASSERT_EQUAL_UINT32(80, textPendingAckDeferMs(7));
    TEST_ASSERT_EQUAL_UINT32(150, textPendingAckDeferMs(10));
    TEST_ASSERT_EQUAL_UINT32(250, textPendingAckDeferMs(11));
    TEST_ASSERT_EQUAL_UINT32(400, textPendingAckDeferMs(12));
    TEST_ASSERT_TRUE(textPendingAckDeferMs(12) > textPendingAckDeferMs(11));
    TEST_ASSERT_TRUE(textPendingAckDeferMs(11) > textPendingAckDeferMs(7));
}

void test_soft_stale_route_floods() {
    TEST_ASSERT_FALSE(shouldFloodSoftStaleRoute(1000, 200000));
    TEST_ASSERT_FALSE(shouldFloodSoftStaleRoute(200000, 200000));
    TEST_ASSERT_TRUE(shouldFloodSoftStaleRoute(200001, 200000));
    TEST_ASSERT_FALSE(shouldFloodSoftStaleRoute(999999, 0)); // softAge disabled
}

// --- SF-keyed channel-ACK timing helpers -------------------------------------
// These are step functions on spreading factor. The step boundaries are easy to
// get off by one, and the ordering matters: if a higher SF ever returned a
// shorter margin than a lower one, slotted ACKs would start before the previous
// transmission cleared the air and collide.

void test_channel_ack_base_delay_steps_and_ordering() {
    TEST_ASSERT_EQUAL_UINT32(150, channelAckBaseDelayMs(7));
    TEST_ASSERT_EQUAL_UINT32(150, channelAckBaseDelayMs(8));
    TEST_ASSERT_EQUAL_UINT32(250, channelAckBaseDelayMs(9));
    TEST_ASSERT_EQUAL_UINT32(350, channelAckBaseDelayMs(10));
    TEST_ASSERT_EQUAL_UINT32(600, channelAckBaseDelayMs(11));
    TEST_ASSERT_EQUAL_UINT32(1000, channelAckBaseDelayMs(12));
    // Above the top step stays clamped, never wraps back down.
    TEST_ASSERT_EQUAL_UINT32(1000, channelAckBaseDelayMs(13));
    for (uint8_t sf = 8; sf <= 13; sf++) {
        TEST_ASSERT_TRUE(channelAckBaseDelayMs(sf) >= channelAckBaseDelayMs(sf - 1));
    }
}

void test_channel_ack_airtime_margin_steps_and_ordering() {
    for (uint8_t sf = 7; sf <= 12; sf++) {
        TEST_ASSERT_TRUE(channelAckAirtimeMarginMs(sf) > loraAirtimeMs(sf, CHANNEL_ACK_FRAME_BYTES));
    }
    TEST_ASSERT_EQUAL_UINT32(channelAckAirtimeMarginMs(12), channelAckAirtimeMarginMs(13)); // clamped SF
    for (uint8_t sf = 8; sf <= 13; sf++) {
        TEST_ASSERT_TRUE(channelAckAirtimeMarginMs(sf) >= channelAckAirtimeMarginMs(sf - 1));
    }
}

void test_channel_ack_jitter_cap_steps_and_ordering() {
    TEST_ASSERT_EQUAL_UINT32(100, channelAckJitterCapMs(8));
    TEST_ASSERT_EQUAL_UINT32(120, channelAckJitterCapMs(9));
    TEST_ASSERT_EQUAL_UINT32(140, channelAckJitterCapMs(10));
    TEST_ASSERT_EQUAL_UINT32(180, channelAckJitterCapMs(11));
    TEST_ASSERT_EQUAL_UINT32(200, channelAckJitterCapMs(12));
    for (uint8_t sf = 8; sf <= 13; sf++) {
        TEST_ASSERT_TRUE(channelAckJitterCapMs(sf) >= channelAckJitterCapMs(sf - 1));
    }
    // Jitter must stay small relative to the base delay it perturbs, or slots
    // from adjacent nodes overlap.
    for (uint8_t sf = 7; sf <= 12; sf++) {
        TEST_ASSERT_TRUE(channelAckJitterCapMs(sf) <= channelAckBaseDelayMs(sf));
    }
}

void test_channel_insurance_jitter_cap_steps_and_ordering() {
    TEST_ASSERT_EQUAL_UINT32(500, channelInsuranceJitterCapMs(9));
    TEST_ASSERT_EQUAL_UINT32(600, channelInsuranceJitterCapMs(10));
    TEST_ASSERT_EQUAL_UINT32(800, channelInsuranceJitterCapMs(11));
    TEST_ASSERT_EQUAL_UINT32(800, channelInsuranceJitterCapMs(12));
    for (uint8_t sf = 8; sf <= 13; sf++) {
        TEST_ASSERT_TRUE(channelInsuranceJitterCapMs(sf) >= channelInsuranceJitterCapMs(sf - 1));
    }
}

void test_early_flood_gap_steps_and_ordering() {
    TEST_ASSERT_EQUAL_UINT32(800, earlyFloodGapMs(9));
    TEST_ASSERT_EQUAL_UINT32(1200, earlyFloodGapMs(10));
    TEST_ASSERT_EQUAL_UINT32(1800, earlyFloodGapMs(11));
    TEST_ASSERT_EQUAL_UINT32(2500, earlyFloodGapMs(12));
    TEST_ASSERT_EQUAL_UINT32(2500, earlyFloodGapMs(13));
    for (uint8_t sf = 8; sf <= 13; sf++) {
        TEST_ASSERT_TRUE(earlyFloodGapMs(sf) >= earlyFloodGapMs(sf - 1));
    }
}

void test_clampf_bounds() {
    TEST_ASSERT_EQUAL_FLOAT(0.0f, clampf(-5.0f, 0.0f, 10.0f));
    TEST_ASSERT_EQUAL_FLOAT(10.0f, clampf(50.0f, 0.0f, 10.0f));
    TEST_ASSERT_EQUAL_FLOAT(4.5f, clampf(4.5f, 0.0f, 10.0f));
    // Values exactly on each bound are returned unchanged.
    TEST_ASSERT_EQUAL_FLOAT(0.0f, clampf(0.0f, 0.0f, 10.0f));
    TEST_ASSERT_EQUAL_FLOAT(10.0f, clampf(10.0f, 0.0f, 10.0f));
    // Negative ranges (SNR clamps) behave the same way.
    TEST_ASSERT_EQUAL_FLOAT(-20.0f, clampf(-100.0f, -20.0f, 10.0f));
}

void test_preamble_length_is_sf_aware() {
    TEST_ASSERT_EQUAL_UINT16(32, preambleLengthForSf(7));
    TEST_ASSERT_EQUAL_UINT16(32, preambleLengthForSf(8));
    TEST_ASSERT_EQUAL_UINT16(16, preambleLengthForSf(9));
    TEST_ASSERT_EQUAL_UINT16(16, preambleLengthForSf(11));
    TEST_ASSERT_EQUAL_UINT16(16, preambleLengthForSf(12));
}

void test_direct_relayer_credit_requires_one_hop_decrement() {
    TEST_ASSERT_TRUE(isDirectRelayerCredit(3, 4));
    TEST_ASSERT_FALSE(isDirectRelayerCredit(2, 4)); // overheard a copy
    TEST_ASSERT_FALSE(isDirectRelayerCredit(4, 4)); // not decremented
    TEST_ASSERT_FALSE(isDirectRelayerCredit(3, 0));
    TEST_ASSERT_TRUE(isDirectRelayerCredit(0, 1));
}

void test_flood_loop_helpers() {
    TEST_ASSERT_TRUE(isEchoLoop(0xAA, 0x11, 0x11));
    TEST_ASSERT_FALSE(isEchoLoop(0x11, 0x11, 0x11)); // originator loopback
    TEST_ASSERT_FALSE(isEchoLoop(0xAA, 0x22, 0x11));
    TEST_ASSERT_FALSE(isEchoLoop(0xAA, 0x11, 0));
    TEST_ASSERT_TRUE(hopLimitIsSane(0));
    TEST_ASSERT_TRUE(hopLimitIsSane(8));
    TEST_ASSERT_TRUE(hopLimitIsSane(9));
    TEST_ASSERT_TRUE(hopLimitIsSane(16));
    TEST_ASSERT_FALSE(hopLimitIsSane(17));
    TEST_ASSERT_TRUE(isHopLimitInflation(2, 4, 0, 0));
    TEST_ASSERT_FALSE(isHopLimitInflation(2, 4, 0, 1)); // genuine retry
    TEST_ASSERT_FALSE(isHopLimitInflation(4, 3, 0, 0));
}

void test_hop_start_sanity() {
    TEST_ASSERT_TRUE(hopStartIsSane(0, 16));   // legacy sender
    TEST_ASSERT_TRUE(hopStartIsSane(12, 12));
    TEST_ASSERT_TRUE(hopStartIsSane(12, 1));
    TEST_ASSERT_FALSE(hopStartIsSane(4, 6));   // hop_limit rewound above start
    TEST_ASSERT_FALSE(hopStartIsSane(17, 3));
}

void test_transmissions_taken() {
    TEST_ASSERT_EQUAL_UINT32(1, transmissionsTaken(8, 8));   // heard originator directly
    TEST_ASSERT_EQUAL_UINT32(5, transmissionsTaken(12, 8));
    TEST_ASSERT_EQUAL_UINT32(0, transmissionsTaken(0, 3));   // unknown
    TEST_ASSERT_EQUAL_UINT32(0, transmissionsTaken(4, 6));   // malformed
}

void test_reply_hop_limit_covers_the_request_path() {
    // Direct neighbor: never below the old fixed budget.
    TEST_ASSERT_EQUAL_UINT32(4, replyHopLimit(8, 8, 4));
    // 7 transmissions + margin 2 = 9, capped at the originator's start of 8
    // so legacy relays that carried the request still carry the reply.
    TEST_ASSERT_EQUAL_UINT32(8, replyHopLimit(8, 2, 4));
    // Extended range: 10 transmissions + 2.
    TEST_ASSERT_EQUAL_UINT32(12, replyHopLimit(16, 7, 4));
    TEST_ASSERT_EQUAL_UINT32(16, replyHopLimit(16, 1, 4));
    // Legacy sender: follow configured reach, 4..8.
    TEST_ASSERT_EQUAL_UINT32(4, replyHopLimit(0, 3, 2));
    TEST_ASSERT_EQUAL_UINT32(6, replyHopLimit(0, 3, 6));
    TEST_ASSERT_EQUAL_UINT32(8, replyHopLimit(0, 3, 16));
}

void test_phone_origin_hop_limit_applies_node_setting() {
    TEST_ASSERT_EQUAL_UINT32(0, phoneOriginHopLimit(0, 12, false));  // local only
    TEST_ASSERT_EQUAL_UINT32(1, phoneOriginHopLimit(1, 12, false));  // direct ping
    TEST_ASSERT_EQUAL_UINT32(12, phoneOriginHopLimit(4, 12, false)); // chat follows setting
    TEST_ASSERT_EQUAL_UINT32(2, phoneOriginHopLimit(4, 2, false));   // ...both ways
    TEST_ASSERT_EQUAL_UINT32(7, phoneOriginHopLimit(7, 4, true));    // trace keeps reach
    TEST_ASSERT_EQUAL_UINT32(12, phoneOriginHopLimit(7, 12, true));
    TEST_ASSERT_EQUAL_UINT32(4, phoneOriginHopLimit(6, 0, false));   // unset config
    TEST_ASSERT_EQUAL_UINT32(16, phoneOriginHopLimit(30, 20, true)); // clamped
}

void test_effective_blur_picks_coarser_radius() {
    TEST_ASSERT_EQUAL_UINT32(200, effectiveBlurRadiusM(50, 200));
    TEST_ASSERT_EQUAL_UINT32(200, effectiveBlurRadiusM(200, 50));
    TEST_ASSERT_EQUAL_UINT32(0, effectiveBlurRadiusM(0, 0));
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
    RUN_TEST(test_channel_store_forward_helpers);
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
    RUN_TEST(test_text_pending_ack_defer_scales_with_sf);
    RUN_TEST(test_soft_stale_route_floods);
    RUN_TEST(test_channel_ack_base_delay_steps_and_ordering);
    RUN_TEST(test_channel_ack_airtime_margin_steps_and_ordering);
    RUN_TEST(test_channel_ack_jitter_cap_steps_and_ordering);
    RUN_TEST(test_channel_insurance_jitter_cap_steps_and_ordering);
    RUN_TEST(test_early_flood_gap_steps_and_ordering);
    RUN_TEST(test_clampf_bounds);
    RUN_TEST(test_preamble_length_is_sf_aware);
    RUN_TEST(test_direct_relayer_credit_requires_one_hop_decrement);
    RUN_TEST(test_flood_loop_helpers);
    RUN_TEST(test_lora_airtime_matches_semtech_reference);
    RUN_TEST(test_channel_receipts_stop_when_slots_run_out);
    RUN_TEST(test_telemetry_interval_respects_airtime_budget);
    RUN_TEST(test_route_aging_follows_beacon_cadence);
    RUN_TEST(test_channel_flood_slots_never_overlap);
    RUN_TEST(test_channel_flood_slot_assignment_spreads_and_reshuffles);
    RUN_TEST(test_channel_ack_waits_out_the_relay_wave);
    RUN_TEST(test_hop_start_sanity);
    RUN_TEST(test_transmissions_taken);
    RUN_TEST(test_reply_hop_limit_covers_the_request_path);
    RUN_TEST(test_phone_origin_hop_limit_applies_node_setting);
    RUN_TEST(test_effective_blur_picks_coarser_radius);
    return UNITY_END();
}
