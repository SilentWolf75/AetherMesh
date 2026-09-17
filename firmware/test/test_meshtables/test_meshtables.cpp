/**
 * Tests for the routing bookkeeping extracted from MeshRouter: the dedup ring,
 * route primary/backup promotion, and eviction victim selection.
 *
 * These paths previously had no coverage because MeshRouter.cpp cannot be
 * compiled off-device. MeshTables.h takes the clock as a parameter, so expiry
 * and the 49.7-day millis() rollover can be driven directly here.
 */
#include <unity.h>

#include "../../src/MeshTables.h"

using namespace meshtables;

static const uint32_t TTL = 120000;            // SEEN_PACKET_TIMEOUT_MS
static const uint32_t ROUTE_TIMEOUT = 600000;  // ROUTE_TIMEOUT_MS
static const uint32_t SOFT_AGE = 200000;       // ROUTE_SOFT_AGE_MS

// ---------------------------------------------------------------- dedup ring

void test_seen_cache_matches_only_within_ttl() {
    SeenCache<8> cache;
    cache.reset();
    cache.markSeen(1000, 0xAA, 7, 0);

    TEST_ASSERT_TRUE(cache.hasSeen(1000, 0xAA, 7, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1000 + TTL - 1, 0xAA, 7, TTL));
    // Past the TTL the entry stops counting as seen.
    TEST_ASSERT_FALSE(cache.hasSeen(1000 + TTL + 1, 0xAA, 7, TTL));
    // A different sender with the same packet id is a different packet.
    TEST_ASSERT_FALSE(cache.hasSeen(1000, 0xBB, 7, TTL));
}

void test_duplicate_allows_higher_retry_through() {
    SeenCache<8> cache;
    cache.reset();
    cache.markSeen(1000, 0xAA, 7, 0);

    // Same or older attempt is a duplicate and must be suppressed.
    TEST_ASSERT_TRUE(cache.isDuplicate(1100, 0xAA, 7, 0, TTL));
    // A genuine retransmit carries a higher retry_count and must pass so relays
    // forward the newer attempt.
    TEST_ASSERT_FALSE(cache.isDuplicate(1100, 0xAA, 7, 1, TTL));

    cache.markSeen(1200, 0xAA, 7, 1);
    TEST_ASSERT_TRUE(cache.isDuplicate(1300, 0xAA, 7, 1, TTL));
    TEST_ASSERT_FALSE(cache.isDuplicate(1300, 0xAA, 7, 2, TTL));
}

void test_reobserving_refreshes_slot_instead_of_consuming_one() {
    SeenCache<4> cache;
    cache.reset();
    // One chatty sender must not be able to evict the whole table.
    for (int i = 0; i < 20; i++) {
        cache.markSeen(1000 + (uint32_t)i, 0xAA, 7, 0);
    }
    cache.markSeen(1100, 0xBB, 1, 0);
    cache.markSeen(1100, 0xCC, 2, 0);
    cache.markSeen(1100, 0xDD, 3, 0);

    TEST_ASSERT_TRUE(cache.hasSeen(1100, 0xAA, 7, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1100, 0xBB, 1, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1100, 0xCC, 2, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1100, 0xDD, 3, TTL));
}

void test_seen_cache_evicts_in_ring_order_when_full() {
    SeenCache<4> cache;
    cache.reset();
    for (uint32_t i = 0; i < 4; i++) {
        cache.markSeen(1000, 0x10 + i, i, 0);
    }
    TEST_ASSERT_TRUE(cache.hasSeen(1000, 0x10, 0, TTL));

    // A fifth distinct packet overwrites the oldest ring slot.
    cache.markSeen(1000, 0x20, 9, 0);
    TEST_ASSERT_FALSE(cache.hasSeen(1000, 0x10, 0, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1000, 0x20, 9, TTL));
    TEST_ASSERT_TRUE(cache.hasSeen(1000, 0x13, 3, TTL));
}

void test_seen_cache_survives_millis_rollover() {
    SeenCache<8> cache;
    cache.reset();
    const uint32_t beforeWrap = 0xFFFFFF00u;
    cache.markSeen(beforeWrap, 0xAA, 7, 0);
    const uint32_t afterWrap = beforeWrap + 0x200u;  // wraps past zero
    TEST_ASSERT_TRUE(afterWrap < beforeWrap);
    TEST_ASSERT_TRUE(cache.hasSeen(afterWrap, 0xAA, 7, TTL));
}

