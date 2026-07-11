package com.example.aethermesh.ui.main

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class MapHelpersTest {
    private fun zip(entryName: String): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test
    fun validTileArchiveInstallsAndReplacesExistingFile() {
        val directory = createTempDirectory("map-test-").toFile()
        val destination = File(directory, "offline_map.zip").apply { writeText("old") }
        val info = OfflineMapArchive.install(ByteArrayInputStream(zip("3/4/5.png")), destination)
        assertEquals(1, info.entries)
        assertTrue(destination.length() > 3)
        assertFalse(File(directory, "offline_map.zip.backup").exists())
        directory.deleteRecursively()
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathTraversalArchiveIsRejected() {
        val directory = createTempDirectory("map-test-").toFile()
        try {
            OfflineMapArchive.install(ByteArrayInputStream(zip("../outside.png")), File(directory, "map.zip"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectedArchivePreservesExistingMap() {
        val directory = createTempDirectory("map-test-").toFile()
        val destination = File(directory, "map.zip").apply { writeText("known-good") }
        try {
            val result = runCatching {
                OfflineMapArchive.install(ByteArrayInputStream(zip("notes.txt")), destination)
            }
            assertTrue(result.isFailure)
            assertEquals("known-good", destination.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun archiveSizeLimitIsEnforcedBeforeInstall() {
        val directory = createTempDirectory("map-test-").toFile()
        try {
            OfflineMapArchive.install(ByteArrayInputStream(zip("3/4/5.png")), File(directory, "map.zip"), 8)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun kmlUsesLongitudeLatitudeOrder() {
        val kml = MapExport.buildKml(listOf(38.5 to -94.6))
        assertTrue(kml.contains("-94.6,38.5,0"))
        assertTrue(kml.endsWith("</kml>\n"))
    }
}
