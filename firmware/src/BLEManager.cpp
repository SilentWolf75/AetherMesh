#include "BLEManager.h"

// SPSC ring of received phone packets. The BLE stack task produces, the main
// loop consumes. Multiple slots matter for OTA: firmware chunks arrive in
// bursts faster than one loop pass, and the old single-slot buffer dropped
// everything after the first.
static constexpr size_t BLE_RX_BUFFER_SIZE = 512; // OTA MeshPackets with 224B chunks need >256 once offset varints grow
static constexpr size_t BLE_RX_RING_SLOTS = 16; // absorbs OTA bursts across flash-erase stalls
struct BleRxSlot {
    uint8_t data[BLE_RX_BUFFER_SIZE];
    size_t len;
    uint32_t generation; // connection the write arrived on (see BleSession.h)
};
static BleRxSlot bleRxRing[BLE_RX_RING_SLOTS];
static volatile size_t bleRxHead = 0; // producer writes here
static volatile size_t bleRxTail = 0; // consumer reads here

// Bumped by the BLE stack on every connect and disconnect. Trust in main.cpp is
// keyed to this, so a missed disconnect/reconnect pair cannot carry an
// authenticated session over to a different phone.
static volatile uint32_t gConnectionGeneration = 0;
// Generation of the packet currently inside phoneCallback.
static volatile uint32_t gDeliveringGeneration = 0;

static void bumpConnectionGeneration() {
    uint32_t next = gConnectionGeneration + 1;
    if (next == 0) next = 1; // 0 means "no connection yet"
    gConnectionGeneration = next;
}

static void queuePhonePacket(const uint8_t* data, size_t len);

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
static BLEManager* espBLEInstance = nullptr;
#endif

static void deliverPhonePacket(const uint8_t* data, size_t len) {
    if (!data || len == 0) {
        return;
    }
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
    if (espBLEInstance && espBLEInstance->inlinePhoneDelivery && espBLEInstance->phoneCallback) {
        // Copy to a stack buffer — characteristic value may be reused.
        if (len > BLE_RX_BUFFER_SIZE) {
            Serial.println("BLE packet too large; dropping.");
            return;
        }
        uint8_t tmp[BLE_RX_BUFFER_SIZE];
        memcpy(tmp, data, len);
        gDeliveringGeneration = gConnectionGeneration;
        espBLEInstance->phoneCallback(tmp, len);
        return;
    }
#endif
    queuePhonePacket(data, len);
}

static void queuePhonePacket(const uint8_t* data, size_t len) {
    if (!data || len == 0) {
        return;
    }

    if (len > BLE_RX_BUFFER_SIZE) {
        Serial.println("BLE packet too large; dropping.");
        return;
    }

    size_t next = (bleRxHead + 1) % BLE_RX_RING_SLOTS;
    if (next == bleRxTail) {
        Serial.println("BLE packet ring full; dropping.");
        return;
    }

    memcpy(bleRxRing[bleRxHead].data, data, len);
    bleRxRing[bleRxHead].len = len;
    bleRxRing[bleRxHead].generation = gConnectionGeneration;
    bleRxHead = next;
}

#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>

// ESP32 Static Pointers
static BLEServer* espBLEServer = nullptr;
static BLECharacteristic* espRxChar = nullptr;
static esp_bd_addr_t espPeerAddr = {};
static bool espPeerKnown = false;

class EspServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        bumpConnectionGeneration();
        if (espBLEInstance) espBLEInstance->isConnected = true;
        Serial.println("Phone connected via BLE (ESP32).");
    }

    // Phones propose a 5s supervision timeout, and until now the node just took
    // it. This peripheral shares one CPU with a LoRa radio and writes flash
    // during OTA, so it routinely stops answering for longer than that and the
    // central tears the link down (GATT status 8 / 147). Ask for headroom:
    // 20s of supervision, no peripheral latency, and an interval fast enough to
    // keep OTA throughput reasonable.
    void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t* param) override {
        if (param == nullptr) return;
        memcpy(espPeerAddr, param->connect.remote_bda, sizeof(espPeerAddr));
        espPeerKnown = true;
        // Units: interval x1.25ms, timeout x10ms.
        pServer->updateConnParams(param->connect.remote_bda,
                                  /*minInterval=*/6,    // 7.5 ms
                                  /*maxInterval=*/12,   //  15 ms
                                  /*latency=*/0,
                                  /*timeout=*/2000);    //  20 s
        Serial.println("BLE: requested 20s supervision timeout (was 5s default).");
    }
    void onDisconnect(BLEServer* pServer) override {
        bumpConnectionGeneration();
        if (espBLEInstance) espBLEInstance->isConnected = false;
        Serial.println("Phone disconnected from BLE (ESP32).");
        // Restart advertising through BLEManager so isAdvertising stays honest.
        // main.cpp still owns the Battery Saver 5-minute window / stop timer.
        if (espBLEInstance) {
            espBLEInstance->startAdvertising();
        } else {
            pServer->startAdvertising();
        }
    }
};

// Pairing uses LE Secure Connections with "just works" confirmation: the link
// key comes from an ECDH exchange, so a listener in range cannot read the link.
class EspSecurityCallbacks : public BLESecurityCallbacks {
    uint32_t onPassKeyRequest() override { return 0; }
    void onPassKeyNotify(uint32_t) override {}
    bool onConfirmPIN(uint32_t) override { return true; }
    bool onSecurityRequest() override { return true; }
    void onAuthenticationComplete(esp_ble_auth_cmpl_t result) override {
        if (result.success) {
            Serial.println("BLE: link encrypted and bonded.");
        } else {
            Serial.printf("BLE: pairing failed (reason 0x%02X).\n", result.fail_reason);
        }
    }
};

class EspCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            deliverPhonePacket((const uint8_t*)value.data(), value.length());
        }
    }
};

#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO) || defined(SEEED_T1000_E)
#include <bluefruit.h>

// Nordic Static Pointers
static BLEManager* nrfBLEInstance = nullptr;
static BLEService nrfService(SERVICE_UUID);
static BLECharacteristic nrfTxChar(TX_CHAR_UUID);
static BLECharacteristic nrfRxChar(RX_CHAR_UUID);

void nrfConnectCallback(uint16_t conn_handle) {
    bumpConnectionGeneration();
    if (nrfBLEInstance) nrfBLEInstance->isConnected = true;
    Serial.println("Phone connected via BLE (Nordic).");
}

void nrfDisconnectCallback(uint16_t conn_handle, uint8_t reason) {
    bumpConnectionGeneration();
    if (nrfBLEInstance) nrfBLEInstance->isConnected = false;
    Serial.print("Phone disconnected from BLE (Nordic). Reason: 0x");
    Serial.println(reason, HEX);
}

void nrfPairCompleteCallback(uint16_t conn_handle, uint8_t auth_status) {
    if (auth_status == BLE_GAP_SEC_STATUS_SUCCESS) {
        Serial.println("BLE: link encrypted and bonded.");
    } else {
        Serial.printf("BLE: pairing failed (status 0x%02X).\n", auth_status);
    }
}

void nrfWriteCallback(uint16_t conn_h, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
    if (len > 0) {
        deliverPhonePacket(data, len);
    }
}
#endif

BLEManager::BLEManager() {
    isConnected = false;
    isAdvertising = false;
    phoneCallback = nullptr;
    inlinePhoneDelivery = false;
    nodeUniqueId = 0;
}

