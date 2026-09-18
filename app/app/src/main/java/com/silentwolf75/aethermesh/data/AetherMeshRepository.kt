package com.silentwolf75.aethermesh.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.silentwolf75.aethermesh.ble.BleConnectionManager
import com.silentwolf75.aethermesh.ble.DfuSessionPolicy
import com.silentwolf75.aethermesh.ble.DfuZipPolicy
import com.silentwolf75.aethermesh.ble.OtaStartDecision
import com.silentwolf75.aethermesh.ble.OtaStartPolicy
import com.silentwolf75.aethermesh.ble.OtaTransferPolicy
import com.silentwolf75.aethermesh.ble.OtaVerifyDecision
import com.silentwolf75.aethermesh.ble.OtaVerifyPolicy
import com.silentwolf75.aethermesh.proto.MeshPacket
import com.silentwolf75.aethermesh.proto.Telemetry
import com.silentwolf75.aethermesh.proto.Ack
import com.silentwolf75.aethermesh.proto.RangeTestControl
import com.silentwolf75.aethermesh.proto.NodeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

data class RouteHopInfo(
    val targetId: Long,
    val nextHopId: Long,
    val hops: Int,
    val lastSnr: Float = 0f,
    val lastRssi: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SendMessageResult {
    Sent,
    NotReady,
    EncryptFailed
}

class AetherMeshRepository(private val context: Context) {

    companion object {
        private const val TAG = "MeshRepository"
        const val DEFAULT_CHANNEL = IncomingChatPolicy.DEFAULT_CHANNEL
    }

    private val securePrefs = SecurePreferences.open(context)
    val dbHelper = DatabaseHelper(context)
    val bleManager = BleConnectionManager(context)
    private val outboundDeliveryStore = OutboundDeliveryStore(dbHelper)
    private val deliveryRetries by lazy {
        DeliveryRetryController(
            outboundDeliveryStore,
            ready = { bleManager.isConnected && bleManager.isGattReady &&
                _isDeviceAuthenticated.value && !_isRangeTestActive.value },
            senderId = { bleManager.connectedNodeId },
            send = { bleManager.sendPacket(it) }
        )
    }
    private var trustedControlNodeId = 0L
    private var privacySupported: Boolean? = null
    private var reportedPrivacy: ChannelPrivacy? = null
    private var privacySyncJob: Job? = null
    private val _channelPrivacyStatus = MutableStateFlow(ChannelPrivacyStatus.DISCONNECTED)
    val channelPrivacyStatus: StateFlow<ChannelPrivacyStatus> = _channelPrivacyStatus.asStateFlow()
    private val otaInbox = OtaStatusInbox()
    private val esp32OtaSender = Esp32OtaSender(
        inbox = otaInbox,
        send = { bytes, timeout, withResponse ->
            bleManager.sendPacket(
                bytes,
                timeoutMs = timeout,
                withResponse = withResponse,
                otaStream = true
            )
        },
        negotiatedMtu = { bleManager.negotiatedMtu },
        isConnected = { bleManager.isConnected }
    )
    private val pendingRangePings = RangeTestPendingStore()
    private val prefs = context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)

    fun appPrefs(): android.content.SharedPreferences = prefs

    // Flow for active node lists and messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()

    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected.asStateFlow()
    private val _bleConnectionPhase =
        MutableStateFlow(com.silentwolf75.aethermesh.ble.BleConnectionPhase.Disconnected)
    val bleConnectionPhase: StateFlow<com.silentwolf75.aethermesh.ble.BleConnectionPhase> =
        _bleConnectionPhase.asStateFlow()
    private val _bleReconnectAttempt = MutableStateFlow(0)
    val bleReconnectAttempt: StateFlow<Int> = _bleReconnectAttempt.asStateFlow()
    private val _bleReconnectGaveUp = MutableStateFlow(false)
    val bleReconnectGaveUp: StateFlow<Boolean> = _bleReconnectGaveUp.asStateFlow()

    // Group channels: the list of known channel names and the one currently being viewed.
    private val _channels = MutableStateFlow(listOf(DEFAULT_CHANNEL))
    val channels: StateFlow<List<String>> = _channels.asStateFlow()

    private val _selectedChannel = MutableStateFlow(DEFAULT_CHANNEL)
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    private val _chatKeysRevision = MutableStateFlow(0)
    val chatKeysRevision: StateFlow<Int> = _chatKeysRevision.asStateFlow()

    // activeChatId: null means showing the group channel, Long value means DM with that nodeId
    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId: StateFlow<Long?> = _activeChatId.asStateFlow()

    // Observed routes from packet headers
    private val _observedRoutes = MutableStateFlow<Map<Long, RouteHopInfo>>(emptyMap())
    val observedRoutes: StateFlow<Map<Long, RouteHopInfo>> = _observedRoutes.asStateFlow()

    private val _meshDiagnostics = MutableStateFlow<MeshDiagnosticsSnapshot?>(null)
    val meshDiagnostics: StateFlow<MeshDiagnosticsSnapshot?> = _meshDiagnostics.asStateFlow()

    private val _meshSelfTest = MutableStateFlow(MeshSelfTestResult())
    val meshSelfTest: StateFlow<MeshSelfTestResult> = _meshSelfTest.asStateFlow()
    private var meshSelfTestJob: Job? = null
    /** Packet id of the last successful [sendMessage] (for self-test scoring). */
    @Volatile
    private var lastOutboundPacketId: Int = 0

    private val _traceRouteState = MutableStateFlow(TraceRouteState())
    val traceRouteState: StateFlow<TraceRouteState> = _traceRouteState.asStateFlow()
    private var traceRouteJob: Job? = null

    // Device Authentication flows
    private val _isDeviceAuthenticated = MutableStateFlow(false)
    val isDeviceAuthenticated: StateFlow<Boolean> = _isDeviceAuthenticated.asStateFlow()

    // null = checking, true = prompt password, false = prompt set initial password
    private val _authenticationRequired = MutableStateFlow<Boolean?>(null)
    val authenticationRequired: StateFlow<Boolean?> = _authenticationRequired.asStateFlow()

    /** Bumped when an AuthResponse reports failure (wrong password / rejected). */
    private val _authFailureTick = MutableStateFlow(0)
    val authFailureTick: StateFlow<Int> = _authFailureTick.asStateFlow()

    // After auth, device reports whether the user has confirmed LoRa region.
    private val _needsRegionSetup = MutableStateFlow(false)
    val needsRegionSetup: StateFlow<Boolean> = _needsRegionSetup.asStateFlow()

    // Bumped when a device→app NodeConfig report hydrates SharedPreferences.
    private val _deviceConfigSyncEpoch = MutableStateFlow(0)
    val deviceConfigSyncEpoch: StateFlow<Int> = _deviceConfigSyncEpoch.asStateFlow()

    data class RemoteConfigReport(val nodeId: Long, val config: NodeConfig)
    data class RemoteConfigResultEvent(
        val nodeId: Long,
        val requestPacketId: Int,
        val status: com.silentwolf75.aethermesh.proto.ConfigResult.Status,
        val message: String
    )

    private val _remoteConfigReport = MutableSharedFlow<RemoteConfigReport>(extraBufferCapacity = 4)
    val remoteConfigReport: SharedFlow<RemoteConfigReport> = _remoteConfigReport.asSharedFlow()
    private val _remoteConfigResult = MutableSharedFlow<RemoteConfigResultEvent>(extraBufferCapacity = 4)
    val remoteConfigResult: SharedFlow<RemoteConfigResultEvent> = _remoteConfigResult.asSharedFlow()

    private var pendingAuthPassword: String? = null
    /** True while waiting for AuthResponse to a change-password request. */
    @Volatile
    private var pendingPasswordChange: Boolean = false
    private val _passwordChangeResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val passwordChangeResult: SharedFlow<Boolean> = _passwordChangeResult.asSharedFlow()
    private var autoAuthJob: Job? = null
    @Volatile
    private var lastAuthChallengeResubmitMs: Long = 0L
    /** Generation bumped on each BLE connect so in-flight auto-auth cannot unlock a newer session. */
    @Volatile
    private var authSessionGeneration: Int = 0
    /** After Apply Settings we expect MCU reboot; auto-auth is more patient and may force-refresh GATT. */
    @Volatile
    private var expectPostSettingsReconnect: Boolean = false
    @Volatile
    private var postReconnectAuthRefreshUsed: Boolean = false

    // Range Test Engine properties
    private val _isRangeTestActive = MutableStateFlow(false)
    val isRangeTestActive: StateFlow<Boolean> = _isRangeTestActive.asStateFlow()

    private val _rangeTestLogs = MutableStateFlow<List<RangeTestLog>>(emptyList())
    val rangeTestLogs: StateFlow<List<RangeTestLog>> = _rangeTestLogs.asStateFlow()

    private var rangeTestTargetId: Long = 0L
    private var rangeTestSf: Int = NodeSettingsPrefs.DEFAULT_SF
    val activeRangeTestTargetId: Long
        get() = rangeTestTargetId
    /** Wall-clock when the current (or last) range-test session started; 0 if never. */
    private val _rangeTestSessionStartMs = MutableStateFlow(0L)
    val rangeTestSessionStartMs: StateFlow<Long> = _rangeTestSessionStartMs.asStateFlow()
    private var rangeTestRxBaseline: Long = -1L
    private var rangeTestJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.Default + Job())

    // --- BLE firmware update (OTA) state ---
    private val _otaState = MutableStateFlow(OtaState())
    val otaState: StateFlow<OtaState> = _otaState.asStateFlow()

    /**
     * After reconnect/auth (and post-OTA), Installed / Node Details should not
     * confidently show a possibly-stale cached firmware string until a fresh
     * Telemetry.firmware_version arrives (firmware already loopbacks post-auth).
     */
    private val _firmwareFreshness = MutableStateFlow(FirmwareFreshness())
    val firmwareFreshness: StateFlow<FirmwareFreshness> = _firmwareFreshness.asStateFlow()

    // Remember which node / expected label we flashed so DFU completion can
    // invalidate the Room-cached "Firmware:" string (telemetry may be minutes away).
    @Volatile
    private var otaTargetNodeId: Long = 0L
    @Volatile
    private var otaExpectedFirmwareVersion: String = ""
    @Volatile
    private var otaPreFlashFirmwareVersion: String = ""
    @Volatile
    private var otaVerifyExpected: String = ""
    @Volatile
    private var otaVerifyPre: String = ""
    @Volatile
    private var otaVerifyNodeId: Long = 0L

    private val diagnosticRing = ArrayDeque<String>(64)
    private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    fun appendDiagnostic(message: String) {
        val next: List<String>
        synchronized(diagnosticRing) {
            next = DiagnosticLogPolicy.nextRing(
                diagnosticRing,
                message,
                System.currentTimeMillis()
            )
            diagnosticRing.clear()
            diagnosticRing.addAll(next)
            _diagnosticLogs.value = next
        }
        Log.d(TAG, message)
    }
    private var otaJob: Job? = null

    // Single thread for the heavy DB reads in refreshData: keeps queries off the
    // main thread (jank/ANR with a large history) while preserving their order.
    private val dbDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // Recovers messages stored before their key was saved. Decrypts on its own
    // worker so a large backlog cannot stall incoming messages or refreshes.
    private val decryptionRecovery = PendingDecryptionRecovery(
        scope = repositoryScope,
        dbDispatcher = dbDispatcher,
        workDispatcher = Dispatchers.Default.limitedParallelism(1),
        db = dbHelper,
        journal = PrefsRecoveryJournal(context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)),
        keyFor = { getChatKey(it) },
        onBatchRecovered = { _, _ -> refreshData() },
        // Chat identifiers stay out of diagnostics, which users export and share.
        onError = { _, error ->
            Log.e(TAG, "Decryption recovery failed", error)
            appendDiagnostic("Message recovery failed: ${error.javaClass.simpleName}")
        }
    )

    // Fresh phone GPS for range test rows (position, speed, accuracy). The map
    // tab's overlay only updates location while that tab is visible, so the
    // range test runs its own listener for the duration of the test.
    @Volatile
    private var lastPhoneLocation: android.location.Location? = null
    private var rangeTestLocationListener: android.location.LocationListener? = null

    private fun startRangeTestLocationUpdates() {
        if (rangeTestLocationListener != null) return
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    lastPhoneLocation = location
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            lm.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                1000L, 0f, listener, android.os.Looper.getMainLooper()
            )
            rangeTestLocationListener = listener
            Log.d(TAG, "Range test GPS updates started.")
        } catch (e: SecurityException) {
            Log.w(TAG, "No location permission for range test GPS: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start range test GPS updates: ${e.message}")
        }
    }

    private fun stopRangeTestLocationUpdates() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            rangeTestLocationListener?.let { lm.removeUpdates(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop range test GPS updates: ${e.message}")
        }
        rangeTestLocationListener = null
    }

    init {
        // Purge old sign-extended negative node ID records and ghost node ID 0
        // records from previous versions. Off the main thread: this runs during
        // Application.onCreate and can also trigger DB migrations.
        repositoryScope.launch(dbDispatcher) {
            try {
                val db = dbHelper.writableDatabase
                db.execSQL("DELETE FROM nodes WHERE node_id <= 0")
                db.execSQL("DELETE FROM messages WHERE sender_id <= 0 OR recipient_id <= 0")
                _observedRoutes.value = dbHelper.getRouteObservations().associateBy { it.targetId }
                _meshDiagnostics.value = dbHelper.getLatestMeshDiagnostics()
                // Range-test control rows stored as chat messages by older builds:
                // once marked FAILED they were auto-resent as DMs forever.
                db.execSQL("DELETE FROM messages WHERE content LIKE 'PING@_%' ESCAPE '@' OR content LIKE 'PONG@_%' ESCAPE '@'")
                Log.d(TAG, "Successfully purged invalid ID and legacy range-test records from database.")
            } catch (e: Exception) {
                Log.e(TAG, "Error purging invalid ID records: ${e.message}")
            }
        }
        // Finish decryption recovery cut short when the app last closed, and pick
        // up stored messages whose key arrived some other way (e.g. migration).
        decryptionRecovery.resumeAtStartup(::logDecryptionRecovery)


        // Load initial data
        refreshData()
        startPendingMessageTimeoutMonitor()

        // Configure BLE connection listener
        bleManager.onConnectionStateChanged = { connected ->
            Log.d(TAG, "BLE Connection changed: $connected")
            _isBleConnected.value = connected
            trustedControlNodeId = 0L
            privacySyncJob?.cancel()
            privacySupported = null
            reportedPrivacy = null
            _channelPrivacyStatus.value = if (connected) ChannelPrivacyStatus.CHECKING else ChannelPrivacyStatus.DISCONNECTED
            if (connected) {
                // New link: never inherit a pre-reboot "authenticated" flag.
                authSessionGeneration += 1
                val session = authSessionGeneration
                _isDeviceAuthenticated.value = false
                _authenticationRequired.value = null
                _needsRegionSetup.value = false
                _bleReconnectGaveUp.value = false
                lastAuthChallengeResubmitMs = 0L
                postReconnectAuthRefreshUsed = false
                val mac = bleManager.getConnectedDeviceAddress()
                if (mac != null) {
                    autoAuthenticate(mac, session)
                }
                refreshData()
            } else {
                autoAuthJob?.cancel()
                autoAuthJob = null
                authSessionGeneration += 1
                pendingPasswordChange = false
                pendingAuthPassword = null
                _isDeviceAuthenticated.value = false
                _authenticationRequired.value = null
                _needsRegionSetup.value = false
                // Keep post-OTA verify across the intentional disconnect; only clear
                // "checking…" when we're not waiting to confirm an update.
                if (otaVerifyNodeId == 0L) {
                    _firmwareFreshness.value = FirmwareFreshness()
                }
                if (_isRangeTestActive.value) {
                    stopRangeTest()
                }
                // In-flight traceroute / remote-config waiters must not hang until
                // their own timeouts after BLE drops (device switch or link loss).
                if (_traceRouteState.value.active) {
                    traceRouteJob?.cancel()
                    _traceRouteState.value = TraceRoutePolicy.disconnected(
                        _traceRouteState.value,
                        System.currentTimeMillis()
                    )
                }
                refreshData()
            }
        }
        bleManager.onConnectionPhaseChanged = { phase, attempt ->
            _bleConnectionPhase.value = phase
            _bleReconnectAttempt.value = attempt
            _bleReconnectGaveUp.value = bleManager.reconnectGaveUp
        }

        // Configure BLE packet receiver
        bleManager.onPacketReceived = { bytes ->
            try {
                // Parse protobuf packet
                val packet = MeshPacket.parseFrom(bytes)
                handleMeshPacket(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding mesh packet: ${e.message}")
                appendDiagnostic("Decode error: ${e.message}")
            }
        }
    }

    private fun lookupSavedPassword(macAddress: String): String? =
        SavedPasswordPolicy.lookup(securePrefs, macAddress, bleManager.connectedNodeId)

    private fun saveNodePassword(macAddress: String?, nodeId: Long, password: String) {
        if (!SavedPasswordPolicy.save(securePrefs, macAddress, nodeId, password)) return
        ControlKeyDerivation.clearCache()
    }

    private fun clearSavedPassword(macAddress: String?, nodeId: Long = 0L) {
        SavedPasswordPolicy.clear(securePrefs, macAddress, nodeId)
        ControlKeyDerivation.clearCache()
    }

    private fun autoAuthenticate(macAddress: String, session: Int = authSessionGeneration) {
        autoAuthJob?.cancel()
        autoAuthJob = repositoryScope.launch {
            // State machine: GATT ready (notify armed) → AuthRequest retries →
            // AuthResponse(success). Never treat writeCharacteristic alone as unlock.
            val postSettings = expectPostSettingsReconnect
            delay(AutoAuthPolicy.initialDelayMs(postSettings))
            if (!isActive || session != authSessionGeneration) return@launch
            if (AutoAuthPolicy.shouldStop(bleManager.isConnected, _isDeviceAuthenticated.value)) return@launch

            var readyWait = 0
            while (isActive && readyWait < AutoAuthPolicy.GATT_READY_POLLS &&
                bleManager.isConnected && !bleManager.isGattReady
            ) {
                delay(AutoAuthPolicy.GATT_READY_POLL_MS)
                readyWait++
            }
            if (!isActive || session != authSessionGeneration) return@launch
            if (!bleManager.isConnected || !bleManager.isGattReady) {
                Log.w(TAG, "Auto-auth aborted: GATT not ready (connected=${bleManager.isConnected})")
                return@launch
            }

            val savedPass = lookupSavedPassword(macAddress)
            val password = savedPass.orEmpty()
            val attempts = AutoAuthPolicy.attemptCount(password.isNotEmpty(), postSettings)
            Log.d(
                TAG,
                if (password.isNotEmpty()) {
                    "Found saved password for ${SavedPasswordPolicy.normalizeMac(macAddress)}. Auto-auth up to $attempts attempts (postSettings=$postSettings)..."
                } else {
                    "No saved password for ${SavedPasswordPolicy.normalizeMac(macAddress)}. Querying auth status..."
                }
            )

            for (attempt in 1..attempts) {
                if (!isActive || session != authSessionGeneration) return@launch
                if (AutoAuthPolicy.shouldStop(bleManager.isConnected, _isDeviceAuthenticated.value)) return@launch
                if (!bleManager.isGattReady) {
                    delay(AutoAuthPolicy.GATT_GAP_MS)
                    continue
                }
                Log.d(TAG, "Auto-auth attempt $attempt/$attempts")
                val wrote = sendAuthRequest(password)
                delay(AutoAuthPolicy.responseWaitMs(wrote, postSettings))
                if (_isDeviceAuthenticated.value) {
                    expectPostSettingsReconnect = false
                    return@launch
                }
            }

            if (!isActive || session != authSessionGeneration) return@launch
            when (
                AutoAuthPolicy.onExhausted(
                    bleManager.isConnected,
                    _isDeviceAuthenticated.value,
                    postSettings,
                    postReconnectAuthRefreshUsed
                )
            ) {
                AutoAuthExhausted.ForceRefresh -> {
                    postReconnectAuthRefreshUsed = true
                    Log.w(TAG, "Post-settings auto-auth failed; forcing GATT refresh")
                    bleManager.forceRefreshConnection(AutoAuthPolicy.FORCE_REFRESH_MS)
                }
                AutoAuthExhausted.PromptUnlock -> {
                    Log.w(TAG, "Auto-auth did not complete; prompting unlock UI")
                    expectPostSettingsReconnect = false
                    if (AutoAuthPolicy.shouldShowUnlockPrompt(_authenticationRequired.value)) {
                        _authenticationRequired.value = true
                    }
                }
                AutoAuthExhausted.Idle -> {}
            }
        }
    }

    /** Resubmit saved password when the node challenges after reboot/reconnect. */
    private fun resubmitSavedPasswordOnChallenge() {
        val mac = bleManager.getConnectedDeviceAddress() ?: return
        val password = lookupSavedPassword(mac) ?: return
        val now = System.currentTimeMillis()
        if (!AutoAuthPolicy.shouldResubmitChallenge(
                now,
                lastAuthChallengeResubmitMs,
                bleManager.isConnected,
                bleManager.isGattReady,
                password.isNotEmpty()
            )
        ) return
        lastAuthChallengeResubmitMs = now
        val session = authSessionGeneration
        Log.d(TAG, "Auth challenge received; resubmitting saved password")
        repositoryScope.launch {
            delay(AutoAuthPolicy.CHALLENGE_DELAY_MS)
            if (session != authSessionGeneration) return@launch
            if (AutoAuthPolicy.shouldStop(bleManager.isConnected, _isDeviceAuthenticated.value)) return@launch
            sendAuthRequest(password)
        }
    }

    /** Force the unlock dialog when connected but auth prompt never appeared. */
    fun promptDeviceAuthentication() {
        if (_isDeviceAuthenticated.value) return
        if (AutoAuthPolicy.shouldShowUnlockPrompt(_authenticationRequired.value)) {
            _authenticationRequired.value = true
        }
    }

    fun sendAuthRequest(password: String): Boolean {
        if (!bleManager.isConnected || !bleManager.isGattReady) return false
        val packet = AuthRequestApply.buildUnlock(
            bleManager.connectedNodeId, PacketIdGenerator.next(), password
        )
        pendingAuthPassword = password
        return bleManager.sendPacket(packet.toByteArray())
    }

    fun changeDevicePassword(currentPassword: String, newPassword: String): Boolean {
        if (!bleManager.isConnected) return false
        val packet = AuthRequestApply.buildChangePassword(
            bleManager.connectedNodeId,
            PacketIdGenerator.next(),
            currentPassword,
            newPassword
        )
        pendingPasswordChange = true
        pendingAuthPassword = newPassword
        val sent = bleManager.sendPacket(packet.toByteArray())
        if (!sent) {
            pendingPasswordChange = false
            pendingAuthPassword = null
        }
        return sent
    }

    /**
     * Password-change replies must not go through unlock/lock handling:
     * a wrong current password would otherwise clear the session and wipe
     * the remembered password while the firmware stays authenticated.
     * Lock challenges always drop local auth so SENT bubbles cannot sit
     * while the MCU drops unauthenticated text.
     */
    private fun applyAuthResponse(
        senderId: Long,
        success: Boolean,
        passwordNotSet: Boolean,
        message: String
    ) {
        val action = AuthResponsePolicy.decide(
            pendingPasswordChange = pendingPasswordChange,
            success = success,
            passwordNotSet = passwordNotSet,
            message = message,
            hasPendingPassword = !pendingAuthPassword.isNullOrEmpty(),
            oldNodeId = bleManager.connectedNodeId,
            senderId = senderId
        )
        when (action) {
            is AuthResponseAction.PasswordChanged -> {
                pendingPasswordChange = false
                if (action.savePendingPassword) {
                    val passwordToSave = pendingAuthPassword
                    if (!passwordToSave.isNullOrEmpty()) {
                        saveNodePassword(bleManager.getConnectedDeviceAddress(), senderId, passwordToSave)
                    }
                }
                pendingAuthPassword = null
                _passwordChangeResult.tryEmit(action.success)
            }
            is AuthResponseAction.Unlocked -> {
                pendingPasswordChange = false
                bleManager.connectedNodeId = senderId
                trustedControlNodeId = senderId
                if (action.migrateFromNodeId != 0L) {
                    dbHelper.migrateNodeIdentity(action.migrateFromNodeId, senderId)
                    Log.d(TAG, "Migrated BLE placeholder ID to hardware ID 0x${senderId.toString(16).uppercase()}")
                }
                _isDeviceAuthenticated.value = true
                _authenticationRequired.value = null
                _authFailureTick.value = 0
                expectPostSettingsReconnect = false
                ChatThreadPrefs.recordBleController(context, senderId)
                if (action.savePendingPassword) {
                    val passwordToSave = pendingAuthPassword
                    if (!passwordToSave.isNullOrEmpty()) {
                        saveNodePassword(bleManager.getConnectedDeviceAddress(), senderId, passwordToSave)
                    }
                }
                pendingAuthPassword = null
                val nodePrefs = context.getSharedPreferences(NodeSettingsPrefs.prefsName(senderId), Context.MODE_PRIVATE)
                NodeNamePolicy.pendingFromPrefs(
                    nodePrefs.getString(NodeSettingsPrefs.KEY_NODE_NAME, null),
                    nodePrefs.getString(NodeSettingsPrefs.KEY_NODE_SHORT, null),
                    senderId
                )?.let { pending ->
                    dbHelper.updateNodeNameAndShortName(senderId, pending.longName, pending.shortName)
                    nodePrefs.edit()
                        .remove(NodeSettingsPrefs.KEY_NODE_NAME)
                        .remove(NodeSettingsPrefs.KEY_NODE_SHORT)
                        .apply()
                }
                markAwaitingFirmwareTelemetry(senderId)
                refreshData()
                // Config report usually follows unlock; kick sync so CHECKING
                // resolves even if the report was already applied or is delayed.
                syncChannelPrivacy()
            }
            is AuthResponseAction.Challenge -> {
                // Drop any in-flight password-change wait so a later unlock is
                // not treated as PasswordChanged(success).
                pendingPasswordChange = false
                pendingAuthPassword = null
                val wasAuthenticated = _isDeviceAuthenticated.value
                _isDeviceAuthenticated.value = false
                privacySyncJob?.cancel()
                privacySupported = null
                reportedPrivacy = null
                _channelPrivacyStatus.value = ChannelPrivacyStatus.DISCONNECTED
                _needsRegionSetup.value = false
                if (action.needsInitialPassword) {
                    _authenticationRequired.value = false
                } else {
                    if (wasAuthenticated) {
                        Log.w(TAG, "Node re-challenged after local auth; session desync cleared")
                    }
                    resubmitSavedPasswordOnChallenge()
                }
            }
            is AuthResponseAction.Rejected -> {
                _isDeviceAuthenticated.value = false
                privacySyncJob?.cancel()
                privacySupported = null
                reportedPrivacy = null
                _channelPrivacyStatus.value = ChannelPrivacyStatus.DISCONNECTED
                _needsRegionSetup.value = false
                if (action.needsInitialPassword) {
                    _authenticationRequired.value = false
                } else {
                    _authenticationRequired.value = true
                    if (action.forgetSavedPassword) {
                        clearSavedPassword(bleManager.getConnectedDeviceAddress(), senderId)
                    }
                    _authFailureTick.value = _authFailureTick.value + 1
                }
                pendingAuthPassword = null
            }
        }
    }

    private fun handleMeshPacket(packet: MeshPacket) {
        if (!IncomingPacketPolicy.acceptControl(packet, trustedControlNodeId, _isDeviceAuthenticated.value)) {
            Log.w(TAG, "Ignoring control response outside the authenticated local session")
            return
        }
        val senderId = IncomingPacketPolicy.unsignedNodeId(packet.senderId)
        val recipientId = IncomingPacketPolicy.unsignedNodeId(packet.recipientId)
        val localNodeId = bleManager.connectedNodeId

        // Handle AuthResponse packet first
        if (packet.payloadCase == MeshPacket.PayloadCase.AUTH_RESPONSE) {
            val authResp = packet.authResponse
            Log.d(TAG, "AuthResponse received: success=${authResp.success}, msg=${authResp.message}, notSet=${authResp.passwordNotSet}")
            applyAuthResponse(
                senderId = senderId,
                success = authResp.success,
                passwordNotSet = authResp.passwordNotSet,
                message = authResp.message.orEmpty()
            )
            return
        }

        // Device → phone config snapshot (after auth / remote report). Hydrate
        // local prefs and notify Remote Config UI when it is a live read-back.
        if (packet.payloadCase == MeshPacket.PayloadCase.CONFIG && packet.config.reportOnly) {
            if (recipientId == 0L && senderId == trustedControlNodeId) {
                privacySupported = packet.config.positionPrivacySupported
                reportedPrivacy = ChannelPrivacy.fromReport(packet.config)
                if (reportedPrivacy == ChannelPrivacy.fromChannels(dbHelper.getChannelsList())) {
                    privacySyncJob?.cancel()
                    syncChannelPrivacy()
                } else if (privacySyncJob?.isActive != true) {
                    syncChannelPrivacy()
                }
            }
            hydrateNodeSettingsFromDevice(senderId, packet.config)
            _remoteConfigReport.tryEmit(RemoteConfigReport(senderId, packet.config))
            return
        }

        if (packet.payloadCase == MeshPacket.PayloadCase.CONFIG_RESULT) {
            val result = packet.configResult
            Log.d(TAG, "ConfigResult from 0x${senderId.toString(16)} status=${result.status} req=${result.requestPacketId}")
            _remoteConfigResult.tryEmit(
                RemoteConfigResultEvent(
                    nodeId = senderId,
                    requestPacketId = result.requestPacketId,
                    status = result.status,
                    message = result.message.orEmpty()
                )
            )
            return
        }

        if (packet.payloadCase == MeshPacket.PayloadCase.DELIVERY_STATUS) {
            val delivery = packet.deliveryStatus
            val fromId = DeliveryStatusPolicy.fromNodeId(delivery.fromNodeId)
            when (val action = DeliveryStatusPolicy.decide(delivery.state, fromId)) {
                DeliveryStatusAction.Ignore -> Unit
                is DeliveryStatusAction.Heard -> {
                    val count = if (action.recordHearer) {
                        dbHelper.recordChannelHearing(delivery.packetId, action.fromNodeId, delivery.heardCount)
                    } else {
                        dbHelper.setMessageHeardCount(delivery.packetId, delivery.heardCount)
                        delivery.heardCount
                    }
                    Log.d(TAG, "Channel HEARD for packet ${delivery.packetId}: count=$count from=0x${fromId.toString(16)}")
                    refreshData()
                }
                DeliveryStatusAction.DeliveredIfDirect -> {
                    if (!dbHelper.isChannelMessage(delivery.packetId)) {
                        dbHelper.updateMessageStatus(delivery.packetId, DeliveryStatusPolicy.DELIVERED)
                        refreshData()
                    }
                }
                is DeliveryStatusAction.SetStatus -> {
                    dbHelper.updateMessageStatus(delivery.packetId, action.status)
                    refreshData()
                }
            }
            Log.d(
                TAG,
                "DeliveryStatus for packet ${delivery.packetId}: ${delivery.state}, " +
                    "reason=${delivery.reason}, retry=${delivery.retryCount}, heard=${delivery.heardCount}"
            )
            return
        }

        // OTA status/acks are BLE-only control messages addressed to the phone
        // (recipient_id = 0), so they must be handled before the invalid-recipient
        // guard below - the same as AuthResponse and DeliveryStatus above.
        if (packet.payloadCase == MeshPacket.PayloadCase.OTA_STATUS) {
            if (_otaState.value.active) otaInbox.offer(packet.otaStatus)
            return
        }

        if (packet.payloadCase == MeshPacket.PayloadCase.DIAGNOSTICS) {
            val snapshot = IncomingDiagnosticsPolicy.fromProto(packet.diagnostics)
            dbHelper.insertMeshDiagnostics(snapshot)
            _meshDiagnostics.value = snapshot
            return
        }

        // Range-test PONGs are scored even if recipient_id is unexpectedly 0.
        val isRangePong = IncomingPacketPolicy.isRangePong(
            packet.payloadCase == MeshPacket.PayloadCase.TEXT,
            packet.text.content
        )
        if (IncomingPacketPolicy.shouldDropZeroIds(senderId, recipientId, isRangePong)) {
            Log.w(TAG, "Ignoring packet with invalid sender/recipient: sender=0x${senderId.toString(16)}, recipient=0x${recipientId.toString(16)}")
            return
        }

        Log.d(TAG, "Received mesh packet from 0x${senderId.toString(16).uppercase()}")

        IncomingPacketPolicy.observeRoute(
            senderId = senderId,
            prevHopId = IncomingPacketPolicy.unsignedNodeId(packet.prevHopId),
            rxRssi = packet.rxRssi,
            rxSnr = packet.rxSnr,
            nowMs = System.currentTimeMillis()
        )?.let { observation ->
            val currentMap = _observedRoutes.value.toMutableMap()
            currentMap[senderId] = observation
            dbHelper.upsertRouteObservation(observation)
            _observedRoutes.value = currentMap
        }

        when (packet.payloadCase) {
            MeshPacket.PayloadCase.TEXT -> {
                val textMsg = packet.text
                val isEncrypted = textMsg.isEncrypted
                val contentReceived = textMsg.content
                val targetChan = textMsg.channel

                // Range-test control traffic never belongs in chat history.
                // A PONG scores the outstanding ping (packet.rxRssi/Snr = how our
                // node heard the PONG over the air).
                if (RangeTestPolicy.isPongContent(contentReceived)) {
                    val parsed = RangeTestPolicy.parsePong(contentReceived)
                    val pongId = parsed?.pingId
                    val pending = pongId?.let { pendingRangePings[it] }
                    when (
                        val decision = RangeTestPolicy.decidePong(
                            _isRangeTestActive.value, contentReceived, pending, senderId
                        )
                    ) {
                        RangeScoreDecision.IgnoreInactive -> {
                            Log.d(TAG, "Ignoring PONG while range test inactive: $contentReceived")
                        }
                        RangeScoreDecision.Unmatched, RangeScoreDecision.Ignore -> {
                            Log.w(
                                TAG,
                                "Range-test PONG not matched (id=$pongId pending=${pending != null}): $contentReceived " +
                                    "from 0x${senderId.toString(16)} outstanding=${pendingRangePings.keys}"
                            )
                        }
                        is RangeScoreDecision.Score -> {
                            if (decision.senderMismatch) {
                                Log.w(
                                    TAG,
                                    "PONG sender 0x${senderId.toString(16)} != target " +
                                        "0x${decision.pending.targetId.toString(16)} for ping ${decision.packetId} — scoring anyway"
                                )
                            }
                            if (pendingRangePings.remove(decision.packetId, decision.pending)) {
                                Log.d(TAG, "Direct range-test PONG matched ping ${decision.packetId}")
                                logRangeTestResult(
                                    pending = decision.pending,
                                    success = true,
                                    rssi = packet.rxRssi,
                                    snr = packet.rxSnr,
                                    remoteRssi = decision.remoteRssi,
                                    remoteSnr = decision.remoteSnr
                                )
                            }
                        }
                    }
                    return
                }
                when (val kind = IncomingChatPolicy.classify(contentReceived, senderId, recipientId, targetChan, localNodeId)) {
                    IncomingTextKind.IgnorePing,
                    IncomingTextKind.IgnoreUnaddressed -> return
                    is IncomingTextKind.Chat -> {
                        // Packets arrive on the main looper. Decryption runs a
                        // 120k-iteration PBKDF2 and storing touches SQLite, so
                        // hand off to the single-threaded dbDispatcher, which
                        // keeps arrival order and serializes the duplicate check.
                        val packetId = packet.packetId
                        repositoryScope.launch(dbDispatcher) {
                            storeIncomingChat(
                                senderId = senderId,
                                recipientId = recipientId,
                                content = contentReceived,
                                isEncrypted = isEncrypted,
                                packetId = packetId,
                                plan = kind.plan
                            )
                        }
                    }
                }
            }
            MeshPacket.PayloadCase.NODE_IDENTITY -> {
                // Our own radio verified the signature and decided what this
                // announcement means; the phone records that verdict and the
                // fingerprint a person can compare out loud.
                val identity = packet.nodeIdentity
                val edKey = identity.ed25519Public.toByteArray()
                val state = NodeIdentityPolicy.stateOf(identity.trust)
                val fingerprint = NodeIdentityPolicy.fingerprint(edKey)
                if (fingerprint.isEmpty() || state == NodeIdentityPolicy.State.UNKNOWN) {
                    Log.w(TAG, "Ignoring identity for 0x${senderId.toString(16)}: unusable key or verdict")
                    return
                }
                repositoryScope.launch(dbDispatcher) {
                    dbHelper.recordNodeIdentity(senderId, fingerprint, state.name, identity.keyEpoch)
                    if (NodeIdentityPolicy.needsAttention(state)) {
                        Log.w(TAG, "Identity ${state.name} for 0x${senderId.toString(16)}: $fingerprint")
                    }
                    refreshData()
                }
                return
            }
            MeshPacket.PayloadCase.TELEMETRY -> {
                val telemetry = packet.telemetry
                val channels = getChannelsList()
                val privacy = PhoneLocationShare.privacyOf(channels)
                val coords = IncomingTelemetryPolicy.displayCoords(
                    telemetry.latitude,
                    telemetry.longitude,
                    senderId,
                    channels
                )
                if (coords.fuzzed) {
                    Log.d(TAG, "Location fuzzer applied: channel privacy floor ±${privacy.radiusM} m")
                }

                dbHelper.updateNode(
                    nodeId = senderId,
                    battery = telemetry.batteryLevel,
                    lat = coords.latitude,
                    lon = coords.longitude,
                    model = telemetry.nodeModel,
                    uptimeSeconds = IncomingTelemetryPolicy.unsigned32(telemetry.uptimeSeconds),
                    firmwareVersion = telemetry.firmwareVersion,
                    isCharging = telemetry.isCharging,
                    rssi = packet.rxRssi,
                    snr = packet.rxSnr,
                    voltage = telemetry.batteryVoltage,
                    positionPrecision = telemetry.positionPrecision,
                    advertisedName = telemetry.nodeName,
                    protocolVersion = IncomingTelemetryPolicy.trustedProtocolVersion(packet.protocolVersion),
                    loraSf = telemetry.loraSf,
                    region = IncomingTelemetryPolicy.trustedRegion(telemetry.loraSf, telemetry.region),
                    gps = TelemetryGps(
                        state = telemetry.gpsStateValue,
                        positionSource = telemetry.positionSourceValue,
                        satellitesUsed = telemetry.gpsSatellitesUsed,
                        satellitesInView = telemetry.gpsSatellitesInView,
                        hdopX10 = telemetry.gpsHdopX10,
                        fixAgeSecs = telemetry.gpsFixAgeSecs
                    )
                )
                // Append to telemetry history for battery/voltage graphs.
                dbHelper.insertTelemetrySample(senderId, telemetry.batteryLevel, telemetry.batteryVoltage, telemetry.isCharging)
                notifyLowBattery(senderId, telemetry.batteryLevel, telemetry.isCharging)
                retryQueuedDirectMessages(senderId)
                if (telemetry.firmwareVersion.isNotBlank()) {
                    onFreshFirmwareTelemetry(senderId, telemetry.firmwareVersion)
                }
                refreshData()
            }
            MeshPacket.PayloadCase.TRACE_ROUTE -> {
                val current = _traceRouteState.value
                val applied = TraceRoutePolicy.applyResponse(
                    current, packet, localNodeId, System.currentTimeMillis()
                )
                if (applied != null) {
                    traceRouteJob?.cancel()
                    _traceRouteState.value = applied
                    val observation = IncomingPacketPolicy.observeTracePath(
                        current.targetId, applied.forward, System.currentTimeMillis()
                    )
                    if (observation != null) {
                        val routes = _observedRoutes.value.toMutableMap()
                        routes[current.targetId] = observation
                        dbHelper.upsertRouteObservation(observation)
                        _observedRoutes.value = routes
                    }
                }
            }
            MeshPacket.PayloadCase.ACK -> {
                if (localNodeId != 0L && !MeshNodeId.same(recipientId, localNodeId)) {
                    Log.d(TAG, "Ignoring ACK addressed to 0x${recipientId.toString(16)} (localNodeId=0x${localNodeId.toString(16)})")
                    return
                }
                val ackedId = packet.ack.ackedPacketId
                Log.d(TAG, "ACK received for packet: $ackedId from 0x${senderId.toString(16)}")
                when (val action = DeliveryStatusPolicy.onMeshAck(dbHelper.isChannelMessage(ackedId), senderId)) {
                    is DeliveryStatusAction.Heard -> {
                        if (action.recordHearer) {
                            dbHelper.recordChannelHearing(ackedId, action.fromNodeId)
                        }
                    }
                    DeliveryStatusAction.DeliveredIfDirect -> {
                        dbHelper.updateMessageStatus(ackedId, DeliveryStatusPolicy.DELIVERED)
                    }
                    else -> Unit
                }
                val pending = pendingRangePings[ackedId]
                when (
                    val decision = RangeTestPolicy.decideAck(
                        _isRangeTestActive.value,
                        ackedId,
                        pending,
                        senderId,
                        packet.ack.ackedRxRssi,
                        packet.ack.ackedRxSnr
                    )
                ) {
                    is RangeScoreDecision.Score -> {
                        if (pendingRangePings.remove(decision.packetId, decision.pending)) {
                            Log.d(TAG, "Range test ACK matched packet $ackedId")
                            logRangeTestResult(
                                pending = decision.pending,
                                success = true,
                                rssi = packet.rxRssi,
                                snr = packet.rxSnr,
                                remoteRssi = decision.remoteRssi,
                                remoteSnr = decision.remoteSnr
                            )
                        }
                    }
                    else -> Unit
                }
                refreshData()
            }
            else -> {
                Log.d(TAG, "Unhandled payload ${packet.payloadCase} from 0x${senderId.toString(16)}")
            }
        }
    }

    fun getChatKey(chatIdentifier: String): String? {
        val found = ChatKeyStorePolicy.lookup(securePrefs, chatIdentifier) {
            dbHelper.getChatKey(chatIdentifier)
        }
        if (found.migrated) dbHelper.deleteChatKey(chatIdentifier)
        return found.value
    }

    fun saveChatKey(chatIdentifier: String, key: String) {
        val storeKey = {
            ChatKeyStorePolicy.save(securePrefs, chatIdentifier, key)
            dbHelper.deleteChatKey(chatIdentifier)
        }
        if (key.isBlank()) {
            storeKey()
        } else {
            // Journals the chat before the key is stored, then recovers messages
            // that were waiting for it.
            decryptionRecovery.saveKeyAndRecover(chatIdentifier, storeKey, ::logDecryptionRecovery)
        }
        _chatKeysRevision.value = _chatKeysRevision.value + 1
    }

    // Runs on dbDispatcher.
    private fun storeIncomingChat(
        senderId: Long,
        recipientId: Long,
        content: String,
        isEncrypted: Boolean,
        packetId: Int,
        plan: IncomingChatPlan
    ) {
        // Mesh relays deliver the same frame repeatedly; reject copies before
        // paying for key derivation. insertMessage re-checks as a backstop.
        if (dbHelper.hasMessage(senderId, packetId)) {
            Log.d(TAG, "Skipping duplicate message sender=0x${senderId.toString(16)} packetId=$packetId")
            return
        }
        val passcode = if (isEncrypted) getChatKey(plan.chatIdentifier) else null
        val decrypted = if (!passcode.isNullOrEmpty()) {
            decryptAES(content, passcode, plan.cryptoContext)
        } else {
            ""
        }
        val resolved = IncomingChatPolicy.resolveContent(
            encrypted = isEncrypted,
            hasKey = !passcode.isNullOrEmpty(),
            raw = content,
            decrypted = decrypted
        )
        val rowId = dbHelper.insertMessage(
            senderId = senderId,
            recipientId = recipientId,
            content = resolved.content,
            channel = plan.channelForRow,
            packetId = packetId,
            status = "SENT",
            isEncrypted = isEncrypted,
            pendingCipher = resolved.pendingCipherText?.let {
                PendingCipher(it, plan.chatIdentifier, plan.cryptoContext)
            }
        )
        if (rowId < 0L) {
            Log.d(TAG, "Skipping duplicate message sender=0x${senderId.toString(16)} packetId=$packetId")
            return
        }
        refreshData()
        notifyIncomingMessage(
            senderId = senderId,
            chatIdentifier = plan.chatIdentifier,
            channel = plan.channelForRow,
            content = resolved.content,
            isBroadcast = plan.isBroadcast
        )
    }

    private fun logDecryptionRecovery(result: PendingDecryptionRecovery.Result) {
        if (result.scanned == 0) return
        appendDiagnostic("Recovered ${result.recovered} of ${result.scanned} stored encrypted messages")
    }

    private fun deleteChatKey(chatIdentifier: String) {
        ChatKeyStorePolicy.delete(securePrefs, chatIdentifier)
        dbHelper.deleteChatKey(chatIdentifier)
        _chatKeysRevision.value = _chatKeysRevision.value + 1
    }

    fun sendMessage(recipientId: Long, content: String, channel: String = _selectedChannel.value): SendMessageResult =
        sendMessageInternal(recipientId, content, channel)

    private fun sendMessageInternal(
        recipientId: Long, content: String, channel: String, existingMessage: ChatMessage? = null
    ): SendMessageResult {
        // Require notify-ready GATT + AuthResponse(success). A zombie post-reboot
        // link can report isConnected with stale auth and produce fake SENT rows.
        if (!bleManager.isConnected || !bleManager.isGattReady || !_isDeviceAuthenticated.value) {
            return SendMessageResult.NotReady
        }
        val generatedPacketId = existingMessage?.packetId?.takeIf { it != 0 } ?: PacketIdGenerator.next()
        val localNodeId = bleManager.connectedNodeId
        val chatIdentifier = ChatSendPolicy.chatIdentifier(recipientId, channel)
        val passcode = getChatKey(chatIdentifier)
        val plan = ChatSendPolicy.plan(
            localNodeId,
            recipientId,
            content,
            channel,
            hasPasscode = !passcode.isNullOrEmpty(),
            existingWasEncrypted = existingMessage?.isEncrypted == true
        ) ?: return SendMessageResult.EncryptFailed

        // Encrypt FIRST and refuse to send on failure — never fall back to
        // transmitting plaintext on a chat the user believes is encrypted.
        val contentToSend = if (plan.isEncrypted) {
            encryptAES(plan.boundedContent, passcode ?: return SendMessageResult.EncryptFailed, plan.cryptoContext) ?: run {
                Log.e(TAG, "Encryption failed; message NOT sent.")
                return SendMessageResult.EncryptFailed
            }
        } else {
            plan.boundedContent
        }

        val packet = ChatSendPolicy.buildPacket(
            localNodeId,
            recipientId,
            generatedPacketId,
            contentToSend,
            plan.boundedChannel,
            plan.isEncrypted
        )

        // Commit the local bubble only after Android accepted the BLE write.
        // Otherwise the composer retains the text and reports the failed handoff.
        if (!bleManager.sendPacket(packet.toByteArray())) return SendMessageResult.NotReady
        lastOutboundPacketId = generatedPacketId
        val messageId = existingMessage?.id ?: dbHelper.insertMessage(
            senderId = localNodeId,
            recipientId = recipientId,
            content = plan.boundedContent,
            channel = plan.persistChannel,
            packetId = generatedPacketId,
            status = plan.localStatus,
            isEncrypted = plan.isEncrypted
        )
        if (existingMessage != null) dbHelper.updateMessageStatusById(messageId, DeliveryStatusPolicy.PENDING)
        if (!plan.isChannelSend) outboundDeliveryStore.track(messageId, packet.toByteArray(), System.currentTimeMillis())
        refreshData()
        return SendMessageResult.Sent
    }

    fun retryMessage(message: ChatMessage): Boolean {
        if (!ChatSendPolicy.canRetry(
                message.recipientId,
                message.channel,
                message.senderId,
                bleManager.connectedNodeId,
                _isRangeTestActive.value,
                message.status
            )
        ) return false
        val sent = if (outboundDeliveryStore.hasPayload(message.id)) {
            deliveryRetries.retry(message.id, manual = true)
        } else {
            // Legacy records have no saved wire payload. Only an explicit user
            // retry may rebuild it, and encrypted messages still require their key.
            sendMessageInternal(message.recipientId, message.content, "", message) == SendMessageResult.Sent
        }
        refreshData()
        return sent
    }

    private fun retryQueuedDirectMessages(recipientId: Long) {
        repositoryScope.launch(dbDispatcher) {
            for (attempt in deliveryRetries.candidates(recipientId)) {
                deliveryRetries.retry(attempt.messageId)
                delay(SettingsRebootPolicy.QUEUED_RETRY_STAGGER_MS)
            }
            refreshData()
        }
    }

    private fun connectedLoraSf(): Int {
        val nodeId = bleManager.connectedNodeId
        if (nodeId == 0L) return NodeSettingsPrefs.DEFAULT_SF
        return NodeSettingsPrefs.readLoraSf(
            context.getSharedPreferences(NodeSettingsPrefs.prefsName(nodeId), Context.MODE_PRIVATE)
        )
    }

    private fun startPendingMessageTimeoutMonitor() {
        repositoryScope.launch(dbDispatcher) {
            while (true) {
                delay(MeshReplyPolicy.ACK_POLL_MS)
                val cutoff = MeshReplyPolicy.ackCutoff(System.currentTimeMillis(), connectedLoraSf())
                val changed = dbHelper.markTimedOutPendingMessages(cutoff) +
                    outboundDeliveryStore.expire(System.currentTimeMillis())
                if (changed > 0) {
                    refreshData()
                }
            }
        }
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
        gpsDutyIntervalSecs: Int = 900,
        fixedPosition: Boolean = false,
        fixedLatitude: Float = 0f,
        fixedLongitude: Float = 0f,
        fixedAltitude: Int = 0,
        meshHopLimit: Int = 4,
        rebroadcastTxdelayX100: Int = 100
    ): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false

        val localNodeId = bleManager.connectedNodeId
        val packet = LocalNodeConfigApply.build(
            localNodeId,
            PacketIdGenerator.next(),
            LocalNodeConfigRequest(
                name = name,
                shortName = shortName,
                sf = sf,
                bw = bw,
                txPower = txPower,
                region = region,
                role = role,
                telemetryInterval = telemetryInterval,
                screenTimeout = screenTimeout,
                powerSaveMode = powerSaveMode,
                positionPrecision = positionPrecision,
                gpsMode = gpsMode,
                gpsDutyIntervalSecs = gpsDutyIntervalSecs,
                fixedPosition = fixedPosition,
                fixedLatitude = fixedLatitude,
                fixedLongitude = fixedLongitude,
                fixedAltitude = fixedAltitude,
                meshHopLimit = meshHopLimit,
                rebroadcastTxdelayX100 = rebroadcastTxdelayX100,
                maxHopLimit = NodeSettingsPrefs.readMaxHopLimit(
                    context.getSharedPreferences(NodeSettingsPrefs.prefsName(localNodeId), Context.MODE_PRIVATE)
                )
            )
        )

        val success = bleManager.sendPacket(packet.toByteArray())
        if (success) {
            val clippedShort = packet.config.nodeShortName
            dbHelper.updateNodeNameAndShortName(localNodeId, name, clippedShort)
            if (localNodeId != 0L) {
                context.getSharedPreferences(NodeSettingsPrefs.prefsName(localNodeId), Context.MODE_PRIVATE)
                    .edit()
                    .putString(NodeSettingsPrefs.KEY_NODE_NAME, name)
                    .putString(NodeSettingsPrefs.KEY_NODE_SHORT, clippedShort)
                    .putBoolean(NodeSettingsPrefs.KEY_POWER_SAVE, powerSaveMode)
                    .apply()
            }
            context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(AppUiPrefs.LAST_POWER_SAVE, powerSaveMode)
                .apply()
            refreshData()
            expectPostSettingsReconnect = true
            _isDeviceAuthenticated.value = false
            _authenticationRequired.value = null
            Log.d(TAG, "Settings applied — scheduling post-reboot BLE refresh")
            bleManager.prepareForNodeReboot(
                closeAfterMs = SettingsRebootPolicy.CLOSE_AFTER_MS,
                reconnectAfterMs = SettingsRebootPolicy.RECONNECT_AFTER_MS
            )
        }
        return success
    }

    /** Persist on-device settings into phone prefs and trigger Settings UI reload. */
    private fun hydrateNodeSettingsFromDevice(nodeId: Long, config: NodeConfig) {
        if (nodeId == 0L) return
        val prefs = context.getSharedPreferences(NodeSettingsPrefs.prefsName(nodeId), Context.MODE_PRIVATE)
        NodeSettingsStore.writeFromDevice(prefs, config)

        context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AppUiPrefs.LAST_POWER_SAVE, config.powerSaveMode)
            .apply()

        if (NodeNamePolicy.shouldHydrateNames(config.nodeName, config.nodeShortName)) {
            val existing = dbHelper.getNodes().firstOrNull { MeshNodeId.same(it.nodeId, nodeId) }
            val names = NodeNamePolicy.namesFromDevice(
                nodeId = nodeId,
                deviceLongName = config.nodeName,
                deviceShortName = config.nodeShortName,
                prefsShort = prefs.getString(NodeSettingsPrefs.KEY_NODE_SHORT, null),
                existingName = existing?.name.orEmpty(),
                existingShort = existing?.shortName.orEmpty()
            )
            if (names.persistDeviceShort) {
                prefs.edit().putString(NodeSettingsPrefs.KEY_NODE_SHORT, names.shortName).apply()
            }
            if (names.writeDb) {
                dbHelper.updateNodeNameAndShortName(nodeId, names.longName, names.shortName)
            }
        }

        _needsRegionSetup.value = !config.regionConfigured
        _deviceConfigSyncEpoch.value = _deviceConfigSyncEpoch.value + 1
        Log.d(
            TAG,
            "Hydrated node settings from device 0x${nodeId.toString(16)} " +
                "SF=${config.loraSf} region=${config.region} regionConfigured=${config.regionConfigured}"
        )
        refreshData()
    }

    fun clearRegionSetupPrompt() {
        _needsRegionSetup.value = false
    }

    fun updateNodeNameAndShortName(nodeId: Long, name: String, shortName: String) {
        val short = NodeNamePolicy.clipShort(shortName)
        dbHelper.updateNodeNameAndShortName(nodeId, name, short)
        if (nodeId != 0L) {
            context.getSharedPreferences(NodeSettingsPrefs.prefsName(nodeId), Context.MODE_PRIVATE)
                .edit()
                .putString(NodeSettingsPrefs.KEY_NODE_NAME, name)
                .putString(NodeSettingsPrefs.KEY_NODE_SHORT, short)
                .apply()
        }
        refreshData()
        // Push onto the connected node so the name lives in mesh telemetry
        // (survives a fresh app install). Remote nodes need Remote Config +
        // admin password — phone-only renames are temporary until then.
        if (bleManager.isConnected && _isDeviceAuthenticated.value &&
            MeshNodeId.same(nodeId, bleManager.connectedNodeId)
        ) {
            sendNameOnlyConfig(nodeId, name, shortName = short)
        }
    }

    private fun remoteControlAuthProtocol(nodeId: Long): Int {
        val peerVersion = _nodes.value.firstOrNull { MeshNodeId.same(it.nodeId, nodeId) }?.protocolVersion ?: 1
        return RemoteControlAuthPolicy.authProtocolForPeer(peerVersion)
    }

    /**
     * Writes only name/short-name onto a node (no radio reboot). Local BLE when
     * [nodeId] is the connected node; otherwise authenticated remote config.
     */
    fun sendNameOnlyConfig(
        nodeId: Long,
        name: String,
        adminPassword: String = "",
        shortName: String = ""
    ): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false
        val localNodeId = bleManager.connectedNodeId
        val isLocal = MeshNodeId.same(nodeId, localNodeId)
        val packet = NameOnlyConfigApply.build(
            localNodeId = localNodeId,
            nodeId = nodeId,
            name = name,
            shortName = shortName,
            adminPassword = adminPassword,
            authProtocol = remoteControlAuthProtocol(nodeId),
            packetId = PacketIdGenerator.next(),
            isLocal = isLocal
        ) ?: return false

        val success = bleManager.sendPacket(packet.toByteArray())
        if (success) {
            dbHelper.updateNodeNameAndShortName(
                nodeId, packet.config.nodeName, packet.config.nodeShortName
            )
            context.getSharedPreferences(NodeSettingsPrefs.prefsName(nodeId), Context.MODE_PRIVATE)
                .edit()
                .putString(NodeSettingsPrefs.KEY_NODE_NAME, packet.config.nodeName)
                .putString(NodeSettingsPrefs.KEY_NODE_SHORT, packet.config.nodeShortName)
                .apply()
            refreshData()
        }
        return success
    }

    fun startTraceRoute(targetId: Long): Boolean {
        val localNodeId = bleManager.connectedNodeId
        if (!TraceRoutePolicy.canStart(
                bleManager.isConnected, _isDeviceAuthenticated.value, localNodeId, targetId
            )
        ) return false

        val traceId = PacketIdGenerator.next()
        val packet = TraceRoutePolicy.buildRequest(localNodeId, targetId, traceId)
        if (!bleManager.sendPacket(packet.toByteArray())) return false

        val sf = connectedLoraSf()
        traceRouteJob?.cancel()
        _traceRouteState.value = TraceRoutePolicy.starting(targetId, traceId, System.currentTimeMillis())
        traceRouteJob = repositoryScope.launch {
            delay(TraceRoutePolicy.timeoutMs(sf))
            TraceRoutePolicy.timedOut(
                _traceRouteState.value, traceId, System.currentTimeMillis()
            )?.let { _traceRouteState.value = it }
        }
        return true
    }

    fun hideTraceRouteDialog() {
        _traceRouteState.value = _traceRouteState.value.copy(showDialog = false)
    }

    /** Stop an in-flight traceroute and surface a cancelled result in the dialog. */
    fun cancelTraceRoute() {
        val updated = TraceRoutePolicy.cancelled(_traceRouteState.value, System.currentTimeMillis())
            ?: return
        traceRouteJob?.cancel()
        _traceRouteState.value = updated
    }

    fun clearTraceRouteResult() {
        traceRouteJob?.cancel()
        _traceRouteState.value = _traceRouteState.value.copy(
            visible = false,
            showDialog = false,
            active = false
        )
    }

    /**
     * @return packet_id of the sent request, or null if not sent.
     */
    fun requestRemoteConfigReport(nodeId: Long, password: String): Int? {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return null
        val localNodeId = bleManager.connectedNodeId
        val packetId = PacketIdGenerator.next()
        val packet = RemoteConfigApply.buildReportRequest(
            localNodeId = localNodeId,
            nodeId = nodeId,
            password = password,
            authProtocol = remoteControlAuthProtocol(nodeId),
            packetId = packetId
        ) ?: return null
        return if (bleManager.sendPacket(packet.toByteArray())) packetId else null
    }

    /**
     * @param applyMask non-zero sparse bitmask ([ConfigApplyMask]); required for safe remote updates.
     * @return packet_id if sent, null otherwise.
     */
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
        gpsDutyIntervalSecs: Int = 900,
        fixedPosition: Boolean = false,
        fixedLatitude: Float = 0f,
        fixedLongitude: Float = 0f,
        fixedAltitude: Int = 0,
        meshHopLimit: Int = 0,
        rebroadcastTxdelayX100: Int = 0,
        applyMask: Int
    ): Int? {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return null
        val localNodeId = bleManager.connectedNodeId
        val packetId = PacketIdGenerator.next()
        val packet = RemoteConfigApply.buildApply(
            localNodeId = localNodeId,
            request = RemoteConfigApplyRequest(
                nodeId = nodeId,
                name = name,
                password = password,
                sf = sf,
                bw = bw,
                txPower = txPower,
                region = region,
                role = role,
                telemetryInterval = telemetryInterval,
                screenTimeout = screenTimeout,
                powerSaveMode = powerSaveMode,
                positionPrecision = positionPrecision,
                gpsMode = gpsMode,
                gpsDutyIntervalSecs = gpsDutyIntervalSecs,
                fixedPosition = fixedPosition,
                fixedLatitude = fixedLatitude,
                fixedLongitude = fixedLongitude,
                fixedAltitude = fixedAltitude,
                meshHopLimit = meshHopLimit,
                rebroadcastTxdelayX100 = rebroadcastTxdelayX100,
                applyMask = applyMask,
                maxHopLimit = NodeSettingsPrefs.readMaxHopLimit(
                    context.getSharedPreferences(NodeSettingsPrefs.prefsName(nodeId), Context.MODE_PRIVATE)
                )
            ),
            authProtocol = remoteControlAuthProtocol(nodeId),
            packetId = packetId
        ) ?: return null
        return if (bleManager.sendPacket(packet.toByteArray())) packetId else null
    }

    fun startFirmwareUpdate(firmware: ByteArray) {
        when (
            val start = OtaStartPolicy.begin(
                _otaState.value.active, bleManager.isConnected, _isDeviceAuthenticated.value
            )
        ) {
            OtaStartDecision.AlreadyActive -> return
            is OtaStartDecision.Blocked -> {
                _otaState.value = start.state
                return
            }
            OtaStartDecision.Run -> {
                val nodeId = bleManager.connectedNodeId
                otaTargetNodeId = nodeId
                captureOtaPreFlashVersion(nodeId)
                otaJob = repositoryScope.launch(Dispatchers.IO) {
                    bleManager.otaExclusive = true
                    try {
                        esp32OtaSender.upload(
                            firmware = firmware,
                            nodeId = nodeId,
                            expectedVersion = otaExpectedFirmwareVersion,
                            onState = { _otaState.value = it },
                            onSuccess = { markOtaFirmwareCacheUpdated() },
                            onDiagnostic = { appendDiagnostic(it) },
                            requestHighPriority = { bleManager.requestHighConnectionPriority() },
                            resumeAfter = { bleManager.resumeAfterDfu() }
                        )
                    } finally {
                        bleManager.otaExclusive = false
                    }
                }
            }
        }
    }

    fun cancelFirmwareUpdate() {
        // Abort DFU first so the progress listener can own the final status.
        dfuController?.abort()
        otaJob?.cancel()
        bleManager.otaExclusive = false
    }

    // --- RAK/nRF52 firmware update via the Nordic DFU bootloader ---
    // ENTER_DFU reboots the node into its Adafruit/Nordic bootloader; the
    // Nordic DFU library then streams the .zip package to the bootloader
    // directly (same mechanism Meshtastic uses on the RAK4631). If the
    // transfer never starts, the bootloader times out back into the current
    // firmware - nothing is lost.
    private var dfuController: no.nordicsemi.android.dfu.DfuServiceController? = null

    // Set by any DFU listener callback; a watchdog uses it to detect a DFU
    // service that silently never engaged (e.g. bootloader not found).
    @Volatile
    private var dfuSawActivity = false

    private val dfuProgressListener = object : no.nordicsemi.android.dfu.DfuProgressListenerAdapter() {
        override fun onDeviceConnecting(deviceAddress: String) {
            dfuSawActivity = true
            _otaState.value = OtaState(
                active = true,
                status = DfuSessionPolicy.STATUS_CONNECTING,
                expectedVersion = otaExpectedFirmwareVersion
            )
        }

        override fun onDfuProcessStarting(deviceAddress: String) {
            dfuSawActivity = true
            _otaState.value = OtaState(
                active = true,
                status = DfuSessionPolicy.STATUS_PROCESS_STARTING,
                expectedVersion = otaExpectedFirmwareVersion
            )
        }

        override fun onFirmwareValidating(deviceAddress: String) {
            dfuSawActivity = true
            _otaState.value = OtaState(
                active = true,
                progress = 100,
                status = DfuSessionPolicy.STATUS_VALIDATING,
                expectedVersion = otaExpectedFirmwareVersion
            )
        }

        override fun onDeviceDisconnecting(deviceAddress: String?) {
            dfuSawActivity = true
        }

        override fun onProgressChanged(
            deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float,
            currentPart: Int, partsTotal: Int
        ) {
            dfuSawActivity = true
            val partHint = if (partsTotal > 1) " (part $currentPart/$partsTotal)" else ""
            _otaState.value = OtaState(
                active = true,
                progress = percent,
                status = DfuSessionPolicy.uploadingStatus(percent, partHint),
                expectedVersion = otaExpectedFirmwareVersion
            )
        }

        override fun onDfuCompleted(deviceAddress: String) {
            val expected = otaExpectedFirmwareVersion
            markOtaFirmwareCacheUpdated()
            _otaState.value = OtaTransferPolicy.successState(
                expected = expected,
                status = OtaTransferPolicy.successStatus(expected, dfu = true)
            )
            dfuController = null
            bleManager.resumeAfterDfu()
            // Follow-up status once reconnect scheduling is running.
            repositoryScope.launch {
                delay(1_500)
                if (_otaState.value.done && !_otaState.value.suspectRollback) {
                    _otaState.value = OtaTransferPolicy.successState(
                        expected = expected,
                        status = OtaTransferPolicy.reconnectingStatus(expected, dfu = true)
                    )
                }
            }
        }

        override fun onDfuAborted(deviceAddress: String) {
            _otaState.value = OtaState(error = true, status = DfuSessionPolicy.STATUS_CANCELLED)
            dfuController = null
            bleManager.resumeAfterDfu()
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
            _otaState.value = OtaState(
                error = true,
                status = DfuSessionPolicy.failedStatus(error, message)
            )
            dfuController = null
            bleManager.resumeAfterDfu()
        }
    }

    init {
        no.nordicsemi.android.dfu.DfuServiceListenerHelper.registerProgressListener(context, dfuProgressListener)
    }

    // Scan for a device advertising a Nordic DFU service (legacy or secure).
    // Returns its address, or null if none appears within the timeout.
    private suspend fun findDfuDevice(timeoutMs: Long): String? {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val scanner = btManager?.adapter?.bluetoothLeScanner ?: return null

        val legacyDfu = android.os.ParcelUuid.fromString(DfuSessionPolicy.LEGACY_SERVICE_UUID)
        val secureDfu = android.os.ParcelUuid.fromString(DfuSessionPolicy.SECURE_SERVICE_UUID)
        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder().setServiceUuid(legacyDfu).build(),
            android.bluetooth.le.ScanFilter.Builder().setServiceUuid(secureDfu).build()
        )
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val found = kotlinx.coroutines.CompletableDeferred<String?>()
        val callback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                Log.d(TAG, "DFU scan hit: ${result.device.address}")
                found.complete(result.device.address)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "DFU scan failed: $errorCode")
                found.complete(null)
            }
        }

        return try {
            scanner.startScan(filters, settings, callback)
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { found.await() }
        } catch (e: SecurityException) {
            Log.e(TAG, "DFU scan permission error: ${e.message}")
            null
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
        }
    }

    fun startRakDfuUpdate(zipUri: android.net.Uri) {
        when (
            val start = OtaStartPolicy.begin(
                _otaState.value.active, bleManager.isConnected, _isDeviceAuthenticated.value
            )
        ) {
            OtaStartDecision.AlreadyActive -> return
            is OtaStartDecision.Blocked -> {
                _otaState.value = start.state
                return
            }
            OtaStartDecision.Run -> { }
        }
        val mac = bleManager.getConnectedDeviceAddress()
        when (val addr = OtaStartPolicy.requireAddress(mac)) {
            is OtaStartDecision.Blocked -> {
                _otaState.value = addr.state
                return
            }
            else -> { }
        }
        val deviceName = bleManager.connectedDeviceName ?: "AetherMesh"
        val nodeId = bleManager.connectedNodeId
        otaTargetNodeId = nodeId
        captureOtaPreFlashVersion(nodeId)

        otaJob = repositoryScope.launch(Dispatchers.IO) {
            try {
                // SAF content:// grants are activity-scoped; the DFU service needs a
                // FileProvider URI under our cacheDir.
                val readableZip = copyZipForDfuService(zipUri)
                val expected = otaExpectedFirmwareVersion

                _otaState.value = OtaState(
                    active = true,
                    status = DfuSessionPolicy.STATUS_REBOOTING,
                    expectedVersion = expected
                )
                otaInbox.drain()

                esp32OtaSender.sendControl(nodeId, com.silentwolf75.aethermesh.proto.OtaControl.Op.ENTER_DFU, 0, "")
                otaInbox.awaitState(
                    com.silentwolf75.aethermesh.proto.OtaStatus.State.READY,
                    DfuSessionPolicy.ENTER_ACK_TIMEOUT_MS,
                    "DFU mode acknowledgment"
                )

                // The node is rebooting into its bootloader; release our GATT and
                // pause auto-reconnect so the DFU library owns the connection.
                bleManager.detachForDfu()
                delay(DfuSessionPolicy.BOOTLOADER_SETTLE_MS) // bootloader boot + advertising settle

                // Nordic-family bootloaders often advertise on a DIFFERENT
                // address (MAC+1) and name in DFU mode, so scan for the DFU
                // service instead of assuming the application's address.
                _otaState.value = OtaState(
                    active = true,
                    status = DfuSessionPolicy.STATUS_SEARCHING,
                    expectedVersion = expected
                )
                val dfuMac = findDfuDevice(DfuSessionPolicy.SCAN_TIMEOUT_MS)
                    ?: throw Exception(DfuSessionPolicy.ERR_NOT_ADVERTISING)
                Log.d(TAG, "DFU bootloader found at $dfuMac (app was at $mac)")

                dfuSawActivity = false
                _otaState.value = OtaState(
                    active = true,
                    status = DfuSessionPolicy.STATUS_STARTING_TRANSFER,
                    expectedVersion = expected
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        no.nordicsemi.android.dfu.DfuServiceInitiator.createDfuNotificationChannel(context)
                    }
                    dfuController = no.nordicsemi.android.dfu.DfuServiceInitiator(dfuMac)
                        .setDeviceName(deviceName)
                        .setKeepBond(false)
                        .setZip(readableZip)
                        .setForeground(true)
                        .setDisableNotification(false)
                        .start(context, com.silentwolf75.aethermesh.ble.DfuService::class.java)
                }

                // Watchdog: if the DFU service shows no life at all, surface an
                // error instead of leaving the bar stuck forever.
                delay(DfuSessionPolicy.SERVICE_ENGAGE_WATCHDOG_MS)
                if (!dfuSawActivity && _otaState.value.active) {
                    dfuController?.abort()
                    throw Exception(DfuSessionPolicy.ERR_NEVER_ENGAGED)
                }
                // From here the DfuProgressListener drives otaState.
            } catch (e: kotlinx.coroutines.CancellationException) {
                // abort() listener usually sets "DFU cancelled"; don't overwrite as failure.
                if (_otaState.value.active &&
                    !_otaState.value.status.contains("cancel", ignoreCase = true)
                ) {
                    _otaState.value = OtaState(error = true, status = DfuSessionPolicy.STATUS_CANCELLED)
                    bleManager.resumeAfterDfu()
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "DFU start failed: ${e.message}")
                _otaState.value = OtaState(
                    error = true,
                    status = DfuSessionPolicy.failedStatus(e.message)
                )
                bleManager.resumeAfterDfu()
            }
        }
    }

    /** Copy a DFU zip into cacheDir so the Nordic DFU service can open it. */
    private fun copyZipForDfuService(zipUri: android.net.Uri): android.net.Uri {
        val bytes = DfuZipPolicy.requireBytes(
            context.contentResolver.openInputStream(zipUri)?.use { it.readBytes() }
        )
        val dir = java.io.File(context.cacheDir, DfuZipPolicy.CACHE_SUBDIR).apply { mkdirs() }
        val safeName = DfuZipPolicy.safeFileName(zipUri.lastPathSegment)
        val file = java.io.File(dir, safeName)
        file.writeBytes(bytes)
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    fun resetOtaState() {
        if (!_otaState.value.active) _otaState.value = OtaState()
    }

    /**
     * Remember the catalog / package version label before starting BLE OTA or DFU.
     * On success we write this into the node row so Installed / Node Details do not
     * keep showing the pre-flash firmware until telemetry arrives.
     */
    fun rememberOtaExpectedFirmware(versionLabel: String?) {
        otaExpectedFirmwareVersion = FirmwareFreshnessPolicy.expectedLabel(versionLabel)
    }

    private fun captureOtaPreFlashVersion(nodeId: Long) {
        val memory = _nodes.value.firstOrNull { MeshNodeId.same(it.nodeId, nodeId) }?.firmwareVersion
        val needDb = nodeId != 0L && FirmwareFreshnessPolicy.expectedLabel(memory).isEmpty()
        val db = if (needDb) {
            dbHelper.getNodes().firstOrNull { MeshNodeId.same(it.nodeId, nodeId) }?.firmwareVersion
        } else {
            null
        }
        otaPreFlashFirmwareVersion = FirmwareFreshnessPolicy.pickPreFlashVersion(nodeId, memory, db)
    }

    private fun markAwaitingFirmwareTelemetry(nodeId: Long) {
        FirmwareFreshnessPolicy.awaiting(nodeId)?.let { _firmwareFreshness.value = it }
    }

    private fun onFreshFirmwareTelemetry(nodeId: Long, reported: String) {
        if (!FirmwareFreshnessPolicy.acceptReported(reported)) return
        val fresh = reported.trim()
        _firmwareFreshness.value = FirmwareFreshnessPolicy.onReported(
            _firmwareFreshness.value, nodeId, fresh
        )
        maybeConfirmOtaApplied(nodeId, fresh)
    }

    private fun maybeConfirmOtaApplied(nodeId: Long, reported: String) {
        when (
            val decision = OtaVerifyPolicy.decide(
                otaVerifyNodeId, nodeId, otaVerifyExpected, otaVerifyPre, reported
            )
        ) {
            OtaVerifyDecision.Ignore, OtaVerifyDecision.Wait -> return
            is OtaVerifyDecision.Finish -> {
                if (decision.state.suspectRollback) {
                    Log.w(TAG, decision.state.status)
                }
                _otaState.value = decision.state
                otaVerifyNodeId = 0L
                otaVerifyExpected = ""
                otaVerifyPre = ""
            }
        }
    }

    private fun markOtaFirmwareCacheUpdated() {
        val nodeId = otaTargetNodeId.takeIf { it != 0L } ?: bleManager.connectedNodeId
        if (nodeId == 0L) return
        val label = otaExpectedFirmwareVersion
        val pre = otaPreFlashFirmwareVersion
        // Prefer the known package label; otherwise clear so UI shows unknown
        // instead of a confidently wrong pre-OTA string.
        dbHelper.setNodeFirmwareVersion(nodeId, label)
        otaVerifyNodeId = nodeId
        otaVerifyExpected = label
        otaVerifyPre = pre
        otaExpectedFirmwareVersion = ""
        otaPreFlashFirmwareVersion = ""
        otaTargetNodeId = 0L
        markAwaitingFirmwareTelemetry(nodeId)
        refreshData()
        Log.d(TAG, "Post-OTA firmware cache for 0x${nodeId.toString(16)} -> '${label.ifEmpty { "(cleared)" }}' (pre='$pre')")
    }

    // Per-node battery-alert state: the lowest threshold we've already warned
    // about (0 = none). Cleared when a node recharges above RECOVER, so a
    // charge/drain cycle re-arms the alerts. Prevents re-notifying every 60s
    // telemetry while a node sits below a threshold.
    private val batteryAlertLevel = mutableMapOf<Long, Int>()

    private fun notifyLowBattery(nodeId: Long, level: Int, isCharging: Boolean) {
        when (
            val action = BatteryAlertPolicy.decide(
                nodeId, level, isCharging, batteryAlertLevel[nodeId] ?: 0
            )
        ) {
            BatteryAlertAction.Ignore -> return
            BatteryAlertAction.Rearm -> {
                batteryAlertLevel.remove(nodeId)
                return
            }
            is BatteryAlertAction.Notify -> {
                batteryAlertLevel[nodeId] = action.threshold
                val prefs = context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)
                val notifGranted = android.os.Build.VERSION.SDK_INT < NotificationGatePolicy.POST_NOTIFICATIONS_SDK ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!NotificationGatePolicy.mayNotifyBattery(
                        prefs.getBoolean(AppUiPrefs.BG_ALERTS, true),
                        android.os.Build.VERSION.SDK_INT,
                        notifGranted
                    )
                ) return

                val spanish = AppUiPrefs.isSpanish(prefs.getString(AppUiPrefs.LANGUAGE, AppUiPrefs.LANG_ENGLISH))
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val chanId = BatteryAlertPolicy.CHANNEL_ID
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(
                            chanId,
                            BatteryAlertPolicy.channelName(spanish),
                            android.app.NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = BatteryAlertPolicy.channelDescription(spanish)
                        }
                    )
                }
                val name = dbHelper.getNodes().find { MeshNodeId.same(it.nodeId, nodeId) }?.name
                    ?: BatteryAlertPolicy.fallbackName(nodeId, spanish)
                val title = BatteryAlertPolicy.title(name, action.critical, spanish)
                val body = BatteryAlertPolicy.body(level, action.critical, spanish)

                val tapIntent = android.content.Intent(context, com.silentwolf75.aethermesh.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    // Pin to this app explicitly. The class above already makes it
                    // explicit, but a notification's PendingIntent is handed to the
                    // system, and nothing else should ever be able to receive it.
                    setPackage(context.packageName)
                    putExtra(com.silentwolf75.aethermesh.MainActivity.EXTRA_OPEN_NODE_ID, nodeId)
                }
                val notifyId = BatteryAlertPolicy.notifyId(nodeId)
                val pi = android.app.PendingIntent.getActivity(
                    context, notifyId, tapIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val notif = androidx.core.app.NotificationCompat.Builder(context, chanId)
                    .setSmallIcon(com.silentwolf75.aethermesh.R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setGroup(BatteryAlertPolicy.GROUP)
                    .build()
                nm.notify(notifyId, notif)

                val summary = androidx.core.app.NotificationCompat.Builder(context, chanId)
                    .setSmallIcon(com.silentwolf75.aethermesh.R.drawable.ic_notification)
                    .setContentTitle(BatteryAlertPolicy.summaryTitle(spanish))
                    .setContentText(BatteryAlertPolicy.summaryText(spanish))
                    .setStyle(
                        androidx.core.app.NotificationCompat.InboxStyle()
                            .setSummaryText(BatteryAlertPolicy.inboxSummary(spanish))
                    )
                    .setGroup(BatteryAlertPolicy.GROUP)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
                nm.notify(BatteryAlertPolicy.summaryNotifyId(), summary)
            }
        }
    }

    // Post a system notification for an incoming chat message, Meshtastic-style:
    // one notification per conversation (newest message replaces the previous),
    // tapping opens the app. Suppressed while the app is on screen, when the
    // user turned off Background Alerts in Settings, or without POST_NOTIFICATIONS.
    private fun notifyIncomingMessage(
        senderId: Long,
        chatIdentifier: String,
        channel: String,
        content: String,
        isBroadcast: Boolean
    ) {
        val prefs = context.getSharedPreferences(AppUiPrefs.FILE, Context.MODE_PRIVATE)
        val app = context.applicationContext as? com.silentwolf75.aethermesh.AetherMeshApplication
        val notifGranted = android.os.Build.VERSION.SDK_INT < NotificationGatePolicy.POST_NOTIFICATIONS_SDK ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!NotificationGatePolicy.mayNotifyMessage(
                prefs.getBoolean(AppUiPrefs.BG_ALERTS, true),
                ChatThreadPrefs.isMuted(prefs, chatIdentifier),
                app?.isActivityVisible == true,
                android.os.Build.VERSION.SDK_INT,
                notifGranted
            )
        ) return

        val spanish = AppUiPrefs.isSpanish(prefs.getString(AppUiPrefs.LANGUAGE, AppUiPrefs.LANG_ENGLISH))
        val notifChannelId = IncomingMessageAlertPolicy.CHANNEL_ID
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    notifChannelId,
                    IncomingMessageAlertPolicy.channelName(spanish),
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = IncomingMessageAlertPolicy.channelDescription(spanish)
                }
            )
        }

        val senderName = dbHelper.getNodes().find { MeshNodeId.same(it.nodeId, senderId) }?.name
            ?: IncomingMessageAlertPolicy.fallbackName(senderId, spanish)
        val title = IncomingMessageAlertPolicy.title(senderName, channel, isBroadcast)
        val notifyId = IncomingMessageAlertPolicy.notifyId(chatIdentifier)

        val tapIntent = android.content.Intent(context, com.silentwolf75.aethermesh.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Pin to this app explicitly; see the battery alert above.
            setPackage(context.packageName)
            if (isBroadcast) {
                putExtra(com.silentwolf75.aethermesh.MainActivity.EXTRA_OPEN_CHANNEL, channel)
            } else {
                putExtra(com.silentwolf75.aethermesh.MainActivity.EXTRA_OPEN_DM_PEER, senderId)
            }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, notifyId, tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val groupKey = IncomingMessageAlertPolicy.GROUP
        val notification = androidx.core.app.NotificationCompat.Builder(context, notifChannelId)
            .setSmallIcon(com.silentwolf75.aethermesh.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .build()
        nm.notify(notifyId, notification)

        val summary = androidx.core.app.NotificationCompat.Builder(context, notifChannelId)
            .setSmallIcon(com.silentwolf75.aethermesh.R.drawable.ic_notification)
            .setContentTitle(IncomingMessageAlertPolicy.summaryTitle(spanish))
            .setContentText(IncomingMessageAlertPolicy.summaryText(spanish))
            .setStyle(
                androidx.core.app.NotificationCompat.InboxStyle()
                    .setSummaryText(IncomingMessageAlertPolicy.inboxSummary(spanish))
            )
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify(IncomingMessageAlertPolicy.summaryNotifyId(), summary)
    }

    fun refreshData() {
        // Keep directory / history visible while the password dialog is up.
        // Send paths already require auth; wiping UI made Nodes/Map look empty.
        repositoryScope.launch(dbDispatcher) {
            val chatNodeId = _activeChatId.value
            val list = if (chatNodeId == null) {
                dbHelper.getMessages(0L, isChannel = true, channel = _selectedChannel.value)
            } else {
                dbHelper.getMessages(chatNodeId, isChannel = false, channel = "")
            }
            _messages.value = list

            _nodes.value = dbHelper.getNodes()

            // Keep the channel list in sync
            _channels.value = ChannelPersistPolicy.mergeInboxNames(
                dbNames = dbHelper.getChannels(),
                current = _channels.value,
                selected = _selectedChannel.value
            )
        }
    }

    // Switch the channel shown in the Chats view.
    fun selectChannel(channel: String) {
        if (!ChannelPersistPolicy.canSelect(channel)) return
        _selectedChannel.value = channel
        _activeChatId.value = null
        if (!_channels.value.contains(channel)) {
            _channels.value = (_channels.value + channel).distinct()
        }
        refreshData()
    }

    // Switch to a private Direct Message chat
    fun selectDirectMessage(nodeId: Long) {
        _activeChatId.value = nodeId
        refreshData()
    }

    // Create (and switch to) a new empty channel. Returns false if it already exists.
    fun createChannel(channel: String): Boolean {
        return when (val plan = ChannelPersistPolicy.planCreate(_channels.value, channel)) {
            ChannelCreateResult.Invalid -> false
            is ChannelCreateResult.AlreadyExists -> {
                selectChannel(plan.name)
                false
            }
            is ChannelCreateResult.Created -> {
                selectChannel(plan.name)
                true
            }
        }
    }

    // Retrieve specific direct chat messages
    fun getDirectMessages(peerNodeId: Long): List<ChatMessage> {
        return dbHelper.getMessages(peerNodeId, isChannel = false)
    }

    fun clearAllMessages() {
        dbHelper.clearAllMessages()
        val editor = securePrefs.edit()
        ChatKeyStorePolicy.dmPrefKeys(securePrefs.all.keys).forEach(editor::remove)
        editor.apply()
        refreshData()
    }

    fun clearAllNodes() {
        dbHelper.clearAllNodes()
        _observedRoutes.value = emptyMap()
        if (_isRangeTestActive.value) {
            stopRangeTest()
        }
        _rangeTestLogs.value = emptyList()
        refreshData()
    }

    // AES-256-GCM v2; decrypt also accepts v1 GCM and legacy ECB.
    // Returns null on encrypt failure — callers must refuse to send, never
    // fall back to plaintext.
    fun encryptAES(plainText: String, passcode: String, chatIdentifier: String = ""): String? {
        val cipher = ChatCrypto.encrypt(plainText, passcode, chatIdentifier)
        if (cipher == null) Log.e(TAG, "Encryption failed")
        return cipher
    }

    fun decryptAES(cipherText: String, passcode: String, chatIdentifier: String = ""): String {
        val plain = ChatCrypto.decrypt(cipherText, passcode, chatIdentifier)
        if (plain.startsWith("[Decryption Error")) {
            Log.e(TAG, "Decryption failed: $plain")
        }
        return plain
    }

    // Range Test Engine Methods
    fun startRangeTest(targetId: Long, intervalSeconds: Int) {
        val localNodeId = bleManager.connectedNodeId
        val sf = NodeSettingsPrefs.readLoraSf(
            context.getSharedPreferences(NodeSettingsPrefs.prefsName(localNodeId), Context.MODE_PRIVATE)
        )
        when (val start = RangeTestPolicy.begin(localNodeId, targetId, intervalSeconds, sf)) {
            RangeTestStart.SelfTarget -> {
                Log.w(
                    TAG,
                    "Refusing range test: target 0x${targetId.toString(16).uppercase()} is the " +
                        "BLE-connected node 0x${localNodeId.toString(16).uppercase()} " +
                        "(half-duplex radio cannot ping itself)."
                )
                return
            }
            is RangeTestStart.Run -> {
                val intervalSecondsClamped = start.intervalSeconds
                if (intervalSecondsClamped != intervalSeconds) {
                    Log.w(TAG, "Range test interval clamped $intervalSeconds → ${intervalSecondsClamped}s (SF$sf)")
                }
                if (rangeTestJob != null) stopRangeTest()
                rangeTestTargetId = targetId
                rangeTestSf = sf
                _rangeTestSessionStartMs.value = System.currentTimeMillis()
                rangeTestRxBaseline = _meshDiagnostics.value?.rxPackets ?: -1L
                _isRangeTestActive.value = true
                _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
                startRangeTestLocationUpdates()
                pendingRangePings.clear()
                sendRangeTestControl(RangeTestControl.Op.START)

                rangeTestJob = repositoryScope.launch {
                    while (_isRangeTestActive.value) {
                        expirePendingRangePings()
                        sendRangePing(targetId)
                        val nextPingAt = System.currentTimeMillis() + intervalSecondsClamped * 1000L
                        while (_isRangeTestActive.value && System.currentTimeMillis() < nextPingAt) {
                            val remaining = (nextPingAt - System.currentTimeMillis()).coerceAtLeast(1L)
                            delay(minOf(1000L, remaining))
                            expirePendingRangePings()
                        }
                    }
                }
                Log.d(TAG, "Direct range test started targeting node 0x${targetId.toString(16).uppercase()} every ${intervalSecondsClamped}s.")
            }
        }
    }

    fun loadRangeTestLogs(targetId: Long) {
        // Never retarget a live test — the banner / reopen / stop path keys off
        // rangeTestTargetId, and dialogs for other nodes only need historical logs.
        rangeTestTargetId = RangeTestPolicy.sessionTargetAfterLoad(
            _isRangeTestActive.value, rangeTestTargetId, targetId
        )
        _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
    }

    fun stopRangeTest() {
        rangeTestJob?.cancel()
        rangeTestJob = null
        _isRangeTestActive.value = false
        // Score outstanding pings as stopped rather than silent drops.
        pendingRangePings.drainAll().forEach { pending ->
            logRangeTestResult(
                pending = pending,
                success = false,
                rssi = RangeTestPolicy.FAIL_RSSI,
                snr = RangeTestPolicy.FAIL_SNR,
                failureReason = RangeTestPolicy.FAIL_STOPPED
            )
        }
        stopRangeTestLocationUpdates()
        sendRangeTestControl(RangeTestControl.Op.STOP)
        Log.d(TAG, "Range Test stopped.")
    }

    private fun sendRangeTestControl(op: RangeTestControl.Op) {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return
        val packet = RangeTestPolicy.buildControl(
            bleManager.connectedNodeId, PacketIdGenerator.next(), op
        )
        if (!bleManager.sendPacket(packet.toByteArray())) {
            Log.w(TAG, "Failed to send range-test control $op (quiet mode may be unavailable on older firmware).")
        }
    }

    private fun sendRangePing(targetId: Long) {
        if (!bleManager.isConnected) {
            stopRangeTest()
            return
        }
        if (!_isDeviceAuthenticated.value) {
            Log.w(TAG, "Range test ping skipped: BLE not authenticated.")
            logRangeTestResult(
                pending = PendingRangePing(
                    targetId = targetId,
                    sentAtMs = System.currentTimeMillis(),
                    position = captureRangeTestPosition()
                ),
                success = false,
                rssi = RangeTestPolicy.FAIL_RSSI,
                snr = RangeTestPolicy.FAIL_SNR,
                failureReason = RangeTestPolicy.FAIL_AUTH
            )
            return
        }

        val localNodeId = bleManager.connectedNodeId
        if (MeshNodeId.same(localNodeId, targetId)) {
            Log.w(TAG, "Range test ping skipped: target is the BLE-connected node.")
            logRangeTestResult(
                pending = PendingRangePing(
                    targetId = targetId,
                    sentAtMs = System.currentTimeMillis(),
                    position = captureRangeTestPosition()
                ),
                success = false,
                rssi = RangeTestPolicy.FAIL_RSSI,
                snr = RangeTestPolicy.FAIL_SNR,
                failureReason = RangeTestPolicy.FAIL_SELF
            )
            stopRangeTest()
            return
        }

        val generatedPacketId = pendingRangePings.allocatePingId { PacketIdGenerator.next() }

        val pending = PendingRangePing(
            targetId = targetId,
            sentAtMs = System.currentTimeMillis(),
            position = captureRangeTestPosition()
        )
        pendingRangePings.put(generatedPacketId, pending)

        val packet = RangeTestPolicy.buildPing(localNodeId, targetId, generatedPacketId)

        if (bleManager.sendPacket(packet.toByteArray())) {
            Log.d(TAG, "Range test ping sent: packetId=$generatedPacketId")
        } else {
            Log.w(TAG, "Range test ping failed to send over BLE.")
            if (pendingRangePings.remove(generatedPacketId, pending)) {
                logRangeTestResult(
                    pending = pending,
                    success = false,
                    rssi = RangeTestPolicy.FAIL_RSSI,
                    snr = RangeTestPolicy.FAIL_SNR,
                    failureReason = RangeTestPolicy.FAIL_BLE
                )
            }
        }
    }

    private fun captureRangeTestPosition(): RangeTestPosition {
        val phoneLoc = lastPhoneLocation
        val localNodeId = bleManager.connectedNodeId
        val localNode = dbHelper.getNodes().firstOrNull { MeshNodeId.same(it.nodeId, localNodeId) }
        return RangeTestPolicy.pickPosition(
            phoneLat = phoneLoc?.latitude,
            phoneLon = phoneLoc?.longitude,
            phoneTimeMs = phoneLoc?.time ?: 0L,
            phoneSpeed = phoneLoc?.takeIf { it.hasSpeed() }?.speed,
            phoneAccuracy = phoneLoc?.takeIf { it.hasAccuracy() }?.accuracy,
            nowMs = System.currentTimeMillis(),
            nodeLat = localNode?.latitude?.toDouble() ?: 0.0,
            nodeLon = localNode?.longitude?.toDouble() ?: 0.0
        )
    }

    private fun expirePendingRangePings() {
        pendingRangePings.expire(
            System.currentTimeMillis(),
            RangeTestPolicy.pingTimeoutMs(rangeTestSf)
        ).forEach { pending ->
            logRangeTestResult(
                pending = pending,
                success = false,
                rssi = RangeTestPolicy.FAIL_RSSI,
                snr = RangeTestPolicy.FAIL_SNR,
                failureReason = RangeTestPolicy.FAIL_TIMEOUT
            )
        }
    }

    private fun logRangeTestResult(
        pending: PendingRangePing,
        success: Boolean,
        rssi: Float,
        snr: Float,
        remoteRssi: Float? = null,
        remoteSnr: Float? = null,
        failureReason: String? = null
    ) {
        val position = pending.position
        dbHelper.insertRangeTestLog(
            pending.targetId,
            position.latitude,
            position.longitude,
            rssi,
            snr,
            success,
            remoteRssi,
            remoteSnr,
            position.speedMps,
            position.gpsAccuracyM,
            pending.sentAtMs,
            failureReason = if (success) null else failureReason
        )
        if (MeshNodeId.same(pending.targetId, rangeTestTargetId)) {
            _rangeTestLogs.value = dbHelper.getRangeTestLogs(pending.targetId)
        }
    }

    fun clearRangeTestLogs(targetId: Long) {
        dbHelper.clearRangeTestLogs(targetId)
        _rangeTestLogs.value = emptyList()
        pendingRangePings.removeForTarget(targetId)
    }

    fun getAllRangeTestLogs(): List<RangeTestLog> {
        return dbHelper.getAllRangeTestLogs()
    }

    fun getAllChatMessages(): List<ChatMessage> = dbHelper.getAllMessages()

    fun getMeshDiagnosticsHistory(): List<MeshDiagnosticsSnapshot> =
        dbHelper.getMeshDiagnosticsHistory()

    fun countQueuedStoreForwardMessages(): Int =
        dbHelper.countQueuedStoreForwardMessages()

    fun countQueuedMessagesForRecipient(recipientId: Long): Int =
        dbHelper.countQueuedMessagesForRecipient(recipientId)

    /**
     * Channel mesh self-test: send N short channel texts, score HEARD receipts
     * (when hearer receipts are on) plus RX packet delta from diagnostics.
     */
    fun startMeshSelfTest(pingCount: Int = 5) {
        val localNodeId = bleManager.connectedNodeId
        val sf = NodeSettingsPrefs.readLoraSf(
            context.getSharedPreferences(NodeSettingsPrefs.prefsName(localNodeId), Context.MODE_PRIVATE)
        )
        when (
            val start = MeshSelfTestPolicy.begin(
                pingCount,
                bleManager.isConnected,
                bleManager.isGattReady,
                _isDeviceAuthenticated.value,
                _isRangeTestActive.value,
                sf
            )
        ) {
            is MeshSelfTestStart.Refused -> {
                _meshSelfTest.value = start.result
                return
            }
            is MeshSelfTestStart.Run -> {
                stopMeshSelfTest()
                val planned = start.planned
                val settleMs = start.settleMs
                val channel = _selectedChannel.value.ifBlank { DEFAULT_CHANNEL }
                val rxBaseline = _meshDiagnostics.value?.rxPackets ?: 0L
                val packetIds = mutableListOf<Int>()
                _meshSelfTest.value = MeshSelfTestPolicy.starting(planned)
                meshSelfTestJob = repositoryScope.launch {
                    try {
                        for (i in 1..planned) {
                            if (!isActive) return@launch
                            val content = MeshSelfTestPolicy.pingContent(System.currentTimeMillis(), i)
                            val result = sendMessage(MeshSelfTestPolicy.BROADCAST_RECIPIENT, content, channel)
                            if (result != SendMessageResult.Sent) {
                                _meshSelfTest.value = MeshSelfTestPolicy.sendFailed(_meshSelfTest.value, result)
                                return@launch
                            }
                            val newId = lastOutboundPacketId
                            if (newId != 0) packetIds += newId
                            _meshSelfTest.value = MeshSelfTestPolicy.progress(_meshSelfTest.value, i, planned)
                            delay(settleMs)
                        }
                        delay(MeshSelfTestPolicy.TAIL_WAIT_MS)
                        val heardPings = packetIds.count { dbHelper.getMessageHeardCount(it) > 0 }
                        val hearers = dbHelper.collectUniqueHearers(packetIds)
                        val rxNow = _meshDiagnostics.value?.rxPackets ?: rxBaseline
                        val rxDelta = (rxNow - rxBaseline).coerceAtLeast(0L)
                        _meshSelfTest.value = MeshSelfTestPolicy.finished(
                            planned, heardPings, hearers.size, rxDelta
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Mesh self-test failed: ${e.message}")
                        _meshSelfTest.value = MeshSelfTestPolicy.failed(e.message)
                    }
                }
            }
        }
    }

    fun stopMeshSelfTest() {
        meshSelfTestJob?.cancel()
        meshSelfTestJob = null
        if (_meshSelfTest.value.active) {
            _meshSelfTest.value = MeshSelfTestPolicy.stopped(_meshSelfTest.value)
        }
    }

    fun clearMeshSelfTestResult() {
        if (!_meshSelfTest.value.active) {
            _meshSelfTest.value = MeshSelfTestResult()
        }
    }

    /** Latest phone GPS fix (fresh only while a range test is running). */
    fun lastPhoneFix(): android.location.Location? = lastPhoneLocation

    fun getTelemetryHistory(nodeId: Long) = dbHelper.getTelemetryHistory(nodeId)

    fun getChannelInboxPreviews(): Map<String, ChatInboxPreview> = dbHelper.getChannelInboxPreviews()

    fun getDmInboxPreviews(localNodeId: Long): Map<Long, ChatInboxPreview> =
        dbHelper.getDmInboxPreviews(localNodeId)

    fun countUnreadChannelMessages(channel: String, afterTs: Long, excludeSenderId: Long): Int =
        dbHelper.countUnreadChannelMessages(channel, afterTs, excludeSenderId)

    fun countUnreadDmMessages(peerId: Long, localNodeId: Long, afterTs: Long): Int =
        dbHelper.countUnreadDmMessages(peerId, localNodeId, afterTs)

    fun getChannelsList(): List<ChannelConfig> {
        return dbHelper.getChannelsList().map { channel ->
            val identifier = ChatSendPolicy.channelKey(channel.name)
            val hydrate = ChannelPskPolicy.hydrateFromStore(getChatKey(identifier), channel.psk)
            if (hydrate.persistKeyring) saveChatKey(identifier, hydrate.secret)
            if (hydrate.clearSqlitePsk) dbHelper.clearChannelPsk(channel.id)
            channel.copy(psk = hydrate.secret)
        }
    }

    fun insertChannel(channel: ChannelConfig): Long {
        when (val plan = ChannelPersistPolicy.planInsert(dbHelper.getChannelsList(), channel)) {
            is ChannelInsertAction.UpdateExisting -> {
                updateChannel(plan.config)
                return plan.config.id
            }
            is ChannelInsertAction.InsertNew -> {
                saveChatKey(ChatSendPolicy.channelKey(plan.config.name), plan.config.psk)
                val id = dbHelper.insertChannel(ChannelPersistPolicy.sqliteRow(plan.config))
                refreshChannelsList()
                return id
            }
        }
    }

    fun updateChannel(channel: ChannelConfig) {
        val previous = getChannelsList().firstOrNull { it.id == channel.id }
        ChannelPersistPolicy.previousNameIfRenamed(previous, channel)?.let { oldName ->
            deleteChatKey(ChatSendPolicy.channelKey(oldName))
        }
        saveChatKey(ChatSendPolicy.channelKey(channel.name), channel.psk)
        dbHelper.updateChannel(ChannelPersistPolicy.sqliteRow(channel))
        refreshChannelsList()
    }

    fun deleteChannel(id: Long) {
        getChannelsList().firstOrNull { it.id == id }?.let {
            deleteChatKey(ChatSendPolicy.channelKey(it.name))
        }
        dbHelper.deleteChannel(id)
        refreshChannelsList()
    }

    fun generateRandomPsk(): String = ChannelPskPolicy.generate()

    private fun refreshChannelsList() {
        val list = getChannelsList().map { it.name }
        _channels.value = ChannelPersistPolicy.visibleNames(list)
        syncChannelPrivacy()
    }

    private fun syncChannelPrivacy() {
        privacySyncJob?.cancel()
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) {
            _channelPrivacyStatus.value = ChannelPrivacyStatus.DISCONNECTED
            return
        }
        if (privacySupported != true) {
            _channelPrivacyStatus.value = if (privacySupported == false)
                ChannelPrivacyStatus.UNSUPPORTED else ChannelPrivacyStatus.CHECKING
            return
        }
        val desired = ChannelPrivacy.fromChannels(dbHelper.getChannelsList())
        if (reportedPrivacy == desired) {
            _channelPrivacyStatus.value = ChannelPrivacyStatus.CONFIRMED
            return
        }
        if (_otaState.value.active) {
            _channelPrivacyStatus.value = ChannelPrivacyStatus.OTA_BUSY
            return
        }
        _channelPrivacyStatus.value = ChannelPrivacyStatus.APPLYING
        val nodeId = trustedControlNodeId
        privacySyncJob = repositoryScope.launch {
            ChannelPrivacySync.apply(
                desired = desired,
                sessionCurrent = {
                    nodeId == trustedControlNodeId && bleManager.isConnected &&
                        _isDeviceAuthenticated.value
                },
                otaActive = { _otaState.value.active },
                reported = { reportedPrivacy },
                send = { bleManager.sendPacket(desired.packet(nodeId)) },
                status = { _channelPrivacyStatus.value = it }
            )
        }
    }

    fun getOrCreateEcdhKeys(): Pair<String, String> {
        val pubKey = prefs.getString(EcdhKeyPolicy.PREF_PUBLIC, null)
        val privKey = securePrefs.getString(EcdhKeyPolicy.PREF_PRIVATE, null)
        return EcdhKeyPolicy.existingPair(pubKey, privKey) ?: regenerateEcdhKeys()
    }

    fun regenerateEcdhKeys(): Pair<String, String> {
        val generated = EcdhKeyPolicy.generateOrError()
        if (EcdhKeyPolicy.isError(generated)) {
            Log.e(TAG, "ECDH generation error")
            return generated.asPair()
        }
        prefs.edit().putString(EcdhKeyPolicy.PREF_PUBLIC, generated.publicKey).apply()
        securePrefs.edit().putString(EcdhKeyPolicy.PREF_PRIVATE, generated.privateKey).apply()
        return generated.asPair()
    }

    private var lastPhoneLocationShareMs = 0L

    fun sendPhoneLocation(lat: Double, lon: Double): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false
        val now = android.os.SystemClock.elapsedRealtime()
        val localNodeId = bleManager.connectedNodeId
        val channels = getChannelsList()
        val privacy = PhoneLocationShare.privacyOf(channels)
        when (
            val decision = PhoneLocationShare.decide(
                lat, lon, localNodeId, channels, lastPhoneLocationShareMs, now
            )
        ) {
            PhoneLocationDecision.SkipInvalid -> {
                Log.d(TAG, "sendPhoneLocation: ignoring invalid/no-fix coords ($lat, $lon)")
                return false
            }
            PhoneLocationDecision.SkipThrottled -> return false
            PhoneLocationDecision.SkipPositionDisabled -> {
                Log.d(TAG, "Position sharing disabled by channel privacy. Skipping GPS upload.")
                return false
            }
            is PhoneLocationDecision.Send -> {
                if (decision.latitude != lat || decision.longitude != lon) {
                    Log.d(
                        TAG,
                        "Location fuzzer applied for transmitted GPS: channel privacy floor ±${privacy.radiusM} m"
                    )
                }
                val packet = PhoneLocationShare.buildPacket(
                    localNodeId, PacketIdGenerator.next(), decision.latitude, decision.longitude
                )
                val sent = bleManager.sendPacket(packet.toByteArray())
                if (sent) lastPhoneLocationShareMs = now
                return sent
            }
        }
    }

    fun exportChatKeysForMigration(): Map<String, String> = dbHelper.getAllChatKeys()

    fun exportSecureSecretsForMigration(): Map<String, String> =
        AppMigrationExportPolicy.stringSecrets(securePrefs.all)

    fun exportAppPrefsForMigration(): Map<String, Any?> =
        AppMigrationExportPolicy.filterAppPrefs(prefs.all)

    fun exportNodeSettingsForMigration(): Map<String, Map<String, Any?>> {
        val out = linkedMapOf<String, Map<String, Any?>>()
        context.applicationInfo.dataDir?.let { dataDir ->
            java.io.File(dataDir, "shared_prefs").listFiles()?.forEach { file ->
                val name = AppMigrationExportPolicy.nodeSettingsPrefName(file.name) ?: return@forEach
                out[name] = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            }
        }
        return out
    }

    fun securePrefsForMigration(): android.content.SharedPreferences = securePrefs

    fun importAppMigration(json: String): AppPackageMigration.ImportResult =
        AppPackageMigration.importJson(this, context, json)

    fun exportAppMigrationJson(): String = AppPackageMigration.exportJson(this, context)

    fun isLegacyPackageInstalled(): Boolean =
        AppPackageMigration.isLegacyPackageInstalled(context)
}
