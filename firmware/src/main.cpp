#include <Arduino.h>
#include "RadioManager.h"
#include "MeshRouter.h"
#include "MeshMath.h"
#include "BLEManager.h"
#include "Version.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include <TinyGPS++.h>

#if defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
#include <bluefruit.h>
#include <nrf_nvic.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
using namespace Adafruit_LittleFS_Namespace;

struct NodeSettings {
    uint32_t version;
    char name[17];
    uint32_t sf;
    float bw;
    int32_t txPower;
    uint32_t region;
    char password[33];
    uint32_t role;
    uint32_t telemetryInterval;
    uint32_t screenTimeoutSecs;
    bool powerSaveMode;
    uint32_t positionPrecisionM;
    uint32_t gpsMode;
    bool fixedPosition;
    float fixedLat;
    float fixedLon;
    int32_t fixedAlt;
};
#endif

#ifdef ESP32
#include <Preferences.h>
#include <Update.h>
Preferences preferences;
#endif

#if defined(HELTEC_V4) || defined(HELTEC_V3)
#include <U8g2lib.h>
// SSD1306 128x64 display connection
U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ 21, /* clock=*/ 18, /* data=*/ 17);
#elif defined(LILYGO_T_DECK)
#include <U8g2lib.h>
#include <SPI.h>

static uint16_t tdeckFrame[320UL * 240UL];
static uint8_t tdeckTxLine[320 * 2];

// Direct, lightweight ST7789 driver over shared Hardware SPI to enable physical screen output on LILYGO T-Deck
inline void st7789_write_cmd(uint8_t cmd) {
    digitalWrite(11, LOW); // DC LOW
    digitalWrite(12, LOW); // CS LOW
    SPI.beginTransaction(SPISettings(20000000, MSBFIRST, SPI_MODE0));
    SPI.transfer(cmd);
    SPI.endTransaction();
    digitalWrite(12, HIGH); // CS HIGH
}

inline void st7789_write_data(uint8_t val) {
    digitalWrite(11, HIGH); // DC HIGH
    digitalWrite(12, LOW); // CS LOW
    SPI.beginTransaction(SPISettings(20000000, MSBFIRST, SPI_MODE0));
    SPI.transfer(val);
    SPI.endTransaction();
    digitalWrite(12, HIGH); // CS HIGH
}

inline void st7789_set_window(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    st7789_write_cmd(0x2A); // Column Address Set
    digitalWrite(11, HIGH); // DC HIGH
    digitalWrite(12, LOW); // CS LOW
    SPI.beginTransaction(SPISettings(20000000, MSBFIRST, SPI_MODE0));
    SPI.transfer(x0 >> 8);
    SPI.transfer(x0 & 0xFF);
    SPI.transfer(x1 >> 8);
    SPI.transfer(x1 & 0xFF);
    SPI.endTransaction();
    digitalWrite(12, HIGH); // CS HIGH
    
    st7789_write_cmd(0x2B); // Row Address Set
    digitalWrite(11, HIGH); // DC HIGH
    digitalWrite(12, LOW); // CS LOW
    SPI.beginTransaction(SPISettings(20000000, MSBFIRST, SPI_MODE0));
    SPI.transfer(y0 >> 8);
    SPI.transfer(y0 & 0xFF);
    SPI.transfer(y1 >> 8);
    SPI.transfer(y1 & 0xFF);
    SPI.endTransaction();
    digitalWrite(12, HIGH); // CS HIGH
    
    st7789_write_cmd(0x2C); // Memory Write
}

inline void st7789_clear(uint16_t color) {
    for (uint32_t i = 0; i < 320UL * 240UL; i++) {
        tdeckFrame[i] = color;
    }
}

inline void st7789_fill_rect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
    if (w <= 0 || h <= 0 || x >= 320 || y >= 240 || x + w <= 0 || y + h <= 0) {
        return;
    }
    if (x < 0) { w += x; x = 0; }
    if (y < 0) { h += y; y = 0; }
    if (x + w > 320) w = 320 - x;
    if (y + h > 240) h = 240 - y;
    if (w <= 0 || h <= 0) return;

    for (int16_t row = 0; row < h; row++) {
        uint16_t* dst = &tdeckFrame[(uint32_t)(y + row) * 320UL + x];
        for (int16_t col = 0; col < w; col++) {
            dst[col] = color;
        }
    }
}

inline void st7789_push_frame() {
    st7789_set_window(0, 0, 319, 239);
    digitalWrite(11, HIGH);
    digitalWrite(12, LOW);
    SPI.beginTransaction(SPISettings(40000000, MSBFIRST, SPI_MODE0));
    for (uint16_t y = 0; y < 240; y++) {
        const uint16_t* src = &tdeckFrame[(uint32_t)y * 320UL];
        for (uint16_t x = 0; x < 320; x++) {
            uint16_t color = src[x];
            tdeckTxLine[x * 2] = (uint8_t)(color >> 8);
            tdeckTxLine[x * 2 + 1] = (uint8_t)(color & 0xFF);
        }
        SPI.transferBytes(tdeckTxLine, nullptr, sizeof(tdeckTxLine));
    }
    SPI.endTransaction();
    digitalWrite(12, HIGH);
}

inline void st7789_draw_rect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
    st7789_fill_rect(x, y, w, 1, color);
    st7789_fill_rect(x, y + h - 1, w, 1, color);
    st7789_fill_rect(x, y, 1, h, color);
    st7789_fill_rect(x + w - 1, y, 1, h, color);
}

inline void st7789_draw_line(int16_t x0, int16_t y0, int16_t x1, int16_t y1, uint16_t color) {
    int16_t dx = abs(x1 - x0);
    int16_t sx = x0 < x1 ? 1 : -1;
    int16_t dy = -abs(y1 - y0);
    int16_t sy = y0 < y1 ? 1 : -1;
    int16_t err = dx + dy;
    while (true) {
        st7789_fill_rect(x0, y0, 2, 2, color);
        if (x0 == x1 && y0 == y1) break;
        int16_t e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
    }
}

inline void st7789_fill_circle(int16_t cx, int16_t cy, int16_t r, uint16_t color) {
    for (int16_t y = -r; y <= r; y++) {
        int16_t x = 0;
        while (x * x + y * y <= r * r) x++;
        x--;
        st7789_fill_rect(cx - x, cy + y, x * 2 + 1, 1, color);
    }
}

inline void st7789_draw_circle(int16_t cx, int16_t cy, int16_t r, uint16_t color) {
    int16_t x = -r;
    int16_t y = 0;
    int16_t err = 2 - 2 * r;
    do {
        st7789_fill_rect(cx - x, cy + y, 1, 1, color);
        st7789_fill_rect(cx - y, cy - x, 1, 1, color);
        st7789_fill_rect(cx + x, cy - y, 1, 1, color);
        st7789_fill_rect(cx + y, cy + x, 1, 1, color);
        int16_t e2 = err;
        if (e2 <= y) {
            y++;
            err += y * 2 + 1;
        }
        if (e2 > x || err > y) {
            x++;
            err += x * 2 + 1;
        }
    } while (x < 0);
}

inline void init_st7789() {
    pinMode(12, OUTPUT); // CS
    pinMode(11, OUTPUT); // DC
    pinMode(6, OUTPUT);  // RST
    
    // Ensure SPI is initialized on pins 40 (SCK), 38 (MISO), 41 (MOSI), 9 (SS/LoRa CS)
    SPI.begin(40, 38, 41, 9);
    
    // Hardware Reset
    digitalWrite(6, HIGH);
    delay(50);
    digitalWrite(6, LOW);
    delay(50);
    digitalWrite(6, HIGH);
    delay(150);
    
    st7789_write_cmd(0x01); // Software Reset
    delay(150);
    st7789_write_cmd(0x11); // Sleep Out
    delay(250);
    
    st7789_write_cmd(0x3A); // Interface Pixel Format
    st7789_write_data(0x55); // 16-bit/pixel color
    
    st7789_write_cmd(0x36); // Memory Data Access Control (MADCTL)
    st7789_write_data(0x60); // Landscape orientation
    
    st7789_write_cmd(0x21); // Display Inversion On
    
    st7789_write_cmd(0x13); // Normal Display Mode On
    
    st7789_write_cmd(0x29); // Display On
    delay(100);
    
    st7789_clear(0x0000); // Clear screen to black
    st7789_push_frame();
}

class MyU8G2 : public U8G2_SSD1306_128X64_NONAME_F_4W_SW_SPI {
public:
    using U8G2_SSD1306_128X64_NONAME_F_4W_SW_SPI::U8G2_SSD1306_128X64_NONAME_F_4W_SW_SPI;
    void sendBuffer();
};

extern MyU8G2 u8g2;

inline void st7789_refresh() {
    st7789_clear(0x0841);
    st7789_set_window(32, 56, 287, 183);
    
    digitalWrite(11, HIGH); // DC HIGH
    digitalWrite(12, LOW);  // CS LOW
    SPI.beginTransaction(SPISettings(20000000, MSBFIRST, SPI_MODE0));
    
    uint8_t* buf = u8g2.getBufferPtr();
    
    for (uint8_t y = 0; y < 64; y++) {
        for (uint8_t rep = 0; rep < 2; rep++) {
            uint8_t page = y / 8;
            uint8_t bit = y % 8;
            uint8_t mask = (1 << bit);
            
            for (uint8_t x = 0; x < 128; x++) {
                uint16_t idx = page * 128 + x;
                bool pixel = (buf[idx] & mask) != 0;
                
                // Color: Active is bright neon green (0x07E0), inactive is black (0x0000)
                uint16_t color = pixel ? 0x07E0 : 0x0000;
                uint8_t high = color >> 8;
                uint8_t low = color & 0xFF;
                
                // 2x scale horizontally
                SPI.transfer(high);
                SPI.transfer(low);
                SPI.transfer(high);
                SPI.transfer(low);
            }
        }
    }
    SPI.endTransaction();
    digitalWrite(12, HIGH); // CS HIGH
}

inline void MyU8G2::sendBuffer() {
    st7789_refresh();
}

MyU8G2 u8g2(U8G2_R0, /* clock=*/ 40, /* data=*/ 41, /* cs=*/ 12, /* dc=*/ 11, /* reset=*/ 6);
#endif

#if defined(LILYGO_T_DECK)
#define setBacklight(on) do { \
    digitalWrite(42, (on) ? HIGH : LOW); \
    digitalWrite(4, (on) ? HIGH : LOW); \
} while(0)
#else
#define setBacklight(on) ((void)0)
#endif

#include <Wire.h>

// Instances
RadioManager radioMgr;
MeshRouter router(&radioMgr);
BLEManager bleMgr;
TinyGPSPlus gps;

// Unique Node Identifier
uint32_t localNodeId = 0;
uint32_t rxPacketCount = 0;
uint32_t txPacketCount = 0;
uint32_t rawBeaconCount = 0;
bool batteryCharging = false;
float batteryVoltage = 0.0f; // last measured pack voltage (0 if never measured)

// Recently-heard mesh peers, for the OLED "Nodes" count and NODES page. Small
// fixed table; a peer counts as active if heard within the last 5 minutes.
struct PeerSeen { uint32_t id; uint32_t lastMs; float rssi; };
static PeerSeen peersSeen[12] = {};
static const uint32_t PEER_ACTIVE_WINDOW_MS = 5UL * 60UL * 1000UL;

void notePeerHeard(uint32_t id, float rssi) {
    if (id == 0 || id == localNodeId) return;
    int freeSlot = -1;
    uint32_t oldest = 0xFFFFFFFF;
    int oldestIdx = 0;
    for (int i = 0; i < 12; i++) {
        if (peersSeen[i].id == id) {
            peersSeen[i].lastMs = millis();
            peersSeen[i].rssi = rssi;
            return;
        }
        if (peersSeen[i].id == 0 && freeSlot < 0) freeSlot = i;
        if (peersSeen[i].lastMs < oldest) { oldest = peersSeen[i].lastMs; oldestIdx = i; }
    }
    int slot = (freeSlot >= 0) ? freeSlot : oldestIdx;
    peersSeen[slot].id = id;
    peersSeen[slot].lastMs = millis();
    peersSeen[slot].rssi = rssi;
}

uint8_t countActivePeers() {
    uint8_t n = 0;
    for (int i = 0; i < 12; i++) {
        if (peersSeen[i].id != 0 && (millis() - peersSeen[i].lastMs) < PEER_ACTIVE_WINDOW_MS) n++;
    }
    return n;
}

