/*
 * SenseCAP Card Tracker T1000-E pin map (nRF52840 + LR1110 + AG3335).
 * Derived from the public Meshtastic / Seeed variant (Arduino LGPL header).
 */
#ifndef _VARIANT_SEEED_T1000_E_
#define _VARIANT_SEEED_T1000_E_

#define VARIANT_MCK (64000000ul)
#define USE_LFXO

#include "WVariant.h"

#ifdef __cplusplus
extern "C" {
#endif

#define PINS_COUNT (48)
#define NUM_DIGITAL_PINS (48)
#define NUM_ANALOG_INPUTS (6)
#define NUM_ANALOG_OUTPUTS (0)

#define NRF_APM

#define PIN_3V3_EN (32 + 6)       // P1.6
#define PIN_3V3_ACC_EN (32 + 7)   // P1.7

#define PIN_LED1 (0 + 24)         // P0.24
#define LED_BUILTIN PIN_LED1
#define LED_POWER PIN_LED1
#define LED_STATE_ON 1

#define BUTTON_PIN (0 + 6)        // P0.06 (active high, pulldown)

#define WIRE_INTERFACES_COUNT 1
#define PIN_WIRE_SDA (0 + 26)
#define PIN_WIRE_SCL (0 + 27)

#define PIN_SERIAL1_RX (0 + 14)   // GPS RX
#define PIN_SERIAL1_TX (0 + 13)   // GPS TX
#define PIN_SERIAL2_RX (0 + 17)   // debug UART
#define PIN_SERIAL2_TX (0 + 16)

#define SPI_INTERFACES_COUNT 1
#define PIN_SPI_MISO (32 + 8)     // P1.08
#define PIN_SPI_MOSI (32 + 9)     // P1.09
#define PIN_SPI_SCK (0 + 11)      // P0.11
#define PIN_SPI_NSS (0 + 12)      // P0.12

#define LORA_RESET (32 + 10)      // P1.10
#define LORA_DIO1 (32 + 1)        // P1.01 IRQ
#define LORA_DIO2 (0 + 7)         // P0.07 BUSY
#define LORA_CS PIN_SPI_NSS
#define LORA_SCK PIN_SPI_SCK
#define LORA_MISO PIN_SPI_MISO
#define LORA_MOSI PIN_SPI_MOSI

#define PIN_GPS_EN (32 + 11)      // P1.11
#define GPS_EN_ACTIVE HIGH
#define PIN_GPS_RESET (32 + 15)   // P1.15
#define GPS_RESET_MODE HIGH
#define GPS_VRTC_EN (0 + 8)
#define GPS_SLEEP_INT (32 + 12)
#define GPS_RTC_INT (0 + 15)
#define GPS_RESETB_OUT (32 + 14)

#define BATTERY_PIN 2             // P0.02 / AIN0
#define ADC_MULTIPLIER (2.0F)
#define EXT_CHRG_DETECT (32 + 3)  // P1.03
#define EXT_CHRG_DETECT_VALUE LOW
#define EXT_PWR_DETECT (0 + 5)    // P0.05

#define BUZZER_EN_PIN (32 + 5)    // P1.05
#define PIN_BUZZER (0 + 25)       // P0.25

#define T1000X_SENSOR_EN_PIN (0 + 4)

#ifdef __cplusplus
}
#endif

#endif
