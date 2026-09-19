package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Channel use and duty cycle survive the trip through the diagnostics table. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ChannelUseDiagnosticsTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("aethermesh.db")
        db = DatabaseHelper(context)
    }

    @After fun close() { db.close() }

    @Test fun channelUseRoundTrips() {
        db.insertMeshDiagnostics(
            MeshDiagnosticsSnapshot(
                channelUtilPercent = 31,
                txDutyPercent = 4,
                dutyLimitPercent = 10,
                dutyCycleRefusals = 2
            )
        )
        val latest = db.getLatestMeshDiagnostics()!!
        assertEquals(31, latest.channelUtilPercent)
        assertEquals(4, latest.txDutyPercent)
        assertEquals(10, latest.dutyLimitPercent)
        assertEquals(2L, latest.dutyCycleRefusals)
    }

    @Test fun protoFieldsAreClampedAndWidened() {
        val proto = com.silentwolf75.aethermesh.proto.MeshDiagnostics.newBuilder()
            .setChannelUtilPercent(140)
            .setTxDutyPercent(3)
            .setDutyLimitPercent(10)
            .setDutyCycleRefusals(-1) // uint32 max arrives as a negative Java int
            .build()
        val snapshot = IncomingDiagnosticsPolicy.fromProto(proto, nowMs = 1L)
        assertEquals(100, snapshot.channelUtilPercent)
        assertEquals(0xFFFFFFFFL, snapshot.dutyCycleRefusals)
    }
}
