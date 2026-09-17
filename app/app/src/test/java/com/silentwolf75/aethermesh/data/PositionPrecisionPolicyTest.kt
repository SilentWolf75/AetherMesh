package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionPrecisionPolicyTest {
    @Test
    fun preciseIsZero() {
        assertEquals("Precise", PositionPrecisionPolicy.format(0, imperial = false, spanish = false))
        assertEquals("Precisa", PositionPrecisionPolicy.format(0, imperial = true, spanish = true))
    }

    @Test
    fun metricAndImperialThresholds() {
        assertEquals("±250 m", PositionPrecisionPolicy.format(250, imperial = false, spanish = false))
        assertEquals("±1.0 km", PositionPrecisionPolicy.format(1000, imperial = false, spanish = false))
        assertEquals("±820 ft", PositionPrecisionPolicy.format(250, imperial = true, spanish = false))
        assertEquals("±1.2 mi", PositionPrecisionPolicy.format(2000, imperial = true, spanish = false))
    }

    @Test
    fun nearestStepSnaps() {
        assertEquals(0, PositionPrecisionPolicy.nearestStep(0))
        assertEquals(100, PositionPrecisionPolicy.nearestStep(90))
        assertEquals(1000, PositionPrecisionPolicy.nearestStep(1200))
        assertEquals(10000, PositionPrecisionPolicy.nearestStep(50_000))
    }
}
