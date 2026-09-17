package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CannedReplyPolicyTest {
    @Test
    fun everyReplyFitsEncryptedByteBudget() {
        assertTrue(CannedReplyPolicy.allFitEncryptedBudget())
        CannedReplyPolicy.REPLIES.forEach { reply ->
            assertTrue(reply.id, CannedReplyPolicy.fitsEncryptedBudget(reply))
        }
    }

    @Test
    fun applyToDraftOnlyWhenComposerEmpty() {
        val reply = CannedReplyPolicy.REPLIES.first { it.id == "need_help" }
        assertEquals("Need help", CannedReplyPolicy.applyToDraft("", reply, spanish = false))
        assertEquals("Necesito ayuda", CannedReplyPolicy.applyToDraft("", reply, spanish = true))
        assertEquals("already typing", CannedReplyPolicy.applyToDraft("already typing", reply, spanish = false))
    }

    @Test
    fun labelsFollowLanguage() {
        val reply = CannedReplyPolicy.REPLIES.first { it.id == "on_my_way" }
        assertEquals("On my way", CannedReplyPolicy.label(reply, spanish = false))
        assertEquals("En camino", CannedReplyPolicy.label(reply, spanish = true))
        assertFalse(CannedReplyPolicy.REPLIES.isEmpty())
    }
}
