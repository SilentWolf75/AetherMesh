#include <Arduino.h>
#include "RadioManager.h"
#include "MeshRouter.h"
#include "MeshMath.h"
#include "BLEManager.h"
#include "Version.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include <TinyGPS++.h>

#if defined(RAK4631) || defined(RAK3401_1W)
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
};
#endif

#ifdef ESP32
#include <Preferences.h>
Preferences preferences;
#endif

// Conditional OLED display inclusion for Heltec V4
#if defined(HELTEC_V4)
#include <U8g2lib.h>
// SSD1306 128x64 display connection
U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ 21, /* clock=*/ 18, /* data=*/ 17);
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
#if defined(RAK4631) || defined(RAK3401_1W)
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

    if (externalPowerPresent()) {
        batteryCharging = true;
        return;
    }

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
#if defined(RAK4631) || defined(RAK3401_1W)
    const float riseThreshold = 0.008f; // More sensitive due to cleaner ADC on nRF52
#else
    const float riseThreshold = 0.080f; // High threshold (80mV) to completely block noise/load transient triggers
#endif

    bool rising = (filteredVoltage - minV) > riseThreshold;
    bool high = filteredVoltage >= 4.15f;

    if (rising || high) {
        batteryCharging = true;
        chargingHoldUntil = millis() + 180000; // hold 3 min after last trigger
    } else if ((int32_t)(millis() - chargingHoldUntil) >= 0) {
        batteryCharging = false;
    }

    Serial.printf("Charge detect: V=%.3f floor=%.3f rise=%d high=%d vbus=%d -> charging=%d\n",
                  filteredVoltage, minV, rising, high, externalPowerPresent(), batteryCharging);
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
float inheritedLat = 0.0f;
float inheritedLon = 0.0f;
bool hasInheritedLocation = false;
uint32_t lastInheritedTime = 0;
static const uint32_t SETTINGS_VERSION = 8;

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
#elif defined(RAK4631) || defined(RAK3401_1W)
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
    // positionPrecisionM is persisted from the global on purpose (see its decl)
    preferences.putUInt("pos_prec", positionPrecisionM);
    preferences.putUInt("settings_ver", SETTINGS_VERSION);
    preferences.end();
    Serial.println("Saved settings to NVS.");
#elif defined(RAK4631) || defined(RAK3401_1W)
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
        // positionPrecisionM is persisted from the global on purpose (see its decl)
        settings.positionPrecisionM = positionPrecisionM;

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
#if defined(HELTEC_V4)
    uint64_t mac = ESP.getEfuseMac();
    return (uint32_t)(mac & 0xFFFFFFFF);
#elif defined(RAK4631) || defined(RAK3401_1W)
    return NRF_FICR->DEVICEID[0];
#else
    return 0xDEADBEEF; // Fallback
#endif
}

// Battery measurement helper for Heltec V4. The raw read toggles the divider
// GPIO and blocks 10ms, and callers (display refresh) run every second, so the
// result is cached for 30s.
uint8_t readBatteryLevel() {
#if defined(HELTEC_V4)
    static uint32_t lastSampleTime = 0;
    static uint8_t cachedLevel = 0;
    static bool haveSample = false;

    if (haveSample && (millis() - lastSampleTime < 30000)) {
        return cachedLevel;
    }

    // GPIO 37 controls the voltage divider (pull HIGH to enable)
    pinMode(37, OUTPUT);
    digitalWrite(37, HIGH);
    delay(10); // Wait for stabilizer
    // Average many reads so ADC noise stays below the charge-detection threshold
    uint32_t adcSum = 0;
    for (int i = 0; i < 32; i++) adcSum += analogRead(1); // GPIO 1 is battery voltage input
    int rawValue = adcSum / 32;
    digitalWrite(37, LOW); // Disable divider to save power

    // Calculate battery voltage
    // 3.3V ADC full range, 12-bit (4096 steps). Voltage divider multiplier is ~4.9 * 1.045 on V4
    float voltage = (float)rawValue * (3.3f / 4096.0f) * 4.9f * 1.045f;
    updateChargingState(voltage);

    Serial.print("Battery ADC: ");
    Serial.print(rawValue);
    Serial.print(" | Calc Voltage: ");
    Serial.print(voltage);
    Serial.println(" V");

    // Standard LiPo discharge range: 3.3V (0%) to 4.2V (100%)
    if (voltage >= 4.2f) {
        cachedLevel = 100;
    } else if (voltage <= 3.3f) {
        cachedLevel = 0;
    } else {
        cachedLevel = (uint8_t)((voltage - 3.3f) / (4.2f - 3.3f) * 100.0f);
    }
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

    // Standard LiPo discharge range: 3.3V (0%) to 4.2V (100%)
    if (voltage >= 4.2f) {
        cachedLevel = 100;
    } else if (voltage <= 3.3f) {
        cachedLevel = 0;
    } else {
        cachedLevel = (uint8_t)((voltage - 3.3f) / (4.2f - 3.3f) * 100.0f);
    }
    haveSample = true;
    lastSampleTime = millis();
    return cachedLevel;
#else
    return 98; // Fallback for other boards
#endif
}

