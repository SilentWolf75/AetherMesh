#include <unity.h>

#include "../../src/IdentityPolicy.h"

using namespace identity;

static void fillKey(uint8_t* key, uint8_t value) {
    for (size_t i = 0; i < PUBLIC_KEY_BYTES; i++) key[i] = (uint8_t)(value + i);
}

void test_all_zero_keys_are_rejected() {
    uint8_t zero[PUBLIC_KEY_BYTES] = {0};
    uint8_t good[PUBLIC_KEY_BYTES];
    fillKey(good, 7);
    // An all-zero X25519 public key makes every shared secret zero, so a peer
    // announcing one could read traffic meant for it.
    TEST_ASSERT_FALSE(keyIsUsable(zero, PUBLIC_KEY_BYTES));
    TEST_ASSERT_TRUE(keyIsUsable(good, PUBLIC_KEY_BYTES));
    TEST_ASSERT_FALSE(keyIsUsable(good, 31));
    TEST_ASSERT_FALSE(keyIsUsable(nullptr, PUBLIC_KEY_BYTES));
    TEST_ASSERT_EQUAL(Trust::Invalid, classify(true, false, nullptr, 0, zero, good, 1));
    TEST_ASSERT_EQUAL(Trust::Invalid, classify(true, false, nullptr, 0, good, zero, 1));
}

void test_announce_body_is_canonical_and_domain_separated() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t ed[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(ed, 100);
    uint8_t a[ANNOUNCE_BODY_BYTES] = {0};
    uint8_t b[ANNOUNCE_BODY_BYTES] = {0};
    TEST_ASSERT_TRUE(buildAnnounceBody(a, sizeof(a), 0x14D3228Cu, 3, x, ed));
    TEST_ASSERT_TRUE(buildAnnounceBody(b, sizeof(b), 0x14D3228Cu, 3, x, ed));
    TEST_ASSERT_EQUAL_UINT8_ARRAY(a, b, ANNOUNCE_BODY_BYTES);

    // The tag keeps an announcement signature from being valid anywhere else.
    TEST_ASSERT_EQUAL_UINT8_ARRAY(ANNOUNCE_DOMAIN, a, ANNOUNCE_DOMAIN_LEN);
    // Node id and epoch are covered, so neither can be swapped after signing.
    uint8_t other[ANNOUNCE_BODY_BYTES] = {0};
    TEST_ASSERT_TRUE(buildAnnounceBody(other, sizeof(other), 0x14D3228Du, 3, x, ed));
    TEST_ASSERT_FALSE(memcmp(a, other, ANNOUNCE_BODY_BYTES) == 0);
    TEST_ASSERT_TRUE(buildAnnounceBody(other, sizeof(other), 0x14D3228Cu, 4, x, ed));
    TEST_ASSERT_FALSE(memcmp(a, other, ANNOUNCE_BODY_BYTES) == 0);

    // Refuses to build with a short buffer or unusable keys.
    TEST_ASSERT_FALSE(buildAnnounceBody(a, ANNOUNCE_BODY_BYTES - 1, 1, 1, x, ed));
    uint8_t zero[PUBLIC_KEY_BYTES] = {0};
    TEST_ASSERT_FALSE(buildAnnounceBody(a, sizeof(a), 1, 1, zero, ed));
}

void test_bad_signature_is_never_trusted() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t ed[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(ed, 100);
    TEST_ASSERT_EQUAL(Trust::Invalid, classify(false, false, nullptr, 0, x, ed, 1));
    TEST_ASSERT_EQUAL(Trust::Invalid, classify(false, true, ed, 1, x, ed, 1));
    TEST_ASSERT_FALSE(shouldStore(Trust::Invalid));
    TEST_ASSERT_FALSE(mayEncryptTo(Trust::Invalid));
}

void test_first_use_is_stored_and_usable() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t ed[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(ed, 100);
    Trust t = classify(true, false, nullptr, 0, x, ed, 1);
    TEST_ASSERT_EQUAL(Trust::FirstUse, t);
    TEST_ASSERT_TRUE(shouldStore(t));
    TEST_ASSERT_TRUE(mayEncryptTo(t));
    TEST_ASSERT_FALSE(shouldWarnUser(t));
}

void test_repeat_announcement_matches_and_cannot_rewind_epoch() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t ed[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(ed, 100);
    TEST_ASSERT_EQUAL(Trust::Match, classify(true, true, ed, 4, x, ed, 4));
    TEST_ASSERT_EQUAL(Trust::Match, classify(true, true, ed, 4, x, ed, 5));
    // A captured older announcement replayed at us is not a downgrade path.
    TEST_ASSERT_EQUAL(Trust::Invalid, classify(true, true, ed, 4, x, ed, 3));
}

