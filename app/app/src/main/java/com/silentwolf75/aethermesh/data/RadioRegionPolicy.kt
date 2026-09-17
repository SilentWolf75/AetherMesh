package com.silentwolf75.aethermesh.data

import java.util.Locale

/**
 * LoRa regulatory region codes shared by UI and telemetry.
 * Matches firmware: 0 = US915, 1 = EU868. Unknown/unset is typically -1.
 */
object RadioRegionPolicy {
    const val US915 = 0
    const val EU868 = 1

    const val US915_MHZ = 906.875
    const val EU868_MHZ = 869.525

    fun isKnown(region: Int): Boolean = region == US915 || region == EU868

    /** Short catalog code: US915 / EU868 / Unknown. */
    fun shortLabel(region: Int): String = when (region) {
        US915 -> "US915"
        EU868 -> "EU868"
        else -> "Unknown"
    }

    fun frequencyMhz(region: Int): Double = when (region) {
        EU868 -> EU868_MHZ
        else -> US915_MHZ // default / US915 / unset prefs
    }

    /** e.g. "906.875MHz" for channel header strip. */
    fun frequencyCompact(region: Int): String =
        String.format(Locale.US, "%.3fMHz", frequencyMhz(region))

    /** Dropdown / settings line: "US915 (906.875 MHz)". */
    fun labelWithFrequency(region: Int): String = when (region) {
        EU868 -> "EU868 (869.525 MHz)"
        else -> "US915 (906.875 MHz)"
    }

    /** First-run setup picker: geography hint. */
    fun setupChoiceLabel(region: Int): String = when (region) {
        EU868 -> "EU868 (Europe)"
        else -> "US915 (North America)"
    }

    val SETUP_CHOICES: List<Pair<Int, String>> = listOf(
        US915 to setupChoiceLabel(US915),
        EU868 to setupChoiceLabel(EU868)
    )
}