#if defined(HELTEC_V4)
// Boot splash: mesh glyph + wordmark + firmware version. wavePhase pulses the
// radio arcs while setup runs.
void drawBootSplash(uint8_t wavePhase) {
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
    if (ownFix || phoneFix) {
        double lat = ownFix ? gps.location.lat() : (double)inheritedLat;
        double lon = ownFix ? gps.location.lng() : (double)inheritedLon;
        snprintf(line, sizeof(line), "Src: %s", ownFix ? "Onboard GPS" : "Phone GPS");
        u8g2.drawStr(0, 23, line);
        snprintf(line, sizeof(line), "Lat %.5f", lat);
        u8g2.drawStr(0, 34, line);
        snprintf(line, sizeof(line), "Lon %.5f", lon);
        u8g2.drawStr(0, 45, line);
        if (ownFix) {
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

// Update the OLED display screen
void updateDisplay() {
#if defined(HELTEC_V4)
    if (nodeRole == 2) {
        u8g2.setPowerSave(1); // Put display to sleep/off to save power
        displayIsOn = false;
        return;
    }

    unsigned long now = millis();
    bool shouldBeOn = true;
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
    
    if (hasNewMsgPopup && (now - lastMsgReceivedTime < 10000)) {
        // Always wake screen up for message popup overlay
        shouldBeOn = true;
    }

    if (!shouldBeOn) {
        u8g2.setPowerSave(1); // Put display to sleep/off to save power
        displayIsOn = false;
        return;
    }
    if (!displayIsOn) {
        oledPage = 0; // waking from off always lands on the HOME page
    }
    u8g2.setPowerSave(0); // Ensure awake if not repeater
    displayIsOn = true;
    
    // Check if we should render message popup overlay
    if (hasNewMsgPopup && (now - lastMsgReceivedTime < 10000)) {
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

        // Intercept NodeConfig settings ONLY when addressed to this node. A config
        // aimed at a different node (remote config) must fall through to the LoRa
        // send path below, not reconfigure/reboot the phone's own connected node.
        bool configForLocal = (packet.recipient_id == localNodeId ||
                               packet.recipient_id == 0);
        if (packet.which_payload == aethermesh_MeshPacket_config_tag && configForLocal) {
            Serial.println("Received local NodeConfig packet from phone via BLE.");

            positionPrecisionM = packet.payload.config.position_precision;

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

#if defined(HELTEC_V4)
#define USER_BUTTON_PIN 0
#elif defined(PIN_BUTTON1)
#define USER_BUTTON_PIN PIN_BUTTON1
#elif defined(BUTTON_PIN1)
#define USER_BUTTON_PIN BUTTON_PIN1
#else
#define USER_BUTTON_PIN -1
#endif

void setup() {
    Serial.begin(115200);
    // Wait up to 3 seconds for Serial port to open on PC, but don't block forever if running on battery
    uint32_t startWait = millis();
    while (!Serial && (millis() - startWait < 3000)) {
        delay(10);
    }
    
    Serial.println("\n=== AETHERMESH NODE STARTING ===");
    
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
#if defined(HELTEC_V4)
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
    
    // 2. Initialize display if Heltec V4 — show the boot splash while the
    // radio/BLE/GPS bring-up below runs
#if defined(HELTEC_V4)
    u8g2.begin();
    drawBootSplash(0);
    uint32_t splashShownAt = millis();
#endif

    // 3. Initialize GPS serial port and power toggle
#if defined(HELTEC_V4)
    Serial.println("Initializing GNSS Module (Heltec V4)...");
    pinMode(34, OUTPUT);
    digitalWrite(34, LOW);   // Pull GPIO 34 LOW (P-channel MOSFET power enable)
    
    pinMode(40, OUTPUT);
    digitalWrite(40, HIGH);  // Wakeup pin (Active HIGH)
    
    pinMode(42, OUTPUT);
    digitalWrite(42, HIGH);  // Reset pin (Active HIGH to release reset)
    
    delay(50);
    Serial1.begin(9600, SERIAL_8N1, 39, 38); // RX=39, TX=38
#elif defined(RAK4631) || defined(RAK3401_1W)
    // WisBlock GPS (RAK1910 UART, or RAK12500 in UART mode). Serial1 is mapped to
    // the GPS UART by the RAK BSP; WB_IO2 (pin 34) powers the WisBlock sensor slots.
    Serial.println("Initializing GNSS Module (RAK WisBlock UART & I2C)...");
    pinMode(34, OUTPUT);
    digitalWrite(34, HIGH);   // WB_IO2 HIGH -> power the sensor slot rail (incl. GPS)
    delay(500);               // give the GNSS module time to boot
    Serial1.begin(9600);      // NMEA @ 9600 baud, BSP-mapped Serial1 pins
    Wire.begin();             // Initialize I2C for ZOE-M8Q (RAK12500 I2C mode)
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
    
#if defined(HELTEC_V4)
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
    static uint32_t lastBleActiveTime = millis();
#if defined(USER_BUTTON_PIN) && USER_BUTTON_PIN >= 0
    static bool lastButtonState = HIGH;
    bool currentButtonState = digitalRead(USER_BUTTON_PIN);
    if (currentButtonState == LOW && lastButtonState == HIGH) {
#if defined(HELTEC_V4)
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

    // Read and parse NMEA stream from GPS
#if defined(HELTEC_V4) || defined(RAK4631) || defined(RAK3401_1W)
    while (Serial1.available() > 0) {
        gps.encode(Serial1.read());
    }
#endif

#if defined(RAK4631) || defined(RAK3401_1W)
    // Read and parse NMEA stream from I2C for ZOE-M8Q (RAK12500 default I2C address 0x42)
    static uint32_t lastI2CGPSPoll = 0;
    if (millis() - lastI2CGPSPoll > 100) {
        lastI2CGPSPoll = millis();
        Wire.requestFrom((uint8_t)0x42, (uint8_t)32);
        while (Wire.available()) {
            char c = Wire.read();
            if (c != (char)0xFF) {
                gps.encode(c);
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

            if (gps.location.isValid()) {
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
