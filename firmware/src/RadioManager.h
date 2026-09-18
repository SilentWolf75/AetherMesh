#ifndef RADIO_MANAGER_H
#define RADIO_MANAGER_H

#include <Arduino.h>
#define RADIOLIB_LOW_LEVEL 1
#include <RadioLib.h>
#include "MeshRadio.h"

class RadioManager : public MeshRadio {
public:
    RadioManager();
    bool init();
    void loop();
    
    // Sends a raw packet over LoRa. skipCad=true bypasses channel-activity check
    // for time-critical small replies (e.g. range-test PONG).
    bool sendPacket(uint8_t* payload, size_t len, bool skipCad = false);

    // Low-voltage cutoff: refuse TX while pack is critically low. RX / BLE stay up
    // so leave-behinds recover when voltage returns (do not brick silently).
    void setTxBlocked(bool blocked) { txBlocked = blocked; }
    bool isTxBlocked() const { return txBlocked; }
    // Suppress TX for the duration of a BLE firmware update. A 96-byte
    // frame at SF12/BW125 occupies the radio for seconds, during which the
    // BLE link is not serviced and blows its supervision timeout.
    void setOtaSuppressed(bool suppressed) { otaSuppressed = suppressed; }
    bool isOtaSuppressed() const { return otaSuppressed; }
    
    // Callback registers
    void onReceive(void (*callback)(uint8_t* data, size_t len, float rssi, float snr));
    void onTransmitDone(void (*callback)());
    
    // LoRa parameter tuning
    void setSpreadingFactor(uint8_t sf);
    void setBandwidth(float bw);
    void setCodingRate(uint8_t cr);
    void setTxPower(int8_t power);
    bool reinit(float freq, float bw, uint8_t sf, int8_t power);
    
    // Diagnostics
    float getLastRssi() { return lastRssi; }
    float getLastSnr() { return lastSnr; }
    float getFrequency() { return frequency; }
    uint8_t getSpreadingFactor() { return spreadingFactor; }
    float getBandwidth() { return bandwidth; }
    uint32_t getTxPackets() const { return txPackets; }
    uint32_t getTxFailures() const { return txFailures; }
    uint32_t getRxPackets() const { return rxPackets; }
    uint32_t getCadBusyEvents() const { return cadBusyEvents; }
    uint32_t getAirtimeMs() const { return airtimeMsTotal; }
    // Phase 6: TX airtime accumulated in the current ~10s window (for
    // congestion gates on STORED wake / repair floods).
    uint32_t getRecentAirtimeMs() const;
    
private:
    void noteRecentAirtime(uint32_t airtimeMs);
    // Put the radio in receive mode. On SX1262 boards this also records the
    // "valid LoRa header" event (without raising an interrupt for it) so a
    // transmit can tell that a packet is already arriving.
    int16_t startListening();
    // True while a packet's header has been received and its body is still
    // arriving. Transmitting then would destroy it.
    bool isActivelyReceiving();
#if defined(SEEED_T1000_E)
    LR1110* radio;
#else
    SX1262* radio;
#endif
    
    // Pins
    int pinNss;
    int pinRst;
    int pinBusy;
    int pinDio1;
    
    // State
    float lastRssi;
    float lastSnr;
    bool txBlocked;
    bool otaSuppressed;
    bool isTransmitting;
    uint32_t txStartTime;
    uint32_t txTimeoutMs;   // expected airtime + margin for the in-flight packet
    uint32_t lastRxActivityTime;
    uint32_t lastHealthLogTime;
    uint32_t txPackets;
    uint32_t txFailures;
    uint32_t rxPackets;
    uint32_t cadBusyEvents;
    // Busy-channel hold-off: sends are refused until this time while the
    // radio keeps listening. 0 = not holding off.
    uint32_t channelBusyUntil;
    uint32_t channelBusyStreak;
    uint32_t headerSeenAt;
    uint32_t airtimeMsTotal;
    uint32_t recentAirtimeMs;
    uint32_t recentAirtimeWindowStart;
    
    // Config
    float frequency;
    uint8_t spreadingFactor;
    float bandwidth;
    uint8_t codingRate;
    int8_t txPower;
    
    // Callback pointer
    void (*receiveCallback)(uint8_t* data, size_t len, float rssi, float snr);
    void (*transmitDoneCallback)();
    
    // Helper to configure transceiver
    bool configureRadio();
    
    // Interrupt handler helper
    static void setFlag(void);
};

#endif // RADIO_MANAGER_H
