package com.silentwolf75.aethermesh.ble

/**
 * Pure helpers for staging a Nordic DFU .zip into app cache before the DFU
 * service opens it. File I/O and FileProvider stay in the repository.
 */
object DfuZipPolicy {
    const val CACHE_SUBDIR = "firmware"
    const val DEFAULT_NAME = "firmware.zip"
    const val ERR_READ = "Could not read DFU zip"
    const val ERR_EMPTY = "DFU zip is empty"

    fun requireBytes(bytes: ByteArray?): ByteArray {
        if (bytes == null) throw Exception(ERR_READ)
        if (bytes.isEmpty()) throw Exception(ERR_EMPTY)
        return bytes
    }

    /**
     * Sanitize a SAF / content URI last path segment into a .zip filename
     * safe for cacheDir (no path separators; always ends with .zip).
     */
    fun safeFileName(lastPathSegment: String?): String {
        val nameHint = lastPathSegment?.substringAfterLast('/') ?: DEFAULT_NAME
        val base = nameHint.ifBlank { DEFAULT_NAME }
        return if (base.lowercase().endsWith(".zip")) base else "$base.zip"
    }
}
