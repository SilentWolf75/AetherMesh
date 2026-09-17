package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DfuZipPolicyTest {
    @Test
    fun requireBytesRejectsNullAndEmpty() {
        assertThrows(Exception::class.java) { DfuZipPolicy.requireBytes(null) }
        assertThrows(Exception::class.java) { DfuZipPolicy.requireBytes(ByteArray(0)) }
        assertEquals(3, DfuZipPolicy.requireBytes(byteArrayOf(1, 2, 3)).size)
    }

    @Test
    fun safeFileNameAlwaysEndsWithZip() {
        assertEquals("firmware.zip", DfuZipPolicy.safeFileName(null))
        assertEquals("firmware.zip", DfuZipPolicy.safeFileName(""))
        assertEquals("firmware.zip", DfuZipPolicy.safeFileName("   "))
        assertEquals("pkg.zip", DfuZipPolicy.safeFileName("pkg.zip"))
        assertEquals("pkg.zip", DfuZipPolicy.safeFileName("pkg"))
        assertEquals("aether-1.3.2.zip", DfuZipPolicy.safeFileName("content://x/aether-1.3.2"))
        assertEquals("aether.zip", DfuZipPolicy.safeFileName("folder/aether.zip"))
    }
}