bool BLEManager::init(uint32_t nodeId, const char* customName) {
    nodeUniqueId = nodeId;
    
    char localName[32];
    if (customName && strlen(customName) > 0) {
        strncpy(localName, customName, sizeof(localName) - 1);
        localName[sizeof(localName) - 1] = '\0';
    } else {
        // Construct local name: "AetherMesh-XXXX" where XXXX is hex of lower 16-bits of node ID
        snprintf(localName, sizeof(localName), "AetherMesh-%04X", (uint16_t)(nodeId & 0xFFFF));
    }
    
    Serial.print("Initializing BLE Advertising name: ");
    Serial.println(localName);
    
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
    espBLEInstance = this;
    BLEDevice::init(localName);
    BLEDevice::setMTU(256);
    
    BLEDevice::setSecurityCallbacks(new EspSecurityCallbacks());
    BLESecurity* security = new BLESecurity();
    security->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
    security->setCapability(ESP_IO_CAP_NONE);
    security->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    security->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

    espBLEServer = BLEDevice::createServer();
    espBLEServer->setCallbacks(new EspServerCallbacks());
    
    BLEService* pService = espBLEServer->createService(SERVICE_UUID);
    
    // TX (Phone -> Node): Write
    BLECharacteristic* pTxChar = pService->createCharacteristic(
        TX_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
    );
    // Both characteristics need an encrypted link: the phone's first write or
    // subscription makes it pair.
    pTxChar->setAccessPermissions(ESP_GATT_PERM_WRITE_ENCRYPTED);
    pTxChar->setCallbacks(new EspCharCallbacks());
    
    // RX (Node -> Phone): Notify
    espRxChar = pService->createCharacteristic(
        RX_CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    espRxChar->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED);
    BLE2902* notifyConfig = new BLE2902();
    notifyConfig->setAccessPermissions(ESP_GATT_PERM_READ_ENCRYPTED | ESP_GATT_PERM_WRITE_ENCRYPTED);
    espRxChar->addDescriptor(notifyConfig);
    
    pService->start();
    
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);  // Help iOS connections
    pAdvertising->setMinPreferred(0x12);
    
    BLEDevice::startAdvertising();
    isAdvertising = true;
    Serial.println("ESP32 BLE Service started.");
    return true;

#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO) || defined(SEEED_T1000_E)
    nrfBLEInstance = this;
    
    // Configure BLE stack for maximum bandwidth to support larger MTU (256 bytes)
    Bluefruit.configPrphBandwidth(BANDWIDTH_MAX);
    
    Bluefruit.begin();
    Bluefruit.setTxPower(4); // 4 dBm
    Bluefruit.setName(localName);
    
    Bluefruit.Periph.setConnectCallback(nrfConnectCallback);
    Bluefruit.Periph.setDisconnectCallback(nrfDisconnectCallback);
    // LE Secure Connections, "just works": no PIN, but the link is encrypted
    // with a key an eavesdropper cannot derive. The characteristics below
    // demand it, so the phone pairs on first use.
    Bluefruit.Security.setIOCaps(false, false, false);
    Bluefruit.Security.setMITM(false);
    Bluefruit.Security.setPairCompleteCallback(nrfPairCompleteCallback);
    
    // Initialize Service & Characteristics
    nrfService.begin();
    
    nrfTxChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
    nrfTxChar.setPermission(SECMODE_ENC_NO_MITM, SECMODE_ENC_NO_MITM);
    nrfTxChar.setMaxLen(256);
    nrfTxChar.setWriteCallback(nrfWriteCallback);
    nrfTxChar.begin();
    
    nrfRxChar.setProperties(CHR_PROPS_NOTIFY);
    // The notify subscription (CCCD) takes the read permission, so this also
    // keeps an unpaired phone from subscribing.
    nrfRxChar.setPermission(SECMODE_ENC_NO_MITM, SECMODE_NO_ACCESS);
    nrfRxChar.setMaxLen(256);
    nrfRxChar.begin();
    
    // Set Advertising (split Name and Service UUID to avoid truncation to "Aethe")
    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addTxPower();
    Bluefruit.Advertising.addName(); // Complete Name fits in main advertisement
    
    // Put 128-bit Service UUID in Scan Response
    Bluefruit.ScanResponse.addService(nrfService);
    
    // Fast advertising first 30 seconds, then slow
    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(32, 244); // in unit of 0.625 ms
    Bluefruit.Advertising.setFastTimeout(30);
    Bluefruit.Advertising.start(0); // 0 = advertise forever
    isAdvertising = true;
    Serial.println("Nordic BLE Service started.");
    return true;
