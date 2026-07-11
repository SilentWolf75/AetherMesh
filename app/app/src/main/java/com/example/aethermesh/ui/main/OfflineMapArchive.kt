package com.example.aethermesh.ui.main

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class OfflineMapArchiveInfo(val entries: Int, val compressedBytes: Long, val uncompressedBytes: Long)

object OfflineMapArchive {
    private const val DEFAULT_MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L
    private const val MAX_ENTRIES = 100_000
    private const val MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L
    private val supportedSuffixes = setOf("png", "jpg", "jpeg", "webp", "tile", "sqlite", "mbtiles")

    fun install(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES
    ): OfflineMapArchiveInfo {
        destination.parentFile?.mkdirs()
        val temporary = File.createTempFile("offline-map-", ".zip", destination.parentFile)
        try {
            var copied = 0L
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= maxArchiveBytes) { "Map archive exceeds ${maxArchiveBytes / (1024 * 1024)} MB" }
                    output.write(buffer, 0, count)
                }
            }
            val info = validate(temporary)
            replace(destination, temporary)
            return info
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun validate(file: File): OfflineMapArchiveInfo {
        require(file.length() > 0) { "Map archive is empty" }
        var entries = 0
        var uncompressed = 0L
        var hasTiles = false
        ZipFile(file).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                entries++
                require(entries <= MAX_ENTRIES) { "Map archive contains too many entries" }
                val normalized = entry.name.replace('\\', '/')
                require(!normalized.startsWith('/') && normalized.split('/').none { it == ".." }) {
                    "Map archive contains an unsafe path"
                }
                if (!entry.isDirectory) {
                    val size = entry.size.coerceAtLeast(0)
                    uncompressed += size
                    require(uncompressed <= MAX_UNCOMPRESSED_BYTES) { "Map archive expands beyond 2 GB" }
                    hasTiles = hasTiles || normalized.substringAfterLast('.', "").lowercase() in supportedSuffixes
                }
            }
        }
        require(entries > 0) { "Map archive has no entries" }
        require(hasTiles) { "Map archive does not contain supported map tiles" }
        return OfflineMapArchiveInfo(entries, file.length(), uncompressed)
    }

    private fun replace(destination: File, temporary: File) {
        val backup = File(destination.parentFile, "${destination.name}.backup")
        if (backup.exists()) backup.delete()
        if (destination.exists()) require(destination.renameTo(backup)) { "Could not prepare existing map archive" }
        if (!temporary.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            error("Could not install map archive")
        }
        if (backup.exists()) backup.delete()
    }
}