// Connected node: range-test PINGs are sent once. Retransmitting from the phone
// node collides with PONG replies on the same half-duplex radio; the target
// already retries PONG and re-queues on duplicate PING.
// Hardware external-power detection where the platform provides it.
// On nRF52840 (RAK), VBUSDETECT reads the 5V input rail, which the WisBlock
// base feeds from both USB and the solar connector - instant and definite.
// The ESP32-S3 (Heltec) has no equivalent without extra wiring, so it relies
// on the voltage heuristic below.
bool externalPowerPresent() {
#if defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    uint8_t sd_enabled = 0;
    if (sd_softdevice_is_enabled(&sd_enabled) == 0 && sd_enabled) {
        uint32_t usb_reg = 0;
        sd_power_usbregstatus_get(&usb_reg);
        return (usb_reg & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
    } else {
        return (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
    }
#else
    return false;
#endif
}

// Voltage-based charge detection (no dedicated charger pin required). A LiPo
// only rises while charge current flows; under load it stays flat or falls. So
// we compare the latest (noise-averaged) reading against the minimum of the last
// few minutes: a meaningful rise means charging. Limits: it catches the ONSET of
// charging, not steady-state - a node rebooting mid-charge sees a flat (already
// elevated) voltage and won't trigger until the next visible rise. Where the
// hardware can report external power directly (see externalPowerPresent), that
// signal wins.
void updateChargingState(float voltage) {
    if (voltage < 2.5f) {
        return; // Ignore invalid/stabilizing readings on boot
    }
    batteryVoltage = voltage;

    // VBUSDETECT (nRF52) catches USB power instantly and definitively. It does
    // NOT see the solar connector - field-tested 2026-07-07: the RAK19007's
    // solar input feeds only the battery charger, not the VBUS rail the chip
    // senses - so when VBUS is absent we still fall through to the voltage
    // heuristics below to catch solar charging.
    if (externalPowerPresent()) {
        batteryCharging = true;
        return;
    }

    // Plug/unplug step detection (instant): connecting a charger steps the
    // terminal voltage UP by ~60-200mV within one sample; disconnecting steps
    // it DOWN the same way. Each sample is already a 32-read average taken
    // ~30s apart, so a 60mV jump between consecutive samples is a strong
    // charger signature - detected immediately, long before the windowed rise
    // detectors below can confirm.
    static float prevRawVoltage = -1.0f;
    float rawStep = (prevRawVoltage > 0.0f) ? (voltage - prevRawVoltage) : 0.0f;
    prevRawVoltage = voltage;
    bool plugStep = rawStep > 0.060f;
    bool unplugStep = rawStep < -0.060f;

    // Apply an Exponential Moving Average (EMA) filter to smooth out ADC noise and load transients
    static float filteredVoltage = -1.0f;
    static const int WIN = 8;          // 8 samples x ~30s = ~4 min window
    static float samples[WIN];
    static int idx = 0;
    static uint32_t chargingHoldUntil = 0;

    if (filteredVoltage < 0.0f) {
        filteredVoltage = voltage;
        // Pre-fill the history array with the boot voltage to prevent false rise detection
        for (int i = 0; i < WIN; i++) {
            samples[i] = voltage;
        }
    } else {
        filteredVoltage = filteredVoltage * 0.85f + voltage * 0.15f;
    }

    samples[idx] = filteredVoltage;
    idx = (idx + 1) % WIN;

    float minV = filteredVoltage;
    for (int i = 0; i < WIN; i++) {
        if (samples[i] < minV) minV = samples[i];
    }

    // Rising above the recent floor => charge current present. The absolute
    // 4.15V catch handles the near-full charging plateau where the rise stalls.
    // 80mV threshold on ALL boards: the old nRF52-specific 8mV threshold was
    // below post-TX voltage rebound and caused phantom "charging" forever.
    const float riseThreshold = 0.080f;

    bool rising = (filteredVoltage - minV) > riseThreshold;
    bool high = filteredVoltage >= 4.15f;

    // Slow-charge detection: a trickle charge on a big pack rises only
    // ~50-100mV per HOUR - invisible to the 80mV/4min quick trigger above
    // (which is why a genuinely charging Heltec never showed the bolt).
    // Track a long window (16 samples x 2 min = 32 min) of the filtered
    // voltage and flag charging while the level climbs across it. Post-TX
    // load rebound can't trip this: the window minimum predates the sag.
    static float slowRing[16];
    static int slowIdx = 0;
    static int slowCount = 0;
    static uint32_t lastSlowSampleMs = 0;
    if (millis() - lastSlowSampleMs >= 120000UL) {
        lastSlowSampleMs = millis();
        slowRing[slowIdx] = filteredVoltage;
        slowIdx = (slowIdx + 1) % 16;
        if (slowCount < 16) slowCount++;
    }
    bool slowRising = false;
    if (slowCount >= 8) { // engage once we have >=16 min of history
        float slowMin = filteredVoltage;
        for (int i = 0; i < slowCount; i++) {
            if (slowRing[i] < slowMin) slowMin = slowRing[i];
        }
        slowRising = (filteredVoltage - slowMin) > 0.045f;
    }

    if (unplugStep) {
        // Sharp drop = charger removed; clear immediately instead of letting
        // the hold linger for minutes after unplugging
        batteryCharging = false;
        chargingHoldUntil = 0;
    } else if (rising || high || slowRising || plugStep) {
        batteryCharging = true;
        chargingHoldUntil = millis() + 180000; // hold 3 min after last trigger
    } else if ((int32_t)(millis() - chargingHoldUntil) >= 0) {
        batteryCharging = false;
    }

    Serial.printf("Charge detect: V=%.3f floor=%.3f rise=%d high=%d slow=%d step=%.0fmV -> charging=%d\n",
                  filteredVoltage, minV, rising, high, slowRising, rawStep * 1000.0f, batteryCharging);
}

// Node Settings and NVS Configuration
char nodeCustomName[17] = ""; // Max 16 chars + null terminator
char nodePassword[33] = "";   // Max 32 chars + null terminator
bool isBleClientAuthenticated = false;

// Brute-force protection: 5 wrong passwords -> 30s lockout
uint8_t failedAuthAttempts = 0;
uint32_t authLockoutUntil = 0;

uint32_t loraSF = 9;
float loraBW = 125.0f;
int32_t loraTxPower = 22; // default for Heltec V4
uint32_t nodeRegion = 0; // 0 = US915, 1 = EU868
uint32_t nodeRole = 0;   // 0 = Client, 1 = Router, 2 = Low-Power Repeater
uint32_t telemetryIntervalSec = 60; // default telemetry broadcast interval in seconds
uint32_t screenTimeoutSecs = 30; // Screen timeout in seconds (0 = display off, 0xFFFFFFFF = display always on)
uint32_t lastDisplayActivityTime = 0;
bool powerSaveMode = false;
// Privacy blur radius (m) applied to broadcast positions; 0 = precise.
// Persisted by saveSettings() directly from this global (not a parameter) so a
// call site can't accidentally reset it by omitting a trailing argument.
uint32_t positionPrecisionM = 0;
// GPS power mode: 0 = onboard GNSS powered (default), 1 = off to save power
// (~25% of total draw; position falls back to phone-shared GPS). Persisted
// from the global by saveSettings(), same rationale as positionPrecisionM.
uint32_t gpsMode = 0;
float inheritedLat = 0.0f;
float inheritedLon = 0.0f;
bool hasInheritedLocation = false;
uint32_t lastInheritedTime = 0;
bool hasOnboardGps = true;
uint32_t totalGpsBytesReceived = 0;
bool gpsChecked = false;
static constexpr uint32_t GPS_DETECT_TIMEOUT_MS = 15000;
bool fixedPosition = false;
float fixedLat = 0.0f;
float fixedLon = 0.0f;
int32_t fixedAlt = 0;
static const uint32_t SETTINGS_VERSION = 9;

// OLED message popup state
char lastMsgText[40] = "";
uint32_t lastMsgSender = 0;
uint32_t lastMsgReceivedTime = 0;
bool hasNewMsgPopup = false;

// OLED page carousel: the user button cycles pages while the screen is on
// (waking from off always lands on HOME; a visible popup is dismissed first).
uint8_t oledPage = 0;
const uint8_t OLED_PAGE_COUNT = 5; // HOME, GPS, MESSAGES, NODES, SYSTEM
bool displayIsOn = false;

// Ring of recently received chat messages for the MESSAGES screen page
struct RecentMsg { uint32_t sender; char text[40]; uint32_t atMs; };
RecentMsg recentMsgs[4] = {};
uint8_t recentMsgHead = 0;
uint8_t recentMsgCount = 0;

#if defined(LILYGO_T_DECK)
static const uint8_t TDECK_KEYBOARD_ADDR = 0x55;
static const uint8_t TDECK_KEYBOARD_SDA = 18;
static const uint8_t TDECK_KEYBOARD_SCL = 8;
static const uint8_t TDECK_KEYBOARD_INT = 46;
static const uint8_t TDECK_KB_BRIGHTNESS_CMD = 0x01;
static const uint8_t TDECK_KB_MODE_KEY_CMD = 0x04;
static const uint8_t TDECK_COMPOSE_MAX = 120;
static bool tdeckKeyboardPresent = false;
static bool tdeckComposeActive = false;
static char tdeckComposeText[TDECK_COMPOSE_MAX + 1] = "";
static uint8_t tdeckComposeLen = 0;
static char tdeckKeyboardStatus[32] = "KB CHECK";
static char tdeckKeyboardNotice[36] = "TYPE TO CHAT";
static uint32_t tdeckKeyboardNoticeUntil = 0;
static bool tdeckDisplayDirty = true;
static bool tdeckComposeFrameDrawn = false;
static uint8_t tdeckLastRenderMode = 0xFF;
static uint32_t tdeckLastRenderSig = 0;
#endif

void saveSettings(const char* name, uint32_t sf, float bw, int32_t txPower, uint32_t region, const char* password, uint32_t role, uint32_t telemetryInterval = 60, uint32_t screenTimeout = 30, bool powerSave = false);

void loadSettings() {
#ifdef ESP32
    preferences.begin("aethermesh", true); // Read-only mode
    String name = preferences.getString("node_name", "");
    strncpy(nodeCustomName, name.c_str(), sizeof(nodeCustomName) - 1);
    nodeCustomName[sizeof(nodeCustomName) - 1] = '\0';
    
    String pass = preferences.getString("node_pass", "");
    strncpy(nodePassword, pass.c_str(), sizeof(nodePassword) - 1);
    nodePassword[sizeof(nodePassword) - 1] = '\0';
    
    uint32_t storedVersion = preferences.getUInt("settings_ver", 0);

    if (storedVersion == SETTINGS_VERSION) {
        loraSF = preferences.getUInt("lora_sf", 9);
        loraBW = preferences.getFloat("lora_bw", 125.0f);
        loraTxPower = preferences.getInt("lora_tx_power", 22);
        nodeRegion = preferences.getUInt("region", 0);
        nodeRole = preferences.getUInt("node_role", 0);
        telemetryIntervalSec = preferences.getUInt("tel_interval", 60);
        screenTimeoutSecs = preferences.getUInt("scr_timeout", 30);
        powerSaveMode = preferences.getBool("power_save", false);
        positionPrecisionM = preferences.getUInt("pos_prec", 0);
        gpsMode = preferences.getUInt("gps_mode", 0);
        fixedPosition = preferences.getBool("fixed_pos", false);
        fixedLat = preferences.getFloat("fixed_lat", 0.0f);
        fixedLon = preferences.getFloat("fixed_lon", 0.0f);
        fixedAlt = preferences.getInt("fixed_alt", 0);
    } else {
        loraSF = 9;
        loraBW = 125.0f;
        loraTxPower = 22;
        nodeRegion = 0;
        nodeRole = 0;
        telemetryIntervalSec = 60;
        screenTimeoutSecs = 30;
        powerSaveMode = false;
        positionPrecisionM = 0;
        gpsMode = 0;
        fixedPosition = false;
        fixedLat = 0.0f;
        fixedLon = 0.0f;
        fixedAlt = 0;
        nodePassword[0] = '\0';
        Serial.println("Radio settings version changed; resetting settings to defaults.");
    }
    preferences.end();

    if (storedVersion != SETTINGS_VERSION) {
        saveSettings(nodeCustomName, loraSF, loraBW, loraTxPower, nodeRegion, nodePassword, nodeRole, telemetryIntervalSec, screenTimeoutSecs, powerSaveMode);
    }
    
    Serial.println("Loaded settings from NVS:");
    Serial.printf("  Name: %s\n", nodeCustomName);
    Serial.printf("  SF: %u\n", loraSF);
    Serial.printf("  BW: %.1f\n", loraBW);
    Serial.printf("  TX Power: %d\n", loraTxPower);
    Serial.printf("  Region: %u (%s)\n", nodeRegion, (nodeRegion == 0) ? "US915" : "EU868");
    Serial.printf("  Password Configured: %s\n", (strlen(nodePassword) > 0) ? "Yes" : "No");
    Serial.printf("  Node Role: %u\n", nodeRole);
    Serial.printf("  Telemetry Interval: %u sec\n", telemetryIntervalSec);
    Serial.printf("  Screen Timeout: %u sec\n", screenTimeoutSecs);
    Serial.printf("  Power Save Mode: %s\n", powerSaveMode ? "ON" : "OFF");
    Serial.printf("  Position Precision: %u m\n", positionPrecisionM);
    Serial.printf("  GPS Mode: %s\n", (gpsMode == 0) ? "ON" : "OFF (power save)");
    Serial.printf("  Fixed Position: %s (Lat=%.6f, Lon=%.6f, Alt=%d)\n", fixedPosition ? "YES" : "NO", fixedLat, fixedLon, fixedAlt);
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    InternalFS.begin();
    File file(InternalFS);
    NodeSettings settings;
    bool loaded = false;
    
    if (file.open("/settings.bin", FILE_O_READ)) {
        if (file.read((uint8_t*)&settings, sizeof(settings)) == sizeof(settings)) {
            if (settings.version == SETTINGS_VERSION) {
                strncpy(nodeCustomName, settings.name, sizeof(nodeCustomName) - 1);
                nodeCustomName[sizeof(nodeCustomName) - 1] = '\0';
                strncpy(nodePassword, settings.password, sizeof(nodePassword) - 1);
                nodePassword[sizeof(nodePassword) - 1] = '\0';
                loraSF = settings.sf;
                loraBW = settings.bw;
                loraTxPower = settings.txPower;
                nodeRegion = settings.region;
                nodeRole = settings.role;
                telemetryIntervalSec = settings.telemetryInterval;
                screenTimeoutSecs = settings.screenTimeoutSecs;
                powerSaveMode = settings.powerSaveMode;
                positionPrecisionM = settings.positionPrecisionM;
                gpsMode = settings.gpsMode;
                fixedPosition = settings.fixedPosition;
                fixedLat = settings.fixedLat;
                fixedLon = settings.fixedLon;
                fixedAlt = settings.fixedAlt;
                loaded = true;
            }
        }
        file.close();
    }
    
    if (!loaded) {
        nodeCustomName[0] = '\0';
        nodePassword[0] = '\0';
        loraSF = 9;
        loraBW = 125.0f;
        loraTxPower = 20; // default for RAK
        nodeRegion = 0;
        nodeRole = 0;
        telemetryIntervalSec = 60;
        screenTimeoutSecs = 30;
        powerSaveMode = false;
        positionPrecisionM = 0;
        gpsMode = 0;
        fixedPosition = false;
        fixedLat = 0.0f;
        fixedLon = 0.0f;
        fixedAlt = 0;

        saveSettings(nodeCustomName, loraSF, loraBW, loraTxPower, nodeRegion, nodePassword, nodeRole, telemetryIntervalSec, screenTimeoutSecs, powerSaveMode);
    }
    
    Serial.println("Loaded settings from InternalFS:");
    Serial.print("  Name: "); Serial.println(nodeCustomName);
    Serial.print("  SF: "); Serial.println(loraSF);
    Serial.print("  BW: "); Serial.println(loraBW, 1);
    Serial.print("  TX Power: "); Serial.println(loraTxPower);
    Serial.print("  Region: "); Serial.print(nodeRegion);
    Serial.println((nodeRegion == 0) ? " (US915)" : " (EU868)");
    Serial.print("  Password Configured: "); Serial.println((strlen(nodePassword) > 0) ? "Yes" : "No");
    Serial.print("  Node Role: "); Serial.println(nodeRole);
    Serial.print("  Telemetry Interval: "); Serial.print(telemetryIntervalSec); Serial.println(" sec");
    Serial.print("  Screen Timeout: "); Serial.print(screenTimeoutSecs); Serial.println(" sec");
    Serial.print("  Power Save Mode: "); Serial.println(powerSaveMode ? "ON" : "OFF");
    Serial.print("  Position Precision: "); Serial.print(positionPrecisionM); Serial.println(" m");
    Serial.print("  Fixed Position: "); Serial.print(fixedPosition ? "YES" : "NO");
    Serial.print(" (Lat="); Serial.print(fixedLat, 6); Serial.print(", Lon="); Serial.print(fixedLon, 6);
    Serial.print(", Alt="); Serial.print(fixedAlt); Serial.println(")");
#else
    nodeCustomName[0] = '\0';
    nodePassword[0] = '\0';
    loraSF = 9;
    loraBW = 125.0f;
    loraTxPower = 20;
    nodeRegion = 0;
    nodeRole = 0;
    powerSaveMode = false;
#endif
}

void saveSettings(const char* name, uint32_t sf, float bw, int32_t txPower, uint32_t region, const char* password, uint32_t role, uint32_t telemetryInterval, uint32_t screenTimeout, bool powerSave) {
    nodeRole = role; // Update local global variable
    telemetryIntervalSec = telemetryInterval; // Update local global variable
    screenTimeoutSecs = screenTimeout; // Update local global variable
    powerSaveMode = powerSave; // Update local global variable
#ifdef ESP32
    preferences.begin("aethermesh", false); // Read-write mode
    preferences.putString("node_name", name);
    preferences.putUInt("lora_sf", sf);
    preferences.putFloat("lora_bw", bw);
    preferences.putInt("lora_tx_power", txPower);
    preferences.putUInt("region", region);
    preferences.putString("node_pass", password);
    preferences.putUInt("node_role", role);
    preferences.putUInt("tel_interval", telemetryInterval);
    preferences.putUInt("scr_timeout", screenTimeout);
    preferences.putBool("power_save", powerSave);
    // positionPrecisionM/gpsMode are persisted from the globals on purpose (see decls)
    preferences.putUInt("pos_prec", positionPrecisionM);
    preferences.putUInt("gps_mode", gpsMode);
    preferences.putBool("fixed_pos", fixedPosition);
    preferences.putFloat("fixed_lat", fixedLat);
    preferences.putFloat("fixed_lon", fixedLon);
    preferences.putInt("fixed_alt", fixedAlt);
    preferences.putUInt("settings_ver", SETTINGS_VERSION);
    preferences.end();
    Serial.println("Saved settings to NVS.");
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    InternalFS.begin();
    InternalFS.remove("/settings.bin"); // Overwrite by deleting first
    File file(InternalFS);
    if (file.open("/settings.bin", FILE_O_WRITE)) {
        NodeSettings settings;
        settings.version = SETTINGS_VERSION;
        strncpy(settings.name, name, sizeof(settings.name) - 1);
        settings.name[sizeof(settings.name) - 1] = '\0';
        settings.sf = sf;
        settings.bw = bw;
        settings.txPower = txPower;
        settings.region = region;
        strncpy(settings.password, password, sizeof(settings.password) - 1);
        settings.password[sizeof(settings.password) - 1] = '\0';
        settings.role = role;
        settings.telemetryInterval = telemetryInterval;
        settings.screenTimeoutSecs = screenTimeout;
        settings.powerSaveMode = powerSave;
        // positionPrecisionM/gpsMode are persisted from the globals on purpose (see decls)
        settings.positionPrecisionM = positionPrecisionM;
        settings.gpsMode = gpsMode;
        settings.fixedPosition = fixedPosition;
        settings.fixedLat = fixedLat;
        settings.fixedLon = fixedLon;
        settings.fixedAlt = fixedAlt;

        file.write((const uint8_t*)&settings, sizeof(settings));
        file.close();
        Serial.println("Saved settings to InternalFS.");
    } else {
        Serial.println("Failed to open settings file for writing.");
    }
#endif
}

// Helper to retrieve unique node ID based on hardware
uint32_t getHardwareNodeId() {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    uint64_t mac = ESP.getEfuseMac();
    return (uint32_t)(mac & 0xFFFFFFFF);
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    return NRF_FICR->DEVICEID[0];
#else
    return 0xDEADBEEF; // Fallback
#endif
}

// LiPo state-of-charge from RESTING (open-circuit) voltage. Two things make a
// naive 4.2V=100% curve read low: (1) a LiPo only sits at 4.2V while ON the
// charger - a full pack settles to ~4.13-4.15V within minutes of unplugging
// (multimeter-confirmed: a "full" pack read 4.12V); (2) the discharge curve is
// very nonlinear - it sags off the top fast, then holds a long 3.7-3.9V
// plateau. So 100% is anchored at 4.15V (resting-full), not 4.20V.
uint8_t lipoPercentFromVoltage(float v) {
    static const float ocv[] = {4.15f, 4.10f, 4.05f, 4.00f, 3.92f, 3.85f, 3.80f, 3.77f, 3.74f, 3.70f, 3.65f, 3.55f, 3.45f};
    static const uint8_t pct[] = {100,   96,   90,   82,   72,   62,   52,   42,   32,   22,   14,    6,    0};
    const int N = 13;
    if (v >= ocv[0]) return 100;
    for (int i = 1; i < N; i++) {
        if (v >= ocv[i]) {
            float f = (v - ocv[i]) / (ocv[i - 1] - ocv[i]);
            return (uint8_t)(pct[i] + f * (pct[i - 1] - pct[i]) + 0.5f);
        }
    }
    return 0;
}

// Battery measurement helper for Heltec V4. The raw read toggles the divider
// GPIO and blocks 10ms, and callers (display refresh) run every second, so the
// result is cached for 30s.
uint8_t readBatteryLevel() {
#if defined(HELTEC_V4) || defined(HELTEC_V3)
    static uint32_t lastSampleTime = 0;
    static uint8_t cachedLevel = 0;
    static bool haveSample = false;

    if (haveSample && (millis() - lastSampleTime < 30000)) {
        return cachedLevel;
    }

    // Heltec V4 battery sense: VBAT through a 390k/100k divider into GPIO1
    // (physical ratio ~4.9), gated by GPIO37. Use analogReadMilliVolts(), which
    // applies the ESP32-S3's factory ADC calibration curve - raw analogRead()
    // scaled by hand under-reads badly at the top of the range (a full pack read
    // ~4.0V), which is what the old 1.045 fudge tried and failed to patch.
    // BATT_DIVIDER is fine-tuned against a multimeter (see calibration note).
    static const float BATT_DIVIDER = 4.90f;
    static const float BATT_CAL = 1.005f; // trim: node read 4.101V vs 4.12V measured

    pinMode(37, OUTPUT);
#if defined(HELTEC_V3)
    digitalWrite(37, LOW); // Active-low on V3 to enable divider
#else
    digitalWrite(37, HIGH); // Active-high on V4 to enable divider
#endif
    delay(10); // let the divider settle
    uint32_t mvSum = 0;
    for (int i = 0; i < 32; i++) mvSum += analogReadMilliVolts(1); // calibrated pin mV
    float pinMv = mvSum / 32.0f;
#if defined(HELTEC_V3)
    digitalWrite(37, HIGH); // Pull HIGH to disable divider and save power on V3
#else
    digitalWrite(37, LOW); // Pull LOW to disable divider and save power on V4
#endif

    float voltage = (pinMv / 1000.0f) * BATT_DIVIDER * BATT_CAL;
    updateChargingState(voltage);

    Serial.printf("Battery: pin %.0fmV | %.3f V | %u%%\n",
                  pinMv, voltage, lipoPercentFromVoltage(voltage));

    cachedLevel = lipoPercentFromVoltage(voltage);
    haveSample = true;
    lastSampleTime = millis();
    return cachedLevel;
#elif defined(LILYGO_T_DECK)
    static uint32_t lastSampleTime = 0;
    static uint8_t cachedLevel = 0;
    static bool haveSample = false;

    if (haveSample && (millis() - lastSampleTime < 30000)) {
        return cachedLevel;
    }

    float pinMv = analogReadMilliVolts(4);
    float voltage = (pinMv / 1000.0f) * 2.00f; // 2x multiplier
    updateChargingState(voltage);

    Serial.printf("Battery (T-Deck): pin %.0fmV | %.3f V | %u%%\n",
                  pinMv, voltage, lipoPercentFromVoltage(voltage));

    cachedLevel = lipoPercentFromVoltage(voltage);
    haveSample = true;
    lastSampleTime = millis();
    return cachedLevel;
#elif defined(LILYGO_T_ECHO)
    static uint32_t lastSampleTime = 0;
    static uint8_t cachedLevel = 0;
    static bool haveSample = false;

    if (haveSample && (millis() - lastSampleTime < 30000)) {
        return cachedLevel;
    }

    analogReference(AR_INTERNAL_3_0);
    analogReadResolution(12);
    delay(2);
    // Average 32 reads
    uint32_t adcSum = 0;
    for (int i = 0; i < 32; i++) adcSum += analogRead(4);
    float raw = adcSum / 32.0f;
    float voltage = (raw / 4096.0f) * 3.0f * 2.00f; // 2.0x multiplier
    updateChargingState(voltage);

    Serial.printf("Battery (T-Echo): raw %.0f | %.3f V | %u%%\n",
                  raw, voltage, lipoPercentFromVoltage(voltage));

    cachedLevel = lipoPercentFromVoltage(voltage);
    haveSample = true;
    lastSampleTime = millis();
    return cachedLevel;
#elif defined(RAK4631) || defined(RAK3401_1W)
    static uint32_t lastSampleTime = 0;
    static uint8_t cachedLevel = 0;
    static bool haveSample = false;

    if (haveSample && (millis() - lastSampleTime < 30000)) {
        return cachedLevel;
    }

    // WisBlock battery sense: VBAT through a 1.5M/1M divider on WB_A0.
    // 12-bit ADC with the 3.0V internal reference -> 0.7324 mV/LSB, x1.73 divider.
    analogReference(AR_INTERNAL_3_0);
    analogReadResolution(12);
    delay(2); // let the reference settle
    // Average many reads so ADC noise stays below the charge-detection threshold
    uint32_t adcSum = 0;
    for (int i = 0; i < 32; i++) adcSum += analogRead(WB_A0);
    int rawValue = adcSum / 32;
    float voltage = (float)rawValue * 0.73242188f * 1.73f / 1000.0f;
    updateChargingState(voltage);

    Serial.print("Battery ADC: ");
    Serial.print(rawValue);
    Serial.print(" | Calc Voltage: ");
    Serial.print(voltage);
    Serial.println(" V");

    cachedLevel = lipoPercentFromVoltage(voltage);
    haveSample = true;
    lastSampleTime = millis();
    return cachedLevel;
#else
    return 98; // Fallback for other boards
#endif
}

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
#if defined(LILYGO_T_DECK)
static void drawTDeckBootSplash(uint8_t wavePhase);
#endif
// Boot splash: mesh glyph + wordmark + firmware version. wavePhase pulses the
// radio arcs while setup runs.
void drawBootSplash(uint8_t wavePhase) {
#if defined(LILYGO_T_DECK)
    drawTDeckBootSplash(wavePhase);
    return;
#endif
    u8g2.clearBuffer();

    // Mesh glyph: three linked nodes, radio waves rippling from the top one
    u8g2.drawDisc(64, 16, 3);
    u8g2.drawDisc(44, 28, 2);
    u8g2.drawDisc(84, 28, 2);
    u8g2.drawLine(64, 16, 44, 28);
    u8g2.drawLine(64, 16, 84, 28);
    u8g2.drawLine(44, 28, 84, 28);
    uint8_t r = 7 + (wavePhase % 3) * 4;
    u8g2.drawCircle(64, 16, r, U8G2_DRAW_UPPER_LEFT | U8G2_DRAW_UPPER_RIGHT);
    if (r > 7) {
        u8g2.drawCircle(64, 16, r - 4, U8G2_DRAW_UPPER_LEFT | U8G2_DRAW_UPPER_RIGHT);
    }

    // Wordmark, centered (fall back to a smaller face if it would clip)
    u8g2.setFont(u8g2_font_ncenB14_tr);
    const char* title = "AetherMesh";
    if (u8g2.getStrWidth(title) > 124) {
        u8g2.setFont(u8g2_font_ncenB10_tr);
    }
    u8g2.drawStr((128 - u8g2.getStrWidth(title)) / 2, 50, title);

    // Firmware version, centered at the bottom — lets you check what a node
    // runs without the app (mismatched builds degrade the mesh)
    u8g2.setFont(u8g2_font_5x7_tf);
    char ver[28];
    snprintf(ver, sizeof(ver), "v%s", AETHERMESH_FW_VERSION);
    u8g2.drawStr((128 - u8g2.getStrWidth(ver)) / 2, 62, ver);

    u8g2.sendBuffer();
}

// Battery glyph at (x, y): 20x9 body + terminal nub, fill proportional to pct,
// lightning bolt drawn to the left while charging.
void drawBatteryIcon(uint8_t x, uint8_t y, uint8_t pct, bool charging) {
    u8g2.drawFrame(x, y, 20, 9);
    u8g2.drawBox(x + 20, y + 2, 2, 5);
    uint8_t fill = (uint8_t)(((uint16_t)pct * 16) / 100);
    if (fill > 16) fill = 16;
    if (fill > 0) u8g2.drawBox(x + 2, y + 2, fill, 5);
    if (charging) {
        u8g2.drawLine(x - 4, y, x - 8, y + 5);
        u8g2.drawLine(x - 8, y + 5, x - 5, y + 5);
        u8g2.drawLine(x - 5, y + 5, x - 9, y + 10);
    }
}

// "32s" / "4m" / "2h" style age strings for the NODES/MESSAGES pages
static void formatAge(uint32_t ageMs, char* out, size_t n) {
    uint32_t s = ageMs / 1000UL;
    if (s < 60) snprintf(out, n, "%lus", (unsigned long)s);
    else if (s < 3600) snprintf(out, n, "%lum", (unsigned long)(s / 60));
    else snprintf(out, n, "%luh", (unsigned long)(s / 3600));
}

#if defined(LILYGO_T_DECK)
static const uint16_t TDECK_BG = 0x0841;
static const uint16_t TDECK_PANEL = 0x10C4;
static const uint16_t TDECK_PANEL_2 = 0x1927;
static const uint16_t TDECK_LINE = 0x31A9;
static const uint16_t TDECK_TEXT = 0xE75C;
static const uint16_t TDECK_MUTED = 0x8C71;
static const uint16_t TDECK_GRID = 0x1083;
static const uint16_t TDECK_SHADOW = 0x0020;
static const uint16_t TDECK_CYAN = 0x05FF;
static const uint16_t TDECK_GREEN = 0x07E0;
static const uint16_t TDECK_AMBER = 0xFEA0;
static const uint16_t TDECK_RED = 0xF986;
static const uint16_t TDECK_BLUE = 0x4B5F;
static const uint16_t TDECK_MAGENTA = 0xD2BF;

static void tdeckDrawUpperArc(int16_t cx, int16_t cy, int16_t r, uint16_t color);

static uint16_t tdeckBatteryColor(uint8_t pct) {
    if (pct <= 20) return TDECK_RED;
    if (pct <= 50) return TDECK_AMBER;
    return TDECK_GREEN;
}

static void tdeckDrawText(int16_t x, int16_t y, const uint8_t* font, const char* text, uint16_t color, uint8_t scale = 1) {
    if (!text || text[0] == '\0') return;
    u8g2.clearBuffer();
    u8g2.setFont(font);
    int16_t ascent = u8g2.getAscent();
    int16_t descent = u8g2.getDescent();
    if (descent > 0) descent = 0;
    uint8_t height = (uint8_t)(ascent - descent + 2);
    if (height > 64) height = 64;
    uint16_t width = u8g2.getStrWidth(text) + 2;
    if (width > 128) width = 128;
    u8g2.drawStr(0, ascent + 1, text);

    uint8_t* buf = u8g2.getBufferPtr();
    for (uint8_t row = 0; row < height; row++) {
        for (uint8_t rep = 0; rep < scale; rep++) {
            int16_t runStart = -1;
            for (uint16_t col = 0; col < width; col++) {
                uint16_t idx = (row / 8) * 128 + col;
                bool pixel = (buf[idx] & (1 << (row & 7))) != 0;
                if (pixel && runStart < 0) {
                    runStart = col;
                } else if (!pixel && runStart >= 0) {
                    st7789_fill_rect(x + runStart * scale, y + row * scale + rep, (col - runStart) * scale, 1, color);
                    runStart = -1;
                }
            }
            if (runStart >= 0) {
                st7789_fill_rect(x + runStart * scale, y + row * scale + rep, (width - runStart) * scale, 1, color);
            }
        }
    }
}

static void tdeckDrawBackground(uint16_t accent) {
    st7789_clear(TDECK_BG);
    st7789_fill_rect(0, 0, 320, 240, TDECK_BG);
    for (int16_t x = 8; x < 320; x += 32) {
        st7789_fill_rect(x, 44, 1, 196, TDECK_GRID);
    }
    for (int16_t y = 56; y < 240; y += 32) {
        st7789_fill_rect(0, y, 320, 1, TDECK_GRID);
    }
    st7789_fill_rect(0, 0, 320, 42, 0x1083);
    st7789_fill_rect(0, 40, 320, 2, accent);
    st7789_fill_rect(0, 42, 320, 1, TDECK_LINE);
}

static void tdeckDrawPill(int16_t x, int16_t y, int16_t w, const char* text, uint16_t accent) {
    st7789_fill_rect(x, y, w, 18, TDECK_PANEL_2);
    st7789_draw_rect(x, y, w, 18, accent);
    tdeckDrawText(x + 8, y + 6, u8g2_font_5x7_tf, text, accent, 1);
}

static void tdeckDrawHeader(const char* title, uint8_t page, uint16_t accent) {
    tdeckDrawText(12, 8, u8g2_font_ncenB14_tr, title, TDECK_TEXT, 1);
    char pg[10];
    snprintf(pg, sizeof(pg), "%u/%u", (unsigned)(page + 1), (unsigned)OLED_PAGE_COUNT);
    tdeckDrawPill(276, 12, 32, pg, accent);
    st7789_fill_circle(248, 21, 4, accent);
    st7789_fill_circle(260, 21, 2, TDECK_MUTED);
}

static void tdeckDrawCard(int16_t x, int16_t y, int16_t w, int16_t h, const char* label, uint16_t accent) {
    st7789_fill_rect(x + 2, y + 2, w, h, TDECK_SHADOW);
    st7789_fill_rect(x, y, w, h, TDECK_PANEL);
    st7789_fill_rect(x, y, w, 2, accent);
    st7789_fill_rect(x, y + h - 2, w, 2, TDECK_PANEL_2);
    st7789_draw_rect(x, y, w, h, TDECK_LINE);
    tdeckDrawText(x + 10, y + 10, u8g2_font_5x7_tf, label, TDECK_MUTED, 1);
}

static void tdeckDrawProgressBar(int16_t x, int16_t y, int16_t w, uint8_t pct, uint16_t accent) {
    if (pct > 100) pct = 100;
    st7789_draw_rect(x, y, w, 8, TDECK_LINE);
    int16_t fill = (int16_t)((uint32_t)(w - 4) * pct / 100);
    st7789_fill_rect(x + 2, y + 2, fill, 4, accent);
}

static void tdeckDrawSignalBars(int16_t x, int16_t y, uint8_t bars, uint16_t accent) {
    if (bars > 5) bars = 5;
    for (uint8_t i = 0; i < 5; i++) {
        int16_t h = 5 + i * 4;
        uint16_t color = (i < bars) ? accent : TDECK_LINE;
        st7789_fill_rect(x + i * 8, y + 22 - h, 5, h, color);
    }
}

static void tdeckDrawMiniSignalBars(int16_t x, int16_t y, uint8_t bars, uint16_t accent) {
    if (bars > 5) bars = 5;
    for (uint8_t i = 0; i < 5; i++) {
        int16_t h = 3 + i * 3;
        uint16_t color = (i < bars) ? accent : TDECK_LINE;
        st7789_fill_rect(x + i * 5, y + 15 - h, 3, h, color);
    }
}

static void tdeckDrawNodeTrace(int16_t x, int16_t y, uint16_t accent) {
    st7789_draw_line(x + 9, y + 2, x + 2, y + 14, TDECK_LINE);
    st7789_draw_line(x + 9, y + 2, x + 20, y + 14, TDECK_LINE);
    st7789_draw_line(x + 2, y + 14, x + 20, y + 14, TDECK_LINE);
    st7789_fill_circle(x + 9, y + 2, 3, accent);
    st7789_fill_circle(x + 2, y + 14, 3, TDECK_BLUE);
    st7789_fill_circle(x + 20, y + 14, 3, TDECK_GREEN);
}

static void tdeckDrawStatusDot(int16_t x, int16_t y, uint16_t accent, bool active) {
    st7789_draw_circle(x, y, 4, accent);
    if (active) {
        st7789_fill_circle(x, y, 2, accent);
    }
}

static void tdeckDrawMeshGlyph(int16_t x, int16_t y, uint16_t accent) {
    st7789_draw_line(x + 32, y + 4, x + 9, y + 30, TDECK_LINE);
    st7789_draw_line(x + 32, y + 4, x + 56, y + 30, TDECK_LINE);
    st7789_draw_line(x + 9, y + 30, x + 56, y + 30, TDECK_LINE);
    st7789_fill_circle(x + 32, y + 4, 7, accent);
    st7789_fill_circle(x + 9, y + 30, 6, TDECK_BLUE);
    st7789_fill_circle(x + 56, y + 30, 6, TDECK_GREEN);
    tdeckDrawUpperArc(x + 32, y + 4, 18, accent);
}

static void tdeckDrawEnvelopeIcon(int16_t x, int16_t y, uint16_t accent) {
    st7789_draw_rect(x, y, 34, 22, accent);
    st7789_draw_line(x + 1, y + 1, x + 17, y + 13, accent);
    st7789_draw_line(x + 33, y + 1, x + 17, y + 13, accent);
    st7789_draw_line(x + 1, y + 21, x + 13, y + 11, TDECK_LINE);
    st7789_draw_line(x + 33, y + 21, x + 21, y + 11, TDECK_LINE);
}

static void tdeckDrawGpsGlyph(int16_t x, int16_t y, uint16_t accent) {
    st7789_fill_circle(x + 14, y + 12, 11, accent);
    st7789_fill_circle(x + 14, y + 12, 5, TDECK_PANEL);
    st7789_draw_line(x + 14, y + 23, x + 8, y + 35, accent);
    st7789_draw_line(x + 14, y + 23, x + 20, y + 35, accent);
    st7789_draw_line(x + 8, y + 35, x + 20, y + 35, accent);
}

static void tdeckDrawRadioGlyph(int16_t x, int16_t y, uint16_t accent) {
    st7789_draw_line(x + 8, y + 28, x + 24, y + 4, accent);
    st7789_draw_line(x + 24, y + 4, x + 40, y + 28, accent);
    tdeckDrawUpperArc(x + 24, y + 18, 14, TDECK_BLUE);
    tdeckDrawUpperArc(x + 24, y + 18, 24, accent);
}

static void tdeckDrawBattery(int16_t x, int16_t y, uint8_t pct, bool charging) {
    uint16_t fillColor = tdeckBatteryColor(pct);
    st7789_draw_rect(x, y, 42, 18, TDECK_MUTED);
    st7789_fill_rect(x + 42, y + 5, 4, 8, TDECK_MUTED);
    uint8_t fill = (uint8_t)(((uint16_t)pct * 34) / 100);
    if (fill > 34) fill = 34;
    st7789_fill_rect(x + 4, y + 4, fill, 10, fillColor);
    if (charging) {
        st7789_draw_line(x - 10, y + 1, x - 17, y + 10, TDECK_AMBER);
        st7789_draw_line(x - 17, y + 10, x - 11, y + 10, TDECK_AMBER);
        st7789_draw_line(x - 11, y + 10, x - 18, y + 20, TDECK_AMBER);
    }
}

static void tdeckFormatUptime(char* out, size_t n) {
    uint32_t upSec = millis() / 1000UL;
    if (upSec < 3600) {
        snprintf(out, n, "%lum", (unsigned long)(upSec / 60));
    } else if (upSec < 86400) {
        snprintf(out, n, "%luh %lum", (unsigned long)(upSec / 3600), (unsigned long)((upSec % 3600) / 60));
    } else {
        snprintf(out, n, "%lud %luh", (unsigned long)(upSec / 86400), (unsigned long)((upSec % 86400) / 3600));
    }
}

static void tdeckDrawWrappedText(int16_t x, int16_t y, int16_t w, const char* text, uint16_t color, uint8_t maxLines = 5) {
    if (!text || text[0] == '\0') return;

    uint8_t charsPerLine = (uint8_t)(w / 12);
    if (charsPerLine < 8) charsPerLine = 8;
    if (charsPerLine > 24) charsPerLine = 24;

    size_t pos = 0;
    uint8_t lineNo = 0;
    size_t len = strlen(text);
    while (pos < len && lineNo < maxLines) {
        size_t take = len - pos;
        if (take > charsPerLine) {
            take = charsPerLine;
            size_t breakAt = take;
            while (breakAt > 0 && text[pos + breakAt] != ' ') {
                breakAt--;
            }
            if (breakAt >= 6) {
                take = breakAt;
            }
        }

        char line[25];
        if (take >= sizeof(line)) take = sizeof(line) - 1;
        memcpy(line, text + pos, take);
        line[take] = '\0';
        tdeckDrawText(x, y + (lineNo * 22), u8g2_font_6x10_tf, line, color, 2);

        pos += take;
        while (pos < len && text[pos] == ' ') pos++;
        lineNo++;
    }
}

static void tdeckDrawUpperArc(int16_t cx, int16_t cy, int16_t r, uint16_t color) {
    int16_t lastX = cx - r;
    int16_t lastY = cy;
    for (int16_t dx = -r + 2; dx <= r; dx += 2) {
        float inside = (float)(r * r - dx * dx);
        if (inside < 0.0f) inside = 0.0f;
        int16_t x = cx + dx;
        int16_t y = cy - (int16_t)sqrtf(inside);
        st7789_draw_line(lastX, lastY, x, y, color);
        st7789_draw_line(lastX, lastY - 1, x, y - 1, color);
        lastX = x;
        lastY = y;
    }
}

static void drawTDeckBootSplash(uint8_t wavePhase) {
    st7789_clear(TDECK_BG);
    st7789_fill_rect(0, 0, 320, 240, TDECK_BG);
    st7789_fill_circle(160, 74, 9, TDECK_CYAN);
    st7789_fill_circle(112, 112, 7, TDECK_BLUE);
    st7789_fill_circle(208, 112, 7, TDECK_GREEN);
    st7789_draw_line(160, 74, 112, 112, TDECK_LINE);
    st7789_draw_line(160, 74, 208, 112, TDECK_LINE);
    st7789_draw_line(112, 112, 208, 112, TDECK_LINE);
    uint8_t r = 18 + (wavePhase % 3) * 10;
    tdeckDrawUpperArc(160, 74, r, TDECK_CYAN);
    if (r > 18) {
        tdeckDrawUpperArc(160, 74, r - 10, TDECK_BLUE);
    }

    tdeckDrawText(72, 138, u8g2_font_ncenB14_tr, "AetherMesh", TDECK_TEXT, 2);
    char ver[32];
    snprintf(ver, sizeof(ver), "v%s", AETHERMESH_FW_VERSION);
    tdeckDrawText(92, 202, u8g2_font_6x10_tf, ver, TDECK_MUTED, 1);
    st7789_push_frame();
}

static void drawTDeckMessagePopup() {
    st7789_clear(TDECK_BG);
    st7789_fill_rect(18, 28, 284, 184, TDECK_PANEL);
    st7789_fill_rect(18, 28, 284, 6, TDECK_AMBER);
    st7789_draw_rect(18, 28, 284, 184, TDECK_AMBER);
    tdeckDrawText(48, 52, u8g2_font_ncenB14_tr, "New Message", TDECK_TEXT, 1);

    char sender[28];
    snprintf(sender, sizeof(sender), "From 0x%08X", (unsigned)lastMsgSender);
    tdeckDrawText(40, 92, u8g2_font_6x10_tf, sender, TDECK_CYAN, 2);

    char line[25];
    strncpy(line, lastMsgText, 24);
    line[24] = '\0';
    tdeckDrawText(40, 132, u8g2_font_6x10_tf, line, TDECK_TEXT, 2);
    if (strlen(lastMsgText) > 24) {
        strncpy(line, lastMsgText + 24, 24);
        line[24] = '\0';
        tdeckDrawText(40, 162, u8g2_font_6x10_tf, line, TDECK_TEXT, 2);
    }
    st7789_push_frame();
}

static void drawTDeckOtaProgress(uint8_t pct, const char* label) {
    if (pct > 100) pct = 100;
    st7789_clear(TDECK_BG);
    st7789_fill_rect(0, 0, 320, 240, TDECK_BG);
    tdeckDrawText(48, 48, u8g2_font_ncenB14_tr, "Firmware Update", TDECK_TEXT, 1);
    tdeckDrawText(108, 82, u8g2_font_6x10_tf, label ? label : "receiving", TDECK_MUTED, 1);
    st7789_draw_rect(34, 116, 252, 26, TDECK_LINE);
    st7789_fill_rect(38, 120, (uint16_t)pct * 244 / 100, 18, pct >= 100 ? TDECK_GREEN : TDECK_CYAN);
    char pctStr[16];
    snprintf(pctStr, sizeof(pctStr), "%u%%", (unsigned)pct);
    tdeckDrawText(134, 164, u8g2_font_ncenB14_tr, pctStr, TDECK_TEXT, 1);
    st7789_push_frame();
}

static void drawTDeckHome() {
    uint8_t batteryPct = readBatteryLevel();
    char title[24];
    if (strlen(nodeCustomName) > 0) {
        snprintf(title, sizeof(title), "%.16s", nodeCustomName);
    } else {
        snprintf(title, sizeof(title), "AetherMesh");
    }
    tdeckDrawBackground(TDECK_CYAN);
    tdeckDrawHeader(title, 0, TDECK_CYAN);
    char idLine[24];
    snprintf(idLine, sizeof(idLine), "0x%08X", (unsigned)localNodeId);
    tdeckDrawText(14, 31, u8g2_font_5x7_tf, idLine, TDECK_MUTED, 1);
    tdeckDrawBattery(226, 12, batteryPct, batteryCharging);

    bool ble = bleMgr.isDeviceConnected();
    tdeckDrawCard(12, 54, 142, 62, "BLE", ble ? TDECK_GREEN : TDECK_AMBER);
    tdeckDrawText(24, 80, u8g2_font_7x14_tf, ble ? "CONNECTED" : "ADVERT", ble ? TDECK_GREEN : TDECK_AMBER, 2);
    tdeckDrawMiniSignalBars(122, 94, ble ? 5 : 2, ble ? TDECK_GREEN : TDECK_AMBER);

    tdeckDrawCard(166, 54, 142, 62, "MESH", TDECK_CYAN);
    char meshLine[18];
    snprintf(meshLine, sizeof(meshLine), "NODES %u", (unsigned)countActivePeers());
    tdeckDrawText(180, 80, u8g2_font_7x14_tf, meshLine, TDECK_CYAN, 2);
    tdeckDrawNodeTrace(278, 92, TDECK_CYAN);

    tdeckDrawCard(12, 128, 296, 42, "RADIO", TDECK_BLUE);
    char radioLine[40];
    snprintf(radioLine, sizeof(radioLine), "SF%u  BW%d  %.1fMHz",
             (unsigned)radioMgr.getSpreadingFactor(), (int)radioMgr.getBandwidth(), radioMgr.getFrequency());
    tdeckDrawText(24, 148, u8g2_font_6x10_tf, radioLine, TDECK_TEXT, 2);
    tdeckDrawMiniSignalBars(270, 148, min((uint8_t)5, radioMgr.getSpreadingFactor() > 9 ? (uint8_t)4 : (uint8_t)3), TDECK_BLUE);

    tdeckDrawCard(12, 182, 142, 42, "TRAFFIC", TDECK_GREEN);
    char traffic[24];
    snprintf(traffic, sizeof(traffic), "RX %u  TX %u", (unsigned)rxPacketCount, (unsigned)txPacketCount);
    tdeckDrawText(24, 202, u8g2_font_6x10_tf, traffic, TDECK_TEXT, 1);
    tdeckDrawProgressBar(24, 214, 112, (uint8_t)((rxPacketCount + txPacketCount) % 101), TDECK_GREEN);

    tdeckDrawCard(166, 182, 142, 42, "GPS", TDECK_AMBER);
    char gpsText[24];
    if (fixedPosition) {
        snprintf(gpsText, sizeof(gpsText), "FIXED");
    } else if (gps.location.isValid()) {
        snprintf(gpsText, sizeof(gpsText), "%lu SAT", (unsigned long)gps.satellites.value());
    } else if (hasInheritedLocation && (millis() - lastInheritedTime < 300000)) {
        snprintf(gpsText, sizeof(gpsText), "PHONE");
    } else if (!hasOnboardGps) {
        snprintf(gpsText, sizeof(gpsText), "NO GPS");
    } else if (gpsMode != 0) {
        snprintf(gpsText, sizeof(gpsText), "OFF");
    } else {
        snprintf(gpsText, sizeof(gpsText), "NO LOCK");
    }
    tdeckDrawText(178, 202, u8g2_font_6x10_tf, gpsText, TDECK_TEXT, 1);
    tdeckDrawStatusDot(294, 203, TDECK_AMBER, fixedPosition || gps.location.isValid() || hasInheritedLocation);

    char footer[48];
    char up[16];
    tdeckFormatUptime(up, sizeof(up));
    snprintf(footer, sizeof(footer), "%s  %s  v%s", (nodeRegion == 0) ? "US915" : "EU868", up, AETHERMESH_FW_VERSION);
    tdeckDrawText(12, 229, u8g2_font_5x7_tf, footer, TDECK_MUTED, 1);

    uint32_t now = millis();
    const char* kbLine = (tdeckKeyboardNoticeUntil != 0 && (int32_t)(now - tdeckKeyboardNoticeUntil) < 0)
                             ? tdeckKeyboardNotice
                             : tdeckKeyboardStatus;
    uint16_t kbColor = tdeckKeyboardPresent ? TDECK_GREEN : TDECK_AMBER;
    tdeckDrawText(208, 229, u8g2_font_5x7_tf, kbLine, kbColor, 1);
    st7789_push_frame();
}

static void drawTDeckCompose() {
    if (!tdeckComposeFrameDrawn) {
        st7789_clear(TDECK_BG);
        st7789_fill_rect(0, 0, 320, 240, TDECK_BG);
        st7789_fill_rect(0, 0, 320, 42, 0x1083);
        st7789_fill_rect(0, 40, 320, 2, TDECK_GREEN);
        tdeckDrawText(14, 10, u8g2_font_ncenB14_tr, "Compose", TDECK_TEXT, 1);

        st7789_fill_rect(16, 58, 288, 124, TDECK_PANEL);
        st7789_fill_rect(16, 58, 288, 4, TDECK_GREEN);
        st7789_draw_rect(16, 58, 288, 124, TDECK_LINE);

        st7789_fill_rect(16, 196, 288, 28, TDECK_PANEL_2);
        st7789_draw_rect(16, 196, 288, 28, TDECK_LINE);
        tdeckDrawText(28, 206, u8g2_font_5x7_tf, "ENTER SENDS   BKSP EDITS   ESC CANCELS", TDECK_MUTED, 1);
        tdeckComposeFrameDrawn = true;
    }

    char count[16];
    snprintf(count, sizeof(count), "%u/%u", (unsigned)tdeckComposeLen, (unsigned)TDECK_COMPOSE_MAX);
    st7789_fill_rect(250, 8, 58, 24, 0x1083);
    tdeckDrawText(260, 18, u8g2_font_5x7_tf, count, TDECK_MUTED, 1);

    st7789_fill_rect(20, 66, 280, 110, TDECK_PANEL);

    if (tdeckComposeLen == 0) {
        tdeckDrawText(34, 106, u8g2_font_6x10_tf, "Start typing...", TDECK_MUTED, 2);
    } else {
        tdeckDrawWrappedText(30, 78, 260, tdeckComposeText, TDECK_TEXT, 5);
    }
    st7789_push_frame();
}

static void tdeckDrawPageChrome(const char* title, uint8_t page, uint16_t accent) {
    tdeckDrawBackground(accent);
    tdeckDrawHeader(title, page, accent);
    tdeckDrawBattery(226, 12, readBatteryLevel(), batteryCharging);
}

static void drawTDeckGpsPage() {
    tdeckDrawPageChrome("GPS", 1, TDECK_AMBER);
    char line[48];
    bool ownFix = gps.location.isValid();
    bool phoneFix = hasInheritedLocation && (millis() - lastInheritedTime < 300000);

    tdeckDrawCard(12, 56, 296, 48, "SOURCE", TDECK_AMBER);
    if (fixedPosition) {
        snprintf(line, sizeof(line), "FIXED POSITION");
    } else if (ownFix) {
        snprintf(line, sizeof(line), "ONBOARD FIX");
    } else if (phoneFix) {
        snprintf(line, sizeof(line), "PHONE SHARED");
    } else if (!hasOnboardGps) {
        snprintf(line, sizeof(line), "NO ONBOARD GPS");
    } else if (gpsMode != 0) {
        snprintf(line, sizeof(line), "GPS OFF");
    } else {
        snprintf(line, sizeof(line), "NO LOCK");
    }
    tdeckDrawText(26, 76, u8g2_font_7x14_tf, line, (ownFix || phoneFix || fixedPosition) ? TDECK_GREEN : TDECK_AMBER, 2);
    tdeckDrawStatusDot(288, 80, (ownFix || phoneFix || fixedPosition) ? TDECK_GREEN : TDECK_AMBER, ownFix || phoneFix || fixedPosition);

    tdeckDrawCard(12, 116, 296, 62, "POSITION", TDECK_CYAN);
    if (fixedPosition || ownFix || phoneFix) {
        double lat = fixedPosition ? fixedLat : (ownFix ? gps.location.lat() : (double)inheritedLat);
        double lon = fixedPosition ? fixedLon : (ownFix ? gps.location.lng() : (double)inheritedLon);
        snprintf(line, sizeof(line), "LAT %.5f", lat);
        tdeckDrawText(26, 138, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
        snprintf(line, sizeof(line), "LON %.5f", lon);
        tdeckDrawText(26, 156, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
    } else {
        tdeckDrawText(26, 145, u8g2_font_6x10_tf, "WAITING FOR POSITION", TDECK_MUTED, 1);
    }

    tdeckDrawCard(12, 190, 142, 34, "SAT", TDECK_BLUE);
    snprintf(line, sizeof(line), "%lu", (unsigned long)gps.satellites.value());
    tdeckDrawText(26, 206, u8g2_font_6x10_tf, line, TDECK_TEXT, 2);
    tdeckDrawMiniSignalBars(108, 202, (uint8_t)min((unsigned long)5, (unsigned long)gps.satellites.value()), TDECK_BLUE);

    tdeckDrawCard(166, 190, 142, 34, "ALT/SPEED", TDECK_GREEN);
    if (ownFix) {
        float spd = (nodeRegion == 0) ? gps.speed.mph() : gps.speed.kmph();
        snprintf(line, sizeof(line), "%dm %.1f%s", (int)gps.altitude.meters(), spd, (nodeRegion == 0) ? "MPH" : "KMH");
    } else if (fixedPosition) {
        snprintf(line, sizeof(line), "%dm", (int)fixedAlt);
    } else {
        snprintf(line, sizeof(line), "--");
    }
    tdeckDrawText(180, 206, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
    st7789_push_frame();
}

static void drawTDeckMessagesPage() {
    tdeckDrawPageChrome("Messages", 2, TDECK_GREEN);
    if (recentMsgCount == 0) {
        tdeckDrawCard(22, 78, 276, 96, "INBOX", TDECK_GREEN);
        tdeckDrawEnvelopeIcon(143, 96, TDECK_GREEN);
        tdeckDrawText(88, 130, u8g2_font_7x14_tf, "NO MESSAGES YET", TDECK_MUTED, 1);
        st7789_push_frame();
        return;
    }

    uint8_t toShow = (recentMsgCount < 3) ? recentMsgCount : 3;
    for (uint8_t k = 0; k < toShow; k++) {
        const RecentMsg& m = recentMsgs[(recentMsgHead + 4 - 1 - k) % 4];
        int16_t y = 54 + k * 58;
        uint16_t accent = (m.sender == localNodeId) ? TDECK_GREEN : TDECK_CYAN;
        tdeckDrawCard(12, y, 296, 48, (m.sender == localNodeId) ? "SENT" : "RECEIVED", accent);
        tdeckDrawStatusDot(286, y + 24, accent, true);
        char age[8];
        formatAge(millis() - m.atMs, age, sizeof(age));
        char hdr[32];
        snprintf(hdr, sizeof(hdr), "0x%04X  %s", (unsigned)(m.sender & 0xFFFF), age);
        tdeckDrawText(94, y + 10, u8g2_font_5x7_tf, hdr, TDECK_MUTED, 1);
        char body[31];
        strncpy(body, m.text, 30);
        body[30] = '\0';
        tdeckDrawText(24, y + 26, u8g2_font_6x10_tf, body, TDECK_TEXT, 1);
    }
    st7789_push_frame();
}

static void drawTDeckNodesPage() {
    tdeckDrawPageChrome("Nodes", 3, TDECK_CYAN);
    char count[20];
    snprintf(count, sizeof(count), "%u ACTIVE", (unsigned)countActivePeers());
    tdeckDrawText(18, 50, u8g2_font_7x14_tf, count, TDECK_CYAN, 2);
    tdeckDrawNodeTrace(280, 58, TDECK_CYAN);

    bool used[12] = {};
    uint8_t shown = 0;
    for (uint8_t row = 0; row < 5; row++) {
        int best = -1;
        for (int i = 0; i < 12; i++) {
            if (used[i] || peersSeen[i].id == 0) continue;
            if (millis() - peersSeen[i].lastMs >= PEER_ACTIVE_WINDOW_MS) continue;
            if (best < 0 || peersSeen[i].lastMs > peersSeen[best].lastMs) best = i;
        }
        if (best < 0) break;
        used[best] = true;
        int16_t y = 82 + row * 28;
        st7789_fill_rect(12, y, 296, 22, (row % 2 == 0) ? TDECK_PANEL : TDECK_PANEL_2);
        char age[8];
        formatAge(millis() - peersSeen[best].lastMs, age, sizeof(age));
        char line[48];
        snprintf(line, sizeof(line), "0x%08X   %.0fdB   %s", (unsigned)peersSeen[best].id, peersSeen[best].rssi, age);
        st7789_fill_circle(22, y + 11, 4, row == 0 ? TDECK_GREEN : TDECK_CYAN);
        tdeckDrawText(34, y + 7, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
        tdeckDrawMiniSignalBars(278, y + 4, (peersSeen[best].rssi > -70) ? 5 : (peersSeen[best].rssi > -90 ? 3 : 1), TDECK_GREEN);
        shown++;
    }
    if (shown == 0) {
        tdeckDrawCard(22, 104, 276, 72, "MESH", TDECK_CYAN);
        tdeckDrawText(48, 134, u8g2_font_6x10_tf, "NO PEERS HEARD RECENTLY", TDECK_MUTED, 1);
    }
    st7789_push_frame();
}

static void drawTDeckSystemPage() {
    tdeckDrawPageChrome("System", 4, TDECK_BLUE);
    char line[52];
    const char* roleName = (nodeRole == 1) ? "Router" : (nodeRole == 2) ? "Repeater" : "Client";

    tdeckDrawCard(12, 54, 296, 40, "NODE", TDECK_BLUE);
    snprintf(line, sizeof(line), "0x%08X  %s", (unsigned)localNodeId, roleName);
    tdeckDrawText(24, 74, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);

    tdeckDrawCard(12, 106, 142, 46, "POWER", TDECK_GREEN);
    snprintf(line, sizeof(line), "%u%%  %.2fV%s", (unsigned)readBatteryLevel(), batteryVoltage, batteryCharging ? " CHG" : "");
    tdeckDrawText(24, 128, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
    tdeckDrawProgressBar(24, 140, 112, readBatteryLevel(), tdeckBatteryColor(readBatteryLevel()));

    tdeckDrawCard(166, 106, 142, 46, "REGION", TDECK_AMBER);
    snprintf(line, sizeof(line), "%s  %ddBm", (nodeRegion == 0) ? "US915" : "EU868", (int)loraTxPower);
    tdeckDrawText(178, 128, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);

    tdeckDrawCard(12, 164, 142, 46, "MEMORY", TDECK_CYAN);
    snprintf(line, sizeof(line), "%luk HEAP", (unsigned long)(ESP.getFreeHeap() / 1024));
    tdeckDrawText(24, 186, u8g2_font_6x10_tf, line, TDECK_TEXT, 1);
    tdeckDrawProgressBar(24, 198, 112, (uint8_t)min(100UL, (unsigned long)(ESP.getFreeHeap() / 2048)), TDECK_CYAN);

    tdeckDrawCard(166, 164, 142, 46, "FIRMWARE", TDECK_BLUE);
    snprintf(line, sizeof(line), "v%s", AETHERMESH_FW_VERSION);
    tdeckDrawText(178, 186, u8g2_font_5x7_tf, line, TDECK_TEXT, 1);

    if (positionPrecisionM > 0) {
        snprintf(line, sizeof(line), "POS +/-%um", (unsigned)positionPrecisionM);
        tdeckDrawText(16, 224, u8g2_font_5x7_tf, line, TDECK_MUTED, 1);
    }
    st7789_push_frame();
}

static void tdeckHashMix(uint32_t& hash, uint32_t value) {
    hash ^= value + 0x9E3779B9UL + (hash << 6) + (hash >> 2);
}

static void tdeckHashText(uint32_t& hash, const char* text) {
    if (!text) return;
    while (*text) {
        tdeckHashMix(hash, (uint8_t)*text++);
    }
}

static uint32_t tdeckHomeRenderSignature(uint32_t now) {
    (void)now;
    uint32_t hash = 0xA37E2D51UL;
    tdeckHashText(hash, nodeCustomName);
    tdeckHashMix(hash, localNodeId);
    tdeckHashMix(hash, bleMgr.isDeviceConnected() ? 1 : 0);
    tdeckHashMix(hash, radioMgr.getSpreadingFactor());
    tdeckHashMix(hash, (uint32_t)radioMgr.getBandwidth());
    tdeckHashMix(hash, (uint32_t)(radioMgr.getFrequency() * 10.0f));
    tdeckHashMix(hash, nodeRegion);
    tdeckHashMix(hash, fixedPosition ? 1 : 0);
    tdeckHashMix(hash, hasOnboardGps ? 1 : 0);
    tdeckHashMix(hash, gpsMode);
    tdeckHashMix(hash, tdeckKeyboardPresent ? 1 : 0);
    tdeckHashText(hash, tdeckKeyboardStatus);
    if (tdeckKeyboardNoticeUntil != 0 && (int32_t)(now - tdeckKeyboardNoticeUntil) < 0) {
        tdeckHashText(hash, tdeckKeyboardNotice);
    }
    return hash;
}

static uint32_t tdeckComposeRenderSignature() {
    uint32_t hash = 0xC0520A11UL;
    tdeckHashMix(hash, tdeckComposeLen);
    tdeckHashText(hash, tdeckComposeText);
    return hash;
}

static uint32_t tdeckPopupRenderSignature() {
    uint32_t hash = 0x9017E551UL;
    tdeckHashMix(hash, lastMsgSender);
    tdeckHashMix(hash, lastMsgReceivedTime);
    tdeckHashText(hash, lastMsgText);
    return hash;
}

static bool tdeckShouldRedraw(uint8_t mode, uint32_t sig) {
    if (!tdeckDisplayDirty && mode == tdeckLastRenderMode && sig == tdeckLastRenderSig) {
        return false;
    }
    tdeckDisplayDirty = false;
    tdeckLastRenderMode = mode;
    tdeckLastRenderSig = sig;
    return true;
}
#endif

// Shared header for the non-HOME pages: title left, "2/5" page indicator right
static void drawPageHeader(const char* title, uint8_t page) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.drawStr(0, 10, title);
    char pg[8];
    snprintf(pg, sizeof(pg), "%u/%u", (unsigned)(page + 1), (unsigned)OLED_PAGE_COUNT);
    u8g2.drawStr(128 - (int)u8g2.getStrWidth(pg), 10, pg);
    u8g2.drawHLine(0, 12, 128);
}

// Page 2/5 — GPS detail: source, coordinates, altitude/speed, satellites
void drawGpsPage() {
    u8g2.clearBuffer();
    drawPageHeader("GPS", 1);
    u8g2.setFont(u8g2_font_6x10_tf);
    char line[26];

    bool ownFix = gps.location.isValid();
    bool phoneFix = hasInheritedLocation && (millis() - lastInheritedTime < 300000);
    if (fixedPosition || ownFix || phoneFix) {
        double lat;
        double lon;
        int32_t alt = 0;
        if (fixedPosition) {
            lat = fixedLat;
            lon = fixedLon;
            alt = fixedAlt;
            snprintf(line, sizeof(line), "Src: Fixed Position");
        } else {
            lat = ownFix ? gps.location.lat() : (double)inheritedLat;
            lon = ownFix ? gps.location.lng() : (double)inheritedLon;
            snprintf(line, sizeof(line), "Src: %s", ownFix ? "Onboard GPS" : "Phone GPS");
        }
        u8g2.drawStr(0, 23, line);
        snprintf(line, sizeof(line), "Lat %.5f", lat);
        u8g2.drawStr(0, 34, line);
        snprintf(line, sizeof(line), "Lon %.5f", lon);
        u8g2.drawStr(0, 45, line);
        if (fixedPosition) {
            snprintf(line, sizeof(line), "Alt %dm", (int)alt);
            u8g2.drawStr(0, 56, line);
        } else if (ownFix) {
            // The region setting hints at preferred units: US915 -> mph
            float spd = (nodeRegion == 0) ? gps.speed.mph() : gps.speed.kmph();
            snprintf(line, sizeof(line), "Alt %dm  Spd %.1f%s",
                     (int)gps.altitude.meters(), spd, (nodeRegion == 0) ? "mph" : "km/h");
            u8g2.drawStr(0, 56, line);
            snprintf(line, sizeof(line), "Sats %lu  HDOP %.1f",
                     (unsigned long)gps.satellites.value(), gps.hdop.hdop());
            u8g2.drawStr(0, 64, line);
        } else {
            snprintf(line, sizeof(line), "Phone fix %lus ago",
                     (unsigned long)((millis() - lastInheritedTime) / 1000));
            u8g2.drawStr(0, 56, line);
        }
    } else if (!hasOnboardGps) {
        u8g2.setFont(u8g2_font_7x14_tf);
        u8g2.drawStr(0, 32, "NO ONBOARD GPS");
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(0, 47, "Onboard module absent");
        u8g2.drawStr(0, 58, "Share phone GPS in app");
    } else if (gpsMode != 0) {
        u8g2.setFont(u8g2_font_7x14_tf);
        u8g2.drawStr(0, 32, "GPS DISABLED");
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(0, 47, "Off by config (power)");
        u8g2.drawStr(0, 58, "Uses phone GPS if shared");
    } else {
        u8g2.setFont(u8g2_font_7x14_tf);
        u8g2.drawStr(0, 32, "NO GPS LOCK");
        u8g2.setFont(u8g2_font_6x10_tf);
        snprintf(line, sizeof(line), "Sats in view: %lu", (unsigned long)gps.satellites.value());
        u8g2.drawStr(0, 47, line);
        u8g2.drawStr(0, 58, "Waiting for fix...");
    }
    u8g2.sendBuffer();
}

// Page 3/5 — last received chat messages (newest first, up to 2 shown)
void drawMsgsPage() {
    u8g2.clearBuffer();
    drawPageHeader("MESSAGES", 2);
    if (recentMsgCount == 0) {
        u8g2.setFont(u8g2_font_6x10_tf);
        u8g2.drawStr(0, 36, "No messages yet");
        u8g2.sendBuffer();
        return;
    }
    u8g2.setFont(u8g2_font_5x8_tf);
    uint8_t y = 22;
    uint8_t toShow = (recentMsgCount < 2) ? recentMsgCount : 2;
    for (uint8_t k = 0; k < toShow; k++) {
        const RecentMsg& m = recentMsgs[(recentMsgHead + 4 - 1 - k) % 4];
        char age[8];
        formatAge(millis() - m.atMs, age, sizeof(age));
        char hdr[28];
        snprintf(hdr, sizeof(hdr), "0x%04X  %s ago:", (unsigned)(m.sender & 0xFFFF), age);
        u8g2.drawStr(0, y, hdr);
        y += 8;
        char lineBuf[26];
        strncpy(lineBuf, m.text, 25);
        lineBuf[25] = '\0';
        u8g2.drawStr(4, y, lineBuf);
        y += 8;
        if (strlen(m.text) > 25) {
            strncpy(lineBuf, m.text + 25, 25);
            lineBuf[25] = '\0';
            u8g2.drawStr(4, y, lineBuf);
            y += 8;
        }
        y += 3; // gap between messages
    }
    u8g2.sendBuffer();
}

// Page 4/5 — mesh peers heard in the last 5 minutes (id, signal, age)
void drawNodesPage() {
    u8g2.clearBuffer();
    char title[16];
    snprintf(title, sizeof(title), "NODES %u", (unsigned)countActivePeers());
    drawPageHeader(title, 3);
    u8g2.setFont(u8g2_font_6x10_tf);

    bool used[12] = {};
    uint8_t shown = 0;
    for (uint8_t row = 0; row < 4; row++) {
        int best = -1;
        for (int i = 0; i < 12; i++) {
            if (used[i] || peersSeen[i].id == 0) continue;
            if (millis() - peersSeen[i].lastMs >= PEER_ACTIVE_WINDOW_MS) continue;
            if (best < 0 || peersSeen[i].lastMs > peersSeen[best].lastMs) best = i;
        }
        if (best < 0) break;
        used[best] = true;
        char age[8];
        formatAge(millis() - peersSeen[best].lastMs, age, sizeof(age));
        char line[26];
        snprintf(line, sizeof(line), "%08X %.0fdB %s",
                 (unsigned)peersSeen[best].id, peersSeen[best].rssi, age);
        u8g2.drawStr(0, 23 + row * 11, line);
        shown++;
    }
    if (shown == 0) {
        u8g2.drawStr(0, 34, "No peers heard in");
        u8g2.drawStr(0, 45, "the last 5 minutes");
    }
    u8g2.sendBuffer();
}

// Page 5/5 — system detail: id, role/power/region, battery, heap, version
void drawSysPage() {
    u8g2.clearBuffer();
    drawPageHeader("SYSTEM", 4);
    u8g2.setFont(u8g2_font_6x10_tf);
    char line[28];

    snprintf(line, sizeof(line), "ID 0x%08X", (unsigned)localNodeId);
    u8g2.drawStr(0, 23, line);
    const char* roleName = (nodeRole == 1) ? "Router" : (nodeRole == 2) ? "Repeater" : "Client";
    snprintf(line, sizeof(line), "%s TX %ddBm %s", roleName, (int)loraTxPower,
             (nodeRegion == 0) ? "US915" : "EU868");
    u8g2.drawStr(0, 34, line);
    snprintf(line, sizeof(line), "Batt %u%% %.2fV%s", (unsigned)readBatteryLevel(),
             batteryVoltage, batteryCharging ? " CHG" : "");
    u8g2.drawStr(0, 45, line);
    snprintf(line, sizeof(line), "Heap %luk", (unsigned long)(ESP.getFreeHeap() / 1024));
    u8g2.drawStr(0, 56, line);

    u8g2.setFont(u8g2_font_5x7_tf);
    snprintf(line, sizeof(line), "v%s", AETHERMESH_FW_VERSION);
    u8g2.drawStr(0, 64, line);
    if (positionPrecisionM > 0) {
        char pp[16];
        snprintf(pp, sizeof(pp), "Pos +/-%um", (unsigned)positionPrecisionM);
        u8g2.drawStr(128 - (int)u8g2.getStrWidth(pp), 64, pp);
    }
    u8g2.sendBuffer();
}
#endif

// OTA progress owns the screen while a firmware update streams in; declared
// ahead of its definition further down.
extern bool otaActive;

// Update the OLED display screen
void updateDisplay() {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    if (otaActive) {
        return; // drawOtaProgress() renders during firmware updates
    }
    if (nodeRole == 2) {
#if defined(LILYGO_T_DECK)
        setBacklight(false);
#else
        u8g2.setPowerSave(1); // Put display to sleep/off to save power
        setBacklight(false);
#endif
        displayIsOn = false;
        return;
    }

    unsigned long now = millis();
    bool shouldBeOn = true;
    bool popupActive = hasNewMsgPopup && (now - lastMsgReceivedTime < 10000);
    // Power save caps the screen-on window at 10s, but never overrides "Always Off"
    // (0) and downgrades "Always On" (0xFFFFFFFF) to the 10s window.
    uint32_t activeTimeout = screenTimeoutSecs;
    if (powerSaveMode && activeTimeout > 10) {
        activeTimeout = 10;
    }
    if (activeTimeout == 0) {
        shouldBeOn = false;
    } else if (activeTimeout == 0xFFFFFFFF) {
        shouldBeOn = true;
    } else {
        shouldBeOn = (now - lastDisplayActivityTime < activeTimeout * 1000UL);
    }
    
    if (popupActive) {
        // Always wake screen up for message popup overlay
        shouldBeOn = true;
    }

    if (!shouldBeOn) {
#if defined(LILYGO_T_DECK)
        setBacklight(false);
#else
        u8g2.setPowerSave(1); // Put display to sleep/off to save power
        setBacklight(false);
#endif
        displayIsOn = false;
        return;
    }
    if (!displayIsOn) {
#if !defined(LILYGO_T_DECK)
        oledPage = 0; // waking from off always lands on the HOME page
#endif
    }
#if defined(LILYGO_T_DECK)
    setBacklight(true);
#else
    u8g2.setPowerSave(0); // Ensure awake if not repeater
    setBacklight(true);
#endif
    displayIsOn = true;

#if defined(LILYGO_T_DECK)
    uint8_t tdeckMode = tdeckComposeActive ? 10 : (popupActive ? 11 : oledPage);
    uint32_t tdeckSig;
    if (tdeckComposeActive) {
        tdeckSig = tdeckComposeRenderSignature();
    } else if (popupActive) {
        tdeckSig = tdeckPopupRenderSignature();
    } else if (oledPage == 0) {
        tdeckSig = tdeckHomeRenderSignature(now);
    } else {
        tdeckSig = (uint32_t)oledPage << 24;
    }
    if (!tdeckShouldRedraw(tdeckMode, tdeckSig)) {
        return;
    }

    if (tdeckComposeActive) {
        drawTDeckCompose();
        return;
    }
#endif
    
    // Check if we should render message popup overlay
    if (popupActive) {
#if defined(LILYGO_T_DECK)
        drawTDeckMessagePopup();
        return;
#endif
        u8g2.clearBuffer();
        
        // Draw double border
        u8g2.drawFrame(0, 0, 128, 64);
        u8g2.drawFrame(2, 2, 124, 60);
        
        // Header
        u8g2.setFont(u8g2_font_7x14_tf); // Slightly larger font for header
        u8g2.drawStr(12, 18, "NEW MESSAGE");
        u8g2.drawHLine(4, 22, 120);
        
        // Sender Info
        u8g2.setFont(u8g2_font_6x10_tf);
        char senderStr[32];
        snprintf(senderStr, sizeof(senderStr), "From: 0x%08X", lastMsgSender);
        u8g2.drawStr(10, 35, senderStr);
        
        // Message Content (first 20 chars on line 1, next 20 on line 2)
        char line1[21];
        char line2[21];
        
        strncpy(line1, lastMsgText, 20);
        line1[20] = '\0';
        
        if (strlen(lastMsgText) > 20) {
            strncpy(line2, lastMsgText + 20, 20);
            line2[20] = '\0';
        } else {
            line2[0] = '\0';
        }
        
        u8g2.drawStr(10, 48, line1);
        if (line2[0] != '\0') {
            u8g2.drawStr(10, 58, line2);
        }
        
        u8g2.sendBuffer();
        return;
    } else {
        hasNewMsgPopup = false;
    }

#if defined(LILYGO_T_DECK)
    switch (oledPage) {
        case 1: drawTDeckGpsPage(); return;
        case 2: drawTDeckMessagesPage(); return;
        case 3: drawTDeckNodesPage(); return;
        case 4: drawTDeckSystemPage(); return;
        default: drawTDeckHome(); return;
    }
#endif

    switch (oledPage) {
        case 1: drawGpsPage(); return;
        case 2: drawMsgsPage(); return;
        case 3: drawNodesPage(); return;
        case 4: drawSysPage(); return;
        default: break; // HOME below
    }

    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_6x10_tf);

    // Header: name + short id (left), battery icon w/ charge bolt (right).
    // The old layout printed the name twice (header AND a "Name:" line).
    char nameHeader[24];
    if (strlen(nodeCustomName) > 0) {
        snprintf(nameHeader, sizeof(nameHeader), "%.9s 0x%04X", nodeCustomName, (unsigned int)(localNodeId & 0xFFFF));
    } else {
        snprintf(nameHeader, sizeof(nameHeader), "Node 0x%08X", localNodeId);
    }
    u8g2.drawStr(0, 10, nameHeader);
    drawBatteryIcon(105, 1, readBatteryLevel(), batteryCharging);
    u8g2.drawHLine(0, 12, 128);

    // Radio config (compare across nodes: SF / bandwidth / frequency)
    char cfgStr[32];
    snprintf(cfgStr, sizeof(cfgStr), "SF%u BW%d %.1fMHz",
             (unsigned)radioMgr.getSpreadingFactor(), (int)radioMgr.getBandwidth(),
             radioMgr.getFrequency());
    u8g2.drawStr(0, 23, cfgStr);

    // BLE state (left) + last received signal (right)
    u8g2.drawStr(0, 34, bleMgr.isDeviceConnected() ? "BLE Connected" : "BLE Advertising");
    float lastRssi = radioMgr.getLastRssi();
    if (lastRssi != 0.0f) {
        char rssiStr[12];
        snprintf(rssiStr, sizeof(rssiStr), "%.0fdB", lastRssi);
        u8g2.drawStr(128 - (int)u8g2.getStrWidth(rssiStr), 34, rssiStr);
    }

    // Traffic + how many mesh peers were heard in the last 5 minutes
    char statsStr[32];
    snprintf(statsStr, sizeof(statsStr), "RX %u TX %u Nodes %u", rxPacketCount, txPacketCount, countActivePeers());
    u8g2.drawStr(0, 45, statsStr);

    // GPS Status line
    char gpsStr[32];
    if (gps.location.isValid()) {
        snprintf(gpsStr, sizeof(gpsStr), "GPS %.4f,%.4f", gps.location.lat(), gps.location.lng());
    } else if (hasInheritedLocation && (millis() - lastInheritedTime < 300000)) {
        snprintf(gpsStr, sizeof(gpsStr), "PhGPS %.4f,%.4f", inheritedLat, inheritedLon);
    } else {
        snprintf(gpsStr, sizeof(gpsStr), "GPS No Lock (%lu sat)", (unsigned long)gps.satellites.value());
    }
    u8g2.drawStr(0, 56, gpsStr);

    // Footer: uptime (left) + firmware version (right), small font
    u8g2.setFont(u8g2_font_5x7_tf);
    uint32_t upSec = millis() / 1000UL;
    char upStr[16];
    if (upSec < 3600) {
        snprintf(upStr, sizeof(upStr), "Up %lum", (unsigned long)(upSec / 60));
    } else if (upSec < 86400) {
        snprintf(upStr, sizeof(upStr), "Up %luh%02lum", (unsigned long)(upSec / 3600), (unsigned long)((upSec % 3600) / 60));
    } else {
        snprintf(upStr, sizeof(upStr), "Up %lud%luh", (unsigned long)(upSec / 86400), (unsigned long)((upSec % 86400) / 3600));
    }
    u8g2.drawStr(0, 64, upStr);
    char verStr[24];
    snprintf(verStr, sizeof(verStr), "v%s", AETHERMESH_FW_VERSION);
    u8g2.drawStr(128 - (int)u8g2.getStrWidth(verStr), 64, verStr);

    u8g2.sendBuffer();
#endif
}

#if defined(LILYGO_T_DECK)
static bool tdeckKeyboardProbe() {
    Wire.beginTransmission(TDECK_KEYBOARD_ADDR);
    return Wire.endTransmission() == 0;
}

static void tdeckKeyboardCommand(uint8_t cmd) {
    Wire.beginTransmission(TDECK_KEYBOARD_ADDR);
    Wire.write(cmd);
    Wire.endTransmission();
}

static void tdeckSetKeyboardBrightness(uint8_t level) {
    Wire.beginTransmission(TDECK_KEYBOARD_ADDR);
    Wire.write(TDECK_KB_BRIGHTNESS_CMD);
    Wire.write(level);
    Wire.endTransmission();
}

static void initTDeckKeyboard() {
    Wire.begin(TDECK_KEYBOARD_SDA, TDECK_KEYBOARD_SCL);
    pinMode(TDECK_KEYBOARD_INT, INPUT_PULLUP);

    tdeckKeyboardPresent = tdeckKeyboardProbe();
    if (tdeckKeyboardPresent) {
        tdeckKeyboardCommand(TDECK_KB_MODE_KEY_CMD);
        tdeckSetKeyboardBrightness(48);
        strncpy(tdeckKeyboardStatus, "KB READY", sizeof(tdeckKeyboardStatus) - 1);
        tdeckKeyboardStatus[sizeof(tdeckKeyboardStatus) - 1] = '\0';
        tdeckDisplayDirty = true;
        Serial.println("T-Deck keyboard detected.");
    } else {
        strncpy(tdeckKeyboardStatus, "KB OFFLINE", sizeof(tdeckKeyboardStatus) - 1);
        tdeckKeyboardStatus[sizeof(tdeckKeyboardStatus) - 1] = '\0';
        tdeckDisplayDirty = true;
        Serial.println("T-Deck keyboard not detected.");
    }
}

static int tdeckReadKey() {
    static uint32_t lastProbeMs = 0;
    uint32_t now = millis();
    if (!tdeckKeyboardPresent && (now - lastProbeMs > 2500)) {
        lastProbeMs = now;
        tdeckKeyboardPresent = tdeckKeyboardProbe();
        if (tdeckKeyboardPresent) {
            tdeckKeyboardCommand(TDECK_KB_MODE_KEY_CMD);
            tdeckSetKeyboardBrightness(48);
            strncpy(tdeckKeyboardStatus, "KB READY", sizeof(tdeckKeyboardStatus) - 1);
            tdeckKeyboardStatus[sizeof(tdeckKeyboardStatus) - 1] = '\0';
            tdeckDisplayDirty = true;
        }
    }
    if (!tdeckKeyboardPresent) {
        return -1;
    }

    Wire.beginTransmission(TDECK_KEYBOARD_ADDR);
    if (Wire.endTransmission() != 0) {
        tdeckKeyboardPresent = false;
        strncpy(tdeckKeyboardStatus, "KB OFFLINE", sizeof(tdeckKeyboardStatus) - 1);
        tdeckKeyboardStatus[sizeof(tdeckKeyboardStatus) - 1] = '\0';
        tdeckDisplayDirty = true;
        return -1;
    }

    if (Wire.requestFrom((int)TDECK_KEYBOARD_ADDR, 1) != 1) {
        return -1;
    }
    return Wire.read();
}

static void tdeckSetNotice(const char* text, uint32_t durationMs = 2500) {
    strncpy(tdeckKeyboardNotice, text, sizeof(tdeckKeyboardNotice) - 1);
    tdeckKeyboardNotice[sizeof(tdeckKeyboardNotice) - 1] = '\0';
    tdeckKeyboardNoticeUntil = millis() + durationMs;
    tdeckDisplayDirty = true;
}

static void tdeckStoreRecentOutgoing(const char* text) {
    RecentMsg& slot = recentMsgs[recentMsgHead];
    slot.sender = localNodeId;
    size_t out = 0;
    for (size_t i = 0; text && text[i] != '\0' && out < sizeof(slot.text) - 1; i++) {
        char c = text[i];
        slot.text[out++] = (c >= 32 && c <= 126) ? c : '?';
    }
    slot.text[out] = '\0';
    slot.atMs = millis();
    recentMsgHead = (recentMsgHead + 1) % 4;
    if (recentMsgCount < 4) recentMsgCount++;
}

static void tdeckBeginCompose() {
    tdeckComposeActive = true;
    tdeckComposeFrameDrawn = false;
    hasNewMsgPopup = false;
    oledPage = 0;
}

static void tdeckCancelCompose() {
    tdeckComposeActive = false;
    tdeckComposeFrameDrawn = false;
    tdeckComposeLen = 0;
    tdeckComposeText[0] = '\0';
    tdeckSetNotice("CANCELLED");
}

static void tdeckSendCompose() {
    if (tdeckComposeLen == 0) {
        tdeckCancelCompose();
        return;
    }

    bool sent = router.sendText(0xFFFFFFFF, tdeckComposeText);
    if (sent) {
        tdeckStoreRecentOutgoing(tdeckComposeText);
        tdeckSetNotice("SENT");
    } else {
        tdeckSetNotice("SEND FAILED", 4000);
    }
    tdeckComposeActive = false;
    tdeckComposeFrameDrawn = false;
    tdeckComposeLen = 0;
    tdeckComposeText[0] = '\0';
}

static void tdeckBackspaceCompose() {
    if (tdeckComposeLen > 0) {
        tdeckComposeLen--;
        tdeckComposeText[tdeckComposeLen] = '\0';
    } else {
        tdeckCancelCompose();
    }
}

static void tdeckAppendCompose(char c) {
    if (tdeckComposeLen >= TDECK_COMPOSE_MAX) {
        tdeckSetNotice("MESSAGE FULL");
        return;
    }
    tdeckComposeText[tdeckComposeLen++] = c;
    tdeckComposeText[tdeckComposeLen] = '\0';
}

static bool tdeckHandleControlKey(uint8_t key) {
    if (key == '\r' || key == '\n') {
        if (tdeckComposeActive) {
            tdeckSendCompose();
        } else {
            tdeckBeginCompose();
        }
        return true;
    }
    if (key == 0x08 || key == 0x7F) {
        if (tdeckComposeActive) {
            tdeckBackspaceCompose();
        } else if (oledPage > 0) {
            oledPage--;
        }
        return true;
    }
    if (key == 0x1B) {
        if (tdeckComposeActive) {
            tdeckCancelCompose();
        } else if (hasNewMsgPopup) {
            hasNewMsgPopup = false;
        }
        return true;
    }
    if (key == '\t' && !tdeckComposeActive) {
        oledPage = (oledPage + 1) % OLED_PAGE_COUNT;
        return true;
    }
    return false;
}

static void handleTDeckKey(uint8_t key) {
    if (key == 0 || key == 0xFF) {
        return;
    }

    lastDisplayActivityTime = millis();

    if (tdeckHandleControlKey(key)) {
        updateDisplay();
        return;
    }

    if (key >= 32 && key <= 126) {
        if (!tdeckComposeActive) {
            tdeckBeginCompose();
        }
        tdeckAppendCompose((char)key);
        updateDisplay();
    }
}

static void pollTDeckKeyboard() {
    static int heldKey = -1;
    static uint32_t lastKeyMs = 0;

    int key = tdeckReadKey();
    uint32_t now = millis();
    if (key <= 0) {
        heldKey = -1;
        return;
    }

    if (key == 0xFF) {
        heldKey = -1;
        return;
    }

    if (key == heldKey) {
        return;
    }

    if (now - lastKeyMs < 35) {
        return;
    }

    heldKey = key;
    lastKeyMs = now;
    handleTDeckKey((uint8_t)key);
}
#endif

// Callback: LoRa -> Phone / Router
void onLoRaPacketReceived(uint8_t* data, size_t len, float rssi, float snr) {
    rxPacketCount++;

    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    pb_istream_t stream = pb_istream_from_buffer(data, len);
    bool decodeSuccess = pb_decode(&stream, aethermesh_MeshPacket_fields, &packet);

    if (decodeSuccess) {
        notePeerHeard(packet.sender_id, rssi);
        if (packet.which_payload == aethermesh_MeshPacket_telemetry_tag) {
            Serial.printf("{\"event\":\"telemetry\",\"node_id\":%u,\"battery\":%u,\"lat\":%.6f,\"lon\":%.6f,\"rssi\":%.1f,\"snr\":%.1f,\"model\":\"%s\",\"uptime\":%u,\"fw\":\"%s\"}\n",
                          packet.sender_id,
                          packet.payload.telemetry.battery_level,
                          packet.payload.telemetry.latitude,
                          packet.payload.telemetry.longitude,
                          rssi,
                          snr,
                          packet.payload.telemetry.node_model,
                          packet.payload.telemetry.uptime_seconds,
                          packet.payload.telemetry.firmware_version);
        }
    }

    // 1. Forward to connected phone via BLE if authenticated.
    // Skip mesh rebroadcast duplicates and reflections of our own packets, otherwise
    // the app stores the same chat message once per relay that repeats it. ACKs are
    // control events though: the app's range-test counter depends on seeing them.
    bool isAckPacket = decodeSuccess && packet.which_payload == aethermesh_MeshPacket_ack_tag;
    bool isPongPacket = decodeSuccess &&
                        packet.which_payload == aethermesh_MeshPacket_text_tag &&
                        strncmp(packet.payload.text.content, "PONG_", 5) == 0;
    bool isDuplicate = decodeSuccess &&
                       (packet.sender_id == localNodeId ||
                        ((!isAckPacket && !isPongPacket) && router.hasSeen(packet.sender_id, packet.packet_id)));
    if (bleMgr.isDeviceConnected() && isBleClientAuthenticated && !isDuplicate) {
        if (decodeSuccess) {
            if (isPongPacket) {
                Serial.printf("Range-test PONG received, forwarding to phone: %s\n",
                              packet.payload.text.content);
            }
            packet.rx_rssi = rssi;
            packet.rx_snr = snr;

            uint8_t buffer[256];
            pb_ostream_t outStream = pb_ostream_from_buffer(buffer, sizeof(buffer));
            if (pb_encode(&outStream, aethermesh_MeshPacket_fields, &packet)) {
                bleMgr.sendToPhone(buffer, outStream.bytes_written);
            }
        } else {
            // Fallback: forward raw bytes if decode fails
            bleMgr.sendToPhone(data, len);
        }
    } else if (decodeSuccess && isPongPacket && bleMgr.isDeviceConnected() && !isBleClientAuthenticated) {
        Serial.println("PONG received but BLE client not authenticated; not forwarding to phone.");
    }
    
    // 2. Feed into router for routing processing (unicast/broadcast relays, ACKs, etc)
    router.processIncomingPacket(data, len, rssi, snr);

    // Refresh the display content, but do NOT count radio traffic as display
    // activity — the mesh beacons every few seconds, which would keep resetting
    // the screen timeout and the screen would never sleep. Only the user button
    // and the new-message popup wake the display.
    updateDisplay();
}

void onLoRaPacketTransmitted() {
    txPacketCount++;
    updateDisplay();
}

// Send Authentication response to the BLE client
void sendAuthResponse(bool success, const char* message, bool passwordNotSet) {
    aethermesh_MeshPacket response = aethermesh_MeshPacket_init_zero;
    response.sender_id = localNodeId;
    response.recipient_id = 0; // Local client
    response.packet_id = random(1, 100000);
    response.which_payload = aethermesh_MeshPacket_auth_response_tag;
    response.payload.auth_response.success = success;
    strncpy(response.payload.auth_response.message, message, sizeof(response.payload.auth_response.message) - 1);
    response.payload.auth_response.message[sizeof(response.payload.auth_response.message) - 1] = '\0';
    response.payload.auth_response.password_not_set = passwordNotSet;

    uint8_t buffer[128];
    pb_ostream_t stream = pb_ostream_from_buffer(buffer, sizeof(buffer));
    if (pb_encode(&stream, aethermesh_MeshPacket_fields, &response)) {
        bleMgr.sendToPhone(buffer, stream.bytes_written);
    }
}

void sendDeliveryStatusToPhone(uint32_t packetId, uint32_t recipientId, aethermesh_DeliveryStatus_State state, aethermesh_DeliveryStatus_Reason reason, uint32_t retryCount, float ackRssi, float ackSnr) {
    if (!bleMgr.isDeviceConnected() || !isBleClientAuthenticated) {
        return;
    }

    aethermesh_MeshPacket statusPacket = aethermesh_MeshPacket_init_zero;
    statusPacket.sender_id = localNodeId;
    statusPacket.recipient_id = 0; // Local BLE client
    statusPacket.packet_id = random(1, 100000);
    statusPacket.hop_limit = 1;
    statusPacket.want_ack = false;
    statusPacket.prev_hop_id = localNodeId;
    statusPacket.rx_rssi = ackRssi;
    statusPacket.rx_snr = ackSnr;
    statusPacket.which_payload = aethermesh_MeshPacket_delivery_status_tag;
    statusPacket.payload.delivery_status.packet_id = packetId;
    statusPacket.payload.delivery_status.recipient_id = recipientId;
    statusPacket.payload.delivery_status.state = state;
    statusPacket.payload.delivery_status.reason = reason;
    statusPacket.payload.delivery_status.retry_count = retryCount;

    uint8_t buffer[96];
    pb_ostream_t stream = pb_ostream_from_buffer(buffer, sizeof(buffer));
    if (pb_encode(&stream, aethermesh_MeshPacket_fields, &statusPacket)) {
        bleMgr.sendToPhone(buffer, stream.bytes_written);
    } else {
        Serial.println("Failed to encode delivery status for BLE.");
    }
}

// --- BLE firmware update (OTA). Heltec/ESP32 only: chunks stream over the
// authenticated BLE link into the inactive OTA app partition (dual-slot
// default_16MB layout), MD5-verified before reboot. Windowed flow control:
// the phone sends OTA_WINDOW chunks then waits for our IN_PROGRESS ack.
bool otaActive = false;
uint32_t otaExpectedOffset = 0;
uint32_t otaTotalSize = 0;
uint32_t otaLastChunkMs = 0;
uint32_t otaChunksSinceAck = 0;
static const uint32_t OTA_WINDOW = 8;          // chunks per ack (BLE rx ring holds 16)
static const uint32_t OTA_MAX_CHUNK = 224;     // advertised in READY.next_offset so the
                                               // app can pick fast params; firmware that
                                               // predates this sends 0 -> app uses the
                                               // legacy 192-byte/window-4 profile
static const uint32_t OTA_TIMEOUT_MS = 30000;  // abort if the phone goes silent

// (OTA round-trip test marker #2: distinct hash for a clean confirmation run.)
void sendOtaStatus(aethermesh_OtaStatus_State state, uint32_t nextOffset, const char* msg) {
    if (!bleMgr.isDeviceConnected()) return;
    aethermesh_MeshPacket pkt = aethermesh_MeshPacket_init_zero;
    pkt.sender_id = localNodeId;
    pkt.recipient_id = 0;
    pkt.packet_id = 0;
    pkt.which_payload = aethermesh_MeshPacket_ota_status_tag;
    pkt.payload.ota_status.state = state;
    pkt.payload.ota_status.next_offset = nextOffset;
    if (msg) {
        strncpy(pkt.payload.ota_status.message, msg, sizeof(pkt.payload.ota_status.message) - 1);
    }
    uint8_t buf[128];
    pb_ostream_t stream = pb_ostream_from_buffer(buf, sizeof(buf));
    if (pb_encode(&stream, aethermesh_MeshPacket_fields, &pkt)) {
        bleMgr.sendToPhone(buf, stream.bytes_written);
    }
}

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
void drawOtaProgress(uint8_t pct, const char* label) {
#if defined(LILYGO_T_DECK)
    drawTDeckOtaProgress(pct, label);
    return;
#endif
    u8g2.setPowerSave(0);
    setBacklight(true);
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_7x14_tf);
    u8g2.drawStr((128 - u8g2.getStrWidth("FIRMWARE UPDATE")) / 2, 20, "FIRMWARE UPDATE");
    u8g2.drawFrame(14, 30, 100, 12);
    u8g2.drawBox(16, 32, (uint8_t)((uint16_t)pct * 96 / 100), 8);
    u8g2.setFont(u8g2_font_6x10_tf);
    char pctStr[24];
    snprintf(pctStr, sizeof(pctStr), "%u%%  %s", pct, label ? label : "");
    u8g2.drawStr((128 - u8g2.getStrWidth(pctStr)) / 2, 56, pctStr);
    u8g2.sendBuffer();
}
#endif

void otaAbort(const char* reason) {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    if (otaActive) {
        Update.abort();
    }
#endif
    otaActive = false;
    otaExpectedOffset = 0;
    Serial.printf("OTA aborted: %s\n", reason ? reason : "");
    sendOtaStatus(aethermesh_OtaStatus_State_ERROR, otaExpectedOffset, reason);
}

void handleOtaControl(const aethermesh_OtaControl& ctl) {
    // nRF52/RAK path: firmware updates go through the Adafruit/Nordic DFU
    // bootloader, not this application code. ENTER_DFU reboots into the
    // bootloader; the phone (Nordic DFU library) streams the .zip package to
    // it directly. If the transfer never starts, the bootloader times out
    // back into the current app - nothing is lost.
    if (ctl.op == aethermesh_OtaControl_Op_ENTER_DFU) {
#if defined(RAK4631) || defined(RAK3401_1W)
        Serial.println("Rebooting into OTA DFU bootloader (phone streams the update)...");
        sendOtaStatus(aethermesh_OtaStatus_State_READY, 0, "Entering DFU bootloader");
        delay(600); // let the BLE notify flush before we tear the stack down
        enterOTADfu();
#else
        sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "ENTER_DFU is for RAK/nRF52 boards");
#endif
        return;
    }

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    switch (ctl.op) {
        case aethermesh_OtaControl_Op_BEGIN: {
            if (otaActive) {
                Update.abort();
                otaActive = false;
            }
            if (ctl.total_size == 0) {
                sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "Zero image size");
                return;
            }
            if (!Update.begin(ctl.total_size, U_FLASH)) {
                sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, Update.errorString());
                return;
            }
            if (ctl.md5[0] != '\0') {
                Update.setMD5(ctl.md5);
            }
            otaActive = true;
            otaExpectedOffset = 0;
            otaTotalSize = ctl.total_size;
            otaLastChunkMs = millis();
            otaChunksSinceAck = 0;
            Serial.printf("OTA begin: %u bytes, md5=%s\n", ctl.total_size, ctl.md5);
            drawOtaProgress(0, "receiving");
            // READY.next_offset advertises our max chunk size (capability hint)
            sendOtaStatus(aethermesh_OtaStatus_State_READY, OTA_MAX_CHUNK, "");
            break;
        }
        case aethermesh_OtaControl_Op_END: {
            if (!otaActive) {
                sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "No OTA in progress");
                return;
            }
            if (otaExpectedOffset != otaTotalSize) {
                otaAbort("Incomplete image at END");
                return;
            }
            if (Update.end(true)) {
                otaActive = false;
                Serial.println("OTA success. Rebooting into new firmware...");
                drawOtaProgress(100, "rebooting");
                sendOtaStatus(aethermesh_OtaStatus_State_SUCCESS, otaTotalSize, "Rebooting");
                delay(800); // let the notify flush
                ESP.restart();
            } else {
                otaAbort(Update.errorString());
            }
            break;
        }
        case aethermesh_OtaControl_Op_ABORT:
        default:
            otaAbort("Cancelled by phone");
            break;
    }
#else
    (void)ctl;
    sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "Use DFU mode on this board");
