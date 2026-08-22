package com.silentwolf75.aethermesh.data

/**
 * SharedPreferences filenames for secrets. Must stay in sync with
 * [backup_rules.xml] and [data_extraction_rules.xml] exclude paths.
 */
object SecurePrefsNames {
    const val ENCRYPTED = "aethermesh_secure_prefs"
    const val FALLBACK = "aethermesh_secure_prefs_fallback"

    /** Paths that must appear in both backup rule files (domain sharedpref, no .xml suffix). */
    val backupExcludePaths: Set<String> = setOf(ENCRYPTED, FALLBACK)
}
