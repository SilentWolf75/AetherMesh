package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.BuildConfig
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches BLE OTA / DFU packages from:
 * - **Stable:** GitHub Releases (`SilentWolf75/AetherMesh`) — assets matched by
 *   PlatformIO env tags (`heltec_v4`, `rak4631`, …)
 * - **Latest (tip):** GitHub Pages `firmware/ota-manifest.json` (CI deploy)
 *
 * Verifies size (+ SHA-256 when the catalog provides one) before flashing.
 * Cryptographic image signing is a documented follow-up (see SECURE-RELEASES).
 */
object FirmwareCatalog {
    private const val TAG = "FirmwareCatalog"
    const val BASE_URL = "https://silentwolf75.github.io/AetherMesh/firmware/"
    const val OTA_MANIFEST_URL = BASE_URL + "ota-manifest.json"
    const val GITHUB_REPO = "SilentWolf75/AetherMesh"
    const val GITHUB_RELEASES_URL =
        "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=30"
    const val GITHUB_RELEASES_WEB = "https://github.com/$GITHUB_REPO/releases"
    val USER_AGENT = "AetherMesh-Android/${BuildConfig.VERSION_NAME}"

    /**
     * Honest offline status for airplane mode / no phone data.
     * Mesh BLE + LoRa stay local; only the optional GitHub catalog needs the internet.
     */
    const val OFFLINE_CATALOG_STATUS =
        "Offline — OTA catalog needs phone data (Wi‑Fi/cellular). Mesh stays local. Use a local .bin/.zip, or turn data on and Check again."