#endif
}

void handleOtaData(const aethermesh_OtaData& od) {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    if (!otaActive) {
        sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "No OTA in progress");
        return;
    }
    if (od.offset != otaExpectedOffset) {
        char msg[40];
        snprintf(msg, sizeof(msg), "Offset gap at %u", (unsigned)od.offset);
        otaAbort(msg);
        return;
    }
    size_t written = Update.write((uint8_t*)od.data.bytes, od.data.size);
    if (written != od.data.size) {
        otaAbort(Update.errorString());
        return;
    }
    otaExpectedOffset += od.data.size;
    otaLastChunkMs = millis();

    if (++otaChunksSinceAck >= OTA_WINDOW || otaExpectedOffset == otaTotalSize) {
        otaChunksSinceAck = 0;
        sendOtaStatus(aethermesh_OtaStatus_State_IN_PROGRESS, otaExpectedOffset, "");
    }

    // OLED progress roughly every 2%
    static uint8_t lastDrawnPct = 255;
    uint8_t pct = (uint8_t)((uint64_t)otaExpectedOffset * 100 / otaTotalSize);
    if (pct != lastDrawnPct && (pct % 2 == 0)) {
        lastDrawnPct = pct;
        drawOtaProgress(pct, "receiving");
    }
#else
    (void)od;
    sendOtaStatus(aethermesh_OtaStatus_State_ERROR, 0, "OTA not supported on this board");
