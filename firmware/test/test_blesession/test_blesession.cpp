/**
 * Tests for BLE connection-scoped trust (BleSession.h).
 *
 * Regression cover for two bugs in the phone link:
 *  - OTA packets were handled before the authentication check, so a second
 *    phone connecting inside the 30s OTA timeout could stream its own image.
 *  - Authentication was reset only when loop() sampled a connected-flag edge,
 *    so a disconnect + reconnect inside one loop pass let the new phone
 *    inherit the old session.
 */
#include <unity.h>

#include "../../src/BleSession.h"

using blesession::OtaPacketKind;
using blesession::PhonePacketRoute;
using blesession::Session;

void setUp() {}
void tearDown() {}

void test_no_connection_is_unauthenticated() {
    Session session;
    TEST_ASSERT_FALSE(session.isAuthenticated(blesession::NO_GENERATION));
    TEST_ASSERT_TRUE(session.route(blesession::NO_GENERATION, blesession::NO_GENERATION, false) ==
                     PhonePacketRoute::Drop);
}

void test_authentication_is_scoped_to_its_connection() {
    Session session;
    session.markAuthenticated(1);
    TEST_ASSERT_TRUE(session.isAuthenticated(1));
    // Disconnect (2) and reconnect (3) — the new phone starts unauthenticated.
    TEST_ASSERT_FALSE(session.isAuthenticated(2));
    TEST_ASSERT_FALSE(session.isAuthenticated(3));
}

void test_missed_disconnect_reconnect_does_not_inherit_auth() {
    Session session;
    TEST_ASSERT_TRUE(session.consumeGenerationChange(1));
    session.markAuthenticated(1);
    // loop() was blocked while the stack bumped 1 -> 2 -> 3. The connected flag
    // looks unchanged, but the new phone's packets must hit the auth handshake.
    TEST_ASSERT_TRUE(session.route(3, 3, false) == PhonePacketRoute::AuthHandshake);
    TEST_ASSERT_TRUE(session.consumeGenerationChange(3));
    TEST_ASSERT_FALSE(session.consumeGenerationChange(3));
}

void test_packets_from_previous_connection_are_dropped() {
    Session session;
    session.markAuthenticated(1);
    // Queued while connection 1 was live, drained after it ended.
    TEST_ASSERT_TRUE(session.route(1, 2, false) == PhonePacketRoute::Drop);
    TEST_ASSERT_TRUE(session.route(1, 3, true) == PhonePacketRoute::Drop);
}

void test_authenticated_routes() {
    Session session;
    session.markAuthenticated(5);
    TEST_ASSERT_TRUE(session.route(5, 5, false) == PhonePacketRoute::Normal);
    TEST_ASSERT_TRUE(session.route(5, 5, true) == PhonePacketRoute::OtaOnly);
}

void test_unauthenticated_connection_cannot_reach_ota_during_update() {
    Session session;
    session.markAuthenticated(1);
    session.claimOta(1);
    // A different phone connects while the update is still active.
    TEST_ASSERT_TRUE(session.route(3, 3, true) == PhonePacketRoute::AuthHandshake);
}

void test_ota_is_orphaned_when_connection_changes() {
    Session session;
    session.markAuthenticated(1);
    session.claimOta(1);
    TEST_ASSERT_FALSE(session.otaOrphaned(true, 1));
    TEST_ASSERT_TRUE(session.otaOrphaned(true, 2));
    TEST_ASSERT_FALSE(session.otaOrphaned(false, 2));
    session.releaseOta();
    TEST_ASSERT_FALSE(session.ownsOta(1));
}

void test_ota_stream_only_steered_by_owner() {
    Session session;
    session.claimOta(1);
    TEST_ASSERT_TRUE(session.mayHandleOta(OtaPacketKind::Data, true, 1));
    TEST_ASSERT_TRUE(session.mayHandleOta(OtaPacketKind::End, true, 1));
    TEST_ASSERT_FALSE(session.mayHandleOta(OtaPacketKind::Data, true, 3));
    TEST_ASSERT_FALSE(session.mayHandleOta(OtaPacketKind::End, true, 3));
    TEST_ASSERT_FALSE(session.mayHandleOta(OtaPacketKind::Abort, true, 3));
    // Starting fresh work is allowed for any authenticated connection.
    TEST_ASSERT_TRUE(session.mayHandleOta(OtaPacketKind::Begin, true, 3));
    TEST_ASSERT_TRUE(session.mayHandleOta(OtaPacketKind::EnterDfu, false, 3));
    // Without an active stream the handler reports "No OTA in progress".
    TEST_ASSERT_TRUE(session.mayHandleOta(OtaPacketKind::Data, false, 3));
}

void test_new_password_must_not_be_empty() {
    TEST_ASSERT_FALSE(blesession::isAcceptableNewPassword(nullptr));
    TEST_ASSERT_FALSE(blesession::isAcceptableNewPassword(""));
    TEST_ASSERT_TRUE(blesession::isAcceptableNewPassword("hunter22"));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_no_connection_is_unauthenticated);
    RUN_TEST(test_authentication_is_scoped_to_its_connection);
    RUN_TEST(test_missed_disconnect_reconnect_does_not_inherit_auth);
    RUN_TEST(test_packets_from_previous_connection_are_dropped);
    RUN_TEST(test_authenticated_routes);
    RUN_TEST(test_unauthenticated_connection_cannot_reach_ota_during_update);
    RUN_TEST(test_ota_is_orphaned_when_connection_changes);
    RUN_TEST(test_ota_stream_only_steered_by_owner);
    RUN_TEST(test_new_password_must_not_be_empty);
    return UNITY_END();
}
