package com.silentwolf75.aethermesh.data

data class IncomingTelemetryCoords(
    val latitude: Float,
    val longitude: Float,
    val fuzzed: Boolean
)

/**
 * Inbound TELEMETRY display rules. SQLite, battery alerts, and store-forward
 * retry stay in [AetherMeshRepository]. Proto3 defaults region to 0 (US915);
 * only trust it when SF is also present.
 *
 * Display blur uses the same most-restrictive [ChannelPrivacy] floor as
 * phone→radio GPS inject and onboard telemetry.
 */
object IncomingTelemetryPolicy {
    fun displayCoords(
        latitude: Float,
        longitude: Float,
        senderId: Long,
        channels: List<ChannelConfig>
    ): IncomingTelemetryCoords {
        val privacy = PhoneLocationShare.privacyOf(channels)
        if (!PhoneLocationShare.shouldFuzz(privacy)) {
            return IncomingTelemetryCoords(latitude, longitude, fuzzed = false)
        }
        val (lat, lon) = PhoneLocationShare.fuzzMeters(
            latitude.toDouble(),
            longitude.toDouble(),
            senderId,
            privacy.radiusM
        )
        return IncomingTelemetryCoords(lat.toFloat(), lon.toFloat(), fuzzed = true)
    }

    fun displayCoords(
        latitude: Float,
        longitude: Float,
        senderId: Long,
        primary: ChannelConfig?
    ): IncomingTelemetryCoords =
        displayCoords(latitude, longitude, senderId, listOfNotNull(primary))

    fun channelFloorMeters(channels: List<ChannelConfig>): Int =
        PhoneLocationShare.channelFloorMeters(channels)

    fun channelFloorMeters(primary: ChannelConfig?): Int =
        PhoneLocationShare.channelFloorMeters(primary)

    fun trustedRegion(loraSf: Int, region: Int): Int =
        if (loraSf in 7..12) region else -1

    fun trustedProtocolVersion(version: Int): Int = version.coerceAtLeast(1)

    fun unsigned32(raw: Int): Long = raw.toLong() and 0xFFFFFFFFL
}
