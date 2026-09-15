package com.silentwolf75.aethermesh.ui.main

import android.content.Context
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Online basemaps for the map tab.
 *
 * CARTO raster tiles now require a free API key — configure `cartoApiKey` in
 * app/gradle.properties (see https://carto.com/basemaps/apikey/). Without it,
 * CARTO layers are omitted and OpenTopo / OSM HOT are the defaults.
 */
enum class MapBasemap(
    val id: String,
    val labelEn: String,
    val labelEs: String
) {
    OPEN_TOPO("opentopo", "Terrain", "Terreno"),
    OSM_HOT("osm_hot", "Streets (OSM)", "Calles (OSM)"),
    CARTO_STREETS("carto_voyager", "Streets (CARTO)", "Calles (CARTO)"),
    CARTO_DARK("carto_dark", "Dark (CARTO)", "Oscuro (CARTO)"),
    OSM_DE("osm_de", "OSM Germany", "OSM Alemania");

    fun label(appLanguage: String): String =
        if (appLanguage == "Spanish") labelEs else labelEn

    fun tileSource(): XYTileSource = when (this) {
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
        OSM_HOT -> osmHotTileSource()
        CARTO_STREETS -> cartoVoyagerTileSource()
        CARTO_DARK -> cartoDarkTileSource()
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
        val DEFAULT = OPEN_TOPO

        fun available(): List<MapBasemap> =
            if (MapTileConfig.hasCartoApiKey()) {
                entries
            } else {
                entries.filter { it != CARTO_STREETS && it != CARTO_DARK }
            }

        fun fromId(id: String?): MapBasemap {
            val match = entries.firstOrNull { it.id == id } ?: DEFAULT
            return if (match in available()) match else DEFAULT
        }

        fun load(context: Context): MapBasemap {
            val prefs = context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
            val stored = prefs.getString(PREF_KEY, null)
            if (stored != null) {
                val resolved = fromId(stored)
                if (resolved.id != stored) {
                    prefs.edit().putString(PREF_KEY, resolved.id).apply()
                }
                return resolved
            }
            // Migrate legacy dark_tiles toggle (pre-basemap picker).
            val dark = prefs.getBoolean("dark_tiles", false)
            val migrated = when {
                dark && MapTileConfig.hasCartoApiKey() -> CARTO_DARK
                dark -> OPEN_TOPO
                else -> DEFAULT
            }
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
