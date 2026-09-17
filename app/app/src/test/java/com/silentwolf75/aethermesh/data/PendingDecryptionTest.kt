package com.silentwolf75.aethermesh.data

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Encrypted messages that arrive before the right key is saved used to be
 * stored as a placeholder with the ciphertext discarded, so they were lost for
 * good. The ciphertext now stays on the row until a key recovers it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PendingDecryptionTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseHelper

    private val chatId = "CHANNEL_Trail"
    private val aad = "10>ffffffff#Trail"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("aethermesh.db")
        db = DatabaseHelper(context)
    }

    @After fun close() { db.close() }

    private fun storeUndecryptable(cipher: String, packetId: Int): Long {
        val resolved = IncomingChatPolicy.resolveContent(encrypted = true, hasKey = false, raw = cipher, decrypted = "")
        return db.insertMessage(
            senderId = 10,
            recipientId = ChatSendPolicy.BROADCAST,
            content = resolved.content,
            channel = "Trail",
            packetId = packetId,
            isEncrypted = true,
            pendingCipher = resolved.pendingCipherText?.let { PendingCipher(it, chatId, aad) }
        )
    }

    @Test fun ciphertextSurvivesUntilCorrectKeyRecoversIt() {
        val cipher = ChatCrypto.encrypt("meet at the ridge", "right-key", aad)
        assertNotNull(cipher)
        val rowId = storeUndecryptable(cipher!!, packetId = 7)
        assertEquals(IncomingChatPolicy.ERROR_NO_KEY, db.getAllMessages().single().content)

        // A wrong key leaves the row pending.
        val pending = db.getPendingDecryptions(chatId).single()
        assertEquals(rowId, pending.rowId)
        assertTrue(IncomingChatPolicy.isDecryptFailure(ChatCrypto.decrypt(pending.cipherText, "wrong-key", pending.cryptoContext)))

        val plain = ChatCrypto.decrypt(pending.cipherText, "right-key", pending.cryptoContext)
        db.resolvePendingDecryption(pending.rowId, plain)

        assertEquals("meet at the ridge", db.getAllMessages().single().content)
        assertTrue(db.getPendingDecryptions(chatId).isEmpty())
    }

    @Test fun pendingRowsAreScopedToTheirChat() {
        val cipher = ChatCrypto.encrypt("hi", "k", aad)!!
        storeUndecryptable(cipher, packetId = 8)
        assertTrue(db.getPendingDecryptions("CHANNEL_Other").isEmpty())
        assertEquals(1, db.getPendingDecryptions(chatId).size)
    }

    @Test fun readableMessagesAreNeverPending() {
        db.insertMessage(10, ChatSendPolicy.BROADCAST, "plain", "Trail", 9, "SENT", false)
        assertTrue(db.getPendingDecryptions(chatId).isEmpty())
    }

    @Test fun upgradeFrom23KeepsMessagesAndAddsCipherColumns() {
        db.close()
        context.deleteDatabase("aethermesh.db")
        val old = context.openOrCreateDatabase("aethermesh.db", Context.MODE_PRIVATE, null)
        old.execSQL(
            """CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender_id INTEGER,
            recipient_id INTEGER, content TEXT, timestamp INTEGER, channel TEXT,
            packet_id INTEGER DEFAULT 0, status TEXT DEFAULT 'SENT', is_encrypted INTEGER DEFAULT 0,
            heard_count INTEGER DEFAULT 0, heard_nodes TEXT DEFAULT '', wire_packet BLOB,
            attempt_count INTEGER NOT NULL DEFAULT 0, last_attempt_at INTEGER NOT NULL DEFAULT 0,
            next_retry_at INTEGER NOT NULL DEFAULT 0, expires_at INTEGER NOT NULL DEFAULT 0)"""
        )
        old.execSQL(
            "INSERT INTO messages (id, sender_id, recipient_id, content, timestamp, channel, packet_id, status, is_encrypted) " +
                "VALUES (1, 10, 20, '${IncomingChatPolicy.ERROR_NO_KEY}', 100, 'Trail', 5, 'SENT', 1)"
        )
        old.version = 23
        old.close()

        db = DatabaseHelper(context)
        assertEquals(26, db.readableDatabase.version)
        assertEquals(IncomingChatPolicy.ERROR_NO_KEY, db.getAllMessages().single().content)
        // Messages lost before this version have no ciphertext to recover.
        assertTrue(db.getPendingDecryptions(chatId).isEmpty())
        val cipher = ChatCrypto.encrypt("new", "k", aad)!!
        storeUndecryptable(cipher, packetId = 6)
        assertEquals(1, db.getPendingDecryptions(chatId).size)
    }
}
