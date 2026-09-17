/**
 * Golden-vector tests against the shipped PacketAuth.cpp (buildConfigCanonical +
 * deriveControlKey via setControlPassword/verifyConfig).
 *
 * Native builds inject OpenSSL only as the HMAC primitive (AETHERMESH_NATIVE_CRYPTO);
 * the PBKDF2 iteration/XOR structure and canonical byte layout are the real firmware code.
 */
#include <unity.h>
#include <string.h>
#include <stdint.h>
#include "PacketAuth.h"

static const char* PASSWORD = "admin-key";
static const char* V2_CANONICAL_HEX =
    "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000";
static const char* V2_TAG_HEX = "165a8fa5f809a08d3063ea46c78c64e4";
static const char* V3_CANONICAL_HEX =
    "414d4346473301000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000";
static const char* V3_TAG_HEX = "0cd1d291935a725a4ea210f3ac3dddbf";

static int hexNibble(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static size_t hexDecode(const char* hex, uint8_t* out, size_t capacity) {
    size_t n = 0;
    for (size_t i = 0; hex[i] && hex[i + 1]; i += 2) {
        int hi = hexNibble(hex[i]);
        int lo = hexNibble(hex[i + 1]);
        if (hi < 0 || lo < 0) break;
        if (n >= capacity) return 0;
        out[n++] = (uint8_t)((hi << 4) | lo);
    }
    return n;
}

static void bytesToHex(const uint8_t* data, size_t len, char* out) {
    static const char* digits = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[i * 2] = digits[(data[i] >> 4) & 0xF];
        out[i * 2 + 1] = digits[data[i] & 0xF];
    }
    out[len * 2] = 0;
}

/** Same Relay fixture as ControlAuthTest.kt / tools/test_control_auth_vectors.py. */
static void fillPublishedFixture(aethermesh_MeshPacket* packet) {
    *packet = aethermesh_MeshPacket_init_zero;
    packet->sender_id = 1;
    packet->recipient_id = 2;
    packet->session_id = 0x0102030405060708ULL;
    packet->auth_counter = 7;
    packet->which_payload = aethermesh_MeshPacket_config_tag;
    aethermesh_NodeConfig* cfg = &packet->payload.config;
    strncpy(cfg->node_name, "Relay", sizeof(cfg->node_name) - 1);
    cfg->lora_sf = 9;
    cfg->lora_bw = 125.0f;
    cfg->lora_tx_power = 22;
    cfg->region = 0;
    cfg->node_role = 1;
    cfg->telemetry_interval = 60;
    cfg->screen_timeout_secs = 30;
    cfg->power_save_mode = false;
    cfg->position_precision = 100;
    cfg->gps_mode = 0;
}

static void setAuthTagFromHex(aethermesh_MeshPacket* packet, const char* tagHex) {
    uint8_t tag[16];
    TEST_ASSERT_EQUAL(16, (int)hexDecode(tagHex, tag, sizeof(tag)));
    packet->auth_tag.size = 16;
    memcpy(packet->auth_tag.bytes, tag, 16);
}

void test_buildConfigCanonical_matches_published_v2_vector() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    static const uint8_t domainV2[] = {'A', 'M', 'C', 'F', 'G', '2'};
    uint8_t canonical[256];
    size_t length = packetauth::buildConfigCanonical(
        packet, canonical, sizeof(canonical), domainV2, sizeof(domainV2));
    TEST_ASSERT_EQUAL(101, (int)length);
    char hex[203];
    bytesToHex(canonical, length, hex);
    TEST_ASSERT_EQUAL_STRING(V2_CANONICAL_HEX, hex);
}

void test_buildConfigCanonical_matches_published_v3_vector() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    static const uint8_t domainV3[] = {'A', 'M', 'C', 'F', 'G', '3'};
    uint8_t canonical[256];
    size_t length = packetauth::buildConfigCanonical(
        packet, canonical, sizeof(canonical), domainV3, sizeof(domainV3));
    TEST_ASSERT_EQUAL(101, (int)length);
    char hex[203];
    bytesToHex(canonical, length, hex);
    TEST_ASSERT_EQUAL_STRING(V3_CANONICAL_HEX, hex);
}

void test_verifyConfig_v2_accepts_published_tag() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    packet.protocol_version = 2;
    setAuthTagFromHex(&packet, V2_TAG_HEX);
    packetauth::setRefuseLegacyControl(false);
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, PASSWORD));
}

void test_verifyConfig_v3_accepts_published_tag() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    packet.protocol_version = 3;
    setAuthTagFromHex(&packet, V3_TAG_HEX);
    packetauth::setRefuseLegacyControl(false);
    packetauth::setControlPassword(PASSWORD);
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
}

void test_verifyConfig_v3_rejects_wrong_password() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    packet.protocol_version = 3;
    setAuthTagFromHex(&packet, V3_TAG_HEX);
    packetauth::setControlPassword("not-admin-key");
    TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
}

void test_setControlPassword_skips_derivation_for_unchanged_password() {
    packetauth::setControlPassword(PASSWORD);
    const uint32_t afterFirst = packetauth::controlKeyDerivationCount();
    // saveSettings() passes the same password on every unrelated settings save.
    packetauth::setControlPassword(PASSWORD);
    packetauth::setControlPassword(PASSWORD);
    TEST_ASSERT_EQUAL_UINT32(afterFirst, packetauth::controlKeyDerivationCount());
}

