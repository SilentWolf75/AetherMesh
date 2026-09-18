#include <unity.h>
#include "AirtimeWindow.h"

void setUp() {}
void tearDown() {}

using HourWindow = AirtimeWindow<60, 60000>;
using MinuteWindow = AirtimeWindow<6, 10000>;

void test_counts_airtime_inside_the_window() {
    HourWindow w;
    w.add(1000, 2000);
    w.add(120000, 3000);
    TEST_ASSERT_EQUAL_UINT32(5000, w.totalMs(130000));
}

void test_old_airtime_falls_out_of_the_window() {
    MinuteWindow w;
    w.add(0, 6000);
    TEST_ASSERT_EQUAL_UINT8(10, w.percent(5000));
    TEST_ASSERT_EQUAL_UINT32(6000, w.totalMs(59000));
    TEST_ASSERT_EQUAL_UINT32(0, w.totalMs(61000));
}

void test_long_silence_clears_everything() {
    MinuteWindow w;
    w.add(0, 5000);
    TEST_ASSERT_EQUAL_UINT32(0, w.totalMs(10UL * 60UL * 1000UL));
}

void test_duty_cycle_limit() {
    HourWindow w;  // 10% of an hour is 360 s
    w.add(0, 350000);
    TEST_ASSERT_TRUE(w.fits(1000, 10000, 10));
    TEST_ASSERT_FALSE(w.fits(1000, 10001, 10));
    TEST_ASSERT_TRUE(w.fits(1000, 999999, 100));   // no limit
    // An hour later the budget is back.
    TEST_ASSERT_TRUE(w.fits(3601000UL, 300000, 10));
}

void test_survives_millis_wraparound() {
    MinuteWindow w;
    uint32_t nearWrap = 0xFFFFFFFFu - 15000u;
    w.add(nearWrap, 4000);
    TEST_ASSERT_EQUAL_UINT32(4000, w.totalMs(nearWrap + 20000u));  // wrapped
    TEST_ASSERT_EQUAL_UINT32(0, w.totalMs(nearWrap + 80000u));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_counts_airtime_inside_the_window);
    RUN_TEST(test_old_airtime_falls_out_of_the_window);
    RUN_TEST(test_long_silence_clears_everything);
    RUN_TEST(test_duty_cycle_limit);
    RUN_TEST(test_survives_millis_wraparound);
    return UNITY_END();
}
