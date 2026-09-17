#include <unity.h>
#include "../../src/OnDeviceSettings.h"

using namespace ondevice;

void test_region_toggles_us_eu() {
    TEST_ASSERT_EQUAL_UINT32(RegionEu868, nextRegion(RegionUs915));
    TEST_ASSERT_EQUAL_UINT32(RegionUs915, nextRegion(RegionEu868));
    TEST_ASSERT_EQUAL_STRING("US915", regionLabel(RegionUs915));
    TEST_ASSERT_EQUAL_STRING("EU868", regionLabel(RegionEu868));
    TEST_ASSERT_EQUAL_STRING("US915", regionLabel(99));
}

void test_gps_mode_cycles_on_duty_off() {
    TEST_ASSERT_EQUAL_UINT32(GpsDuty, nextGpsMode(GpsOn));
    TEST_ASSERT_EQUAL_UINT32(GpsOff, nextGpsMode(GpsDuty));
    TEST_ASSERT_EQUAL_UINT32(GpsOn, nextGpsMode(GpsOff));
    TEST_ASSERT_EQUAL_UINT32(GpsOn, nextGpsMode(99));
    TEST_ASSERT_EQUAL_STRING("ON", gpsModeLabel(GpsOn));
    TEST_ASSERT_EQUAL_STRING("DUTY", gpsModeLabel(GpsDuty));
    TEST_ASSERT_EQUAL_STRING("OFF", gpsModeLabel(GpsOff));
}

void test_gps_duty_steps_match_app_chips() {
    TEST_ASSERT_EQUAL_UINT32(300, nextGpsDutyIntervalSecs(0));
    TEST_ASSERT_EQUAL_UINT32(900, nextGpsDutyIntervalSecs(300));
    TEST_ASSERT_EQUAL_UINT32(1800, nextGpsDutyIntervalSecs(900));
    TEST_ASSERT_EQUAL_UINT32(3600, nextGpsDutyIntervalSecs(1800));
    TEST_ASSERT_EQUAL_UINT32(300, nextGpsDutyIntervalSecs(3600));
    TEST_ASSERT_EQUAL_STRING("5m", gpsDutyMinutesLabel(300));
    TEST_ASSERT_EQUAL_STRING("15m", gpsDutyMinutesLabel(900));
    TEST_ASSERT_EQUAL_STRING("30m", gpsDutyMinutesLabel(1800));
    TEST_ASSERT_EQUAL_STRING("60m", gpsDutyMinutesLabel(3600));
}

void test_unconfigured_button_is_region_wizard() {
    TEST_ASSERT_EQUAL_UINT8(ActToggleRegion, shortPressAction(false));
    TEST_ASSERT_EQUAL_UINT8(ActNone, shortPressAction(true));
    TEST_ASSERT_EQUAL_UINT8(ActConfirmRegion, longPressAction(false, PageHome, GpsOn));
    TEST_ASSERT_EQUAL_UINT8(ActConfirmRegion, longPressAction(false, PageGps, GpsOn));
}

void test_configured_long_press_is_gps_or_duty() {
    TEST_ASSERT_EQUAL_UINT8(ActNone, longPressAction(true, PageHome, GpsOn));
    TEST_ASSERT_EQUAL_UINT8(ActCycleGpsMode, longPressAction(true, PageGps, GpsOn));
    TEST_ASSERT_EQUAL_UINT8(ActCycleGpsMode, longPressAction(true, PageSystem, GpsOn));
    TEST_ASSERT_EQUAL_UINT8(ActCycleDuty, longPressAction(true, PageSystem, GpsDuty));
    TEST_ASSERT_EQUAL_UINT8(ActCycleGpsMode, longPressAction(true, PageGps, GpsDuty));
    TEST_ASSERT_EQUAL_UINT8(ActNone, longPressAction(true, PageMessages, GpsOn));
}

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;
    UNITY_BEGIN();
    RUN_TEST(test_region_toggles_us_eu);
    RUN_TEST(test_gps_mode_cycles_on_duty_off);
    RUN_TEST(test_gps_duty_steps_match_app_chips);
    RUN_TEST(test_unconfigured_button_is_region_wizard);
    RUN_TEST(test_configured_long_press_is_gps_or_duty);
    return UNITY_END();
}
