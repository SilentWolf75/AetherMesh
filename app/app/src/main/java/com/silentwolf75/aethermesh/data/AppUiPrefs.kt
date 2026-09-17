package com.silentwolf75.aethermesh.data

/** Phone-side UI prefs in `aethermesh_prefs`. Not node radio config. */
object AppUiPrefs {
    const val FILE = "aethermesh_prefs"
    const val THEME = "app_theme"
    const val LANGUAGE = "app_language"
    const val IMPERIAL = "use_imperial_units"
    const val BG_ALERTS = "bg_alerts_enabled"
    const val PHONE_GPS_SHARING = "enable_phone_gps_sharing"
    /** Last known power-save from the BLE-connected node (scan empty-state hint). */
    const val LAST_POWER_SAVE = "last_connected_power_save"

    const val THEME_SYSTEM = "System"
    const val THEME_DARK = "Dark"
    const val THEME_LIGHT = "Light"
    val THEMES = listOf(THEME_SYSTEM, THEME_DARK, THEME_LIGHT)

    const val LANG_ENGLISH = "English"
    const val LANG_SPANISH = "Spanish"
    val LANGUAGES = listOf(LANG_ENGLISH, LANG_SPANISH)

    fun clampTheme(value: String?): String =
        if (value != null && value in THEMES) value else THEME_SYSTEM

    fun clampLanguage(value: String?): String =
        if (value != null && value in LANGUAGES) value else LANG_ENGLISH

    fun isSpanish(language: String?): Boolean =
        clampLanguage(language) == LANG_SPANISH
}
