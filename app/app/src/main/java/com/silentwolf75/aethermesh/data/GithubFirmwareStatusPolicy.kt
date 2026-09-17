package com.silentwolf75.aethermesh.data

/**
 * English GitHub OTA catalog/download status lines and Spanish UI mapping.
 * Network fetch stays in [FirmwareCatalog] / [com.silentwolf75.aethermesh.ui.main.MainScreenViewModel].
 */
object GithubFirmwareStatusPolicy {
    const val CHECKING_RELEASES = "Checking GitHub Releases (stable)…"
    const val CHECKING_BETA = "Checking GitHub Releases (beta)…"
    const val CHECKING_PAGES = "Checking GitHub Pages (latest)…"
    const val CATALOG_NOT_ON_PAGES =
        "OTA catalog not on GitHub Pages yet. Use a local .bin for now, or retry after the site redeploys."

    fun checking(channel: FirmwareCatalog.Channel): String = when (channel) {
        FirmwareCatalog.Channel.STABLE -> CHECKING_RELEASES
        FirmwareCatalog.Channel.BETA -> CHECKING_BETA
        FirmwareCatalog.Channel.LATEST -> CHECKING_PAGES
    }

    fun downloadingFile(fileName: String): String = "Downloading $fileName…"

    fun downloadingProgress(percent: Int): String = "Downloading… $percent%"

    fun verified(fileName: String): String = "Verified $fileName"

    fun downloadedSizeOk(fileName: String): String = "Downloaded $fileName (size OK)"

    fun downloadFailed(detail: String?): String = "Download failed: ${detail ?: "error"}"

    fun couldNotReach(detail: String?): String = "Could not reach GitHub: ${detail ?: "error"}"

    fun resolveFetchError(error: Throwable): String {
        if (FirmwareCatalog.isOfflineNetworkError(error)) {
            return FirmwareCatalog.OFFLINE_CATALOG_STATUS
        }
        val detail = error.message.orEmpty()
        return when {
            detail.contains("404") || detail.contains("not published", ignoreCase = true) ->
                if (detail.contains("OTA catalog not published")) detail
                else CATALOG_NOT_ON_PAGES
            else -> couldNotReach(error.message)
        }
    }

    fun resolveDownloadError(error: Throwable): String =
        if (FirmwareCatalog.isOfflineNetworkError(error)) FirmwareCatalog.OFFLINE_CATALOG_STATUS
        else downloadFailed(error.message)

    fun localize(status: String, spanish: Boolean): String {
        if (!spanish || status.isBlank()) return status
        return when {
            status == FirmwareCatalog.OFFLINE_CATALOG_STATUS || status.startsWith("Offline —") ->
                "Sin conexión — el catálogo OTA necesita datos del teléfono (Wi‑Fi/móvil). La malla sigue local. Usa un .bin/.zip local, o activa datos y pulsa Buscar."
            status.startsWith("Checking GitHub Releases") ->
                "Consultando GitHub Releases (estable)…"
            status.startsWith("Checking GitHub Pages") ->
                "Consultando GitHub Pages (último)…"
            status.startsWith("Checking GitHub") -> "Consultando GitHub por firmware…"
            status.startsWith("Found ") && status.contains("(stable") -> {
                val name = status.substringAfter("Found ").substringBefore(" (stable")
                val tag = status.substringAfter("(stable ").removeSuffix(")")
                "Encontrado $name (estable $tag)"
            }
            status.startsWith("Found ") && status.contains("(latest") -> {
                val name = status.substringAfter("Found ").substringBefore(" (latest")
                val rest = status.substringAfter("(latest").removeSuffix(")")
                "Encontrado $name (último$rest)"
            }
            status.startsWith("Found ") -> "Encontrado ${status.removePrefix("Found ")}"
            status == "No OTA builds published yet." -> "Aún no hay builds OTA publicados."
            status == "No OTA package matches this node model." ->
                "Ningún paquete OTA coincide con este modelo de nodo."
            status.startsWith("No GitHub Release assets yet") ->
                status.replace(
                    "No GitHub Release assets yet — using latest Pages build:",
                    "Aún no hay assets en GitHub Releases — usando el build Pages más reciente:"
                )
            status.startsWith("No stable GitHub Release for this board yet.") ->
                "Aún no hay Release estable para esta placa. " +
                    localize(
                        status.removePrefix("No stable GitHub Release for this board yet. ").trim(),
                        spanish = true
                    )
            status.startsWith("No stable Release asset matches") ->
                "Ningún asset de Release estable coincide con esta placa" +
                    status.substringAfter("this board")
            status.startsWith("Stable Releases found, but node model") ->
                "Hay Releases estables, pero el modelo del nodo es desconocido — elige un archivo local o espera la telemetría."
            status.startsWith("Connect a known board") ->
                "Conecta una placa conocida para elegir automáticamente, o elige un archivo local."
            status.startsWith("Releases unavailable") ->
                "Releases no disponibles" + status.removePrefix("Releases unavailable")
            status.startsWith("OTA catalog not on GitHub Pages") ->
                "El catálogo OTA aún no está en GitHub Pages. Usa un .bin local por ahora, o reintenta tras el redespliegue."
            status.startsWith("OTA catalog not published") ->
                "Catálogo OTA no publicado aún."
            status.startsWith("Could not reach GitHub Releases:") ->
                "No se pudo contactar GitHub Releases:${status.removePrefix("Could not reach GitHub Releases:")}"
            status.startsWith("Could not reach GitHub:") ->
                "No se pudo contactar GitHub:${status.removePrefix("Could not reach GitHub:")}"
            status.startsWith("Downloading… ") ->
                "Descargando… ${status.removePrefix("Downloading… ")}"
            status.startsWith("Downloading ") ->
                "Descargando ${status.removePrefix("Downloading ")}"
            status.startsWith("Verified ") ->
                "Verificado ${status.removePrefix("Verified ")}"
            status.startsWith("Downloaded ") && status.contains("(size OK)") ->
                "Descargado ${status.removePrefix("Downloaded ").removeSuffix(" (size OK)")} (tamaño OK)"
            status.startsWith("Download failed") ->
                "Error de descarga${status.removePrefix("Download failed")}"
            else -> status
        }
    }
}
