#include "RadioManager.h"
#include "Version.h"
#include <SPI.h>

// Volatile flag for ISR
static volatile bool operationDone = false;

#if defined(ELECROW_CROWPANEL_35)
static SPIClass crowPanelLoraSPI(HSPI);
#endif

#if defined(HELTEC_V4)
static constexpr float HELTEC_V4_TCXO_VOLTAGE = 1.8f;

static void setHeltecV4TransmitEnable(bool txOn) {
    digitalWrite(7, HIGH);             // VFEM - front-end power supply
    digitalWrite(2, HIGH);             // CSD  - front-end chip enable
    digitalWrite(46, txOn ? HIGH : LOW); // CPS - PA mode select (V4.2 GC1109)
    digitalWrite(5, txOn ? HIGH : LOW);  // RX LNA select (V4.3 KCT8103L - LOW enables LNA)
}
#else
#define setHeltecV4TransmitEnable(txOn) ((void)0)
#endif

#if defined(ESP32) || defined(ESP8266)
void IRAM_ATTR RadioManager::setFlag(void) {
#else
void RadioManager::setFlag(void) {
#endif
    operationDone = true;
}

RadioManager::RadioManager() {
    radio = nullptr;
    receiveCallback = nullptr;
    transmitDoneCallback = nullptr;
    lastRssi = 0.0f;
    lastSnr = 0.0f;
    isTransmitting = false;
    txTimeoutMs = 2500;
    lastRxActivityTime = 0;
    lastHealthLogTime = 0;
    
    // Default US915 configuration
    frequency = 906.875f;
    spreadingFactor = 9;
    bandwidth = 125.0f;
    codingRate = 5; // 4/5
    
#if defined(HELTEC_V4) || defined(HELTEC_V3)
    pinNss = 8;
    pinRst = 12;
    pinBusy = 13;
    pinDio1 = 14;
    txPower = 20; // Default to standard safe power
#elif defined(LILYGO_T_DECK)
    pinNss = 9;
    pinRst = 17;
    pinBusy = 13;
    pinDio1 = 45;
    txPower = 20;
#elif defined(ELECROW_CROWPANEL_35)
    pinNss = 0;
    pinRst = 2;
    pinBusy = 46;
    pinDio1 = 1;
    txPower = 20;
#elif defined(LILYGO_T_ECHO)
    pinNss = 24;  // P0.24
    pinRst = 25;  // P0.25
    pinBusy = 17; // P0.17
    pinDio1 = 20; // P0.20
    txPower = 20;
#elif defined(RAK4631) || defined(RAK3401_1W)
    // Predefined pins in RAK Arduino BSP
    pinNss = 42;  // PIN_LORA_NSS
    pinRst = 38;  // PIN_LORA_RESET
    pinBusy = 46; // PIN_LORA_BUSY
    pinDio1 = 47; // PIN_LORA_DIO_1
    txPower = 20; // Default
#if defined(RAK3401_1W)
    // The SX1262 die itself accepts at most 22 dBm — RadioLib rejects anything
    // higher and begin() fails, halting the node. The RAK3401's "1W" comes from
    // its external PA stage, which amplifies whatever the SX1262 outputs.
    txPower = 22;
#endif
#else
    // Fallback defaults
    pinNss = 8;
    pinRst = 12;
    pinBusy = 13;
    pinDio1 = 14;
    txPower = 20;
#endif
}

bool RadioManager::init() {
    Serial.println("Initializing LoRa Radio Module...");
    
    // 1. Power up hardware rails
#if defined(HELTEC_V4)
    Serial.println("Heltec V4: Powering Vext...");
    pinMode(36, OUTPUT);
    digitalWrite(36, LOW); // Pull Vext LOW to power peripherals
    delay(100);

    // Heltec V4 (unlike V3) has a GC1109 RF front-end (PA/LNA) that must be
    // explicitly enabled, or it sits in weak "bypass mode" (~-90s RSSI, RX deaf).
    // Per MeshCore's V4 variant:
    //   GPIO 2  = GC1109 enable (CSD)            -> HIGH to power the front-end
    //   GPIO 46 = GC1109 TX-PA select (CPS)      -> HIGH = full TX PA, LOW = bypass
    //   GPIO 7  = front-end supply (VFEM)        -> HIGH
    // GPIO 46 is CPS: LOW for RX/bypass, HIGH for the full PA during TX.
    // DIO2 handles CTX TX/RX path switching inside the SX1262.
    Serial.println("Heltec V4: Enabling GC1109/KCT8103L RF front-end...");
    pinMode(7, OUTPUT);  digitalWrite(7, HIGH);   // VFEM - front-end power supply
    pinMode(2, OUTPUT);  digitalWrite(2, HIGH);   // CSD  - front-end chip enable
    pinMode(46, OUTPUT); digitalWrite(46, LOW);   // CPS  - RX (V4.2 bypass)
    pinMode(5, OUTPUT);  digitalWrite(5, LOW);    // RX LNA select (V4.3 LOW = LNA enabled)

    SPI.begin(9, 11, 10, 8); // SCK, MISO, MOSI, SS
#elif defined(LILYGO_T_DECK)
    Serial.println("Lilygo T-Deck: Enabling Board Power (GPIO 10)...");
    pinMode(10, OUTPUT);
    digitalWrite(10, HIGH);
    delay(100);
    SPI.begin(40, 38, 41, 9); // SCK, MISO, MOSI, SS
#elif defined(ELECROW_CROWPANEL_35)
    Serial.println("Elecrow CrowPanel 3.5: selecting LoRa module and setting up SPI...");
    pinMode(45, OUTPUT);
    digitalWrite(45, LOW); // Select wireless module instead of microphone path
    pinMode(40, OUTPUT);
    digitalWrite(40, HIGH); // Keep LCD deselected while the radio owns its SPI bus
    pinMode(pinNss, OUTPUT);
    digitalWrite(pinNss, HIGH);
    delay(100);
    crowPanelLoraSPI.begin(10, 9, 3, pinNss); // SCK, MISO, MOSI, SS
#elif defined(LILYGO_T_ECHO)
    Serial.println("Lilygo T-Echo: Setting up SPI pins (23, 19, 22)...");
    SPI.setPins(23, 19, 22); // MISO=23, SCK=19, MOSI=22
    SPI.begin();
#elif defined(RAK4631) || defined(RAK3401_1W)
    Serial.println("RAK WisBlock: Enabling all power rails (Pins 17, 34, 37)...");
    pinMode(17, OUTPUT);
    digitalWrite(17, HIGH); // Enable 3V3_S power rail
    pinMode(34, OUTPUT);
    digitalWrite(34, HIGH); // Enable LoRa module/slot power
    pinMode(37, OUTPUT);
    digitalWrite(37, HIGH); // Power on the LoRa RF switch
    delay(100);
    SPI.setPins(45, 43, 44); // MISO=45, SCK=43, MOSI=44
    SPI.begin();
#endif

    // 2. Initialize Module
#if defined(ELECROW_CROWPANEL_35)
    Module* mod = new Module(pinNss, pinDio1, pinRst, pinBusy, crowPanelLoraSPI);
#else
    Module* mod = new Module(pinNss, pinDio1, pinRst, pinBusy);
#endif
    radio = new SX1262(mod);
    
    // 3. Begin radio with default params
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK)
    int state = radio->begin(frequency, bandwidth, spreadingFactor, codingRate, 0x12, txPower, 8, 1.8, false);
#elif defined(ELECROW_CROWPANEL_35)
    int state = radio->begin(frequency, bandwidth, spreadingFactor, codingRate, 0x12, txPower, 8, 3.3, false);
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO)
    int state = radio->begin(frequency, bandwidth, spreadingFactor, codingRate, 0x12, txPower, 8, 1.6, false);
