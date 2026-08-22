package com.silentwolf75.aethermesh.data

/**
 * Chooses remote-config auth/protocol version from a peer's advertised
 * [MeshNode.protocolVersion] (from telemetry / mesh packets).
 *
 * - peer >= 3 → AMCFG3 + PBKDF2 (app 1.3.5+ / firmware 1.3.2+)
 * - peer >= 2 → AMCFG2 + raw-password HMAC (firmware ≤1.3.1 during rollout)
 * - peer < 2  → plaintext config_password (legacy)
 */
object RemoteControlAuthPolicy {
    fun authProtocolForPeer(peerProtocolVersion: Int): Int = when {
        peerProtocolVersion >= 3 -> 3
        peerProtocolVersion >= 2 -> 2
        else -> 0
    }
}
