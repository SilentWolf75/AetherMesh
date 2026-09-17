package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.NodeConfig
import com.silentwolf75.aethermesh.proto.PositionPrivacy

data class ChannelPrivacy(val disabled: Boolean = false, val radiusM: Int = 0) {
    companion object {
        // Telemetry is shared across channels: the most restrictive setting wins.
        fun fromChannels(channels: List<ChannelConfig>): ChannelPrivacy = ChannelPrivacy(
            disabled = channels.any { !it.positionEnabled },
            radiusM = channels.filter { !it.preciseLocation }.maxOfOrNull {
                val miles = it.precisionMiles
                if (!miles.isFinite() || miles <= 0f) 1609
                else (miles * 1609.34f).toInt().coerceIn(1, 100000)
            } ?: 0
        )
        fun fromReport(config: NodeConfig) = ChannelPrivacy(
            config.channelPositionDisabled, config.channelPrecisionM
        )
    }

    fun packet(nodeId: Long): ByteArray = MeshPacket.newBuilder()
        .setSenderId(nodeId.toInt()).setRecipientId(0).setHopLimit(0)
        .setPositionPrivacy(PositionPrivacy.newBuilder()
            .setPositionDisabled(disabled).setPrecisionM(radiusM))
        .build().toByteArray()
}

enum class ChannelPrivacyStatus {
    DISCONNECTED, CHECKING, UNSUPPORTED, APPLYING, CONFIRMED, FAILED, OTA_BUSY;

    fun label(spanish: Boolean): String = when (this) {
        DISCONNECTED -> if (spanish) "Conecta el nodo para confirmar la privacidad de ubicación."
            else "Connect the node to confirm radio location privacy."
        CHECKING -> if (spanish) "Comprobando la privacidad del nodo…"
            else "Checking radio location privacy…"
        UNSUPPORTED -> if (spanish) "Actualiza el firmware para aplicar la privacidad de canales al GPS del nodo."
            else "Update firmware to apply channel privacy to the radio's GPS."
        APPLYING -> if (spanish) "Aplicando privacidad al nodo…"
            else "Applying location privacy to the radio…"
        CONFIRMED -> if (spanish) "Privacidad confirmada por el nodo. Se aplica el canal más restrictivo."
            else "Location privacy confirmed by the radio. The most restrictive channel applies."
        FAILED -> if (spanish) "Privacidad sin confirmar. Vuelve a conectar antes de confiar en este ajuste."
            else "Location privacy unconfirmed. Reconnect before relying on this setting."
        OTA_BUSY -> if (spanish) "La privacidad se confirmará al reconectar después de la actualización."
            else "Location privacy will be checked after the firmware update reconnects."
    }
}
