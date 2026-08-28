package com.silentwolf75.aethermesh.ui.main

import com.silentwolf75.aethermesh.BuildConfig

/**
 * Online map tile configuration.
 *
 * CARTO raster basemaps now require a free API key (https://carto.com/basemaps/apikey/).
 * Set [BuildConfig.CARTO_API_KEY] via gradle property `cartoApiKey` or env `CARTO_API_KEY`.
 * Without a key, CARTO layers are hidden and OpenTopo / OSM HOT are used instead.
 */
object MapTileConfig {
    private val cartoApiKey: String = BuildConfig.CARTO_API_KEY.trim()

    fun hasCartoApiKey(): Boolean = cartoApiKey.isNotEmpty()

    /** osmdroid appends `{z}/{x}/{y}` + imageExtension — use query string on the extension. */
    fun rasterExtension(baseExt: String = ".png"): String =
        if (hasCartoApiKey()) "$baseExt?key=$cartoApiKey" else baseExt
}
