package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.NodeConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigHydratePolicyTest {
    private val current = RemoteConfigSnapshot(
        name = "Phone",
        sf = 11,
        bw = 125f,
        txPower = 22,
        region = 0,
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
    fun keepsCurrentWhenDeviceFieldsAreUnset() {
        val cfg = NodeConfig.newBuilder()
            .setNodeName("")
            .setLoraSf(0)
            .setLoraBw(0f)
            .setLoraTxPower(0)
            .setTelemetryInterval(0)
            .setMeshHopLimit(0)
            .setRebroadcastTxdelayX100(0)
            .build()
        val next = RemoteConfigHydratePolicy.merge(current, cfg)
        assertEquals("Phone", next.name)
        assertEquals(11, next.sf)
        assertEquals(125f, next.bw)
        assertEquals(22, next.txPower)
        assertEquals(60, next.telemetry)
        assertEquals(4, next.hop)
        assertEquals(100, next.txdelay)
    }

    @Test
    fun appliesValidDeviceValuesAndSnapsDuty() {
        val cfg = NodeConfig.newBuilder()
            .setNodeName("Camp")
            .setLoraSf(9)
            .setLoraBw(250f)
            .setLoraTxPower(14)
            .setRegion(1)
            .setNodeRole(1)
            .setTelemetryInterval(300)
            .setScreenTimeoutSecs(10)
            .setPowerSaveMode(true)
            .setPositionPrecision(50)
            .setGpsMode(2)
            .setGpsDutyIntervalSecs(1000)
            .setFixedPosition(true)
            .setFixedLatitude(35f)
            .setFixedLongitude(-106f)
            .setFixedAltitude(1600)
            .setMeshHopLimit(3)
            .setRebroadcastTxdelayX100(150)
            .build()
        val next = RemoteConfigHydratePolicy.merge(current, cfg)
        assertEquals("Camp", next.name)
        assertEquals(9, next.sf)
        assertEquals(250f, next.bw)
        assertEquals(14, next.txPower)
        assertEquals(1, next.region)
        assertEquals(1, next.role)
        assertEquals(300, next.telemetry)
        assertEquals(10, next.screen)
        assertEquals(true, next.powerSave)
        assertEquals(50, next.posPrec)
        assertEquals(2, next.gpsMode)
        assertEquals(900, next.gpsDutySecs)
        assertEquals(true, next.fixed)
        assertEquals(35f, next.lat)
        assertEquals(-106f, next.lon)
        assertEquals(1600, next.alt)
        assertEquals(3, next.hop)
        assertEquals(150, next.txdelay)
    }
}
