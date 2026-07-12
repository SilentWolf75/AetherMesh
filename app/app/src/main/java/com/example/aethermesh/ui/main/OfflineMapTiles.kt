package com.example.aethermesh.ui.main

import android.content.Context
import android.util.Log
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    /**
     * Apply offline tiles when an archive is present; otherwise use online MAPNIK/Carto.
     * @return true if offline provider is active
     */
    fun applyTileSource(mapView: MapView, context: Context, darkOnline: Boolean): Boolean {
        val offline = archiveFile(context)
        if (offline.exists() && offline.length() > 0L) {
            try {
                val provider = OfflineTileProvider(
                    SimpleRegisterReceiver(context),
                    arrayOf(offline)
                )
                // Zip folders rarely match MAPNIK/Carto names — load any tiles in the archive.
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
        applyOnlineTileSource(mapView, context, darkOnline)
        return false
    }

    fun applyOnlineTileSource(mapView: MapView, context: Context, darkOnline: Boolean) {
        try {
            mapView.setTileProvider(MapTileProviderBasic(context))
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset online tile provider", e)
        }
        val source: ITileSource =
            if (darkOnline) cartoDarkTileSource() else TileSourceFactory.MAPNIK
        mapView.setTileSource(source)
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
