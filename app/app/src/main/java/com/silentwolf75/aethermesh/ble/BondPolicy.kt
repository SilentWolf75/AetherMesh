package com.silentwolf75.aethermesh.ble

/**
 * Pairing housekeeping for nodes whose Bluetooth link is encrypted.
 *
 * If a node loses its pairing keys (full erase, factory reset) while the phone
 * keeps its own, every encrypted connection fails and the node looks dead.
 * The phone's stale bond has to go before the two can pair again.
 */
object BondPolicy {
    /** GATT/HCI disconnect reasons that mean "the keys did not match". */
    private val KEY_MISMATCH_STATUSES = setOf(
        5,    // GATT_INSUFFICIENT_AUTHENTICATION
        6,    // HCI PIN or key missing
        15,   // GATT_INSUFFICIENT_ENCRYPTION
        0x3D, // HCI connection terminated due to MIC failure
        137   // GATT_AUTH_FAIL
    )

    fun isKeyMismatch(status: Int): Boolean = status in KEY_MISMATCH_STATUSES

    /**
     * Forget the phone's bond only when it exists, the failure says the keys
     * disagree, and this has not already been tried for this node since the
     * app started (so a node that simply refuses pairing is not looped on).
     */
    fun shouldForgetBond(status: Int, phoneIsBonded: Boolean, alreadyTried: Boolean): Boolean =
        phoneIsBonded && !alreadyTried && isKeyMismatch(status)
}
