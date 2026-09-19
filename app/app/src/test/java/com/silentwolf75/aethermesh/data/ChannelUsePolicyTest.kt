package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.data.ChannelUsePolicy.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelUsePolicyTest {
    @Test
    fun channelLevelsMatchTheFirmwareThreshold() {
        assertEquals(Level.CALM, ChannelUsePolicy.channelLevel(25))
        assertEquals(Level.BUSY, ChannelUsePolicy.channelLevel(26))
        assertEquals(Level.CONGESTED, ChannelUsePolicy.channelLevel(51))
    }

    @Test
    fun airtimeShowsTheRegionalLimitOnlyWhereOneApplies() {
        assertEquals("3% of 10%", ChannelUsePolicy.airtimeLabel(3, 10))
        assertEquals("3%", ChannelUsePolicy.airtimeLabel(3, 100))
    }

    @Test
    fun airtimeLevelIsRelativeToTheLimit() {
        assertEquals(Level.CALM, ChannelUsePolicy.airtimeLevel(5, 10))
        assertEquals(Level.BUSY, ChannelUsePolicy.airtimeLevel(7, 10))
        assertEquals(Level.CONGESTED, ChannelUsePolicy.airtimeLevel(10, 10))
        assertEquals(Level.CALM, ChannelUsePolicy.airtimeLevel(20, 100))
    }

    @Test
    fun olderFirmwareIsNotReported() {
        assertFalse(ChannelUsePolicy.isReported(0))
        assertTrue(ChannelUsePolicy.isReported(100))
    }
}
