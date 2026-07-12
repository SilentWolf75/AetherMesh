package com.example.aethermesh.ui.main

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aethermesh.data.AetherMeshRepository
import com.example.aethermesh.data.ChatMessage
import com.example.aethermesh.data.ChannelConfig
import com.example.aethermesh.data.MeshNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.aethermesh.data.RouteHopInfo

class MainScreenViewModel(private val repository: AetherMeshRepository) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val isBleConnected: StateFlow<Boolean> = repository.isBleConnected
    val bleConnectionPhase = repository.bleConnectionPhase
    val bleReconnectAttempt = repository.bleReconnectAttempt
    val bleReconnectGaveUp = repository.bleReconnectGaveUp
    val messages: StateFlow<List<ChatMessage>> = repository.messages
    val nodes: StateFlow<List<MeshNode>> = repository.nodes
    val channels: StateFlow<List<String>> = repository.channels
    val selectedChannel: StateFlow<String> = repository.selectedChannel
    val activeChatId: StateFlow<Long?> = repository.activeChatId
    val observedRoutes: StateFlow<Map<Long, RouteHopInfo>> = repository.observedRoutes
    val meshDiagnostics = repository.meshDiagnostics
    val traceRouteState = repository.traceRouteState
    val isDeviceAuthenticated: StateFlow<Boolean> = repository.isDeviceAuthenticated
    val authenticationRequired: StateFlow<Boolean?> = repository.authenticationRequired

    fun getMeshDiagnosticsHistory() = repository.getMeshDiagnosticsHistory()

    val connectedDeviceName: String?
        get() = repository.bleManager.connectedDeviceName

    val connectedDeviceAddress: String?
        get() = repository.bleManager.getConnectedDeviceAddress()

    val connectedNodeId: Long
        get() = repository.bleManager.connectedNodeId

    // BLE scanning state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // GPS Breadcrumbs list (persisted across tab switches)
    val breadcrumbs = androidx.compose.runtime.mutableStateListOf<Pair<Double, Double>>()

    /** Phone GPS used by map + node details (shared across Nav3 destinations). */
    private val _phoneLocation = MutableStateFlow<org.osmdroid.util.GeoPoint?>(null)
    val phoneLocation: StateFlow<org.osmdroid.util.GeoPoint?> = _phoneLocation.asStateFlow()

    fun updatePhoneLocation(lat: Double, lon: Double) {
        _phoneLocation.value = org.osmdroid.util.GeoPoint(lat, lon)
    }

    /** After popping NodeDetails, MainScreen should switch to Chats. */
    private val _pendingOpenChatsTab = MutableStateFlow(false)
    val pendingOpenChatsTab: StateFlow<Boolean> = _pendingOpenChatsTab.asStateFlow()

    fun requestOpenChatsTab() {
        _pendingOpenChatsTab.value = true
    }

    fun consumeOpenChatsTab(): Boolean {
        if (!_pendingOpenChatsTab.value) return false
        _pendingOpenChatsTab.value = false
        return true
    }

    private val _pendingOpenMapTab = MutableStateFlow(false)
    val pendingOpenMapTab: StateFlow<Boolean> = _pendingOpenMapTab.asStateFlow()

    /** When opening Map from NodeDetails, fly to / select this node. */
    private val _pendingFocusNodeId = MutableStateFlow<Long?>(null)
    val pendingFocusNodeId: StateFlow<Long?> = _pendingFocusNodeId.asStateFlow()

    fun requestOpenMapTab(focusNodeId: Long? = null) {
        if (focusNodeId != null && focusNodeId != 0L) {
            _pendingFocusNodeId.value = focusNodeId
        }
        _pendingOpenMapTab.value = true
    }

    fun consumeOpenMapTab(): Boolean {
        if (!_pendingOpenMapTab.value) return false
        _pendingOpenMapTab.value = false
        return true
    }

    fun consumeFocusNodeId(): Long? {
        val id = _pendingFocusNodeId.value ?: return null
        _pendingFocusNodeId.value = null
        return id
    }

    private val _pendingRangeTestTargetId = MutableStateFlow<Long?>(null)
    val pendingRangeTestTargetId: StateFlow<Long?> = _pendingRangeTestTargetId.asStateFlow()

    fun requestRangeTestDialog(targetId: Long) {
        _pendingRangeTestTargetId.value = targetId
    }

    fun dismissRangeTestDialog() {
        _pendingRangeTestTargetId.value = null
    }

    /** Notification deep-link: open this chat after MainActivity resumes. */
    private val _pendingChatDeepLink = MutableStateFlow<PendingChatDeepLink?>(null)
    val pendingChatDeepLink: StateFlow<PendingChatDeepLink?> = _pendingChatDeepLink.asStateFlow()

    private val _chatDeepLinkEpoch = MutableStateFlow(0)
    val chatDeepLinkEpoch: StateFlow<Int> = _chatDeepLinkEpoch.asStateFlow()

    fun requestChatDeepLink(channel: String?, dmPeerId: Long?) {
        _pendingChatDeepLink.value = PendingChatDeepLink(channel = channel, dmPeerId = dmPeerId)
        _chatDeepLinkEpoch.value = _chatDeepLinkEpoch.value + 1
        requestOpenChatsTab()
    }

    fun consumeChatDeepLink(): PendingChatDeepLink? {
        val link = _pendingChatDeepLink.value ?: return null
        _pendingChatDeepLink.value = null
        return link
    }

    /** Overlay remote-config dialog for this node (does not switch tabs). */
    private val _pendingRemoteConfigNodeId = MutableStateFlow<Long?>(null)
    val pendingRemoteConfigNodeId: StateFlow<Long?> = _pendingRemoteConfigNodeId.asStateFlow()

    fun requestRemoteConfig(nodeId: Long) {
        _pendingRemoteConfigNodeId.value = nodeId
    }

    fun dismissRemoteConfigDialog() {
        _pendingRemoteConfigNodeId.value = null
    }

    fun addBreadcrumb(lat: Double, lon: Double) {
        val prefs = repository.appPrefs()
        if (!prefs.getBoolean("enable_phone_gps_sharing", true)) return
        if (breadcrumbs.isEmpty()) {
            breadcrumbs.add(lat to lon)
        } else {
            val last = breadcrumbs.last()
            if (calculateDistance(last.first, last.second, lat, lon) > 0.005) {
                breadcrumbs.add(lat to lon)
                if (breadcrumbs.size > 500) {
                    breadcrumbs.removeAt(0)
                }
            }
        }
    }

    fun clearBreadcrumbs() {
        breadcrumbs.clear()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    // BLE scanning results
    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _scanBlockReason = MutableStateFlow(com.example.aethermesh.ble.BleScanBlockReason.None)
    val scanBlockReason: StateFlow<com.example.aethermesh.ble.BleScanBlockReason> =
        _scanBlockReason.asStateFlow()

    init {
        // Setup BLE device discovery callback
        repository.bleManager.onDeviceDiscovered = { name, mac, rssi ->
            viewModelScope.launch {
                val currentList = _scannedDevices.value.toMutableList()
                val index = currentList.indexOfFirst { it.mac.equals(mac, ignoreCase = true) }
                if (index == -1) {
                    currentList.add(BleDeviceItem(name, mac, rssi))
                } else {
                    val existing = currentList[index]
                    val betterName = existing.name != name && name != "AetherMesh Node"
                    currentList[index] = existing.copy(
                        name = if (betterName) name else existing.name,
                        rssi = rssi
                    )
                }
                _scannedDevices.value = currentList.sortedByDescending { it.rssi }
                if (index == -1) {
                    Log.d(TAG, "BLE Discovered: $name ($mac) rssi=$rssi")
                }
            }
        }
        repository.bleManager.onScanBlocked = { reason ->
            _scanBlockReason.value = reason
            if (reason != com.example.aethermesh.ble.BleScanBlockReason.None) {
                _isScanning.value = false
            }
        }
    }

    fun startScanning() {
        _scannedDevices.value = emptyList()
        _scanBlockReason.value = com.example.aethermesh.ble.BleScanBlockReason.None
        _isScanning.value = true
        repository.bleManager.startScan()
        // Auto stop scan after 12 seconds to conserve power
        viewModelScope.launch {
            kotlinx.coroutines.delay(12000)
            if (_isScanning.value) {
                stopScanning()
            }
        }
    }

    fun stopScanning() {
        _isScanning.value = false
        repository.bleManager.stopScan()
    }

    fun connectToDevice(mac: String) {
        stopScanning()
        repository.bleManager.connect(mac)
    }

    fun disconnectDevice() {
        repository.bleManager.disconnect()
    }

    fun retryBleConnection() {
        repository.bleManager.retryAfterGaveUp()
    }

    fun sendMessage(content: String, recipientId: Long = repository.activeChatId.value ?: 0xFFFFFFFFL): com.example.aethermesh.data.SendMessageResult {
        val targetChannel = if (recipientId == 0xFFFFFFFFL) repository.selectedChannel.value else ""
        return repository.sendMessage(recipientId, content, targetChannel)
    }

    fun retryMessage(message: ChatMessage): Boolean {
        return repository.retryMessage(message)
    }

    fun selectChannel(channel: String) {
        repository.selectChannel(channel)
    }

    fun selectDirectMessage(nodeId: Long) {
        repository.selectDirectMessage(nodeId)
    }

    fun loadRangeTestLogs(nodeId: Long) {
        repository.loadRangeTestLogs(nodeId)
    }

    fun createChannel(channel: String): Boolean {
        return repository.createChannel(channel)
    }

    fun sendNodeConfig(
        name: String,
        shortName: String,
        sf: Int,
        bw: Float,
        txPower: Int,
        region: Int,
        role: Int,
        telemetryInterval: Int = 60,
        screenTimeout: Int = 30,
        powerSaveMode: Boolean = false,
        positionPrecision: Int = 0,
        gpsMode: Int = 0,
        fixedPosition: Boolean = false,
        fixedLatitude: Float = 0f,
        fixedLongitude: Float = 0f,
        fixedAltitude: Int = 0
    ): Boolean {
        return repository.sendNodeConfig(
            name, shortName, sf, bw, txPower, region, role,
            telemetryInterval, screenTimeout, powerSaveMode, positionPrecision, gpsMode,
            fixedPosition, fixedLatitude, fixedLongitude, fixedAltitude
        )
    }

    fun updateNodeNameAndShortName(nodeId: Long, name: String, shortName: String) {
        repository.updateNodeNameAndShortName(nodeId, name, shortName)
    }

    /** @return true if the name was written to the node (mesh-persistent). */
    fun renameNode(
        nodeId: Long,
        name: String,
        shortName: String,
        adminPassword: String = ""
    ): Boolean {
        val clipped = name.trim().take(16)
        val short = shortName.trim().take(4).ifEmpty {
            clipped.replace(Regex("[^a-zA-Z0-9]"), "").take(4).uppercase()
        }
        if (nodeId == connectedNodeId) {
            val meshOk = repository.sendNameOnlyConfig(nodeId, clipped)
            if (!meshOk) {
                // Keep a local rename so the UI updates; caller can toast "phone only".
                repository.updateNodeNameAndShortName(nodeId, clipped, short)
            }
            return meshOk
        }
        if (adminPassword.isNotBlank()) {
            return repository.sendNameOnlyConfig(nodeId, clipped, adminPassword)
        }
        repository.updateNodeNameAndShortName(nodeId, clipped, short)
        return false
    }

    fun sendNameOnlyConfig(nodeId: Long, name: String, adminPassword: String = ""): Boolean =
        repository.sendNameOnlyConfig(nodeId, name, adminPassword)

    fun startTraceRoute(nodeId: Long): Boolean = repository.startTraceRoute(nodeId)

    fun clearTraceRouteResult() = repository.clearTraceRouteResult()

    fun hideTraceRouteDialog() = repository.hideTraceRouteDialog()

    fun sendRemoteConfig(
        nodeId: Long,
        name: String,
        password: String,
        sf: Int,
        bw: Float,
        txPower: Int,
        region: Int,
        role: Int,
        telemetryInterval: Int = 60,
        screenTimeout: Int = 30,
        powerSaveMode: Boolean = false,
        positionPrecision: Int = 0,
        gpsMode: Int = 0,
        fixedPosition: Boolean = false,
        fixedLatitude: Float = 0f,
        fixedLongitude: Float = 0f,
        fixedAltitude: Int = 0
    ): Boolean {
        return repository.sendRemoteConfig(
            nodeId, name, password, sf, bw, txPower, region, role,
            telemetryInterval, screenTimeout, powerSaveMode, positionPrecision, gpsMode,
            fixedPosition, fixedLatitude, fixedLongitude, fixedAltitude
        )
    }

    fun getAllRangeTestLogs() = repository.getAllRangeTestLogs()

    // BLE firmware update (OTA)
    val otaState = repository.otaState
    fun startFirmwareUpdate(firmware: ByteArray) = repository.startFirmwareUpdate(firmware)
    fun startRakDfuUpdate(zipUri: android.net.Uri) = repository.startRakDfuUpdate(zipUri)
    fun cancelFirmwareUpdate() = repository.cancelFirmwareUpdate()
    fun resetOtaState() = repository.resetOtaState()
    val diagnosticLogs = repository.diagnosticLogs

    private val _githubFirmware = MutableStateFlow<com.example.aethermesh.data.FirmwareCatalog.Artifact?>(null)
    val githubFirmware = _githubFirmware.asStateFlow()
    private val _githubFirmwareStatus = MutableStateFlow("")
    val githubFirmwareStatus = _githubFirmwareStatus.asStateFlow()
    private val _githubFirmwareBusy = MutableStateFlow(false)
    val githubFirmwareBusy = _githubFirmwareBusy.asStateFlow()
    private val _githubDownloadProgress = MutableStateFlow(0)
    val githubDownloadProgress = _githubDownloadProgress.asStateFlow()

    fun refreshGithubFirmware(nodeModel: String?) {
        viewModelScope.launch {
            _githubFirmwareBusy.value = true
            _githubFirmwareStatus.value = "Checking GitHub for firmware…"
            _githubFirmware.value = null
            try {
                val list = com.example.aethermesh.data.FirmwareCatalog.fetchOtaManifest()
                val match = com.example.aethermesh.data.FirmwareCatalog.pickForModel(list, nodeModel)
                _githubFirmware.value = match
                _githubFirmwareStatus.value = when {
                    match != null -> "Found ${match.name}"
                    list.isEmpty() -> "No OTA builds published yet."
                    else -> "No OTA package matches this node model."
                }
            } catch (e: Exception) {
                Log.e(TAG, "GitHub firmware catalog failed: ${e.message}")
                val detail = e.message.orEmpty()
                _githubFirmwareStatus.value = when {
                    detail.contains("404") || detail.contains("not published", ignoreCase = true) ->
                        if (detail.contains("OTA catalog not published")) detail
                        else "OTA catalog not on GitHub Pages yet. Use a local .bin for now, or retry after the site redeploys."
                    else -> "Could not reach GitHub: ${e.message}"
                }
                _githubFirmware.value = null
            } finally {
                _githubFirmwareBusy.value = false
            }
        }
    }

    /**
     * Download + SHA-256 verify the selected GitHub OTA package.
     * @return Triple(bytes, fileName, zipUri?) or null on failure
     */
    suspend fun downloadGithubFirmware(
        context: android.content.Context,
        artifact: com.example.aethermesh.data.FirmwareCatalog.Artifact
    ): com.example.aethermesh.data.FirmwareCatalog.DownloadResult? {
        _githubFirmwareBusy.value = true
        _githubDownloadProgress.value = 0
        _githubFirmwareStatus.value = "Downloading ${artifact.file}…"
        return try {
            val result = com.example.aethermesh.data.FirmwareCatalog.download(context, artifact) { pct ->
                _githubDownloadProgress.value = pct
                _githubFirmwareStatus.value = "Downloading… $pct%"
            }
            _githubFirmwareStatus.value = "Verified ${artifact.file}"
            _githubDownloadProgress.value = 100
            result
        } catch (e: Exception) {
            Log.e(TAG, "GitHub firmware download failed: ${e.message}")
            _githubFirmwareStatus.value = "Download failed: ${e.message}"
            null
        } finally {
            _githubFirmwareBusy.value = false
        }
    }

    fun lastPhoneFix() = repository.lastPhoneFix()

    fun getTelemetryHistory(nodeId: Long) = repository.getTelemetryHistory(nodeId)

    fun getChannelInboxPreviews() = repository.getChannelInboxPreviews()

    fun getDmInboxPreviews(localNodeId: Long) = repository.getDmInboxPreviews(localNodeId)

    fun sendAuthRequest(password: String): Boolean {
        return repository.sendAuthRequest(password)
    }

    fun promptDeviceAuthentication() {
        repository.promptDeviceAuthentication()
    }

    fun changeDevicePassword(current: String, new: String): Boolean {
        return repository.changeDevicePassword(current, new)
    }

    // E2EE Keys management
    val chatKeysRevision = repository.chatKeysRevision

    fun getChatKey(chatIdentifier: String): String? {
        return repository.getChatKey(chatIdentifier)
    }

    fun saveChatKey(chatIdentifier: String, key: String) {
        repository.saveChatKey(chatIdentifier, key)
        repository.refreshData()
    }

    // Range Test management
    val isRangeTestActive: StateFlow<Boolean> = repository.isRangeTestActive
    val rangeTestLogs: StateFlow<List<com.example.aethermesh.data.RangeTestLog>> = repository.rangeTestLogs
    val rangeTestTargetId: Long
        get() = repository.activeRangeTestTargetId

    fun startRangeTest(targetId: Long, intervalSec: Int) {
        repository.startRangeTest(targetId, intervalSec)
    }

    fun stopRangeTest() {
        repository.stopRangeTest()
    }

    fun clearRangeTestLogs(targetId: Long) {
        repository.clearRangeTestLogs(targetId)
    }

    fun clearAllMessages() {
        repository.clearAllMessages()
    }

    fun clearAllNodes() {
        repository.clearAllNodes()
    }

    fun disconnect() {
        repository.bleManager.disconnect()
    }

    fun refresh() {
        repository.refreshData()
    }

    fun getChannelsList() = repository.getChannelsList()
    fun insertChannel(channel: ChannelConfig) = repository.insertChannel(channel)
    fun updateChannel(channel: ChannelConfig) = repository.updateChannel(channel)
    fun deleteChannel(id: Long) = repository.deleteChannel(id)
    fun generateRandomPsk() = repository.generateRandomPsk()
    fun getOrCreateEcdhKeys() = repository.getOrCreateEcdhKeys()
    fun regenerateEcdhKeys() = repository.regenerateEcdhKeys()
    
    fun sharePhoneLocation(lat: Double, lon: Double): Boolean {
        return repository.sendPhoneLocation(lat, lon)
    }
}

data class BleDeviceItem(
    val name: String,
    val mac: String,
    val rssi: Int = -127
)

data class PendingChatDeepLink(
    val channel: String? = null,
    val dmPeerId: Long? = null
)
