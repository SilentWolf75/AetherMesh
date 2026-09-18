// Board definition for the Seeed SenseCAP Card Tracker T1000-E.
//
//   MCU     nRF52840, 64 MHz, 32.768 kHz crystal
//   LoRa    Semtech LR1110 on SPI, RF path switched by its own DIO5-DIO8
//   GNSS    Airoha AG3335 on UART1 at 115200
//
// Pin numbers use the nRF52840's flat GPIO numbering: port 0 pins are 0-31 and
// port 1 pins are 32-47. The names below are the ones the Arduino core and the
// AetherMesh firmware look up, so they cannot be renamed.
#ifndef AETHERMESH_VARIANT_T1000_E_H
#define AETHERMESH_VARIANT_T1000_E_H

#define VARIANT_MCK (64000000ul)
#define USE_LFXO

#include "WVariant.h"

#ifdef __cplusplus
extern "C" {
#endif

// GPIO numbering helpers: T1000E_P0(n) is P0.n, T1000E_P1(n) is P1.n.
#define T1000E_P0(n) (0 + (n))
#define T1000E_P1(n) (32 + (n))

#define PINS_COUNT (48)
#define NUM_DIGITAL_PINS (48)
#define NUM_ANALOG_INPUTS (6)
#define NUM_ANALOG_OUTPUTS (0)
#define NRF_APM

// --- Power rails -------------------------------------------------------------
#define PIN_3V3_EN T1000E_P1(6)            // main 3.3 V rail for peripherals
#define PIN_3V3_ACC_EN T1000E_P1(7)        // accelerometer rail
#define T1000X_SENSOR_EN_PIN T1000E_P0(4)  // on-board sensor supply

// --- User interface ----------------------------------------------------------
#define PIN_LED1 T1000E_P0(24)
#define LED_BUILTIN PIN_LED1
#define LED_POWER PIN_LED1
#define LED_STATE_ON 1
#define BUTTON_PIN T1000E_P0(6)            // reads high when pressed
#define BUZZER_EN_PIN T1000E_P1(5)
#define PIN_BUZZER T1000E_P0(25)

// --- Serial buses ------------------------------------------------------------
#define WIRE_INTERFACES_COUNT 1
#define PIN_WIRE_SDA T1000E_P0(26)
#define PIN_WIRE_SCL T1000E_P0(27)

#define PIN_SERIAL1_RX T1000E_P0(14)       // from the GNSS module
#define PIN_SERIAL1_TX T1000E_P0(13)       // to the GNSS module
#define PIN_SERIAL2_RX T1000E_P0(17)       // debug header
#define PIN_SERIAL2_TX T1000E_P0(16)

#define SPI_INTERFACES_COUNT 1
#define PIN_SPI_MISO T1000E_P1(8)
#define PIN_SPI_MOSI T1000E_P1(9)
#define PIN_SPI_SCK T1000E_P0(11)
#define PIN_SPI_NSS T1000E_P0(12)

// --- LR1110 radio ------------------------------------------------------------
#define LORA_CS PIN_SPI_NSS
#define LORA_SCK PIN_SPI_SCK
#define LORA_MISO PIN_SPI_MISO
#define LORA_MOSI PIN_SPI_MOSI
#define LORA_RESET T1000E_P1(10)
#define LORA_DIO1 T1000E_P1(1)             // interrupt
#define LORA_DIO2 T1000E_P0(7)             // BUSY on the LR1110

// --- AG3335 GNSS -------------------------------------------------------------
#define PIN_GPS_EN T1000E_P1(11)
#define GPS_EN_ACTIVE HIGH
#define PIN_GPS_RESET T1000E_P1(15)
#define GPS_RESET_MODE HIGH                // level that holds the module in reset
#define GPS_VRTC_EN T1000E_P0(8)           // keeps the backup domain powered
#define GPS_SLEEP_INT T1000E_P1(12)
#define GPS_RTC_INT T1000E_P0(15)
#define GPS_RESETB_OUT T1000E_P1(14)

// --- Battery and charging ----------------------------------------------------
#define BATTERY_PIN 2                      // AIN0 on P0.02, behind a 1:2 divider
#define ADC_MULTIPLIER (2.0F)
#define EXT_CHRG_DETECT T1000E_P1(3)
#define EXT_CHRG_DETECT_VALUE LOW          // low while charging
#define EXT_PWR_DETECT T1000E_P0(5)

#ifdef __cplusplus
}
#endif

#endif  // AETHERMESH_VARIANT_T1000_E_H
