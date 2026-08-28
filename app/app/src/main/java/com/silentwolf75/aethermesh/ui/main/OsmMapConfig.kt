package com.silentwolf75.aethermesh.ui.main

import android.content.Context
import android.util.Log
import com.silentwolf75.aethermesh.BuildConfig
import org.osmdroid.config.Configuration
import java.io.File

/**
 * OSM / osmdroid bootstrap.
 *
 * OpenStreetMap's public tile servers block generic agents and often reject
 * `com.example.*` package IDs with HTTP 403 ("Access Denied" / Forbidden).
 * We always identify as AetherMesh with a contact URL. Online tiles default to
 * OpenTopo / OSM HOT; optional CARTO layers need a free API key in gradle.properties.
 */
object OsmMapConfig {
    private const val TAG = "OsmMapConfig"
    private const val PREFS = "osmdroid_prefs"

    /** Stable, policy-friendly User-Agent (never the Android applicationId alone). */
    fun userAgent(): String {
        val version = BuildConfig.VERSION_NAME.ifBlank { "dev" }
        return "AetherMesh/$version (+https://github.com/SilentWolf75/AetherMesh; Android)"
    }

    fun configure(context: Context) {
        val app = context.applicationContext
        val base = File(app.filesDir, "osmdroid").apply { mkdirs() }
        val tileCache = File(base, "tiles").apply { mkdirs() }
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = userAgent()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tileCache
        // Prefer app-private storage (scoped storage friendly).
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cfg.load(app, prefs)
        // load() can overwrite UA from prefs — force ours after.
        cfg.userAgentValue = userAgent()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tileCache
        // Drop cached CARTO "API KEY REQUIRED" watermark tiles (Aug 2025 policy change).
        purgeLegacyBlockedCacheOnce(app, tileCache, prefs, "purged_carto_watermark_v1")
        Log.i(TAG, "osmdroid ready ua='${cfg.userAgentValue}' cache=${tileCache.absolutePath}")
    }

    fun clearTileCache(context: Context): Boolean {
        val cache = Configuration.getInstance().osmdroidTileCache
            ?: File(File(context.applicationContext.filesDir, "osmdroid"), "tiles")
        return deleteRecursively(cache).also {
            cache.mkdirs()
            Log.i(TAG, "Tile cache cleared at ${cache.absolutePath}")
        }
    }

    private fun purgeLegacyBlockedCacheOnce(
        app: Context,
        tileCache: File,
        prefs: android.content.SharedPreferences,
        prefKey: String
    ) {
        if (prefs.getBoolean(prefKey, false)) return
        deleteRecursively(tileCache)
        tileCache.mkdirs()
        deleteRecursively(File(app.cacheDir, "osmdroid"))
        prefs.edit().putBoolean(prefKey, true).apply()
        Log.i(TAG, "Purged legacy tile cache ($prefKey)")
    }

    private fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }
}
