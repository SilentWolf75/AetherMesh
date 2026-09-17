package com.silentwolf75.aethermesh.data

/**
 * Node identity across the 32-bit mesh id and the 16-bit BLE-name suffix
 * (`AetherMesh-XXXX`). Zero is never a real node.
 */
object MeshNodeId {
    fun same(a: Long, b: Long): Boolean {
        if (a == 0L || b == 0L) return false
        val a32 = a and 0xFFFFFFFFL
        val b32 = b and 0xFFFFFFFFL
        if (a32 == b32) return true
        return (a32 and 0xFFFFL) == (b32 and 0xFFFFL)
    }

    /**
     * First four MAC octets as little-endian 32-bit id (matches firmware BLE
     * advertising). Malformed MACs return 0.
     */
    fun fromMac(mac: String): Long {
        val parts = mac.split(":")
        if (parts.size < 4) return 0L
        return try {
            val b0 = parts[0].toInt(16)
            val b1 = parts[1].toInt(16)
            val b2 = parts[2].toInt(16)
            val b3 = parts[3].toInt(16)
            (b3.toLong() and 0xFFL shl 24) or
                (b2.toLong() and 0xFFL shl 16) or
                (b1.toLong() and 0xFFL shl 8) or
                (b0.toLong() and 0xFFL)
        } catch (_: Exception) {
            0L
        }
    }
}