#else
    int state = radio->begin(frequency, bandwidth, spreadingFactor, codingRate, 0x12, txPower, 8, 1.8, false);
#endif
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Radio initialization failed, code: ");
        Serial.println(state);
        return false;
    }
    
    // 4. Configure DIO2 RF switch. Heltec V4 uses DIO2 for GC1109 CTX.
    state = radio->setDio2AsRfSwitch(true);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to set DIO2 as RF Switch, code: ");
        Serial.println(state);
        return false;
    }
    
#if defined(HELTEC_V4)
    // Apply undocumented SX1262 register 0x8B5 patch for GC1109/KCT8103L RX sensitivity improvement
    uint8_t patchVal = 0x01;
    state = radio->writeRegister(0x08B5, &patchVal, 1);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to apply register 0x8B5 patch, code: ");
        Serial.println(state);
    } else {
        Serial.println("Applied SX1262 register 0x8B5 patch for RX sensitivity boost.");
    }
#endif
    
    // 5. Setup CAD (Channel Activity Detection) properties
    // We want to avoid packet collisions by checking if the channel is free
    
    // 6. Set interrupt actions
    radio->setPacketReceivedAction(setFlag);
    radio->setPacketSentAction(setFlag);
    
    // 7. Start listening
    state = radio->startReceive();
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to start receive mode, code: ");
        Serial.println(state);
        return false;
    }

    lastRxActivityTime = millis();
    Serial.println("Radio successfully initialized and listening.");
    return true;
}

