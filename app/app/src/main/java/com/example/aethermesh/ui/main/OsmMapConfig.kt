package com.example.aethermesh.ui.main

import android.content.Context
import android.util.Log
import com.example.aethermesh.BuildConfig
import org.osmdroid.config.Configuration
import java.io.File

/**
 * OSM / osmdroid bootstrap.
 *
 * OpenStreetMap's public tile servers block generic agents and often reject
 * `com.example.*` package IDs with HTTP 403 ("Access Denied" / Forbidden).
 * We always identify as AetherMesh with a contact URL, and prefer CARTO
 * basemaps for online tiles (MAPNIK is reserved for offline archives only).
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
        cfg.load(app, app.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        // load() can overwrite UA from prefs — force ours after.
        cfg.userAgentValue = userAgent()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tileCache
        // One-time wipe of old MAPNIK / blocked-tile cache that shows as gray "Access Denied".
        purgeLegacyBlockedCacheOnce(app, tileCache)
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

    private fun purgeLegacyBlockedCacheOnce(app: Context, tileCache: File) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("purged_mapnik_cache_v1", false)) return
        deleteRecursively(tileCache)
        tileCache.mkdirs()
        // Also drop common legacy external cache paths from older builds.
        deleteRecursively(File(app.cacheDir, "osmdroid"))
        prefs.edit().putBoolean("purged_mapnik_cache_v1", true).apply()
        Log.i(TAG, "Purged legacy tile cache (MAPNIK/403 leftovers)")
    }

    private fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }
}
