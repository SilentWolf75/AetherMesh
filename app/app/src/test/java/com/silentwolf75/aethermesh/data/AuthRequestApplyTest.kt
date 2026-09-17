package com.silentwolf75.aethermesh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.silentwolf75.aethermesh.proto.MeshPacket

class AuthRequestApplyTest {
    @Test
    fun unlockTargetsRecipientZeroAndDoesNotChangePassword() {
        val packet = AuthRequestApply.buildUnlock(0x11L, 9, "secret")
        assertEquals(0, packet.recipientId)
        assertEquals(1, packet.hopLimit)
        assertFalse(packet.wantAck)
        assertEquals(MeshPacket.PayloadCase.AUTH_REQUEST, packet.payloadCase)
        assertEquals("secret", packet.authRequest.password)
        assertFalse(packet.authRequest.isChangePassword)
        assertEquals("", packet.authRequest.newPassword)
    }

    @Test
    fun changePasswordSetsBothFields() {
        val packet = AuthRequestApply.buildChangePassword(0x11L, 10, "old", "new")
        assertTrue(packet.authRequest.isChangePassword)
        assertEquals("old", packet.authRequest.password)
        assertEquals("new", packet.authRequest.newPassword)
        assertEquals(0, packet.recipientId)
    }

    @Test
    fun canChangePasswordRequiresBothTrimmed() {
        assertFalse(AuthRequestApply.canChangePassword("", "n"))
        assertFalse(AuthRequestApply.canChangePassword("c", "   "))
        assertTrue(AuthRequestApply.canChangePassword(" c ", " n "))
    }
}