void test_seen_cache_detects_hop_limit_inflation() {
    SeenCache<8> cache;
    cache.reset();
    cache.markSeen(1000, 0xAA, 7, 0, 2);

    TEST_ASSERT_TRUE(cache.isHopInflation(1100, 0xAA, 7, 4, 0, TTL));
    TEST_ASSERT_FALSE(cache.isHopInflation(1100, 0xAA, 7, 1, 0, TTL));
    // Originator retry may restore the original hop_limit.
    TEST_ASSERT_FALSE(cache.isHopInflation(1100, 0xAA, 7, 4, 1, TTL));
    TEST_ASSERT_FALSE(cache.isHopInflation(1100, 0xBB, 7, 4, 0, TTL));

    cache.noteBestHop(1200, 0xAA, 7, 0, 5, TTL);
    TEST_ASSERT_EQUAL_UINT32(5, cache.storedHopLimit(1200, 0xAA, 7, TTL));
    // Lower remaining hop must not erase the best shorter-path observation.
    cache.noteBestHop(1300, 0xAA, 7, 0, 1, TTL);
    TEST_ASSERT_EQUAL_UINT32(5, cache.storedHopLimit(1300, 0xAA, 7, TTL));
}

// ------------------------------------------------------------- route updates

static RouteEntry makeRoute(uint32_t target, uint32_t nextHop, uint8_t metric,
                            uint32_t stamp) {
    RouteEntry entry;
    entry.targetId = target;
    entry.nextHopId = nextHop;
    entry.metric = metric;
    entry.timestamp = stamp;
    entry.backupNextHopId = 0;
    entry.backupMetric = 0;
    entry.backupTimestamp = 0;
    entry.hasBackup = false;
    entry.active = true;
    return entry;
}

void test_same_next_hop_refreshes_primary() {
    RouteEntry entry = makeRoute(0xD0, 0x11, 6, 1000);
    const RouteUpdate outcome =
        applyRouteObservation(entry, 0x11, 4, 50000, ROUTE_TIMEOUT, SOFT_AGE);
    TEST_ASSERT_EQUAL(ROUTE_REFRESHED_PRIMARY, outcome);
    TEST_ASSERT_EQUAL_UINT32(0x11, entry.nextHopId);
    TEST_ASSERT_EQUAL_UINT32(50000, entry.timestamp);
    // Smoothed toward the new observation rather than replaced outright.
    TEST_ASSERT_TRUE(entry.metric <= 6);
    TEST_ASSERT_FALSE(entry.hasBackup);
}

void test_promotion_demotes_previous_primary_to_backup() {
    // Stale primary, clearly better challenger.
    RouteEntry entry = makeRoute(0xD0, 0x11, 9, 0);
    const uint32_t now = ROUTE_TIMEOUT + 1000;
    const RouteUpdate outcome =
        applyRouteObservation(entry, 0x22, 1, now, ROUTE_TIMEOUT, SOFT_AGE);
    TEST_ASSERT_EQUAL(ROUTE_PROMOTED, outcome);
    TEST_ASSERT_EQUAL_UINT32(0x22, entry.nextHopId);
    TEST_ASSERT_EQUAL_UINT8(1, entry.metric);
    TEST_ASSERT_EQUAL_UINT32(now, entry.timestamp);
    // The displaced primary is retained as the backup path.
    TEST_ASSERT_TRUE(entry.hasBackup);
    TEST_ASSERT_EQUAL_UINT32(0x11, entry.backupNextHopId);
    TEST_ASSERT_EQUAL_UINT8(9, entry.backupMetric);
}

void test_worse_alternate_becomes_backup_without_disturbing_primary() {
    RouteEntry entry = makeRoute(0xD0, 0x11, 2, 1000);
    const RouteUpdate outcome =
        applyRouteObservation(entry, 0x22, 8, 2000, ROUTE_TIMEOUT, SOFT_AGE);
    TEST_ASSERT_EQUAL(ROUTE_BACKUP_INSTALLED, outcome);
    TEST_ASSERT_EQUAL_UINT32(0x11, entry.nextHopId);
    TEST_ASSERT_EQUAL_UINT8(2, entry.metric);
    TEST_ASSERT_TRUE(entry.hasBackup);
    TEST_ASSERT_EQUAL_UINT32(0x22, entry.backupNextHopId);
}

