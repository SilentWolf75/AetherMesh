package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.Telemetry

sealed class PhoneLocationDecision {
    data class Send(val latitude: Double, val longitude: Double) : PhoneLocationDecision()
    object SkipInvalid : PhoneLocationDecision()
    object SkipThrottled : PhoneLocationDecision()
    object SkipPositionDisabled : PhoneLocationDecision()
}

/**
 * Phone → node GPS inject. Packet construction only — BLE send stays in
 * [AetherMeshRepository]. Fuzz offset is stable per node id (not random).
 *
 * Uses the same most-restrictive [ChannelPrivacy] rule as the radio's
 * onboard GPS / fixed-position broadcasts.
 */
object PhoneLocationShare {
    const val MIN_INTERVAL_MS = 60_000L
    const val NODE_MODEL = "Phone Inherited"

    fun isValidFix(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    fun shouldThrottle(lastShareElapsedMs: Long, nowElapsedMs: Long): Boolean =
        lastShareElapsedMs != 0L && nowElapsedMs - lastShareElapsedMs < MIN_INTERVAL_MS

    fun privacyOf(channels: List<ChannelConfig>): ChannelPrivacy =
        if (channels.isEmpty()) ChannelPrivacy() else ChannelPrivacy.fromChannels(channels)

    fun shouldFuzz(privacy: ChannelPrivacy): Boolean =
        !privacy.disabled && privacy.radiusM > 0

    /** @deprecated Prefer [shouldFuzz] with [ChannelPrivacy] / channel list. */
    fun shouldFuzz(primary: ChannelConfig?): Boolean =
        shouldFuzz(privacyOf(listOfNotNull(primary)))

    /** Channel privacy floor in meters; 0 when precise (or position disabled). */
    fun channelFloorMeters(privacy: ChannelPrivacy): Int =
        if (shouldFuzz(privacy)) privacy.radiusM else 0

    fun channelFloorMeters(channels: List<ChannelConfig>): Int =
        channelFloorMeters(privacyOf(channels))

    fun channelFloorMeters(primary: ChannelConfig?): Int =
        channelFloorMeters(listOfNotNull(primary))

    fun fuzz(lat: Double, lon: Double, nodeId: Long, precisionMiles: Float): Pair<Double, Double> {
        val milesToDegreesLat = precisionMiles / 69.0f
        val cosLat = Math.cos(Math.toRadians(lat))
        val milesToDegreesLon = precisionMiles / (69.0f * (if (cosLat > 0.0) cosLat else 1.0))
        val stableOffsetLat = (((nodeId.hashCode() and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0
        val stableOffsetLon = ((((nodeId.hashCode() ushr 16) and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0
        return Pair(
            lat + stableOffsetLat * milesToDegreesLat,
            lon + stableOffsetLon * milesToDegreesLon
        )
    }

    fun fuzzMeters(lat: Double, lon: Double, nodeId: Long, radiusM: Int): Pair<Double, Double> {
        val miles = (radiusM.coerceAtLeast(1) / 1609.34).toFloat()
        return fuzz(lat, lon, nodeId, miles)
    }

    fun decide(
        lat: Double,
        lon: Double,
        localNodeId: Long,
        channels: List<ChannelConfig>,
        lastShareElapsedMs: Long,
        nowElapsedMs: Long
    ): PhoneLocationDecision {
        if (!isValidFix(lat, lon)) return PhoneLocationDecision.SkipInvalid
        if (shouldThrottle(lastShareElapsedMs, nowElapsedMs)) return PhoneLocationDecision.SkipThrottled
        val privacy = privacyOf(channels)
        if (privacy.disabled) return PhoneLocationDecision.SkipPositionDisabled
        if (shouldFuzz(privacy)) {
            val (fuzzedLat, fuzzedLon) = fuzzMeters(lat, lon, localNodeId, privacy.radiusM)
            return PhoneLocationDecision.Send(fuzzedLat, fuzzedLon)
        }
        return PhoneLocationDecision.Send(lat, lon)
    }

    /** Single-channel convenience (tests / legacy call sites). */
    fun decide(
        lat: Double,
        lon: Double,
        localNodeId: Long,
        primary: ChannelConfig?,
        lastShareElapsedMs: Long,
        nowElapsedMs: Long
    ): PhoneLocationDecision =
        decide(lat, lon, localNodeId, listOfNotNull(primary), lastShareElapsedMs, nowElapsedMs)

    fun buildPacket(localNodeId: Long, packetId: Int, latitude: Double, longitude: Double): MeshPacket {
        val telemetry = Telemetry.newBuilder()
            .setLatitude(latitude.toFloat())
            .setLongitude(longitude.toFloat())
            .setBatteryLevel(100)
            .setNodeModel(NODE_MODEL)
            .setUptimeSeconds(0)
            .setFirmwareVersion("")
            .build()
        return MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(localNodeId.toInt())
            .setPacketId(packetId)
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setTelemetry(telemetry)
            .build()
    }
}