    /** True when the phone has a validated internet path (not mesh/BLE). */
    fun isPhoneDataAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOfflineNetworkError(error: Throwable?): Boolean {
        var t: Throwable? = error
        while (t != null) {
            when (t) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is InterruptedIOException -> return true
            }
            val msg = t.message.orEmpty().lowercase()
            if (msg.contains("unable to resolve host") ||
                msg.contains("failed to connect") ||
                msg.contains("network is unreachable") ||
                msg.contains("software caused connection abort") ||
                msg.contains("cleartext") && msg.contains("not permitted")
            ) {
                return true
            }
            t = t.cause
        }
        return false
    }

    /**
     * Two channels, both deliberate publications. There is no continuous "tip"
     * channel: an automatic build of whatever last landed is not something to
     * hand a radio in the field, and a hidden third source could serve a build
     * the user did not choose.
     */
    enum class Channel {
        /** Published GitHub Releases — never a pre-release. */
        RELEASE,
        /** GitHub pre-releases: published on purpose, not yet field-qualified. */
        BETA
    }

    data class Artifact(
        val name: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val kind: String,
        val board: String,
        /** Absolute download URL when not under [BASE_URL] (Releases). */
        val absoluteUrl: String? = null,
        val channel: Channel = Channel.RELEASE,
        val releaseTag: String? = null,
        /** Semver base from Pages manifest or parsed from a Release tag (e.g. 1.3.0). */
        val version: String? = null
    ) {
        val downloadUrl: String
            get() = absoluteUrl ?: (BASE_URL + file)
        val isZip: Boolean get() = file.endsWith(".zip", ignoreCase = true)
        /** Prefer release tag, then explicit version, for OTA UI “Available” lines. */
        val displayVersion: String?
            get() = releaseTag?.takeIf { it.isNotBlank() }
                ?: version?.takeIf { it.isNotBlank() }
                ?: Regex("""\bv?\d+\.\d+\.\d+\b""").find(name)?.value

        /**
         * Best-effort label matching Telemetry.firmware_version (e.g. `1.3.0-b75ad7c`).
         * Used to refresh the Room cache immediately after a successful BLE OTA/DFU.
         */
        fun expectedFirmwareVersionLabel(): String? {
            val hash = Regex("""-([0-9a-f]{7,40})(?:-ota)?\.(?:bin|zip)$""", RegexOption.IGNORE_CASE)
                .find(file)?.groupValues?.getOrNull(1)
                ?: Regex("""\b([0-9a-f]{7})\b""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(1)
            val base = version?.trim()?.removePrefix("v")?.takeIf { it.isNotBlank() }
                ?: Regex("""\bv?(\d+\.\d+\.\d+)\b""").find(name)?.groupValues?.getOrNull(1)
                ?: releaseTag?.trim()?.removePrefix("v")?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+.*""")) }
            return when {
                !base.isNullOrBlank() && !hash.isNullOrBlank() -> "$base-$hash"
                !base.isNullOrBlank() -> base
                !hash.isNullOrBlank() -> hash
                else -> null
            }
        }
    }

    data class DownloadResult(
        val bytes: ByteArray,
        val fileName: String,
        val cacheUri: Uri?
    )

    data class CatalogResult(
        val artifact: Artifact?,
        val channel: Channel,
        val status: String,
        /** All OTA-ish assets considered (for debugging / future multi-pick). */
        val candidates: List<Artifact> = emptyList()
    )

    /** Board metadata is generated from config/boards.json. */
    fun boardIdForModel(model: String?): String? = BoardRegistry.forModel(model)?.id

    fun envTagForBoard(boardId: String?): String? = BoardRegistry.forId(boardId)?.env

    fun matchesBoard(artifact: Artifact, boardId: String): Boolean {
        if (artifact.board.isNotBlank()) return artifact.board.equals(boardId, ignoreCase = true)
        return inferBoardFromFileName(artifact.file).equals(boardId, ignoreCase = true)
    }

    fun inferBoardFromFileName(fileName: String): String? = BoardRegistry.forFile(fileName)?.id

    /**
     * Refuse a package that clearly targets a different board than the connected node.
     * @return error message, or null if OK / inconclusive.
     */
    fun boardMismatchError(fileName: String, expectedBoardId: String?): String? {
        if (expectedBoardId.isNullOrBlank()) return null
        val inferred = inferBoardFromFileName(fileName) ?: return null
        if (inferred.equals(expectedBoardId, ignoreCase = true)) return null
        return "Wrong board firmware: file looks like $inferred, node is $expectedBoardId"
    }

    /** True if this asset is a BLE OTA / DFU package (not USB merge / UF2). */
    fun isBleOtaAssetName(fileName: String): Boolean {
        val f = fileName.lowercase()
        if (f.contains("-usb") || f.endsWith(".uf2")) return false
        return f.endsWith("-ota.bin") ||
            f.endsWith(".zip") ||
            (f.endsWith(".bin") && !f.contains("bootloader") && !f.contains("partition"))
    }

    suspend fun fetchForModel(
        model: String?,
        channel: Channel = Channel.RELEASE
    ): CatalogResult = withContext(Dispatchers.IO) {
        val boardId = boardIdForModel(model)
        when (channel) {
            Channel.BETA -> fetchReleases(boardId, model, wantPrerelease = true)
            Channel.RELEASE -> fetchReleases(boardId, model, wantPrerelease = false)
        }
    }

    private fun fetchReleases(
        boardId: String?,
        model: String?,
        wantPrerelease: Boolean
    ): CatalogResult {
        val wanted = if (wantPrerelease) Channel.BETA else Channel.RELEASE
        val label = if (wantPrerelease) "beta" else "release"
        return try {
            val releases = fetchGithubReleases()
            // Stable must never serve a pre-release, and beta must never serve a
            // stable build as if it were one: a channel that quietly falls back
            // to the other is worse than an empty channel.
            val chosen = releases.filter { it.prerelease == wantPrerelease }
            // Releases publish ota-manifest.json alongside the binaries. Without
            // it the API gives no digest and a download can only be size-checked,
            // so pull the digests in and verify the bytes like the Pages channel.
            val digests = chosen.firstOrNull()?.let { digestsForRelease(it) }.orEmpty()
            val assets = chosen.flatMap { it.toArtifacts() }
                .filter { isBleOtaAssetName(it.file) }
                .map { artifact ->
                    val digest = digests[artifact.file]
                    if (digest.isNullOrBlank()) artifact else artifact.copy(sha256 = digest)
                }
            val match = when {
                boardId != null -> assets.firstOrNull { matchesBoard(it, boardId) }
                else -> null
            }
            when {
                match != null -> CatalogResult(
                    artifact = match,
                    channel = wanted,
                    status = "Found ${match.name} ($label ${match.displayVersion ?: match.releaseTag ?: "release"})",
                    candidates = assets
                )
                assets.isEmpty() -> CatalogResult(
                    artifact = null,
                    channel = wanted,
                    status = "Nothing published on the $label channel yet. " +
                        "Pick a local .bin/.zip, or see $GITHUB_RELEASES_WEB",
                    candidates = emptyList()
                )
                boardId == null -> CatalogResult(
                    artifact = null,
                    channel = wanted,
                    status = "Releases found on the $label channel, but node model is unknown — pick a local file or wait for telemetry.",
                    candidates = assets
                )
                else -> CatalogResult(
                    artifact = null,
                    channel = wanted,
                    status = "No $label release asset matches this board (${envTagForBoard(boardId) ?: boardId}). See $GITHUB_RELEASES_WEB",
                    candidates = assets
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "$label Releases fetch failed: ${e.message}")
            if (isOfflineNetworkError(e)) {
                return CatalogResult(
                    artifact = null,
                    channel = wanted,
                    status = OFFLINE_CATALOG_STATUS
                )
            }
            CatalogResult(
                artifact = null,
                channel = wanted,
                status = "Could not reach GitHub Releases: ${e.message}"
            )
        }
    }

    suspend fun fetchOtaManifest(): List<Artifact> = withContext(Dispatchers.IO) {
        fetchOtaManifestSync()
    }

    private fun fetchOtaManifestSync(): List<Artifact> {
        try {
            return fetchManifestUrl(OTA_MANIFEST_URL)
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

    private data class GhRelease(
        val tag: String,
        val name: String,
        val prerelease: Boolean,
        val assets: List<GhAsset>
    )

    private data class GhAsset(
        val name: String,
        val size: Long,
        val downloadUrl: String
    )

    /** Parse `v1.3.0` / `1.3.0` tags so Stable prefers the newest semver release. */
    internal fun parseTagVersionParts(tag: String): List<Int>? {
        val m = Regex("""v?(\d+)\.(\d+)\.(\d+)""").find(tag.trim()) ?: return null
        return m.groupValues.drop(1).map { it.toInt() }
    }

    /**
     * file name -> sha256, read from the release's own ota-manifest.json. An
     * absent or unreadable manifest yields no digests rather than a failure:
     * the download then falls back to the size check it had before.
     */
    private fun digestsForRelease(release: GhRelease): Map<String, String> {
        val manifest = release.assets.firstOrNull { it.name == "ota-manifest.json" } ?: return emptyMap()
        return try {
            val text = readUrlText(manifest.downloadUrl)
            val arr = JSONArray(text)
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val file = obj.optString("file")
                    val sha = obj.optString("sha256")
                    if (file.isNotBlank() && sha.matches(Regex("[0-9a-fA-F]{64}"))) put(file, sha)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Release manifest unreadable (${release.tag}): ${e.message}")
            emptyMap()
        }
    }

    private fun readUrlText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchGithubReleases(): List<GhRelease> {
        val connection = (URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub Releases HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseGithubReleases(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGithubReleases(json: String): List<GhRelease> {
        val arr = JSONArray(json)
        val out = ArrayList<GhRelease>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
            val assets = ArrayList<GhAsset>(assetsArr.length())
            for (j in 0 until assetsArr.length()) {
                val a = assetsArr.getJSONObject(j)
                assets.add(
                    GhAsset(
                        name = a.getString("name"),
                        size = a.optLong("size", 0L),
                        downloadUrl = a.optString(
                            "browser_download_url",
                            a.optString("url")
                        )
                    )
                )
            }
            out.add(
                GhRelease(
                    tag = obj.optString("tag_name"),
                    name = obj.optString("name"),
                    prerelease = obj.optBoolean("prerelease", false),
                    assets = assets
                )
            )
        }
        // Prefer non-prerelease, then highest semver tag (API is usually newest-first,
        // but equal prerelease flags alone must not leave an older stable preferred).
        return out.sortedWith(
            compareBy<GhRelease> { if (it.prerelease) 1 else 0 }
                .thenByDescending { release ->
                    val parts = parseTagVersionParts(release.tag) ?: return@thenByDescending 0L
                    (parts[0].toLong() shl 32) or (parts[1].toLong() shl 16) or parts[2].toLong()
                }
        )
    }

    private fun GhRelease.toArtifacts(): List<Artifact> {
        val ver = parseTagVersionParts(tag)?.joinToString(".")
        return assets.map { a ->
            val board = inferBoardFromFileName(a.name).orEmpty()
            Artifact(
                name = if (name.isNotBlank()) "$name · ${a.name}" else a.name,
                file = a.name,
                size = a.size,
                sha256 = "", // filled in from the release manifest when it publishes one
                kind = "ota",
                board = board,
                absoluteUrl = a.downloadUrl,
                channel = if (prerelease) Channel.BETA else Channel.RELEASE,
                releaseTag = tag,
                version = ver
            )
        }
    }

    private fun fetchManifestUrl(url: String): List<Artifact> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
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
            val ver = obj.optString("version").takeIf { it.isNotBlank() }
            out.add(
                Artifact(
                    name = obj.optString("name"),
                    file = obj.getString("file"),
                    size = obj.optLong("size", 0L),
                    sha256 = obj.optString("sha256"),
                    kind = obj.optString("kind", "ota"),
                    board = obj.optString("board", ""),
                    channel = Channel.RELEASE,
                    version = ver
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
            setRequestProperty("User-Agent", USER_AGENT)
            // GitHub release assets sometimes need Accept: application/octet-stream
            if (artifact.absoluteUrl != null) {
                setRequestProperty("Accept", "application/octet-stream")
            }
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
            Log.d(TAG, "Downloaded ${artifact.file} (${bytes.size} bytes, channel=${artifact.channel})")
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
