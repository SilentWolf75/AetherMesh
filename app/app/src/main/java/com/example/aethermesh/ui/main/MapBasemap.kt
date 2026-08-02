package com.example.aethermesh.ui.main

import android.content.Context
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Online basemaps that actually work for mesh apps.
 *
 * Avoids tile.openstreetmap.org (MAPNIK) — frequently returns 403 Access Denied.
 * Defaults to CARTO (CDN). OpenTopo / OSM.de are Meshtastic-proven fallbacks.
 */
enum class MapBasemap(
    val id: String,
    val labelEn: String,
    val labelEs: String
) {
    CARTO_STREETS("carto_voyager", "Streets", "Calles"),
    CARTO_DARK("carto_dark", "Dark", "Oscuro"),
    OPEN_TOPO("opentopo", "Terrain", "Terreno"),
    OSM_DE("osm_de", "OSM Germany", "OSM Alemania");

    fun label(appLanguage: String): String =
        if (appLanguage == "Spanish") labelEs else labelEn

    fun tileSource(): XYTileSource = when (this) {
        CARTO_STREETS -> cartoVoyagerTileSource()
        CARTO_DARK -> cartoDarkTileSource()
        OPEN_TOPO -> XYTileSource(
            "OpenTopoMap",
            0,
            17,
            256,
            ".png",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            ),
            "© OpenStreetMap, © OpenTopoMap (CC-BY-SA)"
        )
        OSM_DE -> XYTileSource(
            "OpenStreetMapDE",
            0,
            19,
            256,
            ".png",
            arrayOf("https://tile.openstreetmap.de/"),
            "© OpenStreetMap contributors"
        )
    }

    companion object {
        const val PREF_KEY = "basemap_id"
        val DEFAULT = CARTO_STREETS

        fun fromId(id: String?): MapBasemap =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        fun load(context: Context): MapBasemap {
            val prefs = context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
            val stored = prefs.getString(PREF_KEY, null)
            if (stored != null) return fromId(stored)
            // Migrate legacy dark_tiles toggle.
            val dark = prefs.getBoolean("dark_tiles", false)
            val migrated = if (dark) CARTO_DARK else DEFAULT
            prefs.edit().putString(PREF_KEY, migrated.id).apply()
            return migrated
        }

        fun save(context: Context, basemap: MapBasemap) {
            context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, basemap.id)
                .putBoolean("dark_tiles", basemap == CARTO_DARK)
                .apply()
        }
    }
}
