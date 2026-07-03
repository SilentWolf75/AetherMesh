#include "BLEManager.h"

static constexpr size_t BLE_RX_BUFFER_SIZE = 256;
static uint8_t pendingPhoneData[BLE_RX_BUFFER_SIZE];
static size_t pendingPhoneLen = 0;
static volatile bool hasPendingPhoneData = false;

static void queuePhonePacket(const uint8_t* data, size_t len) {
    if (!data || len == 0) {
        return;
    }

    if (len > BLE_RX_BUFFER_SIZE) {
        Serial.println("BLE packet too large; dropping.");
        return;
    }

    if (hasPendingPhoneData) {
        Serial.println("BLE packet queue full; dropping.");
        return;
    }

    memcpy(pendingPhoneData, data, len);
    pendingPhoneLen = len;
    hasPendingPhoneData = true;
}

#if defined(HELTEC_V4)
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ESP32 Static Pointers
static BLEManager* espBLEInstance = nullptr;
static BLEServer* espBLEServer = nullptr;
static BLECharacteristic* espRxChar = nullptr;

class EspServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        if (espBLEInstance) espBLEInstance->isConnected = true;
        Serial.println("Phone connected via BLE (ESP32).");
    }
    void onDisconnect(BLEServer* pServer) override {
        if (espBLEInstance) espBLEInstance->isConnected = false;
        Serial.println("Phone disconnected from BLE (ESP32).");
        // Restart advertising
        pServer->startAdvertising();
    }
};

class EspCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0) {
            queuePhonePacket((const uint8_t*)value.data(), value.length());
        }
    }
};

#elif defined(RAK4631) || defined(RAK3401_1W)
#include <bluefruit.h>

// Nordic Static Pointers
static BLEManager* nrfBLEInstance = nullptr;
static BLEService nrfService(SERVICE_UUID);
static BLECharacteristic nrfTxChar(TX_CHAR_UUID);
static BLECharacteristic nrfRxChar(RX_CHAR_UUID);

void nrfConnectCallback(uint16_t conn_handle) {
    if (nrfBLEInstance) nrfBLEInstance->isConnected = true;
    Serial.println("Phone connected via BLE (Nordic).");
}

void nrfDisconnectCallback(uint16_t conn_handle, uint8_t reason) {
    if (nrfBLEInstance) nrfBLEInstance->isConnected = false;
    Serial.print("Phone disconnected from BLE (Nordic). Reason: 0x");
    Serial.println(reason, HEX);
}

void nrfWriteCallback(uint16_t conn_h, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
    if (len > 0) {
        queuePhonePacket(data, len);
    }
}
#endif

BLEManager::BLEManager() {
    isConnected = false;
    isAdvertising = false;
    phoneCallback = nullptr;
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
    
#if defined(HELTEC_V4)
    espBLEInstance = this;
    BLEDevice::init(localName);
    BLEDevice::setMTU(256);
    
    espBLEServer = BLEDevice::createServer();
    espBLEServer->setCallbacks(new EspServerCallbacks());
    
    BLEService* pService = espBLEServer->createService(SERVICE_UUID);
    
    // TX (Phone -> Node): Write
    BLECharacteristic* pTxChar = pService->createCharacteristic(
        TX_CHAR_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
    );
    pTxChar->setCallbacks(new EspCharCallbacks());
    
    // RX (Node -> Phone): Notify
    espRxChar = pService->createCharacteristic(
        RX_CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    espRxChar->addDescriptor(new BLE2902());
    
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

#elif defined(RAK4631) || defined(RAK3401_1W)
    nrfBLEInstance = this;
    
    // Configure BLE stack for maximum bandwidth to support larger MTU (256 bytes)
    Bluefruit.configPrphBandwidth(BANDWIDTH_MAX);
    
    Bluefruit.begin();
    Bluefruit.setTxPower(4); // 4 dBm
    Bluefruit.setName(localName);
    
    Bluefruit.Periph.setConnectCallback(nrfConnectCallback);
    Bluefruit.Periph.setDisconnectCallback(nrfDisconnectCallback);
    
    // Initialize Service & Characteristics
    nrfService.begin();
    
    nrfTxChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
    nrfTxChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    nrfTxChar.setMaxLen(256);
    nrfTxChar.setWriteCallback(nrfWriteCallback);
    nrfTxChar.begin();
    
    nrfRxChar.setProperties(CHR_PROPS_NOTIFY);
    nrfRxChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
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

void BLEManager::loop() {
    // nRF52 BLE runs on an RTOS background thread automatically.
    // ESP32 BLE also runs in a separate thread.
    if (!hasPendingPhoneData || !phoneCallback) {
        return;
    }

    uint8_t packet[BLE_RX_BUFFER_SIZE];
    size_t len = pendingPhoneLen;
    memcpy(packet, pendingPhoneData, len);
    hasPendingPhoneData = false;
    pendingPhoneLen = 0;

    phoneCallback(packet, len);
}

bool BLEManager::sendToPhone(uint8_t* data, size_t len) {
    if (!isConnected) {
        return false;
    }
    
#if defined(HELTEC_V4)
    if (espRxChar) {
        espRxChar->setValue(data, len);
        espRxChar->notify();
        return true;
    }
#elif defined(RAK4631) || defined(RAK3401_1W)
    if (nrfRxChar.notify(data, len)) {
        return true;
    }
#endif
    return false;
}

void BLEManager::onReceivedFromPhone(void (*callback)(uint8_t* data, size_t len)) {
    phoneCallback = callback;
}

void BLEManager::stopAdvertising() {
#if defined(HELTEC_V4)
    BLEDevice::getAdvertising()->stop();
#elif defined(RAK4631) || defined(RAK3401_1W)
    Bluefruit.Advertising.stop();
#endif
    isAdvertising = false;
    Serial.println("BLE advertising stopped to save power.");
}

void BLEManager::startAdvertising() {
#if defined(HELTEC_V4)
    BLEDevice::startAdvertising();
#elif defined(RAK4631) || defined(RAK3401_1W)
    Bluefruit.Advertising.start(0);
#endif
    isAdvertising = true;
    Serial.println("BLE advertising started/woken up.");
}
