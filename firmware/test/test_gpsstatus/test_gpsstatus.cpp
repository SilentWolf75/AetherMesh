/**
 * GNSS telemetry classification (GpsStatus.h). The field case behind this: a
 * node whose module never saw a satellite was shown as "LOCKED" because the app
 * only had coordinates, which came from the phone.
 */
#include <unity.h>

#include "../../src/GpsStatus.h"

using namespace gpsstatus;

static Inputs searching() {
    Inputs in = {};
    in.moduleDetected = true;
    in.gpsMode = 0;
    in.powered = true;
    return in;
}

void setUp(void) {}
void tearDown(void) {}

void test_powered_module_without_fix_is_searching() {
    Inputs in = searching();
    in.satellitesInView = 0;
    TEST_ASSERT_EQUAL_UINT32(StateSearching, state(in));
    TEST_ASSERT_EQUAL_UINT32(SourceNone, source(in));
}

void test_recent_fix_is_live() {
    Inputs in = searching();
    in.locationValid = true;
    in.locationAgeMs = 900;
    in.satellitesUsed = 7;
    TEST_ASSERT_EQUAL_UINT32(StateFix, state(in));
    TEST_ASSERT_EQUAL_UINT32(SourceGps, source(in));
    TEST_ASSERT_EQUAL_UINT32(0, fixAgeSecs(in));
}

void test_sticky_old_fix_is_not_a_lock() {
    // TinyGPS++ keeps location.isValid() true after the fix is lost.
    Inputs in = searching();
    in.locationValid = true;
    in.locationAgeMs = LIVE_FIX_MAX_AGE_MS + 1;
    TEST_ASSERT_EQUAL_UINT32(StateSearching, state(in));
    TEST_ASSERT_EQUAL_UINT32(SourceGps, source(in)); // position is still the old GPS one
    TEST_ASSERT_EQUAL_UINT32(10, fixAgeSecs(in));
}

void test_phone_position_is_labelled_as_phone() {
    Inputs in = searching();
    in.phonePositionFresh = true;
    TEST_ASSERT_EQUAL_UINT32(StateSearching, state(in));
    TEST_ASSERT_EQUAL_UINT32(SourcePhone, source(in));
}

void test_fixed_position_wins() {
    Inputs in = searching();
    in.fixedPosition = true;
    in.locationValid = true;
    in.phonePositionFresh = true;
    TEST_ASSERT_EQUAL_UINT32(SourceFixed, source(in));
}

void test_module_and_power_states() {
    Inputs in = searching();
    in.moduleDetected = false;
    TEST_ASSERT_EQUAL_UINT32(StateAbsent, state(in));

    in = searching();
    in.gpsMode = 1;
    TEST_ASSERT_EQUAL_UINT32(StateOff, state(in));

    in = searching();
    in.gpsMode = 2;
    in.powered = false;
    TEST_ASSERT_EQUAL_UINT32(StateSleeping, state(in));

    in = searching();
    in.powered = false; // always-on but held off (low-voltage safe mode)
    TEST_ASSERT_EQUAL_UINT32(StateOff, state(in));
}

void test_hdop_scaling() {
    Inputs in = searching();
    TEST_ASSERT_EQUAL_UINT32(0, hdopX10(in));
    in.hdopValid = true;
    in.hdop = 1.74;
    TEST_ASSERT_EQUAL_UINT32(17, hdopX10(in));
    in.hdop = 99.99;
    TEST_ASSERT_EQUAL_UINT32(1000, hdopX10(in));
    in.hdop = 5000.0;
    TEST_ASSERT_EQUAL_UINT32(9999, hdopX10(in));
}

void test_satellites_in_view_ignores_stale_constellations() {
    const uint32_t counts[] = {9, 6, 0, 4};
    const uint32_t ages[] = {800, 1200, 100, 60000}; // BeiDou stopped reporting
    TEST_ASSERT_EQUAL_UINT32(15, satellitesInView(counts, ages, 4, 5000));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_powered_module_without_fix_is_searching);
    RUN_TEST(test_recent_fix_is_live);
    RUN_TEST(test_sticky_old_fix_is_not_a_lock);
    RUN_TEST(test_phone_position_is_labelled_as_phone);
    RUN_TEST(test_fixed_position_wins);
    RUN_TEST(test_module_and_power_states);
    RUN_TEST(test_hdop_scaling);
    RUN_TEST(test_satellites_in_view_ignores_stale_constellations);
    return UNITY_END();
}
