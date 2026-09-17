package com.silentwolf75.aethermesh.ui.main

object DeployConfirmCopy {
    fun roleLabel(role: Int, spanish: Boolean): String = when (role) {
        1 -> if (spanish) "Router" else "Router"
        2 -> if (spanish) "Repetidor" else "Repeater"
        else -> if (spanish) "Cliente" else "Client"
    }

    fun gpsLabel(gpsMode: Int, dutySecs: Int, spanish: Boolean): String = when (gpsMode) {
        1 -> if (spanish) "GPS apagado" else "GPS off"
        2 -> if (spanish) "GPS ciclo ${dutySecs / 60}m" else "GPS duty ${dutySecs / 60}m"
        else -> if (spanish) "GPS siempre" else "GPS always on"
    }

    fun summary(
        spanish: Boolean,
        role: Int,
        gpsMode: Int,
        dutySecs: Int,
        powerSave: Boolean,
        telemetrySecs: Int,
        txPower: Int
    ): String = buildString {
        append(if (spanish) "Rol: " else "Role: ")
        appendLine(roleLabel(role, spanish))
        appendLine(gpsLabel(gpsMode, dutySecs, spanish))
        append(if (spanish) "Ahorro de batería: " else "Battery saver: ")
        appendLine(
            if (powerSave) (if (spanish) "sí" else "yes")
            else (if (spanish) "no" else "no")
        )
        append(if (spanish) "Telemetría: ${telemetrySecs}s" else "Telemetry: ${telemetrySecs}s")
        if (txPower in 10..22) {
            append(if (spanish) " · TX ${txPower} dBm" else " · TX ${txPower} dBm")
        }
    }
}
