package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Channel selection rules. The point of separate channels is that asking for one
 * never quietly gives you another: a stable-only device must not be handed a
 * pre-release, and a beta tester must not be handed a stable build and told it
 * is the beta.
 */
class FirmwareChannelTest {

    @Test
    fun thereAreExactlyTwoDeliberateChannels() {
        val channels = FirmwareCatalog.Channel.entries
        // No continuous "tip" channel: every installable build is one someone
        // chose to publish, and there is no hidden third source to fall back to.
        assertEquals(2, channels.size)
        assertEquals(FirmwareCatalog.Channel.RELEASE, channels[0])
        assertEquals(FirmwareCatalog.Channel.BETA, channels[1])
    }

    @Test
    fun artifactsCarryTheChannelTheyCameFrom() {
        val release = FirmwareCatalog.Artifact(
            name = "heltec-v4", file = "aethermesh-heltec-v4-ota.bin", size = 100,
            sha256 = "", kind = "ota", board = "heltec-v4",
            channel = FirmwareCatalog.Channel.RELEASE, releaseTag = "v1.3.3", version = "1.3.3"
        )
        val beta = release.copy(channel = FirmwareCatalog.Channel.BETA, releaseTag = "v1.3.4-beta.1")
        assertFalse(release.channel == beta.channel)
        // A beta tag must not read as a stable version in the OTA screen.
        assertNotNull(beta.displayVersion)
        assertTrue(beta.displayVersion!!.contains("beta"))
    }

    @Test
    fun releaseAssetsAreRecognizedRegardlessOfChannel() {
        // The same asset naming is used on both release channels, so board
        // matching must not depend on which channel published it.
        val name = "aethermesh-heltec-v4-ota.bin"
        assertTrue(FirmwareCatalog.isBleOtaAssetName(name))
        assertEquals("heltec-v4", FirmwareCatalog.inferBoardFromFileName(name))
    }
}