void test_hearing_known_backup_refreshes_its_timestamp() {
    RouteEntry entry = makeRoute(0xD0, 0x11, 2, 5000);
    entry.hasBackup = true;
    entry.backupNextHopId = 0x22;
    entry.backupMetric = 5;
    entry.backupTimestamp = 1000;

    applyRouteObservation(entry, 0x22, 5, 6000, ROUTE_TIMEOUT, SOFT_AGE);
    TEST_ASSERT_EQUAL_UINT32(0x22, entry.backupNextHopId);
    TEST_ASSERT_EQUAL_UINT32(6000, entry.backupTimestamp);
    // Primary untouched.
    TEST_ASSERT_EQUAL_UINT32(0x11, entry.nextHopId);
}

// ------------------------------------------------------------------ eviction

void test_oldest_route_index_picks_least_recently_refreshed() {
    RouteEntry entries[4];
    entries[0] = makeRoute(0xA, 1, 1, 9000);
    entries[1] = makeRoute(0xB, 2, 1, 1000);  // oldest
    entries[2] = makeRoute(0xC, 3, 1, 7000);
    entries[3] = makeRoute(0xD, 4, 1, 9500);
    TEST_ASSERT_EQUAL_INT(1, oldestRouteIndex(entries, 4, 10000));
}

void test_oldest_route_index_is_rollover_safe() {
    // now has just wrapped past zero; the genuinely old row still carries a
    // large pre-wrap timestamp. A raw less-than comparison would pick index 1,
    // the freshest row, and evict the newest route on a long-running node.
    const uint32_t now = 500;
    RouteEntry entries[3];
    entries[0] = makeRoute(0xA, 1, 1, 0xFFFF0000u);  // oldest
    entries[1] = makeRoute(0xB, 2, 1, 400);          // newest
    entries[2] = makeRoute(0xC, 3, 1, 0xFFFFFF00u);

    TEST_ASSERT_EQUAL_INT(0, oldestRouteIndex(entries, 3, now));
    // Sanity: the naive comparison really would have chosen the newest row.
    TEST_ASSERT_TRUE(entries[1].timestamp < entries[0].timestamp);
}

void test_reply_hop_table_learns_latest_budget_per_sender() {
    ReplyHopTable table;
    TEST_ASSERT_EQUAL_UINT8(0, table.lookup(0xA));
    table.observe(100, 0xA, 12);
    table.observe(200, 0xB, 4);
    TEST_ASSERT_EQUAL_UINT8(12, table.lookup(0xA));
    table.observe(300, 0xA, 6); // path got shorter
    TEST_ASSERT_EQUAL_UINT8(6, table.lookup(0xA));
    table.observe(400, 0, 9);   // ignored
    table.observe(400, 0xC, 0); // ignored
    TEST_ASSERT_EQUAL_UINT8(0, table.lookup(0));
    TEST_ASSERT_EQUAL_UINT8(0, table.lookup(0xC));
}

void test_reply_hop_table_evicts_least_recent_sender() {
    ReplyHopTable table;
    for (uint32_t i = 0; i < (uint32_t)ReplyHopTable::CAPACITY; i++) {
        table.observe(1000 + i, 0x100 + i, 5);
    }
    table.observe(5000, 0x100, 7); // refresh the oldest so 0x101 becomes oldest
    table.observe(6000, 0x999, 9);
    TEST_ASSERT_EQUAL_UINT8(7, table.lookup(0x100));
    TEST_ASSERT_EQUAL_UINT8(0, table.lookup(0x101));
    TEST_ASSERT_EQUAL_UINT8(9, table.lookup(0x999));
}

void setUp(void) {}
void tearDown(void) {}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_seen_cache_matches_only_within_ttl);
    RUN_TEST(test_duplicate_allows_higher_retry_through);
    RUN_TEST(test_reobserving_refreshes_slot_instead_of_consuming_one);
    RUN_TEST(test_seen_cache_evicts_in_ring_order_when_full);
    RUN_TEST(test_seen_cache_survives_millis_rollover);
    RUN_TEST(test_seen_cache_detects_hop_limit_inflation);
    RUN_TEST(test_same_next_hop_refreshes_primary);
    RUN_TEST(test_promotion_demotes_previous_primary_to_backup);
    RUN_TEST(test_worse_alternate_becomes_backup_without_disturbing_primary);
    RUN_TEST(test_hearing_known_backup_refreshes_its_timestamp);
    RUN_TEST(test_oldest_route_index_picks_least_recently_refreshed);
    RUN_TEST(test_oldest_route_index_is_rollover_safe);
    RUN_TEST(test_reply_hop_table_learns_latest_budget_per_sender);
    RUN_TEST(test_reply_hop_table_evicts_least_recent_sender);
    return UNITY_END();
}
