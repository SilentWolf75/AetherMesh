// Seeed SenseCAP T1000-E: Arduino pin table and power-up sequence.
#include "variant.h"

#include "nrf.h"
#include "wiring_constants.h"
#include "wiring_digital.h"

// Arduino pin N is nRF52840 GPIO N for all 48 pins (P0.0-P0.31, P1.0-P1.15),
// so the table is the identity map.
const uint32_t g_ADigitalPinMap[] = {
    0,  1,  2,  3,  4,  5,  6,  7,  8,  9,  10, 11, 12, 13, 14, 15,
    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
    32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
};

// Runs before setup(): switch on the rails the radio, sensors and GNSS need,
// and leave the GNSS module running rather than held in reset.
void initVariant() {
    // Peripheral, accelerometer, sensor and buzzer supplies.
    pinMode(PIN_3V3_EN, OUTPUT);
    digitalWrite(PIN_3V3_EN, HIGH);
    pinMode(PIN_3V3_ACC_EN, OUTPUT);
    digitalWrite(PIN_3V3_ACC_EN, HIGH);
    pinMode(T1000X_SENSOR_EN_PIN, OUTPUT);
    digitalWrite(T1000X_SENSOR_EN_PIN, HIGH);
    pinMode(BUZZER_EN_PIN, OUTPUT);
    digitalWrite(BUZZER_EN_PIN, HIGH);

    // GNSS: powered, backup domain on, out of reset, awake.
    pinMode(PIN_GPS_EN, OUTPUT);
    digitalWrite(PIN_GPS_EN, GPS_EN_ACTIVE);
    pinMode(GPS_VRTC_EN, OUTPUT);
    digitalWrite(GPS_VRTC_EN, HIGH);
    pinMode(PIN_GPS_RESET, OUTPUT);
    digitalWrite(PIN_GPS_RESET, !GPS_RESET_MODE);
    pinMode(GPS_SLEEP_INT, OUTPUT);
    digitalWrite(GPS_SLEEP_INT, HIGH);
    pinMode(GPS_RTC_INT, OUTPUT);
    digitalWrite(GPS_RTC_INT, LOW);
    pinMode(GPS_RESETB_OUT, INPUT_PULLUP);

    // Status LED starts off.
    pinMode(PIN_LED1, OUTPUT);
    digitalWrite(PIN_LED1, !LED_STATE_ON);
}
