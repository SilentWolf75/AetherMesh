package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigApplyMaskTest {
    private val base = RemoteConfigSnapshot(
        name = "Camp",
        sf = 11,
        bw = 125f,
        txPower = 22,
        region = 1,
        role = 0,
        telemetry = 60,
        screen = 30,
        powerSave = false,
        posPrec = 0,
        gpsMode = 0,
        gpsDutySecs = 900,
        fixed = false,
        lat = 0f,
        lon = 0f,
        alt = 0,
        hop = 4,
        txdelay = 100
    )

    @Test
    fun identicalSnapshotsYieldZeroMask() {
        assertEquals(0, ConfigApplyMask.diff(base, base))
    }

    @Test
    fun nameTrimComparedAgainstBaseline() {
        val next = base.copy(name = " Camp ")
        assertEquals(0, ConfigApplyMask.diff(base, next))
        assertEquals(ConfigApplyMask.NAME, ConfigApplyMask.diff(base, base.copy(name = "Base")))
    }

    @Test
    fun gpsModeOrDutySetsGpsBit() {
        assertEquals(
            ConfigApplyMask.GPS_MODE,
            ConfigApplyMask.diff(base, base.copy(gpsMode = 2))
        )
        assertEquals(
            ConfigApplyMask.GPS_MODE,
            ConfigApplyMask.diff(base, base.copy(gpsDutySecs = 1800))
        )
    }

    @Test
    fun fixedAnyFieldSetsFixedBit() {
        assertEquals(ConfigApplyMask.FIXED, ConfigApplyMask.diff(base, base.copy(fixed = true)))
        assertEquals(ConfigApplyMask.FIXED, ConfigApplyMask.diff(base, base.copy(lat = 35f)))
        assertEquals(ConfigApplyMask.FIXED, ConfigApplyMask.diff(base, base.copy(lon = -106f)))
        assertEquals(ConfigApplyMask.FIXED, ConfigApplyMask.diff(base, base.copy(alt = 1600)))
    }

    @Test
    fun multipleChangesOrTogether() {
        val next = base.copy(sf = 9, hop = 3, txdelay = 150)
        val mask = ConfigApplyMask.diff(base, next)
        assertTrue(mask and ConfigApplyMask.SF != 0)
        assertTrue(mask and ConfigApplyMask.HOP != 0)
        assertTrue(mask and ConfigApplyMask.TXDELAY != 0)
        assertEquals(
            ConfigApplyMask.SF or ConfigApplyMask.HOP or ConfigApplyMask.TXDELAY,
            mask
        )
    }

    @Test
    fun bitPositionsMatchFirmwareShiftLayout() {
        assertEquals(1, ConfigApplyMask.NAME)
        assertEquals(1 shl 13, ConfigApplyMask.TXDELAY)
    }
}