#endif
}

// Callback: Phone BLE -> LoRa / Router
void onBlePacketReceived(uint8_t* data, size_t len) {
    Serial.print("BLE Packet received from Phone. Size: ");
    Serial.println(len);
    
    // Deserialize packet
    aethermesh_MeshPacket packet = aethermesh_MeshPacket_init_zero;
    pb_istream_t stream = pb_istream_from_buffer(data, len);
    
    if (pb_decode(&stream, aethermesh_MeshPacket_fields, &packet)) {
        // Enforce authentication
        if (!isBleClientAuthenticated) {
            if (packet.which_payload == aethermesh_MeshPacket_auth_request_tag) {
                bool hasPassword = (strlen(nodePassword) > 0);
                bool isRequestPasswordEmpty = (strlen(packet.payload.auth_request.password) == 0);

                // Brute-force lockout window (status queries still allowed)
                if (!isRequestPasswordEmpty && (int32_t)(millis() - authLockoutUntil) < 0) {
                    Serial.println("Auth attempt rejected: lockout active.");
                    sendAuthResponse(false, "Too many attempts; wait 30s", false);
                    return;
                }

                if (isRequestPasswordEmpty) {
                    // It's a query for password status
                    Serial.println("Received empty password query. Replying status.");
                    sendAuthResponse(false, "Password required", !hasPassword);
                } else if (!hasPassword) {
                    // First connect: set the password
                    strncpy(nodePassword, packet.payload.auth_request.password, sizeof(nodePassword) - 1);
                    nodePassword[sizeof(nodePassword) - 1] = '\0';
                    saveSettings(nodeCustomName, loraSF, loraBW, loraTxPower, nodeRegion, nodePassword, nodeRole, telemetryIntervalSec, screenTimeoutSecs, powerSaveMode);
                    isBleClientAuthenticated = true;
                    Serial.println("Initial device password set successfully.");
                    sendAuthResponse(true, "Password set successfully", false);
                } else {
                    // Verify the password
                    if (strcmp(packet.payload.auth_request.password, nodePassword) == 0) {
                        isBleClientAuthenticated = true;
                        failedAuthAttempts = 0;
                        Serial.println("BLE client authenticated successfully.");
                        sendAuthResponse(true, "Authenticated successfully", false);
                    } else {
                        failedAuthAttempts++;
                        Serial.printf("BLE client authentication failed (attempt %u).\n", failedAuthAttempts);
                        if (failedAuthAttempts >= 5) {
                            failedAuthAttempts = 0;
                            authLockoutUntil = millis() + 30000;
                            Serial.println("Too many failed auth attempts. Locking out for 30s.");
                            sendAuthResponse(false, "Too many attempts; wait 30s", false);
                        } else {
                            sendAuthResponse(false, "Incorrect password", false);
                        }
                    }
                }
            } else {
                Serial.println("BLE packet rejected: Client not authenticated.");
                sendAuthResponse(false, "Authentication required", strlen(nodePassword) == 0);
            }
            return;
        }

        // Handle password change request when already authenticated
        if (packet.which_payload == aethermesh_MeshPacket_auth_request_tag && packet.payload.auth_request.is_change_password) {
            if (strcmp(packet.payload.auth_request.password, nodePassword) == 0) {
                strncpy(nodePassword, packet.payload.auth_request.new_password, sizeof(nodePassword) - 1);
                nodePassword[sizeof(nodePassword) - 1] = '\0';
                saveSettings(nodeCustomName, loraSF, loraBW, loraTxPower, nodeRegion, nodePassword, nodeRole, telemetryIntervalSec, screenTimeoutSecs, powerSaveMode);
                Serial.println("Device password updated successfully.");
                sendAuthResponse(true, "Password changed successfully", false);
            } else {
                Serial.println("Device password update failed: Incorrect current password.");
                sendAuthResponse(false, "Incorrect current password", false);
            }
            return;
        }

        // Firmware update stream (BLE-only, requires the authenticated session
        // established above; never forwarded to LoRa)
        if (packet.which_payload == aethermesh_MeshPacket_ota_control_tag) {
            handleOtaControl(packet.payload.ota_control);
            return;
        }
        if (packet.which_payload == aethermesh_MeshPacket_ota_data_tag) {
            handleOtaData(packet.payload.ota_data);
            return;
        }

        // Intercept NodeConfig settings ONLY when addressed to this node. A config
        // aimed at a different node (remote config) must fall through to the LoRa
        // send path below, not reconfigure/reboot the phone's own connected node.
        bool configForLocal = (packet.recipient_id == localNodeId ||
                               packet.recipient_id == 0);
        if (packet.which_payload == aethermesh_MeshPacket_config_tag && configForLocal) {
            Serial.println("Received local NodeConfig packet from phone via BLE.");

            positionPrecisionM = packet.payload.config.position_precision;
            gpsMode = packet.payload.config.gps_mode;
            fixedPosition = packet.payload.config.fixed_position;
            fixedLat = packet.payload.config.fixed_latitude;
            fixedLon = packet.payload.config.fixed_longitude;
            fixedAlt = packet.payload.config.fixed_altitude;

            // Save to NVS
            saveSettings(
                packet.payload.config.node_name,
                packet.payload.config.lora_sf,
                packet.payload.config.lora_bw,
                packet.payload.config.lora_tx_power,
                packet.payload.config.region,
                nodePassword,
                packet.payload.config.node_role,
                packet.payload.config.telemetry_interval,
                packet.payload.config.screen_timeout_secs,
                packet.payload.config.power_save_mode
            );
            
            Serial.println("Applying settings and restarting MCU in 1.5 seconds...");
            delay(1500);
#ifdef ESP32
            ESP.restart();
#else
            sd_nvic_SystemReset();
#endif
            return;
        }

        // Intercept Telemetry packet from phone (used to share phone's GPS with the node)
        if (packet.which_payload == aethermesh_MeshPacket_telemetry_tag) {
            Serial.println("Received Telemetry packet from phone via BLE.");
            if (!gps.location.isValid()) {
                inheritedLat = packet.payload.telemetry.latitude;
                inheritedLon = packet.payload.telemetry.longitude;
                hasInheritedLocation = true;
                lastInheritedTime = millis();
                Serial.printf("Inherited GPS from Phone: Lat=%.6f, Lon=%.6f\n", inheritedLat, inheritedLon);
            }
            return;
        }

        // Ensure correct sender ID
        packet.sender_id = localNodeId;
        packet.prev_hop_id = localNodeId;

        Serial.print("Sending phone packet over LoRa mesh. Recipient: 0x");
        Serial.println(packet.recipient_id, HEX);
        
        bool isRangePing = packet.which_payload == aethermesh_MeshPacket_text_tag &&
                           strncmp(packet.payload.text.content, "PING_", 5) == 0;
        router.sendRawPacket(&packet, isRangePing);
        
        updateDisplay();
    } else {
        Serial.println("Error decoding BLE packet protobuf.");
    }
}

