#pragma once
#include <stddef.h>
#include <stdint.h>

// Hardware boundary used by the production router and deterministic host tests.
class MeshRadio {
public:
    virtual ~MeshRadio() = default;
    virtual bool sendPacket(uint8_t* payload, size_t len, bool skipCad = false) = 0;
    virtual uint8_t getSpreadingFactor() = 0;
    virtual uint32_t getTxPackets() const = 0;
    virtual uint32_t getTxFailures() const = 0;
    virtual uint32_t getRxPackets() const = 0;
    virtual uint32_t getCadBusyEvents() const = 0;
    virtual uint32_t getAirtimeMs() const = 0;
    virtual uint32_t getRecentAirtimeMs() const = 0;
};
