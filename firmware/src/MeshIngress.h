#pragma once
#include "mesh.pb.h"
#include "MeshMath.h"

namespace meshingress {
// These payloads belong only to the authenticated local BLE session.
inline bool isBleOnly(pb_size_t tag) {
    switch (tag) {
        case aethermesh_MeshPacket_auth_request_tag:
        case aethermesh_MeshPacket_auth_response_tag:
        case aethermesh_MeshPacket_delivery_status_tag:
        case aethermesh_MeshPacket_ota_control_tag:
        case aethermesh_MeshPacket_ota_data_tag:
        case aethermesh_MeshPacket_ota_status_tag:
        case aethermesh_MeshPacket_diagnostics_tag:
        case aethermesh_MeshPacket_range_test_control_tag:
        case aethermesh_MeshPacket_position_privacy_tag:
            return true;
        default: return false;
    }
}
inline bool acceptRadio(const aethermesh_MeshPacket& packet, uint32_t localId) {
    return !isBleOnly(packet.which_payload) && packet.sender_id != 0 &&
           packet.recipient_id != 0 && meshmath::hopLimitIsSane(packet.hop_limit) &&
           meshmath::hopStartIsSane(packet.hop_start, packet.hop_limit) &&
           !meshmath::isEchoLoop(packet.sender_id, packet.prev_hop_id, localId);
}
}
