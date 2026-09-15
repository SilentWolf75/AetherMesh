package com.silentwolf75.aethermesh.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side SQLite proof that retries keep one message identity.
 * BLE reconnect / OTA still need a radio; this covers the durable retry store.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryRetryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper
    private lateinit var store: OutboundDeliveryStore
    private var now = 1_800_000_000_000L
    private val writes = mutableListOf<ByteArray>()
    private val wire = com.silentwolf75.aethermesh.proto.MeshPacket.newBuilder()
        .setPacketId(123).setSenderId(10).setRecipientId(20)
        .setText(com.silentwolf75.aethermesh.proto.TextMessage.newBuilder().setContent("ciphertext").setIsEncrypted(true))
        .build().toByteArray()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("aethermesh.db")
        db = DatabaseHelper(context)
        store = OutboundDeliveryStore(db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase("aethermesh.db")
    }

    @Test
    fun failedSendKeepsOneRowAndHonorsCooldownOnDeviceSqlite() {
        val id = db.insertMessage(10, 20, "hello", "", 123, "FAILED", false)
        store.track(id, wire, now)
        val controller = DeliveryRetryController(store, { true }, { 10L }, {
            writes.add(it)
            true
        }, { now })
        assertFalse(controller.retry(id))
        now += DeliveryRetryController.COOLDOWN_MS
        assertTrue(controller.retry(id))
        assertEquals(1, db.getAllMessages().size)
        assertEquals(123, db.getAllMessages().single().packetId)
        assertEquals(1, writes.size)
    }
}
