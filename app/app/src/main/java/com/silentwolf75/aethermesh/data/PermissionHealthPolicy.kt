package com.silentwolf75.aethermesh.data

data class PermissionHealth(
    val missingBle: Boolean,
    val missingLocation: Boolean,
    val missingNotifications: Boolean
) {
    val hasAnyIssue: Boolean
        get() = missingBle || missingLocation || missingNotifications
}

/**
 * Permission banner gates (BLE / location / notifications). Context checks stay
 * in the UI; this owns the boolean logic and bilingual copy.
 */
object PermissionHealthPolicy {
    /** Android 12 (S) — BLUETOOTH_SCAN / CONNECT. */
    const val ANDROID_12_SDK = 31

    fun evaluate(
        sdkInt: Int,
        bleScanGranted: Boolean,
        bleConnectGranted: Boolean,
        fineLocationGranted: Boolean,
        coarseLocationGranted: Boolean,
        bgAlertsEnabled: Boolean,
        notificationsGranted: Boolean
    ): PermissionHealth {
        val missingBle = if (sdkInt >= ANDROID_12_SDK) {
            !bleScanGranted || !bleConnectGranted
        } else {
            !fineLocationGranted
        }
        val missingLocation = !fineLocationGranted && !coarseLocationGranted
        val missingNotifications =
            bgAlertsEnabled && !NotificationGatePolicy.canPost(sdkInt, notificationsGranted)
        return PermissionHealth(missingBle, missingLocation, missingNotifications)
    }

    fun title(spanish: Boolean): String =
        if (spanish) "Permisos necesarios" else "Permissions needed"

    fun settingsLabel(spanish: Boolean): String =
        if (spanish) "Ajustes" else "Settings"

    fun contentDescription(spanish: Boolean): String =
        if (spanish) "Permisos" else "Permissions"

    fun summary(health: PermissionHealth, spanish: Boolean): String {
        val parts = buildList {
            if (health.missingBle) add("Bluetooth")
            if (health.missingLocation) add(if (spanish) "ubicación" else "location")
            if (health.missingNotifications) {
                add(if (spanish) "notificaciones" else "notifications")
            }
        }
        return if (spanish) {
            "Faltan: ${parts.joinToString(", ")}."
        } else {
            "Missing: ${parts.joinToString(", ")}."
        }
    }

    fun why(health: PermissionHealth, spanish: Boolean): String = when {
        health.missingBle && health.missingLocation ->
            if (spanish)
                "Bluetooth vincula la radio; la ubicación aparece en el mapa (y habilita el escaneo en Android antiguo)."
            else
                "Bluetooth links the radio; location shows you on the map (and enables scanning on older Android)."
        health.missingBle ->
            if (spanish) "Se necesita Bluetooth para encontrar y conectar tu nodo."
            else "Bluetooth is required to find and connect your node."
        health.missingLocation ->
            if (spanish) "La ubicación muestra tu posición en el mapa y distancia a otros nodos."
            else "Location shows your position on the map and distance to other nodes."
        health.missingNotifications ->
            if (spanish) "Las notificaciones avisan de chats cuando la app está en segundo plano."
            else "Notifications alert you to chats while the app is in the background."
        else -> ""
    }
}