void RadioManager::loop() {
    if (!radio) {
        return;
    }

    // Periodic RX health log: proves whether the receive chain is alive even
    // when no packets are arriving (diagnosing "node stopped receiving" reports).
    if (millis() - lastHealthLogTime > 30000) {
        lastHealthLogTime = millis();
        Serial.printf("Radio health: fw %s | mode=%s | cfg %.3fMHz SF%u BW%d %ddBm | IRQ=0x%04X | last RX activity %lus ago | ambient RSSI %.1f dBm\n",
                      AETHERMESH_FW_VERSION,
                      isTransmitting ? "TX" : "RX",
                      frequency, (unsigned)spreadingFactor, (int)bandwidth, (int)txPower,
                      radio->getIrqStatus(),
                      (unsigned long)((millis() - lastRxActivityTime) / 1000),
                      radio->getRSSI(false));
    }

    // RX watchdog: with telemetry from each nearby node every ~60s, two minutes
    // with no RX activity of any kind (packet, CRC error, header error) most
    // likely means the SX1262 silently dropped out of RX mode. Re-arm it.
    // (Harmless no-op if the mesh is genuinely quiet, e.g. a lone node.)
    if (!isTransmitting && (millis() - lastRxActivityTime > 120000)) {
        Serial.printf("RX watchdog: no receive activity for 120s (IRQ=0x%04X). Re-arming receiver.\n",
                      radio->getIrqStatus());
        radio->standby();
#if defined(HELTEC_V4)
        setHeltecV4TransmitEnable(false);
#endif
        radio->startReceive();
        lastRxActivityTime = millis();
    }

    bool processed = false;
    
    // Check if the hardware interrupt occurred
    if (operationDone) {
        operationDone = false;
        processed = true;
    }
    
    // Polling fallback: check for missed TX_DONE once the expected airtime has
    // elapsed (SF12 packets take >2s, so this can't be a fixed value).
    if (isTransmitting && (millis() - txStartTime > txTimeoutMs)) {
        processed = true;
    }
    
    // Polling fallback: check for RX_DONE every 50ms in RX mode
    static uint32_t lastRxPoll = 0;
    if (!processed && !isTransmitting && (millis() - lastRxPoll > 50)) {
        lastRxPoll = millis();
        uint16_t irq = radio->getIrqStatus();
        if (irq & (RADIOLIB_SX126X_IRQ_RX_DONE | RADIOLIB_SX126X_IRQ_CRC_ERR | RADIOLIB_SX126X_IRQ_HEADER_ERR)) {
            processed = true;
        }
    }
    
    if (!processed) {
        return;
    }
    
    uint16_t irq = radio->getIrqStatus();
    
    if (isTransmitting) {
        if (irq & RADIOLIB_SX126X_IRQ_TX_DONE) {
            // Transmission finished for real
            isTransmitting = false;
            radio->finishTransmit();
#if defined(HELTEC_V4)
            setHeltecV4TransmitEnable(false);
#endif
            Serial.println("LoRa packet transmitted successfully.");
            if (transmitDoneCallback) {
                transmitDoneCallback();
            }
            
            // Go back to listening
            radio->startReceive();
        } else if (millis() - txStartTime > txTimeoutMs + 1000) {
            // Hard timeout fallback: clear transmit lock if stuck
            Serial.println("Radio: Transmit timed out. Forcing state reset.");
            isTransmitting = false;
            radio->finishTransmit();
#if defined(HELTEC_V4)
            setHeltecV4TransmitEnable(false);
#endif
            radio->startReceive();
        } else {
            // This is a false interrupt/glitch during transmission.
            // Do NOT abort the transmission! Just log it and keep waiting.
            Serial.printf("LoRa TX glitch ignored (TX_DONE not set). IRQ: 0x%04X\n", irq);
        }
    } else {
        // We are in RX mode
        if (irq & RADIOLIB_SX126X_IRQ_RX_DONE) {
            lastRxActivityTime = millis();
            size_t len = radio->getPacketLength();
            uint8_t* buffer = new uint8_t[len];
            
            int state = radio->readData(buffer, len);
            if (state == RADIOLIB_ERR_NONE) {
                lastRssi = radio->getRSSI();
                lastSnr = radio->getSNR();
                
                Serial.print("LoRa Packet Received! Size: ");
                Serial.print(len);
                Serial.print(" bytes | RSSI: ");
                Serial.print(lastRssi);
                Serial.print(" dBm | SNR: ");
                Serial.println(lastSnr);
                
                if (receiveCallback) {
                    receiveCallback(buffer, len, lastRssi, lastSnr);
                }
            } else {
                float rssi = radio->getRSSI();
                float snr = radio->getSNR();
                Serial.print("Failed to read LoRa data (RX_DONE set), error code: ");
                Serial.print(state);
                Serial.printf(" | RSSI: %.1f dBm | SNR: %.1f dB\n", rssi, snr);
            }
            
            delete[] buffer;
            
            // Resume listening unless the packet handler started an ACK/relay transmit.
            if (!isTransmitting) {
#if defined(HELTEC_V4)
                setHeltecV4TransmitEnable(false);
#endif
                radio->startReceive();
            }
        } else if (irq & (RADIOLIB_SX126X_IRQ_CRC_ERR | RADIOLIB_SX126X_IRQ_HEADER_ERR)) {
            lastRxActivityTime = millis();
            float rssi = radio->getRSSI();
            float snr = radio->getSNR();
            Serial.printf("LoRa RX Error interrupt! IRQ: 0x%04X | RSSI: %.1f dBm | SNR: %.1f dB\n", irq, rssi, snr);
            
            // Clear interrupt flags and resume listening
            radio->startReceive();
        } else {
            // This is a false interrupt/glitch during RX.
            // Do NOT re-initialize RX, just log it. The radio is still receiving.
            Serial.printf("LoRa RX glitch ignored (no RX_DONE/ERR set). IRQ: 0x%04X\n", irq);
        }
    }
}