void onReceivedConfig(uint32_t senderId, const aethermesh_NodeConfig& config) {
    Serial.print("Received RemoteConfig packet over LoRa from 0x");
    Serial.println(senderId, HEX);

    if (nodePassword[0] != '\0' && config.config_password[0] != '\0' &&
        strcmp(config.config_password, nodePassword) == 0) {
        Serial.println("Remote password verified successfully. Applying configuration...");

        positionPrecisionM = config.position_precision;
        gpsMode = config.gps_mode;
        fixedPosition = config.fixed_position;
        fixedLat = config.fixed_latitude;
        fixedLon = config.fixed_longitude;
        fixedAlt = config.fixed_altitude;

        saveSettings(
            config.node_name,
            config.lora_sf,
            config.lora_bw,
            config.lora_tx_power,
            config.region,
            nodePassword,
            config.node_role,
            config.telemetry_interval,
            config.screen_timeout_secs,
            config.power_save_mode
        );

        Serial.println("Remote settings applied. Restarting MCU in 1.5 seconds...");
        delay(1500);
#ifdef ESP32
        ESP.restart();
#else
        sd_nvic_SystemReset();
#endif
    } else {
        Serial.println("Remote config rejected: Incorrect or missing password.");
    }
}

// Callback: Router -> Local output (e.g. debugging print)
void onReceivedTextMessage(uint32_t senderId, const char* text) {
    Serial.print("Local text message consumed from 0x");
    Serial.print(senderId, HEX);
    Serial.print(": ");
    Serial.println(text);

    // Range-test control traffic: don't pop the OLED message overlay for every
    // ping (they arrive every few seconds and would pin the display awake).
    if (text && (strncmp(text, "PING_", 5) == 0 || strncmp(text, "PONG_", 5) == 0)) {
        return;
    }

    // Store message info for OLED popup
    lastMsgSender = senderId;
    size_t out = 0;
    for (size_t i = 0; text && text[i] != '\0' && out < sizeof(lastMsgText) - 1; i++) {
        char c = text[i];
        lastMsgText[out++] = (c >= 32 && c <= 126) ? c : '?';
    }
    lastMsgText[out] = '\0';
    lastMsgReceivedTime = millis();
    hasNewMsgPopup = true;

    // Also keep it in the recent-messages ring for the MESSAGES screen page
    RecentMsg& slot = recentMsgs[recentMsgHead];
    slot.sender = senderId;
    strncpy(slot.text, lastMsgText, sizeof(slot.text) - 1);
    slot.text[sizeof(slot.text) - 1] = '\0';
    slot.atMs = millis();
    recentMsgHead = (recentMsgHead + 1) % 4;
    if (recentMsgCount < 4) recentMsgCount++;

    updateDisplay();
}

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
#define USER_BUTTON_PIN 0
#elif defined(PIN_BUTTON1)
#define USER_BUTTON_PIN PIN_BUTTON1
#elif defined(BUTTON_PIN1)
#define USER_BUTTON_PIN BUTTON_PIN1
#else
#define USER_BUTTON_PIN -1
#endif