#else
    Serial.println("BLE not supported on this architecture.");
    return false;
#endif
}

// Re-assert our preferred connection parameters. The phone lowers the
// supervision timeout back to its own default whenever it requests a high
// priority link (it does this at OTA start), which is exactly when the node
// needs the extra headroom to survive flash writes.
void BLEManager::reassertConnectionParams() {
#if defined(ESP32)
    if (!espPeerKnown || espBLEServer == nullptr) return;
    // latency must stay 0: a peripheral latency of 1 lets the node skip every
    // other connection event, which halves OTA throughput. Measured at 47ms per
    // chunk against a 15ms interval because the phone negotiated latency=1.
    espBLEServer->updateConnParams(espPeerAddr, 6, 12, 0, 2000);
    Serial.println("BLE: re-asserted 20s supervision timeout.");
#endif
}

void BLEManager::loop() {
    // nRF52 BLE runs on an RTOS background thread automatically.
    // ESP32 BLE also runs in a separate thread.
    if (!phoneCallback) {
        return;
    }

    // Drain everything queued since the last pass (OTA sends bursts)
    while (bleRxTail != bleRxHead) {
        uint8_t packet[BLE_RX_BUFFER_SIZE];
        size_t len = bleRxRing[bleRxTail].len;
        uint32_t generation = bleRxRing[bleRxTail].generation;
        memcpy(packet, bleRxRing[bleRxTail].data, len);
        bleRxTail = (bleRxTail + 1) % BLE_RX_RING_SLOTS;

        gDeliveringGeneration = generation;
        phoneCallback(packet, len);
    }
}

bool BLEManager::sendToPhone(uint8_t* data, size_t len) {
    if (!isConnected) {
        return false;
    }
    
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
    if (espRxChar) {
        espRxChar->setValue(data, len);
        espRxChar->notify();
        return true;
    }
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO) || defined(SEEED_T1000_E)
    if (nrfRxChar.notify(data, len)) {
        return true;
    }
#endif
    return false;
}

uint32_t BLEManager::connectionGeneration() const {
    return gConnectionGeneration;
}

uint32_t BLEManager::deliveringGeneration() const {
    return gDeliveringGeneration;
}

void BLEManager::onReceivedFromPhone(void (*callback)(uint8_t* data, size_t len)) {
    phoneCallback = callback;
}

void BLEManager::stopAdvertising() {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
    BLEDevice::getAdvertising()->stop();
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO) || defined(SEEED_T1000_E)
    Bluefruit.Advertising.stop();
#endif
    isAdvertising = false;
    // Ghost-node note: under Battery Saver / leave-behind, phones will no longer
    // scan-discover this node until the user button wakes a fresh advertise
    // window. LoRa mesh RX/TX continue; only BLE discoverability pauses.
    Serial.println("BLE advertising stopped to save power (press button to wake ~5 min window).");
}

void BLEManager::startAdvertising() {
#if defined(HELTEC_V4) || defined(HELTEC_V3) || defined(LILYGO_T_DECK) || defined(ELECROW_CROWPANEL_35)
    BLEDevice::startAdvertising();
#elif defined(RAK4631) || defined(RAK3401_1W) || defined(LILYGO_T_ECHO) || defined(SEEED_T1000_E)
    // restartOnDisconnect may already be running; start(0) is safe to re-arm.
    Bluefruit.Advertising.start(0);
#endif
    isAdvertising = true;
    Serial.println("BLE advertising started/woken up.");
}
