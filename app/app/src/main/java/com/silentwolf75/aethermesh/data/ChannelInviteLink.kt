package com.silentwolf75.aethermesh.data

import android.net.Uri
import android.util.Base64

data class ChannelInvite(
    val name: String,
    val psk: String,
    val uplinkEnabled: Boolean = true,
    val downlinkEnabled: Boolean = true,
    val positionEnabled: Boolean = true,
    val preciseLocation: Boolean = true
)

/**
 * Primary-channel share / join links. Deep link is `aethermesh://channel?...`;
 * HTTPS share wraps that as `https://aethermesh.org/join#<base64>`.
 */
object ChannelInviteLink {
    const val SCHEME_PREFIX = "aethermesh://channel"
    const val SHARE_PREFIX = "https://aethermesh.org/join#"

    fun buildDeepLink(channel: ChannelConfig): String =
        SCHEME_PREFIX +
            "?name=${Uri.encode(channel.name)}" +
            "&psk=${Uri.encode(channel.psk)}" +
            "&uplink=${channel.uplinkEnabled}" +
            "&downlink=${channel.downlinkEnabled}" +
            "&position=${channel.positionEnabled}" +
            "&precise=${channel.preciseLocation}"

    fun buildShareUrl(channel: ChannelConfig): String {
        val encoded = Base64.encodeToString(
            buildDeepLink(channel).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return SHARE_PREFIX + encoded
    }

    fun parse(input: String): ChannelInvite? {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return null
        return try {
            val uri = resolveUri(cleaned) ?: return null
            ChannelInvite(
                name = uri.getQueryParameter("name") ?: "Imported",
                psk = uri.getQueryParameter("psk").orEmpty(),
                uplinkEnabled = uri.getQueryParameter("uplink")?.toBoolean() ?: true,
                downlinkEnabled = uri.getQueryParameter("downlink")?.toBoolean() ?: true,
                positionEnabled = uri.getQueryParameter("position")?.toBoolean() ?: true,
                preciseLocation = uri.getQueryParameter("precise")?.toBoolean() ?: true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveUri(cleaned: String): Uri? {
        if (cleaned.startsWith(SCHEME_PREFIX)) return Uri.parse(cleaned)
        val base64Part = if (cleaned.contains("#")) cleaned.substringAfter("#") else cleaned
        val decoded = String(Base64.decode(base64Part, Base64.DEFAULT), Charsets.UTF_8)
        if (!decoded.startsWith(SCHEME_PREFIX)) return null
        return Uri.parse(decoded)
    }
}