void setup() {
#if defined(LILYGO_T_DECK)
    // Power on board peripherals and screen backlight on boot (pins 10, 42, 4, 6).
    // GPIO 45 is LoRa DIO1 on the T-Deck; leave it under RadioManager control.
    pinMode(10, OUTPUT);
    digitalWrite(10, HIGH);
    pinMode(42, OUTPUT);
    digitalWrite(42, HIGH);
    pinMode(4, OUTPUT);
    digitalWrite(4, HIGH);
    pinMode(6, OUTPUT);
    digitalWrite(6, HIGH); // Release screen reset pin
#endif
    Serial.begin(115200);
    // Wait up to 3 seconds for Serial port to open on PC, but don't block forever if running on battery
    uint32_t startWait = millis();
    while (!Serial && (millis() - startWait < 3000)) {
        delay(10);
    }
    
    delay(4000);
    Serial.println("\n=== AETHERMESH NODE STARTING ===");
    Serial.printf("Firmware: %s\n", AETHERMESH_FW_VERSION);
    
    // Check boot button for factory reset before loading settings
#if defined(USER_BUTTON_PIN) && USER_BUTTON_PIN >= 0
    pinMode(USER_BUTTON_PIN, INPUT_PULLUP);
    delay(100); // let pin voltage settle
    if (digitalRead(USER_BUTTON_PIN) == LOW) {
        Serial.println("!!! BOOT BUTTON HELD ON STARTUP - PERFORMING FACTORY RESET !!!");
        nodeCustomName[0] = '\0';
        nodePassword[0] = '\0';
        loraSF = 9;
        loraBW = 125.0f;
#if defined(HELTEC_V4) || defined(HELTEC_V3)
        loraTxPower = 22;
#else
        loraTxPower = 20;
#endif
        nodeRegion = 0;
        nodeRole = 0; // Client role
        saveSettings(nodeCustomName, loraSF, loraBW, loraTxPower, nodeRegion, nodePassword, nodeRole, 60, 30);
        Serial.println("Factory reset settings saved. Restarting...");
        delay(1000);
#ifdef ESP32
        ESP.restart();
#else
        sd_nvic_SystemReset();
#endif
    }
#endif

    // Load Settings from NVS
    loadSettings();
    
    // Initialize LEDs for RAK targets
#if defined(RAK4631) || defined(RAK3401_1W)
    pinMode(LED_GREEN, OUTPUT);
    pinMode(LED_BLUE, OUTPUT);
    digitalWrite(LED_GREEN, LOW);
    digitalWrite(LED_BLUE, LOW);
#endif
    
    // 1. Unique Hardware Identifier
    localNodeId = getHardwareNodeId();
    randomSeed(localNodeId);
    
    // 2. Initialize display if Heltec V4, V3, or LILYGO T-Deck — show the boot splash while the
    // radio/BLE/GPS bring-up below runs
#if defined(LILYGO_T_DECK)
    // Pull board peripheral power enable HIGH (GPIO 10) to power display/keyboard
    pinMode(10, OUTPUT);
    digitalWrite(10, HIGH);
    delay(20);
    // Pull backlight enable pins HIGH (GPIO 42, 4) to support known board revisions.
    // GPIO 45 is LoRa DIO1 and must remain an input.
    pinMode(42, OUTPUT);
    digitalWrite(42, HIGH);
    pinMode(4, OUTPUT);
    digitalWrite(4, HIGH);
    pinMode(6, OUTPUT);
    digitalWrite(6, HIGH); // Release screen reset pin

    initTDeckKeyboard();
    u8g2.begin();
    init_st7789();
    drawBootSplash(0);
    uint32_t splashShownAt = millis();
#elif defined(HELTEC_V4) || defined(HELTEC_V3)
    u8g2.begin();
    drawBootSplash(0);
    uint32_t splashShownAt = millis();
#endif

    // 3. Initialize GPS serial port and power toggle. gps_mode == 1 keeps the
    // GNSS module UNPOWERED (~25% of total draw saved); position then falls
    // back to phone-shared GPS automatically.
#if defined(HELTEC_V4) || defined(HELTEC_V3)
    if (gpsMode == 0) {
        Serial.println("Initializing GNSS Module (Heltec V4)...");
        pinMode(34, OUTPUT);
        digitalWrite(34, LOW);   // Pull GPIO 34 LOW (P-channel MOSFET power enable)

        pinMode(40, OUTPUT);
        digitalWrite(40, HIGH);  // Wakeup pin (Active HIGH)

        pinMode(42, OUTPUT);
        digitalWrite(42, HIGH);  // Reset pin (Active HIGH to release reset)

        delay(50);
        Serial1.begin(9600, SERIAL_8N1, 39, 38); // RX=39, TX=38
    } else {
        Serial.println("GNSS disabled by config (gps_mode=1) - module unpowered.");
        pinMode(34, OUTPUT);
        digitalWrite(34, HIGH);  // P-MOSFET off -> GNSS unpowered
        pinMode(40, OUTPUT);
        digitalWrite(40, LOW);   // Wakeup inactive
        pinMode(42, OUTPUT);
        digitalWrite(42, LOW);   // Hold in reset
    }
#elif defined(LILYGO_T_ECHO)
    if (gpsMode == 0) {
        Serial.println("Initializing GNSS Module (Lilygo T-Echo L76K)...");
        pinMode(37, OUTPUT); // GPS_RESET
        pinMode(34, OUTPUT); // GPS_WAKEUP
        digitalWrite(37, HIGH);
        digitalWrite(34, HIGH);
        delay(100);
        Serial1.begin(9600); // 9600 baud, default pins P1.8/P1.9 via variant
    } else {
        Serial.println("GNSS disabled by config (gps_mode=1) - module unpowered.");
        pinMode(37, OUTPUT);
        pinMode(34, OUTPUT);
        digitalWrite(37, LOW);
        digitalWrite(34, LOW);
    }
#elif defined(RAK4631) || defined(RAK3401_1W)
    // WisBlock GPS (RAK1910 UART, or RAK12500 in UART mode). Serial1 is mapped to
    // the GPS UART by the RAK BSP; WB_IO2 (pin 34) powers the WisBlock sensor slots.
    if (gpsMode == 0) {
        Serial.println("Initializing GNSS Module (RAK WisBlock UART & I2C)...");
        pinMode(34, OUTPUT);
        digitalWrite(34, HIGH);   // WB_IO2 HIGH -> power the sensor slot rail (incl. GPS)
        delay(500);               // give the GNSS module time to boot
        Serial1.begin(9600);      // NMEA @ 9600 baud, BSP-mapped Serial1 pins
        Wire.begin();             // Initialize I2C for ZOE-M8Q (RAK12500 I2C mode)
    } else {
        Serial.println("GNSS disabled by config (gps_mode=1) - sensor rail unpowered.");
        pinMode(34, OUTPUT);
        digitalWrite(34, LOW);    // WB_IO2 LOW -> sensor slot rail off
    }
#endif

    // 4. Initialize Radio Manager
    if (!radioMgr.init()) {
        Serial.println("Failed to initialize Radio Manager. Halted.");
        while (1) {
#if defined(RAK4631) || defined(RAK3401_1W)
            // Rapid double-blink to visually indicate Radio init failure
            digitalWrite(LED_GREEN, HIGH);
            delay(80);
            digitalWrite(LED_GREEN, LOW);
            delay(80);
            digitalWrite(LED_GREEN, HIGH);
            delay(80);
            digitalWrite(LED_GREEN, LOW);
            delay(600);
#else
            delay(1000);
#endif
        }
    }
    
    // Apply NVS radio parameters
    float freq = (nodeRegion == 1) ? 869.525f : 906.875f;
    radioMgr.reinit(freq, loraBW, (uint8_t)loraSF, (int8_t)loraTxPower);
    radioMgr.onReceive(onLoRaPacketReceived);
    radioMgr.onTransmitDone(onLoRaPacketTransmitted);
    
    // 5. Initialize Mesh Router
    router.init(localNodeId);
    router.onReceivedTextMessage(onReceivedTextMessage);
    router.onReceivedConfig(onReceivedConfig);
    router.onDeliveryStatus(sendDeliveryStatusToPhone);
    
    // 6. Initialize BLE Manager with custom name if configured
    if (nodeRole != 2) {
        if (!bleMgr.init(localNodeId, nodeCustomName)) {
            Serial.println("Failed to initialize BLE Manager.");
        }
        bleMgr.onReceivedFromPhone(onBlePacketReceived);
    } else {
        Serial.println("Low-Power Repeater mode: skipping BLE initialization.");
    }
    
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    // Hold the splash ~2.5s total, pulsing the radio waves. The radio is already
    // live at this point (receive callback armed), so nothing is missed.
    uint8_t splashPhase = 1;
    while (millis() - splashShownAt < 2500) {
        delay(300);
        drawBootSplash(splashPhase++);
    }
#endif

    // Start the screen-timeout window from boot so the display shows initially
    lastDisplayActivityTime = millis();
    updateDisplay();
    Serial.println("Setup completed successfully. Ready.");
}

