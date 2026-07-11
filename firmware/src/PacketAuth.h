#ifndef PACKET_AUTH_H
#define PACKET_AUTH_H

#include <Arduino.h>
#include "mesh.pb.h"

namespace packetauth {

size_t buildConfigCanonical(const aethermesh_MeshPacket& packet,
                            uint8_t* output, size_t capacity);
bool verifyConfig(const aethermesh_MeshPacket& packet, const char* password);

} // namespace packetauth

#endif
