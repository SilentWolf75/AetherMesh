package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalQualityPolicyTest {
    @Test
    fun rssiBarsMatchFieldThresholds() {
        assertEquals(4, SignalQualityPolicy.barsFromRssi(-60f))
        assertEquals(4, SignalQualityPolicy.barsFromRssi(-70f))
        assertEquals(3, SignalQualityPolicy.barsFromRssi(-85f))
        assertEquals(2, SignalQualityPolicy.barsFromRssi(-100f))
        assertEquals(1, SignalQualityPolicy.barsFromRssi(-110f))
        assertEquals(0, SignalQualityPolicy.barsFromRssi(-115f))
        assertEquals(0, SignalQualityPolicy.barsFromRssi(-130f))
    }

    @Test
    fun rssiBandFollowsBarCount() {
        assertEquals(SignalBand.STRONG, SignalQualityPolicy.bandFromRssi(-65f))
        assertEquals(SignalBand.GOOD, SignalQualityPolicy.bandFromRssi(-80f))
        assertEquals(SignalBand.FAIR, SignalQualityPolicy.bandFromRssi(-95f))
        assertEquals(SignalBand.WEAK, SignalQualityPolicy.bandFromRssi(-110f))
        assertEquals(SignalBand.NONE, SignalQualityPolicy.bandFromRssi(-120f))
    }

    @Test
    fun snrBandUsesMeshRoutingCutoffs() {
        assertEquals(SignalBand.NONE, SignalQualityPolicy.bandFromSnr(0f))
        assertEquals(SignalBand.STRONG, SignalQualityPolicy.bandFromSnr(2f))
        assertEquals(SignalBand.GOOD, SignalQualityPolicy.bandFromSnr(-7.5f))
        assertEquals(SignalBand.FAIR, SignalQualityPolicy.bandFromSnr(-12f))
        assertEquals(SignalBand.WEAK, SignalQualityPolicy.bandFromSnr(-15f))
    }

    @Test
    fun snrMeterClampsUnknownAndExtremes() {
        assertEquals(0f, SignalQualityPolicy.snrFillFraction(0f), 0.001f)
        assertEquals(0f, SignalQualityPolicy.snrFillFraction(-20f), 0.001f)
        assertEquals(1f, SignalQualityPolicy.snrFillFraction(10f), 0.001f)
        assertEquals(0.5f, SignalQualityPolicy.snrFillFraction(-5f), 0.001f)
    }
}
