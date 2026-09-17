package com.silentwolf75.aethermesh.data

import android.content.SharedPreferences

sealed class LocalConfigFixed {
    data class Ready(val latitude: Float, val longitude: Float, val altitude: Int) : LocalConfigFixed()
    object Invalid : LocalConfigFixed()
}

/**
 * Local Settings apply gate. BLE send stays in the repository; snackbar
 * display stays in the UI. Fixed position must not be Null Island.
 */
object LocalConfigSavePolicy {
    fun isAllowed(fixedEnabled: Boolean, latitude: Float, longitude: Float): Boolean =
        !fixedEnabled || PhoneLocationShare.isValidFix(latitude.toDouble(), longitude.toDouble())

    fun parse(
        fixedEnabled: Boolean,
        latInput: String,
        lonInput: String,
        altInput: String
    ): LocalConfigFixed {
        val lat = latInput.toFloatOrNull() ?: 0f
        val lon = lonInput.toFloatOrNull() ?: 0f
        val alt = altInput.toIntOrNull() ?: 0
        if (!isAllowed(fixedEnabled, lat, lon)) return LocalConfigFixed.Invalid
        return LocalConfigFixed.Ready(lat, lon, alt)
    }

    fun invalidFixedMessage(spanish: Boolean): String =
        if (spanish) "Posición fija inválida — usa coordenadas reales (no 0,0)."
        else "Invalid fixed position — use real coordinates (not 0,0)."

    fun sentMessage(powerSave: Boolean, spanish: Boolean): String = when {
        powerSave && spanish ->
            "¡Ajustes enviados! El nodo se reiniciará. Con Ahorro de batería: pulsa el botón del nodo, luego escanea — BLE anuncia ~5 min."
        powerSave ->
            "Config sent! Node will reboot. With Battery Saver: press the device button, then scan — BLE advertises ~5 min."
        spanish ->
            "¡Ajustes enviados! El nodo se reiniciará. Otros nodos no cambian — usa Configuración remota para igualar el perfil de radio."
        else ->
            "Config sent! Node will reboot. Other nodes are unchanged — use Remote Config to match the radio profile."
    }

    fun failedMessage(spanish: Boolean): String =
        if (spanish) "Error al enviar la configuración." else "Failed to send configuration."

    fun persistPrefs(
        prefs: SharedPreferences,
        snapshot: NodeSettingsSnapshot,
        meshHopLimit: Int,
        rebroadcastTxdelayX100: Int
    ) {
        val duty = snapshot.gpsDutyIntervalSecs.coerceIn(300, 3600)
        NodeSettingsBackup.writeToPrefs(prefs, snapshot.copy(gpsDutyIntervalSecs = duty))
        prefs.edit()
            .putBoolean(NodeSettingsPrefs.KEY_REGION_CONFIGURED, true)
            .putInt(NodeSettingsPrefs.KEY_MESH_HOP_LIMIT, LocalNodeConfigApply.clampHops(meshHopLimit))
            .putInt(
                NodeSettingsPrefs.KEY_REBROADCAST_TXDELAY,
                LocalNodeConfigApply.clampTxdelay(rebroadcastTxdelayX100)
            )
            .apply()
    }
}
