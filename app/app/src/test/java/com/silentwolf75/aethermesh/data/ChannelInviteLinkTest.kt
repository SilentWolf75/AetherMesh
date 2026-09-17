package com.silentwolf75.aethermesh.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelInviteLinkTest {
    private val channel = ChannelConfig(
        name = "Trail Crew",
        psk = "abc+def/",
        uplinkEnabled = true,
        downlinkEnabled = false,
        positionEnabled = true,
        preciseLocation = false,
        isPrimary = true
    )

    @Test
    fun shareUrlRoundTripsNamePskAndFlags() {
        val url = ChannelInviteLink.buildShareUrl(channel)
        assertTrue(url.startsWith(ChannelInviteLink.SHARE_PREFIX))
        val parsed = ChannelInviteLink.parse(url)
        assertNotNull(parsed)
        assertEquals("Trail Crew", parsed!!.name)
        assertEquals("abc+def/", parsed.psk)
        assertEquals(true, parsed.uplinkEnabled)
        assertEquals(false, parsed.downlinkEnabled)
        assertEquals(true, parsed.positionEnabled)
        assertEquals(false, parsed.preciseLocation)
    }

    @Test
    fun rawDeepLinkParses() {
        val parsed = ChannelInviteLink.parse(ChannelInviteLink.buildDeepLink(channel))
        assertEquals("Trail Crew", parsed!!.name)
        assertEquals("abc+def/", parsed.psk)
        assertEquals(false, parsed.downlinkEnabled)
    }

    @Test
    fun missingNameDefaultsToImportedAndFlagsDefaultTrue() {
        val parsed = ChannelInviteLink.parse("aethermesh://channel?psk=secret")
        assertEquals("Imported", parsed!!.name)
        assertEquals("secret", parsed.psk)
        assertTrue(parsed.uplinkEnabled)
        assertTrue(parsed.downlinkEnabled)
        assertTrue(parsed.positionEnabled)
        assertTrue(parsed.preciseLocation)
    }

    @Test
    fun emptyOrGarbageReturnsNull() {
        assertNull(ChannelInviteLink.parse(""))
        assertNull(ChannelInviteLink.parse("   "))
        assertNull(ChannelInviteLink.parse("https://aethermesh.org/join#not-base64"))
        assertNull(ChannelInviteLink.parse("https://example.com/other"))
    }
}
