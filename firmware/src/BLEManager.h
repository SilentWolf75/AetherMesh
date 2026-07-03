#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include <Arduino.h>

// Service and Characteristic UUIDs for AetherMesh
#define SERVICE_UUID           "a75e0001-8b01-4475-bf7d-9477b83e7953"
#define TX_CHAR_UUID           "a75e0002-8b01-4475-bf7d-9477b83e7953" // Phone -> Node (Write)
#define RX_CHAR_UUID           "a75e0003-8b01-4475-bf7d-9477b83e7953" // Node -> Phone (Notify)

class BLEManager {
public:
    BLEManager();
    bool init(uint32_t nodeId, const char* customName = nullptr);
    void loop();
    
    // Sends a received mesh packet to the connected phone
    bool sendToPhone(uint8_t* data, size_t len);
    
    // Callback registers
    void onReceivedFromPhone(void (*callback)(uint8_t* data, size_t len));
    
    bool isDeviceConnected() { return isConnected; }
    void stopAdvertising();
    void startAdvertising();

    bool isConnected;
    bool isAdvertising;
    void (*phoneCallback)(uint8_t* data, size_t len);

private:
    uint32_t nodeUniqueId;
    
    // Platform-specific helper functions
    void setupNordicBLE();
    void setupEsp32BLE();
    
    // Friends / Static callback helpers
    friend void bleConnectCallback(uint16_t conn_handle);
    friend void bleDisconnectCallback(uint16_t conn_handle, uint8_t reason);
    friend void bleWriteCallback(uint16_t conn_handle, uint8_t* data, uint16_t len);
};

#endif // BLE_MANAGER_H