bool RadioManager::sendPacket(uint8_t* payload, size_t len, bool skipCad) {
    if (isTransmitting) {
        // Polling fallback check
        uint16_t irq = radio->getIrqStatus();
        if (irq & RADIOLIB_SX126X_IRQ_TX_DONE) {
            Serial.println("Radio busy status cleared via polling check during send request.");
            isTransmitting = false;
            radio->finishTransmit();
#if defined(HELTEC_V4)
            setHeltecV4TransmitEnable(false);
#endif
        } else if (millis() - txStartTime > txTimeoutMs + 1000) {
            Serial.println("Radio busy status cleared via timeout check during send request.");
            isTransmitting = false;
            radio->finishTransmit();
#if defined(HELTEC_V4)
            setHeltecV4TransmitEnable(false);
#endif
        } else {
            Serial.println("Radio busy: transmission already in progress.");
            return false;
        }
    }
    
#if defined(HELTEC_V4)
    setHeltecV4TransmitEnable(false);
#endif

    if (!skipCad) {
        // Perform CAD check before sending to avoid collisions
        Serial.println("Performing CAD check...");
        int cadState = radio->scanChannel();
        if (cadState == RADIOLIB_LORA_DETECTED) {
            Serial.println("Channel busy! Backing off...");
            delay(random(50, 200)); // Random backoff
        }
        // scanChannel fires DIO1 (CAD_DONE), leaving a stale operationDone flag that
        // loop() would misreport as a "TX glitch" on every send. Discard it here;
        // the radio is in standby now, so no genuine RX/TX event can be pending.
        operationDone = false;
    }
    
    Serial.print("Sending LoRa Packet. Length: ");
    Serial.println(len);
    
    isTransmitting = true;
    txStartTime = millis();
    // Expected time-on-air for this packet at the current SF/BW, +50% margin.
    // At SF12/125kHz a ~50B packet is ~2.3s, so a fixed timeout would abort it.
    uint32_t airtimeMs = radio->getTimeOnAir(len) / 1000;
    txTimeoutMs = airtimeMs + (airtimeMs / 2) + 300;
#if defined(HELTEC_V4)
    setHeltecV4TransmitEnable(true);
#endif
    int state = radio->startTransmit(payload, len);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to initiate LoRa transmit, code: ");
        Serial.println(state);
        isTransmitting = false;
#if defined(HELTEC_V4)
        setHeltecV4TransmitEnable(false);
#endif
        radio->startReceive();
        return false;
    }
    
    return true;
}