void loop() {
    // Check if onboard GPS is present (run once after GPS_DETECT_TIMEOUT_MS)
    if (!gpsChecked && millis() > GPS_DETECT_TIMEOUT_MS) {
        gpsChecked = true;
        if (gpsMode == 0 && totalGpsBytesReceived == 0) {
            hasOnboardGps = false;
            Serial.println("GPS Check: No serial or I2C bytes received from GNSS module. Concluding onboard GPS is absent.");
#if defined(HELTEC_V4) || defined(HELTEC_V3)
            // Power off the unpopulated GPS divider/power rail to save power
            digitalWrite(34, LOW); 
#elif defined(LILYGO_T_ECHO)
            digitalWrite(34, LOW); 
            digitalWrite(37, LOW); 
#endif
        }
    }

    static uint32_t lastBleActiveTime = millis();
#if defined(LILYGO_T_DECK)
    pollTDeckKeyboard();
#endif
#if defined(USER_BUTTON_PIN) && USER_BUTTON_PIN >= 0
    static bool lastButtonState = HIGH;
    bool currentButtonState = digitalRead(USER_BUTTON_PIN);
    if (currentButtonState == LOW && lastButtonState == HIGH) {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
        // Button behavior: dismiss a visible message popup first; otherwise
        // advance the page carousel while the screen is on. Pressing while the
        // screen is off just wakes it (updateDisplay lands on HOME).
        if (hasNewMsgPopup) {
            hasNewMsgPopup = false;
        } else if (displayIsOn) {
            oledPage = (oledPage + 1) % OLED_PAGE_COUNT;
        }
#endif
        lastDisplayActivityTime = millis();
        if (nodeRole != 2) {
            lastBleActiveTime = millis();
            if (!bleMgr.isAdvertising && !bleMgr.isDeviceConnected()) {
                bleMgr.startAdvertising();
            }
        }
        updateDisplay();
    }
    lastButtonState = currentButtonState;
#endif

    static uint32_t lastDisplayRefresh = 0;
    if (millis() - lastDisplayRefresh > 1000) {
        lastDisplayRefresh = millis();
        updateDisplay();
    }

    // Abort a stalled firmware update (phone app killed / walked away)
    if (otaActive && (millis() - otaLastChunkMs > OTA_TIMEOUT_MS)) {
        otaAbort("Transfer timed out");
    }

    radioMgr.loop();
    router.loop();
    if (nodeRole != 2) {
        if (bleMgr.isAdvertising) {
            bleMgr.loop();
        }

        // Check BLE connection state transitions to reset/manage authentication
        static bool lastBleConnected = false;
        bool currentBleConnected = bleMgr.isDeviceConnected();
        if (currentBleConnected != lastBleConnected) {
            lastBleConnected = currentBleConnected;
            lastBleActiveTime = millis(); // Reset inactive timer on disconnect/connect
            if (currentBleConnected) {
                isBleClientAuthenticated = false;
                Serial.println("BLE client connected. Awaiting auth...");
                // Send unsolicited auth response after 150ms delay for buffers to initialize
                delay(150);
                sendAuthResponse(false, "Authentication required", strlen(nodePassword) == 0);
            } else {
                isBleClientAuthenticated = false;
                Serial.println("BLE client disconnected.");
                if (powerSaveMode) {
                    // Instantly ensure we are advertising, sleep timer starts now
                    if (!bleMgr.isAdvertising) {
                        bleMgr.startAdvertising();
                    }
                }
            }
        }

        // BLE power save sleep timeout check
        if (!currentBleConnected && powerSaveMode) {
            if (bleMgr.isAdvertising && (millis() - lastBleActiveTime > 300000)) {
                bleMgr.stopAdvertising();
            }
        }
    }

    // Blink LED to indicate running state on RAK targets
#if defined(RAK4631) || defined(RAK3401_1W)
    static uint32_t lastBlink = 0;
    static bool ledState = false;
    if (millis() - lastBlink > 1000) {
        lastBlink = millis();
        ledState = !ledState;
        digitalWrite(LED_GREEN, ledState ? HIGH : LOW);
    }
    
    // Light up blue LED if BLE is connected, turn off if disconnected
    digitalWrite(LED_BLUE, bleMgr.isDeviceConnected() ? HIGH : LOW);
#endif

    // Read and parse NMEA stream from GPS (skipped entirely when the module
    // is unpowered by gps_mode=1)
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    if (gpsMode == 0 && hasOnboardGps) {
        while (Serial1.available() > 0) {
            gps.encode(Serial1.read());
            totalGpsBytesReceived++;
        }
    }
#endif

#if defined(RAK4631) || defined(RAK3401_1W)
    // Read and parse NMEA stream from I2C for ZOE-M8Q (RAK12500 default I2C address 0x42)
    static uint32_t lastI2CGPSPoll = 0;
    if (gpsMode == 0 && hasOnboardGps && millis() - lastI2CGPSPoll > 100) {
        lastI2CGPSPoll = millis();
        Wire.requestFrom((uint8_t)0x42, (uint8_t)32);
        while (Wire.available()) {
            char c = Wire.read();
            if (c != (char)0xFF) {
                gps.encode(c);
                totalGpsBytesReceived++;
            }
        }
    }
#endif

    // Raw radio heartbeat for link diagnostics. This bypasses BLE, protobuf, and
    // the mesh router so RX count proves the LoRa layer itself is working.
    // Disabled by default (costs battery + airtime on every node forever);
    // enable with -D ENABLE_RAW_BEACON in platformio.ini for bench debugging.
#ifdef ENABLE_RAW_BEACON
    static uint32_t lastRawBeacon = 0;
    uint32_t beaconInterval = 7000 + (localNodeId % 3000);
    if (millis() - lastRawBeacon > beaconInterval) {
        lastRawBeacon = millis();
        uint8_t beacon[12] = {
            'A', 'M', 'T', 'E',
            (uint8_t)(localNodeId >> 24),
            (uint8_t)(localNodeId >> 16),
            (uint8_t)(localNodeId >> 8),
            (uint8_t)localNodeId,
            (uint8_t)(rawBeaconCount >> 24),
            (uint8_t)(rawBeaconCount >> 16),
            (uint8_t)(rawBeaconCount >> 8),
            (uint8_t)rawBeaconCount
        };
        rawBeaconCount++;
        radioMgr.sendPacket(beacon, sizeof(beacon));
    }
#endif // ENABLE_RAW_BEACON


    // Periodic status logging
    static uint32_t lastPrint = 0;
    if (millis() - lastPrint > 10000) {
        lastPrint = millis();
        // Print routing table to serial for monitoring/debug
        router.printRoutingTable();
        
        // Periodic battery/solar telemetry broadcast
        // Let's broadcast our telemetry to the mesh based on configured interval
        static uint32_t lastTelemetry = 0;
        uint32_t effectiveTelemetrySec = telemetryIntervalSec;
        if (powerSaveMode) {
            uint8_t batt = readBatteryLevel();
            if (batt <= 30) {
                effectiveTelemetrySec *= 4; // quadruple if battery <= 30%
            } else {
                effectiveTelemetrySec *= 2; // double otherwise
            }
        }
        if (millis() - lastTelemetry > (effectiveTelemetrySec * 1000L)) {
            lastTelemetry = millis();
            
            // No fix -> broadcast 0,0 so the app plots no marker (it skips lat/lon == 0),
            // instead of a misleading fallback position.
            float lat = 0.0f;
            float lon = 0.0f;
            int32_t alt = 0;

            if (fixedPosition) {
                lat = fixedLat;
                lon = fixedLon;
                alt = fixedAlt;
                Serial.print("Broadcasting Fixed Position Telemetry: Lat=");
                Serial.print(lat, 6);
                Serial.print(", Lon=");
                Serial.println(lon, 6);
            } else if (gps.location.isValid()) {
                lat = gps.location.lat();
                lon = gps.location.lng();
                alt = gps.altitude.isValid() ? gps.altitude.meters() : 0;
                Serial.print("Broadcasting GPS Telemetry: Lat=");
                Serial.print(lat, 6);
                Serial.print(", Lon=");
                Serial.println(lon, 6);
            } else if (hasInheritedLocation && (millis() - lastInheritedTime < 300000)) {
                lat = inheritedLat;
                lon = inheritedLon;
                Serial.print("Broadcasting Inherited Phone GPS Telemetry: Lat=");
                Serial.print(lat, 6);
                Serial.print(", Lon=");
                Serial.println(lon, 6);
            } else {
                Serial.println("GPS not locked yet. Broadcasting no position (0,0).");
            }
            
            uint8_t battery = readBatteryLevel();

            // 1. Broadcast telemetry over LoRa Mesh — position privacy-blurred
            // when the user configured a precision radius (0 = precise)
            float txLat = lat;
            float txLon = lon;
            meshmath::blurPosition(lat, lon, positionPrecisionM, txLat, txLon);
            router.sendTelemetry(0xFFFFFFFF, battery, txLat, txLon, batteryCharging, batteryVoltage, positionPrecisionM);
            
            // 2. Loopback telemetry to BLE connected phone so the app can plot our own position
            if (bleMgr.isDeviceConnected() && isBleClientAuthenticated) {
                aethermesh_MeshPacket localTelemetryPacket = aethermesh_MeshPacket_init_zero;
                localTelemetryPacket.sender_id = localNodeId;
                localTelemetryPacket.recipient_id = 0xFFFFFFFF; // Broadcast representation
                localTelemetryPacket.packet_id = random(1, 100000);
                localTelemetryPacket.hop_limit = 4;
                localTelemetryPacket.want_ack = false;
                localTelemetryPacket.prev_hop_id = localNodeId;
                localTelemetryPacket.which_payload = aethermesh_MeshPacket_telemetry_tag;
                
                localTelemetryPacket.payload.telemetry.battery_level = battery;
                // Owner's phone gets the TRUE position (it usually supplied it);
                // only the LoRa broadcast above is blurred. Precision is included
                // so the app can show "broadcast as +/- X" on our own node.
                localTelemetryPacket.payload.telemetry.latitude = lat;
                localTelemetryPacket.payload.telemetry.longitude = lon;
                localTelemetryPacket.payload.telemetry.altitude = alt;
                localTelemetryPacket.payload.telemetry.is_charging = batteryCharging;
                localTelemetryPacket.payload.telemetry.battery_voltage = batteryVoltage;
                localTelemetryPacket.payload.telemetry.position_precision = positionPrecisionM;
                localTelemetryPacket.payload.telemetry.uptime_seconds = (uint32_t)(millis() / 1000);
                strncpy(localTelemetryPacket.payload.telemetry.firmware_version, AETHERMESH_FW_VERSION,
                        sizeof(localTelemetryPacket.payload.telemetry.firmware_version) - 1);

#if defined(HELTEC_V4)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "Heltec V4");
#elif defined(HELTEC_V3)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "Heltec V3");
#elif defined(LILYGO_T_DECK)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "T-Deck");
#elif defined(LILYGO_T_ECHO)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "T-Echo");
#elif defined(RAK4631)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "RAK4631");
#elif defined(RAK3401_1W)
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "RAK 1W");
#else
                strcpy(localTelemetryPacket.payload.telemetry.node_model, "Generic Node");
#endif
                
                uint8_t bleBuffer[256];
                pb_ostream_t stream = pb_ostream_from_buffer(bleBuffer, sizeof(bleBuffer));
                if (pb_encode(&stream, aethermesh_MeshPacket_fields, &localTelemetryPacket)) {
                    Serial.println("Forwarding local telemetry loopback to BLE...");
                    bleMgr.sendToPhone(bleBuffer, stream.bytes_written);
                } else {
                    Serial.println("Failed to encode local telemetry loopback protobuf.");
                }
            }
            
            updateDisplay();
        }
    }

    // Check if the message popup has expired
    if (hasNewMsgPopup && (millis() - lastMsgReceivedTime >= 10000)) {
        hasNewMsgPopup = false;
        updateDisplay();
    }
}
