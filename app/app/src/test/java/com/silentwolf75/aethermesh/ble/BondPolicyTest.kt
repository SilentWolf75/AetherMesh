package com.silentwolf75.aethermesh.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BondPolicyTest {
    @Test
    fun staleBondIsForgottenOnceOnKeyMismatch() {
        assertTrue(BondPolicy.shouldForgetBond(0x3D, phoneIsBonded = true, alreadyTried = false))
        assertTrue(BondPolicy.shouldForgetBond(5, phoneIsBonded = true, alreadyTried = false))
        assertFalse(BondPolicy.shouldForgetBond(5, phoneIsBonded = true, alreadyTried = true))
    }

    @Test
    fun ordinaryDisconnectsLeaveTheBondAlone() {
        assertFalse(BondPolicy.shouldForgetBond(8, phoneIsBonded = true, alreadyTried = false))   // timeout
        assertFalse(BondPolicy.shouldForgetBond(19, phoneIsBonded = true, alreadyTried = false))  // remote closed
        assertFalse(BondPolicy.shouldForgetBond(5, phoneIsBonded = false, alreadyTried = false))
    }
}