void RadioManager::onReceive(void (*callback)(uint8_t* data, size_t len, float rssi, float snr)) {
    receiveCallback = callback;
}

void RadioManager::onTransmitDone(void (*callback)()) {
    transmitDoneCallback = callback;
}

void RadioManager::setSpreadingFactor(uint8_t sf) {
    spreadingFactor = sf;
    if (radio) {
        radio->setSpreadingFactor(sf);
    }
}

void RadioManager::setBandwidth(float bw) {
    bandwidth = bw;
    if (radio) {
        radio->setBandwidth(bw);
    }
}

void RadioManager::setCodingRate(uint8_t cr) {
    codingRate = cr;
    if (radio) {
        radio->setCodingRate(cr);
    }
}

void RadioManager::setTxPower(int8_t power) {
    txPower = power;
    if (radio) {
        radio->setOutputPower(power);
    }
}

bool RadioManager::reinit(float freq, float bw, uint8_t sf, int8_t power) {
    Serial.println("Reinitializing LoRa Radio settings dynamically...");
    if (!radio) {
        return false;
    }

    // Sanitize before touching the chip: this runs at every boot with values
    // from NVS/app config, and a single rejected setter must never leave the
    // node with its receiver off.
    if (sf < 7 || sf > 12) {
        Serial.printf("Invalid SF %u from settings; clamping to 9.\n", sf);
        sf = 9;
    }
    if (bw != 125.0f && bw != 250.0f && bw != 500.0f) {
        Serial.printf("Invalid bandwidth %.1f from settings; clamping to 125.0.\n", bw);
        bw = 125.0f;
    }
    if (power > 22) {
        // SX1262 die limit; RadioLib rejects anything above 22 dBm.
        Serial.printf("TX power %d exceeds SX1262 limit; clamping to 22.\n", power);
        power = 22;
    } else if (power < -9) {
        Serial.printf("TX power %d below SX1262 limit; clamping to -9.\n", power);
        power = -9;
    }

    // Put radio in standby to apply configuration changes
    radio->standby();

    frequency = freq;
    bandwidth = bw;
    spreadingFactor = sf;
    txPower = power;

    // Apply everything and keep going on individual failures — the radio MUST
    // end up back in receive mode no matter what.
    bool allApplied = true;
    int state = radio->setFrequency(frequency);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to set frequency, code: ");
        Serial.println(state);
        allApplied = false;
    }

    state = radio->setBandwidth(bandwidth);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to set bandwidth, code: ");
        Serial.println(state);
        allApplied = false;
    }

    state = radio->setSpreadingFactor(spreadingFactor);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to set spreading factor, code: ");
        Serial.println(state);
        allApplied = false;
    }

    state = radio->setOutputPower(txPower);
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to set output power, code: ");
        Serial.println(state);
        allApplied = false;
    }

#if defined(HELTEC_V4)
    setHeltecV4TransmitEnable(false);
    uint8_t patchVal = 0x01;
    radio->writeRegister(0x08B5, &patchVal, 1);
#endif
    state = radio->startReceive();
    if (state != RADIOLIB_ERR_NONE) {
        Serial.print("Failed to restart receive mode, code: ");
        Serial.println(state);
        return false;
    }

    if (allApplied) {
        Serial.println("Radio settings reinitialized successfully.");
    } else {
        Serial.println("Radio reinit finished with errors (see above); receiver re-armed with partial config.");
    }
    return allApplied;
}
