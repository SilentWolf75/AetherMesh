package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OutboundDeliveryTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper
    private lateinit var store: OutboundDeliveryStore
    private var now = 1_800_000_000_000L
    private var connected = true
    private var sender = 10L
    private var accept = true
    private val writes = mutableListOf<ByteArray>()
    private val wire = com.silentwolf75.aethermesh.proto.MeshPacket.newBuilder()
        .setPacketId(123).setSenderId(10).setRecipientId(20)
        .setText(com.silentwolf75.aethermesh.proto.TextMessage.newBuilder().setContent("ciphertext").setIsEncrypted(true))
        .build().toByteArray()

    private fun assertSameMessage(payload: ByteArray) {
        val actual = com.silentwolf75.aethermesh.proto.MeshPacket.parseFrom(payload)
        val original = com.silentwolf75.aethermesh.proto.MeshPacket.parseFrom(wire)
        assertEquals(original.packetId, actual.packetId)
        assertEquals(original.senderId, actual.senderId)
        assertEquals(original.recipientId, actual.recipientId)
        assertEquals(original.text, actual.text)
        assertTrue(actual.retryCount >= 64)
    }
    private fun controller() = DeliveryRetryController(store, { connected }, { sender }, {
        writes.add(it)
        accept
    }, { now })

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("aethermesh.db")
        db = DatabaseHelper(context)
        store = OutboundDeliveryStore(db)
    }

    @After fun close() { db.close() }

    private fun message(): Long {
        val id = db.insertMessage(10, 20, "hello", "", 123, "FAILED", false)
        store.track(id, wire, now)
        return id
    }

    @Test fun retriesPersistAcrossRestartAndKeepOneMessage() {
        val id = message()
        assertFalse(controller().retry(id))
        now += DeliveryRetryController.COOLDOWN_MS
        assertTrue(controller().retry(id))
        db.updateMessageStatus(123, "FAILED")
        db.close()
        db = DatabaseHelper(context)
        store = OutboundDeliveryStore(db)
        assertFalse(controller().retry(id))
        now += DeliveryRetryController.COOLDOWN_MS
        assertTrue(controller().retry(id))
        db.updateMessageStatus(123, "FAILED")
        now += DeliveryRetryController.COOLDOWN_MS
        assertFalse(controller().retry(id))
        assertEquals(1, db.getAllMessages().size)
        assertEquals(123, db.getAllMessages().single().packetId)
        assertEquals(2, writes.size)
        writes.forEach { assertSameMessage(it) }
    }

    @Test fun explicitManualRetryRenewsExpiredMessageWithoutCreatingAnotherBubble() {
        val id = message()
        now += DeliveryRetryController.LIFETIME_MS
        store.expire(now)
        assertTrue(controller().retry(id, manual = true))
        assertEquals(1, db.getAllMessages().size)
        assertEquals("PENDING", db.getAllMessages().single().status)
        assertSameMessage(writes.single())
    }

    @Test fun disconnectedOrDifferentNodeDoesNotClaimAttempt() {
        val id = message()
        now += DeliveryRetryController.COOLDOWN_MS
        connected = false
        assertFalse(controller().retry(id))
        connected = true
        sender = 11
        assertFalse(controller().retry(id))
        sender = 10
        assertTrue(controller().retry(id))
    }

    @Test fun competingRetryAndLateAckCannotRegressDelivery() {
        val id = message()
        now += DeliveryRetryController.COOLDOWN_MS
        val first = controller()
        val second = controller()
        assertTrue(first.retry(id))
        assertFalse(second.retry(id))
        db.updateMessageStatus(123, "DELIVERED")
        db.updateMessageStatus(123, "FAILED")
        db.updateMessageStatus(123, "PENDING")
        assertEquals("DELIVERED", db.getAllMessages().single().status)
        now += DeliveryRetryController.COOLDOWN_MS
        assertFalse(first.retry(id))
    }

    @Test fun expiryIsDurableAndFailedBleHandoffIsBounded() {
        val id = message()
        now += DeliveryRetryController.COOLDOWN_MS
        accept = false
        assertFalse(controller().retry(id))
        assertEquals("FAILED", db.getAllMessages().single().status)
        assertFalse(controller().retry(id))
        now += DeliveryRetryController.LIFETIME_MS
        assertEquals(1, store.expire(now))
        assertEquals("EXPIRED", db.getAllMessages().single().status)
        assertTrue(controller().candidates(20).isEmpty())
    }

    @Test fun timeoutUsesLatestAttemptInsteadOfOriginalMessageAge() {
        val id = message()
        now += DeliveryRetryController.COOLDOWN_MS
        assertTrue(controller().retry(id))
        assertEquals(0, db.markTimedOutPendingMessages(now - 45_000))
        assertEquals(1, db.markTimedOutPendingMessages(now))
    }

    @Test fun upgradeFrom22PreservesMessageAndDoesNotInventRetryPayload() {
        db.close()
        context.deleteDatabase("aethermesh.db")
        val old = context.openOrCreateDatabase("aethermesh.db", Context.MODE_PRIVATE, null)
        old.execSQL("""CREATE TABLE messages (id INTEGER PRIMARY KEY, sender_id INTEGER,
            recipient_id INTEGER, content TEXT, timestamp INTEGER, channel TEXT,
            packet_id INTEGER, status TEXT, is_encrypted INTEGER, heard_count INTEGER, heard_nodes TEXT)""")
        old.execSQL("INSERT INTO messages VALUES (1, 10, 20, 'preserved', 100, '', 123, 'FAILED', 1, 0, '')")
        old.version = 22
        old.close()
        db = DatabaseHelper(context)
        store = OutboundDeliveryStore(db)
        assertEquals("preserved", db.getAllMessages().single().content)
        assertTrue(store.candidates(20, 10, now).isEmpty())
        assertEquals(23, db.readableDatabase.version)
    }
}
