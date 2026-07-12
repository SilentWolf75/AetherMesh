package com.example.aethermesh.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches BLE OTA / DFU packages published to GitHub Pages
 * (`firmware/ota-manifest.json`) and verifies them before flashing.
 */
object FirmwareCatalog {
    private const val TAG = "FirmwareCatalog"
    const val BASE_URL = "https://silentwolf75.github.io/AetherMesh/firmware/"
    const val OTA_MANIFEST_URL = BASE_URL + "ota-manifest.json"

    data class Artifact(
        val name: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val kind: String,
        val board: String
    ) {
        val downloadUrl: String get() = BASE_URL + file
        val isZip: Boolean get() = file.endsWith(".zip", ignoreCase = true)
    }

    data class DownloadResult(
        val bytes: ByteArray,
        val fileName: String,
        val cacheUri: Uri?
    )

    /** Map telemetry `node_model` strings to ota-manifest `board` ids. */
    fun boardIdForModel(model: String?): String? {
        if (model.isNullOrBlank()) return null
        val m = model.lowercase()
        return when {
            m.contains("heltec") && m.contains("v3") -> "heltec-v3"
            m.contains("heltec") -> "heltec-v4"
            m.contains("t-deck") || m.contains("tdeck") -> "t-deck"
            m.contains("crowpanel") -> "crowpanel-35"
            m.contains("19026") -> "rak19026"
            m.contains("1w") || m.contains("3401") -> "rak3401-1w"
            m.contains("rak") -> "rak4631"
            else -> null
        }
    }

    fun matchesBoard(artifact: Artifact, boardId: String): Boolean {
        if (artifact.board.equals(boardId, ignoreCase = true)) return true
        // Fallback for older manifests without board field.
        val f = artifact.file.lowercase()
        return when (boardId) {
            "heltec-v4" -> f.contains("heltec-v4") || (f.contains("heltec") && !f.contains("v3"))
            "heltec-v3" -> f.contains("heltec-v3") || f.contains("v3")
            "t-deck" -> f.contains("t-deck")
            "crowpanel-35" -> f.contains("crowpanel")
            "rak4631" -> f.contains("rak4631")
            "rak3401-1w" -> f.contains("rak3401") || f.contains("1w")
            "rak19026" -> f.contains("rak19026") || f.contains("19026")
            else -> false
        }
    }

    suspend fun fetchOtaManifest(): List<Artifact> = withContext(Dispatchers.IO) {
        try {
            fetchManifestUrl(OTA_MANIFEST_URL)
        } catch (e: IllegalStateException) {
            val msg = e.message.orEmpty()
            if (msg.contains("HTTP 404")) {
                throw IllegalStateException(
                    "OTA catalog not published yet (404). It appears after the next GitHub Pages deploy — or pick a local .bin/.zip for now."
                )
            }
            throw e
        }
    }

    private fun fetchManifestUrl(url: String): List<Artifact> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("OTA manifest HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseManifest(body)
        } finally {
            connection.disconnect()
        }
    }

    fun parseManifest(json: String): List<Artifact> {
        val arr = JSONArray(json)
        val out = ArrayList<Artifact>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                Artifact(
                    name = obj.optString("name"),
                    file = obj.getString("file"),
                    size = obj.optLong("size", 0L),
                    sha256 = obj.optString("sha256"),
                    kind = obj.optString("kind", "ota"),
                    board = obj.optString("board", "")
                )
            )
        }
        return out.filter { it.kind.equals("ota", ignoreCase = true) || it.kind.isBlank() }
    }

    fun pickForModel(artifacts: List<Artifact>, model: String?): Artifact? {
        val boardId = boardIdForModel(model) ?: return null
        return artifacts.firstOrNull { matchesBoard(it, boardId) }
    }

    suspend fun download(
        context: Context,
        artifact: Artifact,
        onProgress: ((Int) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        val connection = (URL(artifact.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download HTTP $code for ${artifact.file}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: artifact.size
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val out = java.io.ByteArrayOutputStream(
                    if (total > 0 && total < Int.MAX_VALUE) total.toInt() else 256 * 1024
                )
                var readTotal = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    readTotal += n
                    if (total > 0 && onProgress != null) {
                        val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
                out.toByteArray()
            }
            if (artifact.size > 0L && bytes.size.toLong() != artifact.size) {
                throw IllegalStateException(
                    "Size mismatch for ${artifact.file}: expected ${artifact.size}, got ${bytes.size}"
                )
            }
            if (artifact.sha256.isNotBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                if (!digest.equals(artifact.sha256, ignoreCase = true)) {
                    throw IllegalStateException("SHA-256 mismatch for ${artifact.file}")
                }
            }
            Log.d(TAG, "Downloaded ${artifact.file} (${bytes.size} bytes)")
            val cacheUri = if (artifact.isZip) {
                writeCacheZip(context, artifact.file, bytes)
            } else {
                null
            }
            DownloadResult(bytes = bytes, fileName = artifact.file, cacheUri = cacheUri)
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCacheZip(context: Context, fileName: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "firmware").apply { mkdirs() }
        val safeName = fileName.substringAfterLast('/').ifBlank { "firmware.zip" }
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
