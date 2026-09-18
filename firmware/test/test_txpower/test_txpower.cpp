#include <unity.h>
#include "TxPower.h"

using namespace txpower;

void setUp() {}
void tearDown() {}

void test_plain_boards_pass_power_straight_through() {
    TEST_ASSERT_EQUAL_INT(20, chipDbmFor(NO_AMPLIFIER, 20));
    TEST_ASSERT_EQUAL_INT(22, chipDbmFor(NO_AMPLIFIER, 30));
    TEST_ASSERT_EQUAL_INT(22, boardMaxDbm(NO_AMPLIFIER));
    TEST_ASSERT_EQUAL_INT(20, outputFor(NO_AMPLIFIER, 20));
}

void test_amplified_output_never_exceeds_the_request() {
    const Amplifier amps[] = {HELTEC_V4_AMP, RAK13302_AMP};
    for (const Amplifier& amp : amps) {
        for (int want = 0; want <= 30; want++) {
            int8_t chip = chipDbmFor(amp, want);
            TEST_ASSERT_TRUE(chip <= chipMaxFor(amp));
            if (want >= outputFor(amp, CHIP_MIN_DBM)) {
                TEST_ASSERT_TRUE(outputFor(amp, chip) <= want);
            }
        }
    }
}

void test_amplified_boards_reach_their_rated_power() {
    TEST_ASSERT_EQUAL_INT(28, boardMaxDbm(HELTEC_V4_AMP));
    TEST_ASSERT_EQUAL_INT(29, boardMaxDbm(RAK13302_AMP));
    // A mid request lands close below it, not far below.
    int out = outputFor(HELTEC_V4_AMP, chipDbmFor(HELTEC_V4_AMP, 22));
    TEST_ASSERT_TRUE(out >= 20 && out <= 22);
}

void test_region_caps_output() {
    TEST_ASSERT_EQUAL_INT(27, effectiveDbm(HELTEC_V4_AMP, 30, 1));   // EU868
    TEST_ASSERT_EQUAL_INT(28, effectiveDbm(HELTEC_V4_AMP, 30, 0));   // US915, board max
    TEST_ASSERT_EQUAL_INT(20, effectiveDbm(HELTEC_V4_AMP, 20, 1));
    TEST_ASSERT_EQUAL_INT(22, effectiveDbm(NO_AMPLIFIER, 30, 1));
}

void test_old_chip_settings_keep_their_real_output() {
    // Firmware before this change set the chip to 22 and got about 28-29 dBm.
    TEST_ASSERT_EQUAL_INT(28, migrateChipSetting(HELTEC_V4_AMP, 22));
    TEST_ASSERT_EQUAL_INT(29, migrateChipSetting(RAK13302_AMP, 22));
    TEST_ASSERT_EQUAL_INT(23, migrateChipSetting(HELTEC_V4_AMP, 10));
    TEST_ASSERT_EQUAL_INT(20, migrateChipSetting(NO_AMPLIFIER, 20));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_plain_boards_pass_power_straight_through);
    RUN_TEST(test_amplified_output_never_exceeds_the_request);
    RUN_TEST(test_amplified_boards_reach_their_rated_power);
    RUN_TEST(test_region_caps_output);
    RUN_TEST(test_old_chip_settings_keep_their_real_output);
    return UNITY_END();
}
