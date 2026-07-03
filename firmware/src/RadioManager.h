#ifndef RADIO_MANAGER_H
#define RADIO_MANAGER_H

#include <Arduino.h>
#define RADIOLIB_LOW_LEVEL 1
#include <RadioLib.h>

class RadioManager {
public:
    RadioManager();
    bool init();
    void loop();
    
    // Sends a raw packet over LoRa
    bool sendPacket(uint8_t* payload, size_t len);
    
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
    
private:
    // SX1262 Instance
    SX1262* radio;
    
    // Pins
    int pinNss;
    int pinRst;
    int pinBusy;
    int pinDio1;
    
    // State
    float lastRssi;
    float lastSnr;
    bool isTransmitting;
    uint32_t txStartTime;
    uint32_t txTimeoutMs;   // expected airtime + margin for the in-flight packet
    uint32_t lastRxActivityTime;
    uint32_t lastHealthLogTime;
    
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
