package com.silentwolf75.aethermesh.data

import com.silentwolf75.aethermesh.proto.ConfigResult

sealed class RemoteConfigResultAction {
    object IgnoreStale : RemoteConfigResultAction()
    data class Handle(
        val statusText: String,
        val persistBaseline: Boolean,
        val showFeedback: Boolean
    ) : RemoteConfigResultAction()
}

/**
 * Remote ConfigResult UX gates and bilingual copy. SharedFlow emit stays in
 * [AetherMeshRepository]; dialog state stays in the UI.
 */
object RemoteConfigResultPolicy {
    /**
     * Only accept a ConfigResult while the UI is waiting on a concrete request.
     * Zero IDs must not act as wildcards — that let unsolicited / forged results
     * through before the user sent anything.
     */
    fun matchesPendingRequest(pendingPacketId: Int, requestPacketId: Int): Boolean {
        if (pendingPacketId == 0 || requestPacketId == 0) return false
        return pendingPacketId == requestPacketId
    }

    /**
     * Live reportOnly CONFIG must only hydrate the dialog after an explicit Load.
     * Node-id match alone is not enough — unsolicited / post-auth reports must not
     * clear busy or overwrite the form mid-edit.
     */
    fun acceptLiveReport(
        awaitingReport: Boolean,
        reportNodeId: Long,
        expectedNodeId: Long
    ): Boolean = awaitingReport &&
        expectedNodeId != 0L &&
        reportNodeId == expectedNodeId

    fun shouldPersistBaseline(status: ConfigResult.Status): Boolean =
        status == ConfigResult.Status.APPLIED ||
            status == ConfigResult.Status.APPLIED_REBOOTING

    fun shouldShowFeedback(status: ConfigResult.Status): Boolean =
        shouldPersistBaseline(status) ||
            status == ConfigResult.Status.AUTH_FAILED ||
            status == ConfigResult.Status.REJECTED_ROLE2 ||
            status == ConfigResult.Status.REJECTED_FIXED_POS

    fun statusText(
        status: ConfigResult.Status,
        message: String,
        spanish: Boolean
    ): String = when (status) {
        ConfigResult.Status.REPORT_OK ->
            if (spanish) "Informe enviado por el nodo…" else "Node sent report…"
        ConfigResult.Status.APPLIED ->
            if (spanish) "Aplicado (sin reinicio)." else "Applied (no reboot)."
        ConfigResult.Status.APPLIED_REBOOTING ->
            if (spanish) "Aplicado — el nodo se reinicia." else "Applied — node rebooting."
        ConfigResult.Status.AUTH_FAILED ->
            if (spanish) "Autenticación fallida." else "Authentication failed."
        ConfigResult.Status.REJECTED_ROLE2 ->
            if (spanish) "Rol Repetidor rechazado (sin BLE)." else "Repeater role rejected (no BLE)."
        ConfigResult.Status.REJECTED_FIXED_POS ->
            if (spanish) "Posición fija rechazada." else "Fixed position rejected."
        else -> message.ifBlank {
            if (spanish) "Respuesta: $status" else "Result: $status"
        }
    }

    fun decide(
        pendingPacketId: Int,
        requestPacketId: Int,
        status: ConfigResult.Status,
        message: String,
        spanish: Boolean
    ): RemoteConfigResultAction {
        if (!matchesPendingRequest(pendingPacketId, requestPacketId)) {
            return RemoteConfigResultAction.IgnoreStale
        }
        val text = statusText(status, message, spanish)
        return RemoteConfigResultAction.Handle(
            statusText = text,
            persistBaseline = shouldPersistBaseline(status),
            showFeedback = shouldShowFeedback(status)
        )
    }

    fun liveSettingsLoaded(spanish: Boolean): String =
        if (spanish) "Ajustes cargados del nodo." else "Live settings loaded."

    fun disconnectCancelled(spanish: Boolean): String =
        if (spanish) "Desconectado — solicitud cancelada."
        else "Disconnected — request cancelled."

    fun passwordRequired(spanish: Boolean): String =
        if (spanish) "Contraseña requerida" else "Password required"

    fun requestingSettings(spanish: Boolean): String =
        if (spanish) "Solicitando ajustes al nodo…" else "Requesting settings from node…"

    fun requestSendFailed(spanish: Boolean): String =
        if (spanish) "No se pudo enviar la solicitud." else "Could not send request."

    fun loadBaselineFirst(spanish: Boolean): String =
        if (spanish) "Carga primero los ajustes del nodo."
        else "Load live settings from the node first."

    fun noChanges(spanish: Boolean): String =
        if (spanish) "Sin cambios que aplicar." else "No changes to apply."

    fun sendingChanges(spanish: Boolean): String =
        if (spanish) "Enviando cambios…" else "Sending changes…"

    fun applySendFailed(spanish: Boolean): String =
        if (spanish) "No se pudo enviar." else "Could not send."

    fun promptEnterPassword(spanish: Boolean): String =
        if (spanish) "Introduce la contraseña y carga los ajustes del nodo."
        else "Enter password and load live settings from the node."
}
