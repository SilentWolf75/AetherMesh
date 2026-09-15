package com.silentwolf75.aethermesh.data

import android.content.ContentValues
import com.silentwolf75.aethermesh.proto.MeshPacket

/** Durable retry state; no new chat bubble or packet identity is created on retry. */
class OutboundDeliveryStore(private val helper: DatabaseHelper) : DeliveryRetryStore {
    fun hasPayload(messageId: Long): Boolean = helper.readableDatabase.rawQuery(
        "SELECT wire_packet IS NOT NULL FROM messages WHERE id = ?", arrayOf(messageId.toString())
    ).use { it.moveToFirst() && it.getInt(0) != 0 }

    fun track(messageId: Long, payload: ByteArray, now: Long) {
        val values = ContentValues().apply {
            put("wire_packet", payload)
            put("attempt_count", 1)
            put("last_attempt_at", now)
            put("next_retry_at", now + DeliveryRetryController.COOLDOWN_MS)
            put("expires_at", now + DeliveryRetryController.LIFETIME_MS)
        }
        helper.writableDatabase.update("messages", values, "id = ?", arrayOf(messageId.toString()))
    }

    fun expire(now: Long): Int = helper.writableDatabase.update(
        "messages", ContentValues().apply { put("status", "EXPIRED") },
        "wire_packet IS NOT NULL AND expires_at <= ? AND status IN ('FAILED', 'QUEUED', 'PENDING')",
        arrayOf(now.toString())
    )

    override fun candidates(peerId: Long, senderId: Long, now: Long): List<OutboundAttempt> {
        expire(now)
        return helper.readableDatabase.rawQuery(
            """SELECT id, wire_packet FROM messages WHERE recipient_id = ? AND sender_id = ?
                AND channel = '' AND wire_packet IS NOT NULL AND status IN ('FAILED', 'QUEUED')
                AND next_retry_at <= ? AND expires_at > ? AND attempt_count < ?
                ORDER BY timestamp ASC LIMIT 5""",
            arrayOf(peerId.toString(), senderId.toString(), now.toString(), now.toString(),
                DeliveryRetryController.MAX_ATTEMPTS.toString())
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(OutboundAttempt(cursor.getLong(0), cursor.getBlob(1))) }
        }
    }

    override fun claim(messageId: Long, senderId: Long, now: Long, manual: Boolean): ByteArray? {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val eligibility = if (manual) {
                "status IN ('FAILED', 'QUEUED', 'EXPIRED')"
            } else {
                "status IN ('FAILED', 'QUEUED') AND next_retry_at <= $now AND expires_at > $now AND attempt_count < ${DeliveryRetryController.MAX_ATTEMPTS}"
            }
            val payload = db.rawQuery(
                """SELECT wire_packet FROM messages WHERE id = ? AND sender_id = ?
                    AND channel = '' AND wire_packet IS NOT NULL AND $eligibility""",
                arrayOf(messageId.toString(), senderId.toString())
            ).use { if (it.moveToFirst()) it.getBlob(0) else null } ?: return null
            // Reserve space for the node's own retries so an immediate manual resend
            // is forwarded by relays whose dedup cache still contains the last attempt.
            val retryPayload = try {
                val packet = MeshPacket.parseFrom(payload)
                packet.toBuilder().setRetryCount(Math.addExact(packet.retryCount, 64)).build().toByteArray()
            } catch (e: Exception) {
                return null
            }
            val attempts = if (manual) "1" else "attempt_count + 1"
            val expiry = if (manual) now + DeliveryRetryController.LIFETIME_MS else null
            db.execSQL(
                """UPDATE messages SET attempt_count = $attempts, status = 'PENDING',
                    last_attempt_at = ?, next_retry_at = ?, expires_at = COALESCE(?, expires_at), wire_packet = ? WHERE id = ?""",
                arrayOf(now, now + DeliveryRetryController.COOLDOWN_MS, expiry, retryPayload, messageId)
            )
            db.setTransactionSuccessful()
            return retryPayload
        } finally {
            db.endTransaction()
        }
    }

    override fun handoffFailed(messageId: Long) {
        helper.writableDatabase.update(
            "messages", ContentValues().apply { put("status", "FAILED") },
            "id = ? AND status = 'PENDING'", arrayOf(messageId.toString())
        )
    }
}
