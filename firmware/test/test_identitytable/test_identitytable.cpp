#include <unity.h>

#include "../../src/IdentityTable.h"

using namespace identity;

static void makeKey(uint8_t* key, uint8_t value) {
    for (size_t i = 0; i < PUBLIC_KEY_BYTES; i++) key[i] = (uint8_t)(value ^ (i + 1));
}

static PeerTable table;
static uint8_t xA[PUBLIC_KEY_BYTES];
static uint8_t edA[PUBLIC_KEY_BYTES];
static uint8_t xB[PUBLIC_KEY_BYTES];
static uint8_t edB[PUBLIC_KEY_BYTES];

void setUp() {
    table.clear();
    makeKey(xA, 0x11);
    makeKey(edA, 0x22);
    makeKey(xB, 0x33);
    makeKey(edB, 0x44);
}

void tearDown() {}

void test_first_announcement_is_remembered() {
    TEST_ASSERT_EQUAL(Trust::FirstUse, table.observe(1000, 0xAA, xA, edA, 1, true));
    TEST_ASSERT_EQUAL_INT(1, table.size());
    const Peer* peer = table.lookup(0xAA);
    TEST_ASSERT_NOT_NULL(peer);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(edA, peer->edPublic, PUBLIC_KEY_BYTES);
    TEST_ASSERT_EQUAL_UINT32(1, peer->epoch);
    TEST_ASSERT_TRUE(table.mayEncrypt(0xAA));
}

void test_unsigned_announcement_stores_nothing() {
    TEST_ASSERT_EQUAL(Trust::Invalid, table.observe(1000, 0xAA, xA, edA, 1, false));
    TEST_ASSERT_EQUAL_INT(0, table.size());
    TEST_ASSERT_FALSE(table.mayEncrypt(0xAA));
    TEST_ASSERT_EQUAL(Trust::Invalid, table.observe(1000, 0, xA, edA, 1, true)); // node id 0
    TEST_ASSERT_EQUAL_INT(0, table.size());
}

void test_repeat_announcement_refreshes_without_changing_the_key() {
    table.observe(1000, 0xAA, xA, edA, 1, true);
    TEST_ASSERT_EQUAL(Trust::Match, table.observe(5000, 0xAA, xA, edA, 1, true));
    const Peer* peer = table.lookup(0xAA);
    TEST_ASSERT_EQUAL_UINT32(5000, peer->lastSeenMs);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(edA, peer->edPublic, PUBLIC_KEY_BYTES);
    TEST_ASSERT_EQUAL_INT(1, table.size());
}

void test_impostor_cannot_overwrite_a_known_key() {
    table.observe(1000, 0xAA, xA, edA, 5, true);
    // Correctly signed by its own key, but claiming a node id we already know
    // and without advancing the epoch.
    TEST_ASSERT_EQUAL(Trust::Changed, table.observe(2000, 0xAA, xB, edB, 5, true));
    const Peer* peer = table.lookup(0xAA);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(edA, peer->edPublic, PUBLIC_KEY_BYTES); // original kept
    TEST_ASSERT_TRUE(peer->unresolvedChange);
    // And nothing gets sealed to that node until the dispute is settled.
    TEST_ASSERT_FALSE(table.mayEncrypt(0xAA));
}

void test_user_can_accept_a_disputed_key() {
    table.observe(1000, 0xAA, xA, edA, 5, true);
    table.observe(2000, 0xAA, xB, edB, 5, true);
    TEST_ASSERT_TRUE(table.acceptChange(3000, 0xAA, xB, edB, 6));
    const Peer* peer = table.lookup(0xAA);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(edB, peer->edPublic, PUBLIC_KEY_BYTES);
    TEST_ASSERT_FALSE(peer->unresolvedChange);
    TEST_ASSERT_TRUE(table.mayEncrypt(0xAA));
    // Accepting an unknown node, or a degenerate key, is refused.
    TEST_ASSERT_FALSE(table.acceptChange(3000, 0xBB, xB, edB, 1));
    uint8_t zero[PUBLIC_KEY_BYTES] = {0};
    TEST_ASSERT_FALSE(table.acceptChange(3000, 0xAA, zero, edB, 7));
}

void test_rotation_updates_the_key_but_pauses_encryption() {
    table.observe(1000, 0xAA, xA, edA, 5, true);
    TEST_ASSERT_EQUAL(Trust::Rotated, table.observe(2000, 0xAA, xB, edB, 6, true));
    const Peer* peer = table.lookup(0xAA);
    TEST_ASSERT_EQUAL_UINT8_ARRAY(edB, peer->edPublic, PUBLIC_KEY_BYTES);
    TEST_ASSERT_EQUAL_UINT32(6, peer->epoch);
    // A reflash by a stranger is indistinguishable from an owner rotating keys,
    // so the user confirms before anything is sealed to the new key.
    TEST_ASSERT_TRUE(peer->unresolvedChange);
    TEST_ASSERT_FALSE(table.mayEncrypt(0xAA));
}

void test_replayed_old_announcement_is_rejected() {
    table.observe(1000, 0xAA, xB, edB, 6, true);
    TEST_ASSERT_EQUAL(Trust::Invalid, table.observe(2000, 0xAA, xB, edB, 5, true));
    TEST_ASSERT_EQUAL_UINT32(6, table.lookup(0xAA)->epoch);
}

void test_full_table_evicts_least_recently_seen_and_reports_it() {
    for (int i = 0; i < PeerTable::CAPACITY; i++) {
        uint8_t x[PUBLIC_KEY_BYTES];
        uint8_t ed[PUBLIC_KEY_BYTES];
        makeKey(x, (uint8_t)(i + 1));
        makeKey(ed, (uint8_t)(0x80 + i));
        table.observe(1000 + (uint32_t)i, (uint32_t)(0x100 + i), x, ed, 1, true);
    }
    TEST_ASSERT_EQUAL_INT(PeerTable::CAPACITY, table.size());
    TEST_ASSERT_EQUAL_UINT32(0, table.evictions());
    TEST_ASSERT_NOT_NULL(table.lookup(0x100)); // oldest, still present

    table.observe(9000, 0xBEEF, xA, edA, 1, true);
    TEST_ASSERT_EQUAL_UINT32(1, table.evictions());
    TEST_ASSERT_NOT_NULL(table.lookup(0xBEEF));
    TEST_ASSERT_NULL(table.lookup(0x100)); // the least recently seen gave way
    TEST_ASSERT_EQUAL_INT(PeerTable::CAPACITY, table.size());
}

void test_unknown_node_is_never_sealed_to() {
    TEST_ASSERT_FALSE(table.mayEncrypt(0x1234));
    TEST_ASSERT_NULL(table.lookup(0x1234));
    TEST_ASSERT_NULL(table.at(0));
    TEST_ASSERT_NULL(table.at(-1));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_first_announcement_is_remembered);
    RUN_TEST(test_unsigned_announcement_stores_nothing);
    RUN_TEST(test_repeat_announcement_refreshes_without_changing_the_key);
    RUN_TEST(test_impostor_cannot_overwrite_a_known_key);
    RUN_TEST(test_user_can_accept_a_disputed_key);
    RUN_TEST(test_rotation_updates_the_key_but_pauses_encryption);
    RUN_TEST(test_replayed_old_announcement_is_rejected);
    RUN_TEST(test_full_table_evicts_least_recently_seen_and_reports_it);
    RUN_TEST(test_unknown_node_is_never_sealed_to);
    return UNITY_END();
}