void test_changed_key_without_epoch_bump_warns_and_is_not_stored() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t mine[PUBLIC_KEY_BYTES];
    uint8_t impostor[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(mine, 100);
    fillKey(impostor, 200);
    // Someone else signing an announcement for a node id we already know.
    Trust t = classify(true, true, mine, 2, x, impostor, 2);
    TEST_ASSERT_EQUAL(Trust::Changed, t);
    TEST_ASSERT_FALSE(shouldStore(t));       // never silently overwritten
    TEST_ASSERT_FALSE(mayEncryptTo(t));      // and never sealed to
    TEST_ASSERT_TRUE(shouldWarnUser(t));     // the user decides
    // Lower epoch is equally untrusted.
    TEST_ASSERT_EQUAL(Trust::Changed, classify(true, true, mine, 9, x, impostor, 1));
}

void test_rotation_is_accepted_but_still_surfaced() {
    uint8_t x[PUBLIC_KEY_BYTES];
    uint8_t oldEd[PUBLIC_KEY_BYTES];
    uint8_t newEd[PUBLIC_KEY_BYTES];
    fillKey(x, 1);
    fillKey(oldEd, 100);
    fillKey(newEd, 200);
    Trust t = classify(true, true, oldEd, 2, x, newEd, 3);
    TEST_ASSERT_EQUAL(Trust::Rotated, t);
    TEST_ASSERT_TRUE(shouldStore(t));
    TEST_ASSERT_TRUE(shouldWarnUser(t));
    // A reflash by a stranger looks exactly like this, so it is not auto-sealed to.
    TEST_ASSERT_FALSE(mayEncryptTo(t));
}

void test_stranger_announcements_are_rate_limited() {
    // Never announce without an identity to announce.
    TEST_ASSERT_FALSE(shouldAnnounceForStranger(100000, 0, false, false));
    // A node that has never announced answers immediately, so a fresh node is
    // reachable for sealed mail without waiting out the slow timer.
    TEST_ASSERT_TRUE(shouldAnnounceForStranger(100000, 0, false, true));
    // After announcing, a burst of strangers must not turn into a burst of
    // announcements: one per gap.
    TEST_ASSERT_FALSE(shouldAnnounceForStranger(100000, 100000, true, true));
    TEST_ASSERT_FALSE(shouldAnnounceForStranger(100000 + ANNOUNCE_REPLY_MIN_GAP_MS - 1,
                                                100000, true, true));
    TEST_ASSERT_TRUE(shouldAnnounceForStranger(100000 + ANNOUNCE_REPLY_MIN_GAP_MS,
                                               100000, true, true));
    // The gap is long enough that even the smallest announcement stays a rounding
    // error against the channel at the slowest speed.
    TEST_ASSERT_TRUE(meshmath::loraAirtimeMs(12, ANNOUNCE_FRAME_BYTES) * 4u <
                     ANNOUNCE_REPLY_MIN_GAP_MS);
}

void test_announcements_stay_far_rarer_than_beacons() {
    for (uint8_t sf = 7; sf <= 12; sf++) {
        const uint32_t beacon = meshmath::telemetryIntervalSecFor(sf, 60);
        const uint32_t announce = announceIntervalSecFor(sf, beacon);
        TEST_ASSERT_TRUE(announce >= beacon * ANNOUNCE_BEACON_MULTIPLE);
        // And never faster than the airtime budget allows for the bigger frame.
        TEST_ASSERT_TRUE(meshmath::loraAirtimeMs(sf, ANNOUNCE_FRAME_BYTES) * 100u <=
                         announce * 1000u * meshmath::TELEMETRY_DUTY_PERCENT);
    }
}

void test_fingerprint_is_stable_groups_of_four() {
    uint8_t digest[32];
    for (size_t i = 0; i < sizeof(digest); i++) digest[i] = (uint8_t)(0xA0 + i);
    char text[FINGERPRINT_CHARS];
    TEST_ASSERT_TRUE(formatFingerprint(text, sizeof(text), digest, sizeof(digest)));
    TEST_ASSERT_EQUAL_STRING("A0A1-A2A3-A4A5-A6A7", text);
    TEST_ASSERT_FALSE(formatFingerprint(text, FINGERPRINT_CHARS - 1, digest, sizeof(digest)));
    TEST_ASSERT_FALSE(formatFingerprint(text, sizeof(text), digest, 4));
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_all_zero_keys_are_rejected);
    RUN_TEST(test_announce_body_is_canonical_and_domain_separated);
    RUN_TEST(test_bad_signature_is_never_trusted);
    RUN_TEST(test_first_use_is_stored_and_usable);
    RUN_TEST(test_repeat_announcement_matches_and_cannot_rewind_epoch);
    RUN_TEST(test_changed_key_without_epoch_bump_warns_and_is_not_stored);
    RUN_TEST(test_rotation_is_accepted_but_still_surfaced);
    RUN_TEST(test_stranger_announcements_are_rate_limited);
    RUN_TEST(test_announcements_stay_far_rarer_than_beacons);
    RUN_TEST(test_fingerprint_is_stable_groups_of_four);
    return UNITY_END();
}
