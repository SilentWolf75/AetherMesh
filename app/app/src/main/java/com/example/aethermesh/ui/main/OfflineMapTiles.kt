package com.example.aethermesh.ui.main

import android.content.Context
import android.util.Log
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import java.io.File

/**
 * Wires an imported osmdroid archive (`offline_map.zip` / sqlite / mbtiles) so the
 * map actually renders offline tiles instead of only saving the file.
 */
object OfflineMapTiles {
    private const val TAG = "OfflineMapTiles"
    const val ARCHIVE_NAME = "offline_map.zip"

    fun archiveFile(context: Context): File =
        File(File(context.filesDir, "osmdroid"), ARCHIVE_NAME)

    fun hasArchive(context: Context): Boolean {
        val file = archiveFile(context)
        return file.exists() && file.length() > 0L
    }

    fun archiveInfo(context: Context): OfflineMapArchiveInfo? {
        val file = archiveFile(context)
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            OfflineMapArchive.validate(file)
        } catch (_: Exception) {
            OfflineMapArchiveInfo(entries = 0, compressedBytes = file.length(), uncompressedBytes = 0)
        }
    }

    fun clearArchive(context: Context): Boolean {
        val file = archiveFile(context)
        val backup = File(file.parentFile, "$ARCHIVE_NAME.backup")
        var ok = true
        if (file.exists()) ok = file.delete() && ok
        if (backup.exists()) ok = backup.delete() && ok
        return ok
    }

    /**
     * Apply offline tiles when an archive is present; otherwise use the selected online basemap.
     * @return true if offline provider is active
     */
    fun applyTileSource(
        mapView: MapView,
        context: Context,
        basemap: MapBasemap = MapBasemap.load(context)
    ): Boolean {
        OsmMapConfig.configure(context)
        val offline = archiveFile(context)
        if (offline.exists() && offline.length() > 0L) {
            try {
                val provider = OfflineTileProvider(
                    SimpleRegisterReceiver(context),
                    arrayOf(offline)
                )
                // Zip folders rarely match online source names — load any tiles in the archive.
                provider.archives?.forEach { it.setIgnoreTileSource(true) }
                mapView.setTileProvider(provider)
                val tileSource = resolveOfflineTileSource(provider, offline)
                mapView.setTileSource(tileSource)
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable offline tiles; falling back to online", e)
            }
        }
        applyOnlineTileSource(mapView, context, basemap)
        return false
    }

    fun applyOnlineTileSource(mapView: MapView, context: Context, basemap: MapBasemap) {
        OsmMapConfig.configure(context)
        try {
            mapView.setTileProvider(MapTileProviderBasic(context))
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset online tile provider", e)
        }
        mapView.setTileSource(basemap.tileSource())
        mapView.overlayManager.tilesOverlay.setColorFilter(null)
    }

    private fun resolveOfflineTileSource(
        provider: OfflineTileProvider,
        archive: File
    ): ITileSource {
        val archives = provider.archives
        if (archives != null) {
            for (file in archives) {
                val sources = file.tileSources
                if (!sources.isNullOrEmpty()) {
                    return FileBasedTileSource.getSource(sources.iterator().next())
                }
            }
        }
        return FileBasedTileSource.getSource(archive.name)
    }
}
