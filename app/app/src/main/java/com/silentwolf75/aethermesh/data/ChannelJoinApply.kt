package com.silentwolf75.aethermesh.data

import android.util.Base64
import java.security.SecureRandom

data class ChannelPskHydrate(
    val secret: String,
    val persistKeyring: Boolean,
    val clearSqlitePsk: Boolean
)

object ChannelPskPolicy {
    const val MAX_NAME = 24
    const val PSK_BYTES = 16
    /** Public legacy key. Preserve existing channels for mixed-version peers. */
    const val MESH_PSK_LEGACY = "AQ=="
    /**
     * AetherMesh StandardMesh default: SHA-256("AetherMesh/StandardMesh/psk/v1")
     * truncated to 16 bytes, Base64 NO_WRAP. Well-known so factory nodes can
     * talk; not Meshtastic's key.
     */
    const val DEFAULT_PSK = "SDSkmWKtWcBVAqi2HfNWJQ=="

    fun clipName(name: String): String = name.trim().take(MAX_NAME)


    fun isCustom(psk: String): Boolean =
        psk.isNotEmpty() && psk != DEFAULT_PSK && psk != MESH_PSK_LEGACY

    /**
     * Directory load preserves key bytes, including public legacy keys.
     * The new default is only for new channels; SQLite fills a missing keyring.
     */
    fun hydrateFromStore(keyringSecret: String?, sqlitePsk: String): ChannelPskHydrate {
        val sqliteNonEmpty = sqlitePsk.isNotEmpty()
        if (keyringSecret.isNullOrEmpty() && sqliteNonEmpty) {
            val secret = sqlitePsk
            return ChannelPskHydrate(
                secret = secret,
                persistKeyring = secret.isNotEmpty(),
                clearSqlitePsk = true
            )
        }
        val canonical = keyringSecret
        return ChannelPskHydrate(
            secret = canonical.orEmpty(),
            persistKeyring = false,
            clearSqlitePsk = sqliteNonEmpty
        )
    }

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(PSK_BYTES)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

enum class ChannelJoinKind {
    PRIMARY_UPDATE,
    SECONDARY_INSERT,
    SECONDARY_UPDATE
}

data class ChannelJoinPlan(
    val kind: ChannelJoinKind,
    val config: ChannelConfig
)

/** Join-link apply plan. Primary is never demoted. */
object ChannelJoinApply {
    fun plan(existingChannels: List<ChannelConfig>, invite: ChannelInvite): ChannelJoinPlan {
        val existing = existingChannels.firstOrNull { it.name.equals(invite.name, ignoreCase = true) }
        return if (existing?.isPrimary == true) {
            ChannelJoinPlan(
                ChannelJoinKind.PRIMARY_UPDATE,
                existing.copy(
                    psk = invite.psk,
                    uplinkEnabled = invite.uplinkEnabled,
                    downlinkEnabled = invite.downlinkEnabled,
                    positionEnabled = invite.positionEnabled,
                    preciseLocation = invite.preciseLocation
                )
            )
        } else {
            ChannelJoinPlan(
                if (existing != null) ChannelJoinKind.SECONDARY_UPDATE else ChannelJoinKind.SECONDARY_INSERT,
                ChannelConfig(
                    name = invite.name,
                    psk = invite.psk,
                    isPrimary = false,
                    uplinkEnabled = invite.uplinkEnabled,
                    downlinkEnabled = invite.downlinkEnabled,
                    positionEnabled = invite.positionEnabled,
                    preciseLocation = invite.preciseLocation,
                    precisionMiles = 1f
                )
            )
        }
    }
}