void test_setControlPassword_rederives_after_password_change() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    packet.protocol_version = 3;
    setAuthTagFromHex(&packet, V3_TAG_HEX);

    packetauth::setControlPassword("not-admin-key");
    TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
    const uint32_t before = packetauth::controlKeyDerivationCount();

    packetauth::setControlPassword(PASSWORD);
    TEST_ASSERT_EQUAL_UINT32(before + 1, packetauth::controlKeyDerivationCount());
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));

    // Changing away must invalidate the cached key, not keep accepting it.
    packetauth::setControlPassword("not-admin-key");
    TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
}

void test_setControlPassword_clear_then_same_password_rederives() {
    aethermesh_MeshPacket packet;
    fillPublishedFixture(&packet);
    packet.protocol_version = 3;
    setAuthTagFromHex(&packet, V3_TAG_HEX);

    packetauth::setControlPassword(PASSWORD);
    packetauth::setControlPassword(nullptr);
    TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
    const uint32_t before = packetauth::controlKeyDerivationCount();
    packetauth::setControlPassword(PASSWORD);
    TEST_ASSERT_EQUAL_UINT32(before + 1, packetauth::controlKeyDerivationCount());
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
}

static void fillV3Fixture(aethermesh_MeshPacket* packet) {
    fillPublishedFixture(packet);
    packet->protocol_version = 3;
    setAuthTagFromHex(packet, V3_TAG_HEX);
}

void test_incremental_derivation_matches_published_vector() {
    aethermesh_MeshPacket packet;
    fillV3Fixture(&packet);

    packetauth::beginControlPassword(PASSWORD);
    uint32_t passes = 0;
    while (!packetauth::serviceControlKey(7777)) {
        TEST_ASSERT_TRUE(packetauth::controlKeyPending());
        // A half-derived key must never verify anything.
        TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
        passes++;
    }
    TEST_ASSERT_FALSE(packetauth::controlKeyPending());
    TEST_ASSERT_EQUAL_UINT32(15, passes); // 120000 iterations in 7777-sized slices
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
}

void test_begin_with_same_password_keeps_progress() {
    aethermesh_MeshPacket packet;
    fillV3Fixture(&packet);

    packetauth::beginControlPassword(PASSWORD);
    TEST_ASSERT_FALSE(packetauth::serviceControlKey(60000));
    // saveSettings() during boot derivation re-sends the same password.
    packetauth::beginControlPassword(PASSWORD);
    TEST_ASSERT_TRUE(packetauth::serviceControlKey(60000));
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
}

void test_password_change_mid_derivation_restarts_with_new_password() {
    aethermesh_MeshPacket packet;
    fillV3Fixture(&packet);
    const uint32_t before = packetauth::controlKeyDerivationCount();

    packetauth::beginControlPassword("not-admin-key");
    TEST_ASSERT_FALSE(packetauth::serviceControlKey(90000));
    packetauth::beginControlPassword(PASSWORD);
    // The abandoned derivation's progress must not carry over.
    TEST_ASSERT_FALSE(packetauth::serviceControlKey(30000));
    TEST_ASSERT_TRUE(packetauth::serviceControlKey(120000));
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
    TEST_ASSERT_EQUAL_UINT32(before + 1, packetauth::controlKeyDerivationCount());
}

void test_changing_password_invalidates_ready_key_immediately() {
    aethermesh_MeshPacket packet;
    fillV3Fixture(&packet);

    packetauth::setControlPassword(PASSWORD);
    TEST_ASSERT_TRUE(packetauth::verifyConfig(packet, nullptr));
    packetauth::beginControlPassword("not-admin-key");
    TEST_ASSERT_TRUE(packetauth::controlKeyPending());
    TEST_ASSERT_FALSE(packetauth::verifyConfig(packet, nullptr));
}

void test_clearing_password_cancels_pending_derivation() {
    packetauth::beginControlPassword(PASSWORD);
    TEST_ASSERT_FALSE(packetauth::serviceControlKey(10));
    packetauth::beginControlPassword(nullptr);
    TEST_ASSERT_FALSE(packetauth::controlKeyPending());
    TEST_ASSERT_TRUE(packetauth::serviceControlKey(1));
}

void setUp(void) {
    packetauth::setRefuseLegacyControl(false);
    packetauth::setControlPassword(nullptr);
}

void tearDown(void) {}

int main(int argc, char** argv) {
    UNITY_BEGIN();
    RUN_TEST(test_buildConfigCanonical_matches_published_v2_vector);
    RUN_TEST(test_buildConfigCanonical_matches_published_v3_vector);
    RUN_TEST(test_verifyConfig_v2_accepts_published_tag);
    RUN_TEST(test_verifyConfig_v3_accepts_published_tag);
    RUN_TEST(test_verifyConfig_v3_rejects_wrong_password);
    RUN_TEST(test_setControlPassword_skips_derivation_for_unchanged_password);
    RUN_TEST(test_setControlPassword_rederives_after_password_change);
    RUN_TEST(test_setControlPassword_clear_then_same_password_rederives);
    RUN_TEST(test_incremental_derivation_matches_published_vector);
    RUN_TEST(test_begin_with_same_password_keeps_progress);
    RUN_TEST(test_password_change_mid_derivation_restarts_with_new_password);
    RUN_TEST(test_changing_password_invalidates_ready_key_immediately);
    RUN_TEST(test_clearing_password_cancels_pending_derivation);
    return UNITY_END();
}
