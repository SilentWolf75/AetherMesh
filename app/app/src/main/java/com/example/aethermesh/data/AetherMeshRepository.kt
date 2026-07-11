package com.example.aethermesh.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.aethermesh.ble.BleConnectionManager
import com.example.aethermesh.proto.MeshPacket
import com.example.aethermesh.proto.TextMessage
import com.example.aethermesh.proto.Telemetry
import com.example.aethermesh.proto.Ack
import com.example.aethermesh.proto.DeliveryStatus
import com.example.aethermesh.proto.TraceRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class RouteHopInfo(
    val targetId: Long,
    val nextHopId: Long,
    val hops: Int,
    val lastSnr: Float = 0f,
    val lastRssi: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeshDiagnosticsSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val txPackets: Long = 0,
    val txFailures: Long = 0,
    val rxPackets: Long = 0,
    val relayedPackets: Long = 0,
    val retries: Long = 0,
    val ackedPackets: Long = 0,
    val ackTimeouts: Long = 0,
    val duplicatePackets: Long = 0,
    val cadBusyEvents: Long = 0,
    val queueDrops: Long = 0,
    val routeChanges: Long = 0,
    val activeRoutes: Int = 0,
    val rebroadcastQueueDepth: Int = 0,
    val pendingAckDepth: Int = 0,
    val airtimeMs: Long = 0,
    val uptimeSeconds: Long = 0,
    val protocolVersion: Int = 1
)

data class TraceHop(
    val nodeId: Long,
    val rssi: Int,
    val snr: Float
)

data class TraceRouteState(
    val visible: Boolean = false,
    val active: Boolean = false,
    val targetId: Long = 0L,
    val traceId: Int = 0,
    val forward: List<TraceHop> = emptyList(),
    val returning: List<TraceHop> = emptyList(),
    val forwardTruncated: Boolean = false,
    val returnTruncated: Boolean = false,
    val error: String? = null
)

class AetherMeshRepository(private val context: Context) {

    companion object {
        private const val TAG = "MeshRepository"
        const val DEFAULT_CHANNEL = "General"
        private const val MAX_TEXT_CONTENT_LENGTH = 127
        // Encrypted payloads grow: base64(12B IV + plaintext + 16B GCM tag) must
        // fit the proto's 168-byte content field -> plaintext capped lower.
        // v2 wire overhead: salt(16) + IV(12) + GCM tag(16), then base64 and "v2:".
        // 76 plaintext bytes keeps the encoded protobuf string below its 168B cap.
        private const val MAX_ENCRYPTED_CONTENT_LENGTH = 76
        private const val MAX_CHANNEL_LENGTH = 31
        private const val MESSAGE_ACK_TIMEOUT_MS = 45_000L
        private const val RANGE_PING_TIMEOUT_MS = 15_000L
        private const val TRACE_ROUTE_TIMEOUT_MS = 30_000L
        private const val DM_RETRY_COOLDOWN_MS = 300_000L // 5 min between auto-retries of the same message
    }

    val dbHelper = DatabaseHelper(context)
    val bleManager = BleConnectionManager(context)
    private val prefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)

    // Keystore-encrypted storage for secrets (node passwords, ECDH private key).
    // Falls back to a plain file only if the Keystore is unavailable on-device.
    private val securePrefs: android.content.SharedPreferences = try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "aethermesh_secure_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plain storage: ${e.message}")
        context.getSharedPreferences("aethermesh_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    // One-time migration of secrets that older builds stored in plain prefs
    private fun migrateSecretsToSecureStorage() {
        try {
            val editorSecure = securePrefs.edit()
            val editorPlain = prefs.edit()
            var moved = 0
            for ((key, value) in prefs.all) {
                if ((key.startsWith("node_pwd_") || key == "ecdh_private_key") && value is String) {
                    editorSecure.putString(key, value)
                    editorPlain.remove(key)
                    moved++
                }
            }
            if (moved > 0) {
                editorSecure.apply()
                editorPlain.apply()
                Log.d(TAG, "Migrated $moved secret(s) from plain prefs to encrypted storage.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Secret migration failed: ${e.message}")
        }
    }

    // Flow for active node lists and messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _nodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodes: StateFlow<List<MeshNode>> = _nodes.asStateFlow()

    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected.asStateFlow()

    // Group channels: the list of known channel names and the one currently being viewed.
    private val _channels = MutableStateFlow(listOf(DEFAULT_CHANNEL))
    val channels: StateFlow<List<String>> = _channels.asStateFlow()

    private val _selectedChannel = MutableStateFlow(DEFAULT_CHANNEL)
    val selectedChannel: StateFlow<String> = _selectedChannel.asStateFlow()

    // activeChatId: null means showing the group channel, Long value means DM with that nodeId
    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId: StateFlow<Long?> = _activeChatId.asStateFlow()

    // Observed routes from packet headers
    private val _observedRoutes = MutableStateFlow<Map<Long, RouteHopInfo>>(emptyMap())
    val observedRoutes: StateFlow<Map<Long, RouteHopInfo>> = _observedRoutes.asStateFlow()

    private val _meshDiagnostics = MutableStateFlow<MeshDiagnosticsSnapshot?>(null)
    val meshDiagnostics: StateFlow<MeshDiagnosticsSnapshot?> = _meshDiagnostics.asStateFlow()

    private val _traceRouteState = MutableStateFlow(TraceRouteState())
    val traceRouteState: StateFlow<TraceRouteState> = _traceRouteState.asStateFlow()
    private var traceRouteJob: Job? = null

    // Device Authentication flows
    private val _isDeviceAuthenticated = MutableStateFlow(false)
    val isDeviceAuthenticated: StateFlow<Boolean> = _isDeviceAuthenticated.asStateFlow()

    // null = checking, true = prompt password, false = prompt set initial password
    private val _authenticationRequired = MutableStateFlow<Boolean?>(null)
    val authenticationRequired: StateFlow<Boolean?> = _authenticationRequired.asStateFlow()

    private var pendingAuthPassword: String? = null

    // Range Test Engine properties
    private val _isRangeTestActive = MutableStateFlow(false)
    val isRangeTestActive: StateFlow<Boolean> = _isRangeTestActive.asStateFlow()

    private val _rangeTestLogs = MutableStateFlow<List<RangeTestLog>>(emptyList())
    val rangeTestLogs: StateFlow<List<RangeTestLog>> = _rangeTestLogs.asStateFlow()

    private var rangeTestTargetId: Long = 0L
    val activeRangeTestTargetId: Long
        get() = rangeTestTargetId
    private data class RangeTestPosition(
        val latitude: Double,
        val longitude: Double,
        val speedMps: Float?,
        val gpsAccuracyM: Float?
    )
    private data class PendingRangePing(
        val targetId: Long,
        val sentAtMs: Long,
        val position: RangeTestPosition
    )
    private val pendingRangePings = java.util.concurrent.ConcurrentHashMap<Int, PendingRangePing>()
    private var rangeTestJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.Default + Job())

    // --- BLE firmware update (OTA) state ---
    data class OtaState(
        val active: Boolean = false,
        val progress: Int = 0,          // 0-100
        val status: String = "",
        val error: Boolean = false,
        val done: Boolean = false
    )
    private val _otaState = MutableStateFlow(OtaState())
    val otaState: StateFlow<OtaState> = _otaState.asStateFlow()
    private val otaStatusChannel =
        kotlinx.coroutines.channels.Channel<com.example.aethermesh.proto.OtaStatus>(kotlinx.coroutines.channels.Channel.BUFFERED)
    private var otaJob: Job? = null

    // Single thread for the heavy DB reads in refreshData: keeps queries off the
    // main thread (jank/ANR with a large history) while preserving their order.
    private val dbDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()

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

        migrateSecretsToSecureStorage()

        // Load initial data
        refreshData()
        startPendingMessageTimeoutMonitor()

        // Configure BLE connection listener
        bleManager.onConnectionStateChanged = { connected ->
            Log.d(TAG, "BLE Connection changed: $connected")
            _isBleConnected.value = connected
            if (connected) {
                _isDeviceAuthenticated.value = false
                _authenticationRequired.value = null
                val mac = bleManager.getConnectedDeviceAddress()
                if (mac != null) {
                    autoAuthenticate(mac)
                }
                refreshData()
            } else {
                _isDeviceAuthenticated.value = false
                _authenticationRequired.value = null
                if (_isRangeTestActive.value) {
                    stopRangeTest()
                }
                refreshData()
            }
        }

        // Configure BLE packet receiver
        bleManager.onPacketReceived = { bytes ->
            try {
                // Parse protobuf packet
                val packet = MeshPacket.parseFrom(bytes)
                handleMeshPacket(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding mesh packet: ${e.message}")
            }
        }
    }

    private fun autoAuthenticate(macAddress: String) {
        val savedPass = securePrefs.getString("node_pwd_$macAddress", null)
        if (!savedPass.isNullOrEmpty()) {
            Log.d(TAG, "Found saved password for $macAddress. Submitting auto-auth...")
            sendAuthRequest(savedPass)
        } else {
            Log.d(TAG, "No saved password found for $macAddress. Sending status query...")
            sendAuthRequest("") // Send empty password to query node auth status
        }
    }

    fun sendAuthRequest(password: String): Boolean {
        if (!bleManager.isConnected) return false
        val localNodeId = bleManager.connectedNodeId
        
        val authBuilder = com.example.aethermesh.proto.AuthRequest.newBuilder()
            .setPassword(password)
            .setIsChangePassword(false)
            
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(0) // Local auth
            .setPacketId(PacketIdGenerator.next())
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setAuthRequest(authBuilder)
            .build()
            
        pendingAuthPassword = password
        return bleManager.sendPacket(packet.toByteArray())
    }

    fun changeDevicePassword(currentPassword: String, newPassword: String): Boolean {
        if (!bleManager.isConnected) return false
        val localNodeId = bleManager.connectedNodeId
        
        val authBuilder = com.example.aethermesh.proto.AuthRequest.newBuilder()
            .setPassword(currentPassword)
            .setIsChangePassword(true)
            .setNewPassword(newPassword)
            
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(0)
            .setPacketId(PacketIdGenerator.next())
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setAuthRequest(authBuilder)
            .build()
            
        pendingAuthPassword = newPassword
        return bleManager.sendPacket(packet.toByteArray())
    }

    private fun handleMeshPacket(packet: MeshPacket) {
        val senderId = packet.senderId.toLong() and 0xFFFFFFFFL
        val recipientId = packet.recipientId.toLong() and 0xFFFFFFFFL
        val localNodeId = bleManager.connectedNodeId
        
        // Handle AuthResponse packet first
        if (packet.payloadCase == MeshPacket.PayloadCase.AUTH_RESPONSE) {
            val authResp = packet.authResponse
            Log.d(TAG, "AuthResponse received: success=${authResp.success}, msg=${authResp.message}, notSet=${authResp.passwordNotSet}")
            if (authResp.success) {
                val oldNodeId = bleManager.connectedNodeId
                // Correct the connectedNodeId in bleManager with the actual hardware node ID from the node
                bleManager.connectedNodeId = senderId
                
                // Preserve the placeholder record while moving it to the authenticated
                // 32-bit hardware identity. This retains user names and history.
                if (oldNodeId != 0L && oldNodeId != senderId) {
                    dbHelper.migrateNodeIdentity(oldNodeId, senderId)
                    Log.d(TAG, "Migrated BLE placeholder ID to hardware ID 0x${senderId.toString(16).uppercase()}")
                }
                
                _isDeviceAuthenticated.value = true
                _authenticationRequired.value = null
                
                // Save password to encrypted storage for auto-auth
                val mac = bleManager.getConnectedDeviceAddress()
                if (mac != null && !pendingAuthPassword.isNullOrEmpty()) {
                    securePrefs.edit().putString("node_pwd_$mac", pendingAuthPassword).apply()
                }
                pendingAuthPassword = null
                
                // Update local node in SQLite database
                val nodePrefs = context.getSharedPreferences("node_settings_$senderId", Context.MODE_PRIVATE)
                val savedName = nodePrefs.getString("node_name", "")?.takeIf { it.isNotBlank() }
                if (savedName != null) {
                    val savedShortName = nodePrefs.getString("node_short_name", "")?.takeIf { it.isNotBlank() }
                        ?: savedName.replace("AetherMesh-", "").replace("Node ", "")
                            .replace(Regex("[^a-zA-Z0-9]"), "").take(4).uppercase()
                            .ifEmpty { String.format("%04X", (senderId and 0xFFFFL).toInt()) }
                    dbHelper.updateNodeNameAndShortName(senderId, savedName, savedShortName)
                    nodePrefs.edit()
                        .remove("node_name")
                        .remove("node_short_name")
                        .apply()
                }

                refreshData()
            } else {
                _isDeviceAuthenticated.value = false
                if (authResp.passwordNotSet) {
                    _authenticationRequired.value = false // Needs to set initial password
                } else {
                    _authenticationRequired.value = true // Incorrect password / prompt user
                    
                    // Clear invalid saved password
                    val mac = bleManager.getConnectedDeviceAddress()
                    if (mac != null) {
                        securePrefs.edit().remove("node_pwd_$mac").apply()
                    }
                }
                pendingAuthPassword = null
            }
            return
        }

        if (packet.payloadCase == MeshPacket.PayloadCase.DELIVERY_STATUS) {
            if (_isRangeTestActive.value) {
                return
            }
            val delivery = packet.deliveryStatus
            val newStatus = when (delivery.state) {
                DeliveryStatus.State.DELIVERED -> "DELIVERED"
                DeliveryStatus.State.FAILED -> "FAILED"
                DeliveryStatus.State.RETRYING -> "PENDING"
                DeliveryStatus.State.QUEUED, DeliveryStatus.State.STORED -> "QUEUED"
                DeliveryStatus.State.EXPIRED -> "EXPIRED"
                else -> null
            }
            if (newStatus != null) {
                Log.d(TAG, "DeliveryStatus for packet ${delivery.packetId}: $newStatus, reason=${delivery.reason}, retry=${delivery.retryCount}")
                dbHelper.updateMessageStatus(delivery.packetId, newStatus)
                refreshData()
            }
            return
        }

        // OTA status/acks are BLE-only control messages addressed to the phone
        // (recipient_id = 0), so they must be handled before the invalid-recipient
        // guard below - the same as AuthResponse and DeliveryStatus above.
        if (packet.payloadCase == MeshPacket.PayloadCase.OTA_STATUS) {
            otaStatusChannel.trySend(packet.otaStatus)
            return
        }

        if (packet.payloadCase == MeshPacket.PayloadCase.DIAGNOSTICS) {
            val value = packet.diagnostics
            val snapshot = MeshDiagnosticsSnapshot(
                txPackets = value.txPackets.toLong(),
                txFailures = value.txFailures.toLong(),
                rxPackets = value.rxPackets.toLong(),
                relayedPackets = value.relayedPackets.toLong(),
                retries = value.retries.toLong(),
                ackedPackets = value.ackedPackets.toLong(),
                ackTimeouts = value.ackTimeouts.toLong(),
                duplicatePackets = value.duplicatePackets.toLong(),
                cadBusyEvents = value.cadBusyEvents.toLong(),
                queueDrops = value.queueDrops.toLong(),
                routeChanges = value.routeChanges.toLong(),
                activeRoutes = value.activeRoutes,
                rebroadcastQueueDepth = value.rebroadcastQueueDepth,
                pendingAckDepth = value.pendingAckDepth,
                airtimeMs = value.airtimeMs.toLong(),
                uptimeSeconds = value.uptimeSeconds.toLong(),
                protocolVersion = value.protocolVersion
            )
            dbHelper.insertMeshDiagnostics(snapshot)
            _meshDiagnostics.value = snapshot
            return
        }

        if (senderId == 0L || recipientId == 0L) {
            Log.w(TAG, "Ignoring packet with invalid sender/recipient: sender=0x${senderId.toString(16)}, recipient=0x${recipientId.toString(16)}")
            return
        }
        
        Log.d(TAG, "Received mesh packet from 0x${senderId.toString(16).uppercase()}")

        // Update routing diagnostics map. Only for frames that carry a real
        // over-the-air reading (rxRssi != 0) - a relayed/loopback frame with
        // rx_rssi 0 must not overwrite a node's known-good signal with a blank.
        val prevHopId = packet.prevHopId.toLong() and 0xFFFFFFFFL
        if (prevHopId != 0L && packet.rxRssi != 0f) {
            val hopsCount = if (senderId == prevHopId) 1 else 2
            val currentMap = _observedRoutes.value.toMutableMap()
            val observation = RouteHopInfo(
                targetId = senderId,
                nextHopId = prevHopId,
                hops = hopsCount,
                lastRssi = packet.rxRssi,
                lastSnr = packet.rxSnr,
                timestamp = System.currentTimeMillis()
            )
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
                if (contentReceived.startsWith("PONG_")) {
                    // New direct-range replies are PONG_<id>_<target RSSI>_<target SNR x4>_D.
                    // The shorter legacy PONG_<id> form remains accepted during firmware rollouts.
                    val fields = contentReceived.removePrefix("PONG_").split('_')
                    val pongId = fields.getOrNull(0)?.toIntOrNull()
                    val targetRssi = fields.getOrNull(1)?.toFloatOrNull()
                    val targetSnr = fields.getOrNull(2)?.toFloatOrNull()?.div(4f)
                    val pending = pongId?.let { pendingRangePings[it] }
                    if (_isRangeTestActive.value && pongId != null && pending != null &&
                        pending.targetId == senderId && pendingRangePings.remove(pongId, pending)
                    ) {
                        Log.d(TAG, "Direct range-test PONG matched ping $pongId")
                        logRangeTestResult(
                            pending = pending,
                            success = true,
                            rssi = packet.rxRssi,
                            snr = packet.rxSnr,
                            remoteRssi = targetRssi,
                            remoteSnr = targetSnr
                        )
                    }
                    return
                }
                if (contentReceived.startsWith("PING_")) {
                    return
                }

                val chatIdentifier = if (recipientId == 0xFFFFFFFFL) "CHANNEL_$targetChan" else "DM_$senderId"
                val cryptoContext = ChatContext.authenticatedLabel(senderId, recipientId, targetChan)
                
                val finalContent = if (isEncrypted) {
                    val passcode = getChatKey(chatIdentifier)
                    if (!passcode.isNullOrEmpty()) {
                        decryptAES(contentReceived, passcode, cryptoContext)
                    } else {
                        "[Encrypted Message - No Key Configured]"
                    }
                } else {
                    contentReceived
                }

                dbHelper.insertMessage(
                    senderId = senderId,
                    recipientId = recipientId,
                    content = finalContent,
                    channel = targetChan,
                    packetId = packet.packetId,
                    status = "SENT",
                    isEncrypted = isEncrypted
                )
                refreshData()
                notifyIncomingMessage(
                    senderId = senderId,
                    chatIdentifier = chatIdentifier,
                    channel = targetChan,
                    content = finalContent,
                    isBroadcast = recipientId == 0xFFFFFFFFL
                )
            }
            MeshPacket.PayloadCase.TELEMETRY -> {
                val telemetry = packet.telemetry
                var lat = telemetry.latitude
                var lon = telemetry.longitude
                
                // Retrieve the primary channel to check for location fuzzer privacy settings
                val primaryChan = getChannelsList().firstOrNull { it.isPrimary }
                if (primaryChan != null && !primaryChan.preciseLocation && primaryChan.precisionMiles > 0f) {
                    val milesToDegreesLat = primaryChan.precisionMiles / 69.0f
                    val cosLat = Math.cos(Math.toRadians(lat.toDouble()))
                    val milesToDegreesLon = primaryChan.precisionMiles / (69.0f * (if (cosLat > 0.0) cosLat else 1.0).toFloat())
                    
                    val stableOffsetLat = (((senderId.hashCode() and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0
                    val stableOffsetLon = ((((senderId.hashCode() ushr 16) and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0

                    lat += (stableOffsetLat * milesToDegreesLat).toFloat()
                    lon += (stableOffsetLon * milesToDegreesLon).toFloat()
                    Log.d(TAG, "Location fuzzer applied for primary channel: stable offset by ±${primaryChan.precisionMiles} miles")
                }

                dbHelper.updateNode(
                    nodeId = senderId,
                    battery = telemetry.batteryLevel,
                    lat = lat,
                    lon = lon,
                    model = telemetry.nodeModel,
                    uptimeSeconds = telemetry.uptimeSeconds.toLong() and 0xFFFFFFFFL,
                    firmwareVersion = telemetry.firmwareVersion,
                    isCharging = telemetry.isCharging,
                    rssi = packet.rxRssi,
                    snr = packet.rxSnr,
                    voltage = telemetry.batteryVoltage,
                    positionPrecision = telemetry.positionPrecision,
                    advertisedName = telemetry.nodeName,
                    protocolVersion = packet.protocolVersion.coerceAtLeast(1)
                )
                // Append to telemetry history for battery/voltage graphs.
                dbHelper.insertTelemetrySample(senderId, telemetry.batteryLevel, telemetry.batteryVoltage, telemetry.isCharging)
                notifyLowBattery(senderId, telemetry.batteryLevel, telemetry.isCharging)
                retryQueuedDirectMessages(senderId)
                refreshData()
            }
            MeshPacket.PayloadCase.TRACE_ROUTE -> {
                val trace = packet.traceRoute
                val current = _traceRouteState.value
                if (trace.type == TraceRoute.Type.RESPONSE && trace.traceId == current.traceId &&
                    trace.originId.toLong().and(0xFFFFFFFFL) == localNodeId
                ) {
                    val forward = trace.forwardNodeIdsList.mapIndexed { index, id ->
                        TraceHop(
                            nodeId = id.toLong() and 0xFFFFFFFFL,
                            rssi = trace.forwardRssiList.getOrElse(index) { 0 },
                            snr = trace.forwardSnrQuarterDbList.getOrElse(index) { 0 } / 4f
                        )
                    }
                    val returning = trace.returnNodeIdsList.mapIndexed { index, id ->
                        TraceHop(
                            nodeId = id.toLong() and 0xFFFFFFFFL,
                            rssi = trace.returnRssiList.getOrElse(index) { 0 },
                            snr = trace.returnSnrQuarterDbList.getOrElse(index) { 0 } / 4f
                        )
                    }.toMutableList()
                    if (returning.lastOrNull()?.nodeId != localNodeId) {
                        returning += TraceHop(localNodeId, packet.rxRssi.toInt(), packet.rxSnr)
                    }

                    traceRouteJob?.cancel()
                    _traceRouteState.value = current.copy(
                        active = false,
                        forward = forward,
                        returning = returning,
                        forwardTruncated = trace.forwardTruncated,
                        returnTruncated = trace.returnTruncated,
                        error = null
                    )

                    if (forward.isNotEmpty()) {
                        val routes = _observedRoutes.value.toMutableMap()
                        val observation = RouteHopInfo(
                            targetId = current.targetId,
                            nextHopId = forward.first().nodeId,
                            hops = forward.size,
                            lastRssi = forward.last().rssi.toFloat(),
                            lastSnr = forward.last().snr
                        )
                        routes[current.targetId] = observation
                        dbHelper.upsertRouteObservation(observation)
                        _observedRoutes.value = routes
                    }
                }
            }
            MeshPacket.PayloadCase.ACK -> {
                val ackedId = packet.ack.ackedPacketId
                Log.d(TAG, "ACK received for packet: $ackedId")
                dbHelper.updateMessageStatus(ackedId, "DELIVERED")
                val pending = pendingRangePings[ackedId]
                if (_isRangeTestActive.value && pending != null && pending.targetId == senderId &&
                    pendingRangePings.remove(ackedId, pending)
                ) {
                    Log.d(TAG, "Range test ACK matched packet $ackedId")
                    logRangeTestResult(
                        pending = pending,
                        success = true,
                        rssi = packet.rxRssi,
                        snr = packet.rxSnr,
                        remoteRssi = packet.ack.ackedRxRssi.takeIf { it != 0f },
                        remoteSnr = packet.ack.ackedRxSnr.takeIf { it != 0f }
                    )
                }
                refreshData()
            }
            else -> {
                Log.d(TAG, "Unhandled payload ${packet.payloadCase} from 0x${senderId.toString(16)}")
            }
        }
    }

    private fun secureChatKeyName(chatIdentifier: String): String {
        val kind = if (chatIdentifier.startsWith("CHANNEL_")) "channel" else "dm"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(chatIdentifier.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "chat_key_${kind}_$digest"
    }

    fun getChatKey(chatIdentifier: String): String? {
        val prefKey = secureChatKeyName(chatIdentifier)
        securePrefs.getString(prefKey, null)?.let { return it }

        // One-time migration from the legacy plaintext SQLite key table.
        val legacy = dbHelper.getChatKey(chatIdentifier)
        if (!legacy.isNullOrEmpty()) {
            securePrefs.edit().putString(prefKey, legacy).apply()
            dbHelper.deleteChatKey(chatIdentifier)
            return legacy
        }
        return null
    }

    fun saveChatKey(chatIdentifier: String, key: String) {
        val prefKey = secureChatKeyName(chatIdentifier)
        if (key.isBlank()) {
            securePrefs.edit().remove(prefKey).apply()
        } else {
            securePrefs.edit().putString(prefKey, key).apply()
        }
        dbHelper.deleteChatKey(chatIdentifier)
    }

    private fun deleteChatKey(chatIdentifier: String) {
        securePrefs.edit().remove(secureChatKeyName(chatIdentifier)).apply()
        dbHelper.deleteChatKey(chatIdentifier)
    }

    fun sendMessage(recipientId: Long, content: String, channel: String = _selectedChannel.value): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false
        val boundedChannel = channel.take(MAX_CHANNEL_LENGTH)
        val generatedPacketId = PacketIdGenerator.next()
        val localNodeId = bleManager.connectedNodeId

        val chatIdentifier = if (recipientId == 0xFFFFFFFFL) "CHANNEL_$boundedChannel" else "DM_$recipientId"
        val cryptoContext = ChatContext.authenticatedLabel(localNodeId, recipientId, boundedChannel)
        val passcode = getChatKey(chatIdentifier)
        val isEncrypted = !passcode.isNullOrEmpty()
        val boundedContent = content.takeUtf8Bytes(
            if (isEncrypted) MAX_ENCRYPTED_CONTENT_LENGTH else MAX_TEXT_CONTENT_LENGTH
        )

        // Encrypt FIRST and refuse to send on failure — never fall back to
        // transmitting plaintext on a chat the user believes is encrypted.
        val contentToSend = if (isEncrypted) {
            encryptAES(boundedContent, passcode, cryptoContext) ?: run {
                Log.e(TAG, "Encryption failed; message NOT sent.")
                return false
            }
        } else {
            boundedContent
        }

        // Insert into local DB as sent by local user (store plaintext locally)
        dbHelper.insertMessage(
            senderId = localNodeId,
            recipientId = recipientId,
            content = boundedContent,
            channel = if (recipientId == 0xFFFFFFFFL) boundedChannel else "",
            packetId = generatedPacketId,
            status = if (recipientId == 0xFFFFFFFFL) "SENT" else "PENDING",
            isEncrypted = isEncrypted
        )
        refreshData()

        // 2. Build protobuf packet
        val textBuilder = TextMessage.newBuilder()
            .setContent(contentToSend)
            .setChannel(if (recipientId == 0xFFFFFFFFL) boundedChannel else "")
            .setIsEncrypted(isEncrypted)

        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(recipientId.toInt())
            .setPacketId(generatedPacketId)
            .setHopLimit(4)
            .setWantAck(recipientId != 0xFFFFFFFFL)
            .setPrevHopId(localNodeId.toInt())
            .setText(textBuilder)
            .build()

        // 3. Write over BLE
        return bleManager.sendPacket(packet.toByteArray())
    }

    fun retryMessage(message: ChatMessage): Boolean {
        if (message.recipientId == 0xFFFFFFFFL || message.channel.isNotEmpty()) return false
        val sent = sendMessage(message.recipientId, message.content, "")
        if (sent) {
            dbHelper.updateMessageStatusById(message.id, "RETRIED")
            refreshData()
        }
        return sent
    }

    // Per-message cooldown so store-and-forward doesn't re-send the same failed
    // DM on every telemetry from the target (that loop burned airtime forever).
    // Accessed only on dbDispatcher (single thread).
    private val dmRetryLastAttempt = mutableMapOf<Long, Long>()

    private fun retryQueuedDirectMessages(recipientId: Long) {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return
        // Never compete with an active range test for the radio: DM retransmits
        // through the connected node were observed jamming ping/PONG exchanges.
        if (_isRangeTestActive.value) return
        repositoryScope.launch(dbDispatcher) {
            val now = System.currentTimeMillis()
            val retryable = dbHelper.getRetryableDirectMessages(recipientId)
                // Legacy range-test control rows must never be resent as DMs
                .filter { !it.content.startsWith("PING_") && !it.content.startsWith("PONG_") }
                .filter { (dmRetryLastAttempt[it.id] ?: 0L) + DM_RETRY_COOLDOWN_MS < now }
            for (message in retryable) {
                dmRetryLastAttempt[message.id] = now
                dbHelper.updateMessageStatusById(message.id, "QUEUED")
                val sent = sendMessage(message.recipientId, message.content, "")
                dbHelper.updateMessageStatusById(message.id, if (sent) "RETRIED" else "FAILED")
                // Space resends out; a burst of tracked DMs each retrying 3x
                // saturates the node's half-duplex radio.
                delay(2_000L)
            }
            if (retryable.isNotEmpty()) {
                refreshData()
            }
        }
    }

    private fun startPendingMessageTimeoutMonitor() {
        repositoryScope.launch(dbDispatcher) {
            while (true) {
                delay(5_000L)
                val cutoff = System.currentTimeMillis() - MESSAGE_ACK_TIMEOUT_MS
                val changed = dbHelper.markTimedOutPendingMessages(cutoff)
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
        fixedPosition: Boolean = false,
        fixedLatitude: Float = 0f,
        fixedLongitude: Float = 0f,
        fixedAltitude: Int = 0
    ): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false

        val localNodeId = bleManager.connectedNodeId

        // Build NodeConfig message
        val configBuilder = com.example.aethermesh.proto.NodeConfig.newBuilder()
            .setNodeName(name)
            .setLoraSf(sf)
            .setLoraBw(bw)
            .setLoraTxPower(txPower)
            .setRegion(region)
            .setNodeRole(role)
            .setTelemetryInterval(telemetryInterval)
            .setScreenTimeoutSecs(screenTimeout)
            .setPowerSaveMode(powerSaveMode)
            .setPositionPrecision(positionPrecision)
            .setGpsMode(gpsMode)
            .setFixedPosition(fixedPosition)
            .setFixedLatitude(fixedLatitude)
            .setFixedLongitude(fixedLongitude)
            .setFixedAltitude(fixedAltitude)

        // Build MeshPacket wrapper
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(localNodeId.toInt())
            .setPacketId(PacketIdGenerator.next())
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(configBuilder)
            .build()

        // Write over BLE
        val success = bleManager.sendPacket(packet.toByteArray())
        if (success) {
            // Update node name and short name locally in our DB so it matches right away
            dbHelper.updateNodeNameAndShortName(localNodeId, name, shortName)
            refreshData()
        }
        return success
    }

    fun updateNodeNameAndShortName(nodeId: Long, name: String, shortName: String) {
        dbHelper.updateNodeNameAndShortName(nodeId, name, shortName)
        refreshData()
    }

    fun startTraceRoute(targetId: Long): Boolean {
        val localNodeId = bleManager.connectedNodeId
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value || localNodeId == 0L ||
            targetId == 0L || targetId == localNodeId
        ) return false

        val traceId = PacketIdGenerator.next()
        val trace = TraceRoute.newBuilder()
            .setType(TraceRoute.Type.REQUEST)
            .setTraceId(traceId)
            .setOriginId(localNodeId.toInt())
            .setTargetId(targetId.toInt())
            .build()
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(targetId.toInt())
            .setPacketId(traceId)
            .setHopLimit(7)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setTraceRoute(trace)
            .build()

        if (!bleManager.sendPacket(packet.toByteArray())) return false

        traceRouteJob?.cancel()
        _traceRouteState.value = TraceRouteState(visible = true, active = true, targetId = targetId, traceId = traceId)
        traceRouteJob = repositoryScope.launch {
            delay(TRACE_ROUTE_TIMEOUT_MS)
            val pending = _traceRouteState.value
            if (pending.active && pending.traceId == traceId) {
                _traceRouteState.value = pending.copy(active = false, error = "No route response received")
            }
        }
        return true
    }

    fun clearTraceRouteResult() {
        traceRouteJob?.cancel()
        _traceRouteState.value = _traceRouteState.value.copy(visible = false, active = false)
    }

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
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false

        val localNodeId = bleManager.connectedNodeId

        val supportsV2 = _nodes.value.firstOrNull { it.nodeId == nodeId }?.protocolVersion?.let { it >= 2 } == true
        val config = com.example.aethermesh.proto.NodeConfig.newBuilder()
            .setNodeName(name)
            .setConfigPassword(if (supportsV2) "" else password)
            .setLoraSf(sf)
            .setLoraBw(bw)
            .setLoraTxPower(txPower)
            .setRegion(region)
            .setNodeRole(role)
            .setTelemetryInterval(telemetryInterval)
            .setScreenTimeoutSecs(screenTimeout)
            .setPowerSaveMode(powerSaveMode)
            .setPositionPrecision(positionPrecision)
            .setGpsMode(gpsMode)
            .setFixedPosition(fixedPosition)
            .setFixedLatitude(fixedLatitude)
            .setFixedLongitude(fixedLongitude)
            .setFixedAltitude(fixedAltitude)
            .build()

        val packetBuilder = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(nodeId.toInt())
            .setPacketId(PacketIdGenerator.next())
            .setHopLimit(4)
            .setWantAck(true)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(config)

        if (supportsV2) {
            val identity = ControlAuthSession.next()
            val tag = ControlAuth.sign(localNodeId, nodeId, identity, config, password)
            packetBuilder
                .setProtocolVersion(2)
                .setSessionId(identity.sessionId)
                .setAuthCounter(identity.counter)
                .setAuthTag(com.google.protobuf.ByteString.copyFrom(tag))
        }

        return bleManager.sendPacket(packetBuilder.build().toByteArray())
    }

    // --- BLE firmware update (OTA) sender ---
    // Streams a firmware image to the connected node in 192-byte chunks,
    // OTA_WINDOW chunks per node ack (the node's BLE rx ring holds 8, and our
    // writes are WRITE_TYPE_NO_RESPONSE, so flow control is on us). The node
    // MD5-verifies the complete image before it reboots into it.
    // Fast profile - only used when the node's READY reply advertises support
    // (READY.next_offset >= 224). Older firmware can't decode 224-byte chunks
    // (its nanopb field cap is 192) and its 8-slot rx ring can't absorb
    // 8-chunk bursts, so unknown/old nodes get the proven legacy profile.
    private val OTA_CHUNK_FAST = 224
    private val OTA_WINDOW_FAST = 8
    private val OTA_CHUNK_LEGACY = 192
    private val OTA_WINDOW_LEGACY = 4

    fun startFirmwareUpdate(firmware: ByteArray) {
        if (_otaState.value.active) return
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) {
            _otaState.value = OtaState(error = true, status = "Not connected/authenticated")
            return
        }
        val nodeId = bleManager.connectedNodeId

        otaJob = repositoryScope.launch(Dispatchers.IO) {
            try {
                _otaState.value = OtaState(active = true, status = "Preparing...")

                val md5 = MessageDigest.getInstance("MD5").digest(firmware)
                    .joinToString("") { "%02x".format(it) }
                val sha256 = MessageDigest.getInstance("SHA-256").digest(firmware)
                    .joinToString("") { "%02x".format(it) }

                // Drain stale statuses from a previous attempt
                while (otaStatusChannel.tryReceive().isSuccess) { /* drain */ }

                // The node erases its OTA flash partition before it can reply
                // READY, which can take 10-20s; and if it just rebooted from a
                // prior update the BLE session may still be re-authenticating.
                // So allow a generous window and retry BEGIN once.
                var chunkHint = -1
                for (attempt in 1..2) {
                    sendOtaControl(nodeId, com.example.aethermesh.proto.OtaControl.Op.BEGIN, firmware.size, md5, sha256)
                    try {
                        chunkHint = awaitOtaState(com.example.aethermesh.proto.OtaStatus.State.READY, 25_000, "start acknowledgment")
                        break
                    } catch (e: Exception) {
                        if (attempt == 2) throw e
                        _otaState.value = OtaState(active = true, status = "Retrying start...")
                        delay(1500)
                        while (otaStatusChannel.tryReceive().isSuccess) { /* drain */ }
                    }
                }
                if (chunkHint < 0) throw Exception("Node never became ready")

                // Negotiate transfer profile from the node's capability hint
                val chunkSize = if (chunkHint >= OTA_CHUNK_FAST) OTA_CHUNK_FAST else OTA_CHUNK_LEGACY
                val window = if (chunkHint >= OTA_CHUNK_FAST) OTA_WINDOW_FAST else OTA_WINDOW_LEGACY
                Log.d(TAG, "OTA profile: chunk=$chunkSize window=$window (node hint $chunkHint)")

                _otaState.value = OtaState(active = true, status = "Uploading...")
                var offset = 0
                while (offset < firmware.size) {
                    var windowEndOffset = offset
                    for (w in 0 until window) {
                        if (windowEndOffset >= firmware.size) break
                        val len = minOf(chunkSize, firmware.size - windowEndOffset)
                        val chunk = com.example.aethermesh.proto.OtaData.newBuilder()
                            .setOffset(windowEndOffset)
                            .setData(com.google.protobuf.ByteString.copyFrom(firmware, windowEndOffset, len))
                        val pkt = MeshPacket.newBuilder()
                            .setSenderId(nodeId.toInt())
                            .setRecipientId(nodeId.toInt())
                            .setHopLimit(1)
                            .setOtaData(chunk)
                            .build()
                            .toByteArray()
                        // sendPacket now gates on the stack's write-complete
                        // callback internally, so writes stream at the radio's
                        // real pace - no artificial delays needed
                        var tries = 0
                        while (!bleManager.sendPacket(pkt)) {
                            if (++tries > 20) throw Exception("BLE write failed repeatedly")
                            delay(20)
                        }
                        windowEndOffset += len
                    }

                    // Consume progress acks until the node confirms this window
                    // (tolerant of the node acking at a different cadence).
                    var acked = 0
                    while (acked < windowEndOffset) {
                        acked = awaitOtaProgress(10_000)
                        if (acked > windowEndOffset) {
                            throw Exception("Node acked $acked past window end $windowEndOffset")
                        }
                    }
                    offset = windowEndOffset
                    _otaState.value = OtaState(
                        active = true,
                        progress = (offset.toLong() * 100 / firmware.size).toInt(),
                        status = "Uploading... ${offset / 1024} / ${firmware.size / 1024} kB"
                    )
                }

                _otaState.value = OtaState(active = true, progress = 100, status = "Verifying...")
                sendOtaControl(nodeId, com.example.aethermesh.proto.OtaControl.Op.END, 0, "")
                awaitOtaState(com.example.aethermesh.proto.OtaStatus.State.SUCCESS, 20_000, "verification")

                _otaState.value = OtaState(
                    progress = 100, done = true,
                    status = "Update verified - node is rebooting into the new firmware"
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                try {
                    sendOtaControl(bleManager.connectedNodeId, com.example.aethermesh.proto.OtaControl.Op.ABORT, 0, "")
                } catch (_: Exception) {}
                _otaState.value = OtaState(error = true, status = "Update cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OTA failed: ${e.message}")
                try {
                    sendOtaControl(bleManager.connectedNodeId, com.example.aethermesh.proto.OtaControl.Op.ABORT, 0, "")
                } catch (_: Exception) {}
                _otaState.value = OtaState(error = true, status = "Update failed: ${e.message}")
            }
        }
    }

    fun cancelFirmwareUpdate() {
        otaJob?.cancel()
        dfuController?.abort()
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
            _otaState.value = OtaState(active = true, status = "DFU: connecting to bootloader...")
        }

        override fun onDfuProcessStarting(deviceAddress: String) {
            dfuSawActivity = true
            _otaState.value = OtaState(active = true, status = "DFU: starting transfer...")
        }

        override fun onFirmwareValidating(deviceAddress: String) {
            dfuSawActivity = true
            _otaState.value = OtaState(active = true, progress = 100, status = "DFU: validating firmware...")
        }

        override fun onDeviceDisconnecting(deviceAddress: String?) {
            dfuSawActivity = true
        }

        override fun onProgressChanged(
            deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float,
            currentPart: Int, partsTotal: Int
        ) {
            dfuSawActivity = true
            _otaState.value = OtaState(active = true, progress = percent, status = "DFU uploading... $percent%")
        }

        override fun onDfuCompleted(deviceAddress: String) {
            _otaState.value = OtaState(
                progress = 100, done = true,
                status = "DFU complete - node rebooting into the new firmware"
            )
            dfuController = null
            bleManager.resumeAfterDfu()
        }

        override fun onDfuAborted(deviceAddress: String) {
            _otaState.value = OtaState(error = true, status = "DFU cancelled")
            dfuController = null
            bleManager.resumeAfterDfu()
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
            _otaState.value = OtaState(error = true, status = "DFU failed: ${message ?: "error $error"}")
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

        val legacyDfu = android.os.ParcelUuid.fromString("00001530-1212-EFDE-1523-785FEABCD123")
        val secureDfu = android.os.ParcelUuid.fromString("0000FE59-0000-1000-8000-00805F9B34FB")
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
        if (_otaState.value.active) return
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) {
            _otaState.value = OtaState(error = true, status = "Not connected/authenticated")
            return
        }
        val mac = bleManager.getConnectedDeviceAddress()
        if (mac == null) {
            _otaState.value = OtaState(error = true, status = "No device address")
            return
        }
        val deviceName = bleManager.connectedDeviceName ?: "AetherMesh"
        val nodeId = bleManager.connectedNodeId

        otaJob = repositoryScope.launch(Dispatchers.IO) {
            try {
                _otaState.value = OtaState(active = true, status = "Rebooting node into DFU bootloader...")
                while (otaStatusChannel.tryReceive().isSuccess) { /* drain */ }

                sendOtaControl(nodeId, com.example.aethermesh.proto.OtaControl.Op.ENTER_DFU, 0, "")
                awaitOtaState(com.example.aethermesh.proto.OtaStatus.State.READY, 10_000, "DFU mode acknowledgment")

                // The node is rebooting into its bootloader; release our GATT and
                // pause auto-reconnect so the DFU library owns the connection.
                bleManager.detachForDfu()
                delay(3000) // bootloader boot + advertising settle

                // Nordic-family bootloaders often advertise on a DIFFERENT
                // address (MAC+1) and name in DFU mode, so scan for the DFU
                // service instead of assuming the application's address.
                _otaState.value = OtaState(active = true, status = "Searching for DFU bootloader...")
                val dfuMac = findDfuDevice(15_000)
                    ?: throw Exception("DFU bootloader not advertising (node may need a newer bootloader)")
                Log.d(TAG, "DFU bootloader found at $dfuMac (app was at $mac)")

                dfuSawActivity = false
                _otaState.value = OtaState(active = true, status = "Starting DFU transfer...")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        no.nordicsemi.android.dfu.DfuServiceInitiator.createDfuNotificationChannel(context)
                    }
                    dfuController = no.nordicsemi.android.dfu.DfuServiceInitiator(dfuMac)
                        .setDeviceName(deviceName)
                        .setKeepBond(false)
                        .setZip(zipUri)
                        .setForeground(true)
                        .setDisableNotification(false)
                        .start(context, com.example.aethermesh.ble.DfuService::class.java)
                }

                // Watchdog: if the DFU service shows no life at all, surface an
                // error instead of leaving the bar stuck forever.
                delay(45_000)
                if (!dfuSawActivity && _otaState.value.active) {
                    dfuController?.abort()
                    throw Exception("DFU service never engaged the bootloader")
                }
                // From here the DfuProgressListener drives otaState.
            } catch (e: Exception) {
                Log.e(TAG, "DFU start failed: ${e.message}")
                _otaState.value = OtaState(error = true, status = "DFU failed: ${e.message}")
                bleManager.resumeAfterDfu()
            }
        }
    }

    fun resetOtaState() {
        if (!_otaState.value.active) _otaState.value = OtaState()
    }

    private fun sendOtaControl(
        nodeId: Long,
        op: com.example.aethermesh.proto.OtaControl.Op,
        size: Int,
        md5: String,
        sha256: String = ""
    ) {
        val ctl = com.example.aethermesh.proto.OtaControl.newBuilder()
            .setOp(op)
            .setTotalSize(size)
            .setMd5(md5)
            .setSha256(sha256)
        val pkt = MeshPacket.newBuilder()
            .setSenderId(nodeId.toInt())
            .setRecipientId(nodeId.toInt())
            .setHopLimit(1)
            .setOtaControl(ctl)
            .build()
            .toByteArray()
        if (!bleManager.sendPacket(pkt)) {
            throw Exception("BLE write failed (${op.name})")
        }
    }

    // Wait for a specific state and return its next_offset (READY uses it as a
    // capability hint). ERROR from the node always throws.
    private suspend fun awaitOtaState(
        wanted: com.example.aethermesh.proto.OtaStatus.State,
        timeoutMs: Long,
        what: String
    ): Int {
        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            var value = -1
            var got = false
            while (!got) {
                val st = otaStatusChannel.receive()
                when (st.state) {
                    wanted -> { value = st.nextOffset; got = true }
                    com.example.aethermesh.proto.OtaStatus.State.ERROR ->
                        throw Exception(st.message.ifEmpty { "node reported error" })
                    else -> { /* keep waiting */ }
                }
            }
            value
        }
        return result ?: throw Exception("Timed out waiting for $what")
    }

    // Wait for the next IN_PROGRESS ack and return the node's flashed offset.
    private suspend fun awaitOtaProgress(timeoutMs: Long): Int {
        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            var acked = -1
            while (acked < 0) {
                val st = otaStatusChannel.receive()
                when (st.state) {
                    com.example.aethermesh.proto.OtaStatus.State.IN_PROGRESS -> acked = st.nextOffset
                    com.example.aethermesh.proto.OtaStatus.State.ERROR ->
                        throw Exception(st.message.ifEmpty { "node reported error" })
                    else -> { /* keep waiting */ }
                }
            }
            acked
        }
        return result ?: throw Exception("Timed out waiting for chunk ack")
    }

    // Per-node battery-alert state: the lowest threshold we've already warned
    // about (0 = none). Cleared when a node recharges above RECOVER, so a
    // charge/drain cycle re-arms the alerts. Prevents re-notifying every 60s
    // telemetry while a node sits below a threshold.
    private val batteryAlertLevel = mutableMapOf<Long, Int>()
    private val BATT_ALERT_CRITICAL = 10
    private val BATT_ALERT_LOW = 20
    private val BATT_ALERT_RECOVER = 25

    private fun notifyLowBattery(nodeId: Long, level: Int, isCharging: Boolean) {
        if (nodeId == 0L) return
        // A charging node, or one that has recovered, re-arms future alerts
        if (isCharging || level >= BATT_ALERT_RECOVER) {
            batteryAlertLevel.remove(nodeId)
            return
        }
        val already = batteryAlertLevel[nodeId] ?: 0
        val threshold = when {
            level <= BATT_ALERT_CRITICAL -> BATT_ALERT_CRITICAL
            level <= BATT_ALERT_LOW -> BATT_ALERT_LOW
            else -> return
        }
        // Only alert on first crossing of each threshold (already tracks the
        // lowest threshold hit; a smaller threshold number = more severe)
        if (already != 0 && threshold >= already) return
        batteryAlertLevel[nodeId] = threshold

        val prefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("bg_alerts_enabled", true)) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val chanId = "aethermesh_battery"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    chanId, "Battery Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Warns when a mesh node's battery runs low" }
            )
        }
        val name = dbHelper.getNodes().find { it.nodeId == nodeId }?.name
            ?: "Node %04X".format(nodeId and 0xFFFF)
        val critical = threshold == BATT_ALERT_CRITICAL
        val title = if (critical) "⚠ $name battery critical" else "$name battery low"
        val body = "$level% remaining" + if (critical) " — charge it now" else ""

        val tapIntent = android.content.Intent(context, com.example.aethermesh.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            context, "batt$nodeId".hashCode(), tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = androidx.core.app.NotificationCompat.Builder(context, chanId)
            .setSmallIcon(com.example.aethermesh.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        // Stable per-node id so a critical alert replaces the earlier low one
        nm.notify("batt$nodeId".hashCode(), notif)
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
        val prefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("bg_alerts_enabled", true)) return
        val app = context.applicationContext as? com.example.aethermesh.AetherMeshApplication
        if (app?.isActivityVisible == true) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val notifChannelId = "aethermesh_messages"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    notifChannelId, "Messages",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Incoming mesh chat messages" }
            )
        }

        val senderName = dbHelper.getNodes().find { it.nodeId == senderId }?.name
            ?: "Node %04X".format(senderId and 0xFFFF)
        val title = if (isBroadcast) "$senderName @ $channel" else senderName

        val tapIntent = android.content.Intent(context, com.example.aethermesh.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, chatIdentifier.hashCode(), tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, notifChannelId)
            .setSmallIcon(com.example.aethermesh.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup("aethermesh_messages")
            .build()
        nm.notify(chatIdentifier.hashCode(), notification)
    }

    fun refreshData() {
        // Suppress displaying messages if the device is connected but not authenticated
        if (bleManager.isConnected && !_isDeviceAuthenticated.value) {
            _messages.value = emptyList()
            _nodes.value = emptyList()
            return
        }

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
            val merged = (listOf(DEFAULT_CHANNEL) + dbHelper.getChannels() + _channels.value + _selectedChannel.value)
                .distinct()
            _channels.value = merged
        }
    }

    // Switch the channel shown in the Chats view.
    fun selectChannel(channel: String) {
        if (channel.isBlank()) return
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
        val name = channel.trim()
        if (name.isEmpty()) return false
        val exists = _channels.value.any { it.equals(name, ignoreCase = true) }
        selectChannel(name)
        return !exists
    }

    // Retrieve specific direct chat messages
    fun getDirectMessages(peerNodeId: Long): List<ChatMessage> {
        return dbHelper.getMessages(peerNodeId, isChannel = false)
    }

    fun clearAllMessages() {
        dbHelper.clearAllMessages()
        val editor = securePrefs.edit()
        securePrefs.all.keys.filter { it.startsWith("chat_key_dm_") }.forEach(editor::remove)
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

    // Legacy key derivation retained only to read v1/ECB messages.
    private fun deriveLegacyKey(passcode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(passcode.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    // AES-256-GCM with a random 12-byte IV prepended to the ciphertext.
    // Returns null on failure — callers must refuse to send, never fall back
    // to plaintext.
    fun encryptAES(plainText: String, passcode: String, chatIdentifier: String = ""): String? {
        return try {
            val salt = ByteArray(16)
            java.security.SecureRandom().nextBytes(salt)
            val keySpec = ChatKeyDerivation.derive(passcode, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv))
            if (chatIdentifier.isNotEmpty()) {
                cipher.updateAAD(chatIdentifier.toByteArray(Charsets.UTF_8))
            }
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "v2:" + Base64.encodeToString(salt + iv + encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            null
        }
    }

    fun decryptAES(cipherText: String, passcode: String, chatIdentifier: String = ""): String {
        if (cipherText.startsWith("v2:")) {
            try {
                val decoded = Base64.decode(cipherText.removePrefix("v2:"), Base64.NO_WRAP)
                if (decoded.size <= 44) return "[Decryption Error - Invalid Message]"
                val salt = decoded.copyOfRange(0, 16)
                val iv = decoded.copyOfRange(16, 28)
                val keySpec = ChatKeyDerivation.derive(passcode, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv))
                if (chatIdentifier.isNotEmpty()) {
                    cipher.updateAAD(chatIdentifier.toByteArray(Charsets.UTF_8))
                }
                return String(cipher.doFinal(decoded, 28, decoded.size - 28), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "v2 decryption failed: ${e.message}")
                return "[Decryption Error - Bad Key or Context]"
            }
        }

        val keySpec = deriveLegacyKey(passcode)
        // Current format: base64(IV[12] + ciphertext + GCM tag[16])
        try {
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            if (decoded.size > 28) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, decoded, 0, 12))
                return String(cipher.doFinal(decoded, 12, decoded.size - 12), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // fall through to the legacy format
        }
        // Legacy format from older builds: AES/ECB (no IV, no integrity)
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            "[Decryption Error - Bad Key]"
        }
    }

    // Range Test Engine Methods
    fun startRangeTest(targetId: Long, intervalSeconds: Int) {
        if (rangeTestJob != null) stopRangeTest()
        rangeTestTargetId = targetId
        _isRangeTestActive.value = true
        _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
        startRangeTestLocationUpdates()
        pendingRangePings.clear()

        rangeTestJob = repositoryScope.launch {
            while (_isRangeTestActive.value) {
                expirePendingRangePings()
                sendRangePing(targetId)
                val nextPingAt = System.currentTimeMillis() + intervalSeconds * 1000L
                while (_isRangeTestActive.value && System.currentTimeMillis() < nextPingAt) {
                    val remaining = (nextPingAt - System.currentTimeMillis()).coerceAtLeast(1L)
                    delay(minOf(1000L, remaining))
                    expirePendingRangePings()
                }
            }
        }
        Log.d(TAG, "Direct range test started targeting node 0x${targetId.toString(16).uppercase()} every ${intervalSeconds}s.")
    }

    fun loadRangeTestLogs(targetId: Long) {
        rangeTestTargetId = targetId
        _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
    }

    fun stopRangeTest() {
        rangeTestJob?.cancel()
        rangeTestJob = null
        _isRangeTestActive.value = false
        pendingRangePings.clear()
        stopRangeTestLocationUpdates()
        Log.d(TAG, "Range Test stopped.")
    }

    private fun sendRangePing(targetId: Long) {
        if (!bleManager.isConnected) {
            stopRangeTest()
            return
        }
        if (!_isDeviceAuthenticated.value) {
            Log.w(TAG, "Range test ping skipped: BLE not authenticated.")
            return
        }

        val localNodeId = bleManager.connectedNodeId
        if (localNodeId == targetId) {
            Log.w(TAG, "Range test target 0x${targetId.toString(16)} is the BLE-connected node; " +
                    "connect to a different node to test this target.")
        }

        var generatedPacketId: Int
        do {
            generatedPacketId = PacketIdGenerator.next()
        } while (pendingRangePings.containsKey(generatedPacketId))

        val pending = PendingRangePing(
            targetId = targetId,
            sentAtMs = System.currentTimeMillis(),
            position = captureRangeTestPosition()
        )
        pendingRangePings[generatedPacketId] = pending

        val textBuilder = TextMessage.newBuilder()
            .setContent("PING_${generatedPacketId}_D")
            .setChannel("")
            .setIsEncrypted(false)

        // want_ack = false by design: pings are scored via the target's PONG reply
        // (which the target retries itself). ACK-tracking a ping would make the
        // connected node retransmit it, colliding with the inbound PONGs.
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(targetId.toInt())
            .setPacketId(generatedPacketId)
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setText(textBuilder)
            .build()

        if (bleManager.sendPacket(packet.toByteArray())) {
            Log.d(TAG, "Range test ping sent: packetId=$generatedPacketId")
        } else {
            Log.w(TAG, "Range test ping failed to send over BLE.")
            pendingRangePings.remove(generatedPacketId)
        }
    }

    private fun captureRangeTestPosition(): RangeTestPosition {
        var lat = 0.0
        var lon = 0.0
        var speedMps: Float? = null
        var gpsAccuracyM: Float? = null

        val phoneLoc = lastPhoneLocation
        if (phoneLoc != null && System.currentTimeMillis() - phoneLoc.time < 30000) {
            lat = phoneLoc.latitude
            lon = phoneLoc.longitude
            if (phoneLoc.hasSpeed()) speedMps = phoneLoc.speed
            if (phoneLoc.hasAccuracy()) gpsAccuracyM = phoneLoc.accuracy
        } else {
            val localNodeId = bleManager.connectedNodeId
            val localNode = dbHelper.getNodes().firstOrNull { it.nodeId == localNodeId }
            if (localNode != null) {
                lat = localNode.latitude.toDouble()
                lon = localNode.longitude.toDouble()
            }
        }
        return RangeTestPosition(lat, lon, speedMps, gpsAccuracyM)
    }

    private fun expirePendingRangePings() {
        val cutoff = System.currentTimeMillis() - RANGE_PING_TIMEOUT_MS
        pendingRangePings.entries
            .filter { it.value.sentAtMs <= cutoff }
            .forEach { entry ->
                if (pendingRangePings.remove(entry.key, entry.value)) {
                    logRangeTestResult(
                        pending = entry.value,
                        success = false,
                        rssi = -140f,
                        snr = -20f
                    )
                }
            }
    }

    private fun logRangeTestResult(
        pending: PendingRangePing,
        success: Boolean,
        rssi: Float,
        snr: Float,
        remoteRssi: Float? = null,
        remoteSnr: Float? = null
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
            pending.sentAtMs
        )
        if (pending.targetId == rangeTestTargetId) {
            _rangeTestLogs.value = dbHelper.getRangeTestLogs(pending.targetId)
        }
    }

    fun clearRangeTestLogs(targetId: Long) {
        dbHelper.clearRangeTestLogs(targetId)
        _rangeTestLogs.value = emptyList()
        pendingRangePings.entries
            .filter { it.value.targetId == targetId }
            .forEach { pendingRangePings.remove(it.key, it.value) }
    }

    fun getAllRangeTestLogs(): List<RangeTestLog> {
        return dbHelper.getAllRangeTestLogs()
    }

    fun getMeshDiagnosticsHistory(): List<MeshDiagnosticsSnapshot> =
        dbHelper.getMeshDiagnosticsHistory()

    /** Latest phone GPS fix (fresh only while a range test is running). */
    fun lastPhoneFix(): android.location.Location? = lastPhoneLocation

    fun getTelemetryHistory(nodeId: Long) = dbHelper.getTelemetryHistory(nodeId)

    fun getChannelsList(): List<ChannelConfig> {
        return dbHelper.getChannelsList().map { channel ->
            val identifier = "CHANNEL_${channel.name}"
            var secret = getChatKey(identifier)
            if (secret.isNullOrEmpty() && channel.psk.isNotEmpty()) {
                secret = channel.psk
                saveChatKey(identifier, secret)
            }
            if (channel.psk.isNotEmpty()) {
                dbHelper.clearChannelPsk(channel.id)
            }
            channel.copy(psk = secret ?: "")
        }
    }

    fun insertChannel(channel: ChannelConfig): Long {
        saveChatKey("CHANNEL_${channel.name}", channel.psk)
        val id = dbHelper.insertChannel(channel.copy(psk = ""))
        refreshChannelsList()
        return id
    }

    fun updateChannel(channel: ChannelConfig) {
        val previous = getChannelsList().firstOrNull { it.id == channel.id }
        if (previous != null && previous.name != channel.name) {
            deleteChatKey("CHANNEL_${previous.name}")
        }
        saveChatKey("CHANNEL_${channel.name}", channel.psk)
        dbHelper.updateChannel(channel.copy(psk = ""))
        refreshChannelsList()
    }

    fun deleteChannel(id: Long) {
        getChannelsList().firstOrNull { it.id == id }?.let {
            deleteChatKey("CHANNEL_${it.name}")
        }
        dbHelper.deleteChannel(id)
        refreshChannelsList()
    }

    fun generateRandomPsk(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun refreshChannelsList() {
        val list = getChannelsList().map { it.name }
        _channels.value = if (list.isEmpty()) listOf(DEFAULT_CHANNEL) else list
    }

    fun getOrCreateEcdhKeys(): Pair<String, String> {
        val pubKey = prefs.getString("ecdh_public_key", null)
        val privKey = securePrefs.getString("ecdh_private_key", null)
        if (pubKey != null && privKey != null) {
            return Pair(pubKey, privKey)
        }
        return regenerateEcdhKeys()
    }

    fun regenerateEcdhKeys(): Pair<String, String> {
        return try {
            val keyGen = java.security.KeyPairGenerator.getInstance("EC")
            keyGen.initialize(256)
            val pair = keyGen.generateKeyPair()
            val pub = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
            val priv = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
            prefs.edit().putString("ecdh_public_key", pub).apply()
            securePrefs.edit().putString("ecdh_private_key", priv).apply()
            Pair(pub, priv)
        } catch (e: Exception) {
            Log.e(TAG, "ECDH generation error: ${e.message}")
            Pair("ErrorGeneratingPublicKey", "ErrorGeneratingPrivateKey")
        }
    }

    fun sendPhoneLocation(lat: Double, lon: Double): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false

        // Reject a no-fix / invalid reading. A real GPS fix is never exactly
        // (0,0); sending it caused the node to broadcast Null Island - and with
        // location fuzzing on, an offset turned it into a fake ~0.017,-0.006
        // position that looked "locked" in the app.
        if ((lat == 0.0 && lon == 0.0) || lat.isNaN() || lon.isNaN() ||
            kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) {
            Log.d(TAG, "sendPhoneLocation: ignoring invalid/no-fix coords ($lat, $lon)")
            return false
        }

        val localNodeId = bleManager.connectedNodeId

        var fuzzedLat = lat
        var fuzzedLon = lon

        // Retrieve the primary channel to check for location fuzzer privacy settings
        val primaryChan = getChannelsList().firstOrNull { it.isPrimary }
        if (primaryChan != null) {
            // If position sharing is disabled on the channel, do not send GPS coordinates
            if (!primaryChan.positionEnabled) {
                Log.d(TAG, "Position sharing is disabled on primary channel. Skipping GPS upload.")
                return false
            }
            
            // If precise location is disabled, fuzz the transmitted GPS coordinates
            if (!primaryChan.preciseLocation && primaryChan.precisionMiles > 0f) {
                val milesToDegreesLat = primaryChan.precisionMiles / 69.0f
                val cosLat = Math.cos(Math.toRadians(lat))
                val milesToDegreesLon = primaryChan.precisionMiles / (69.0f * (if (cosLat > 0.0) cosLat else 1.0))
                
                val stableOffsetLat = (((localNodeId.hashCode() and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0
                val stableOffsetLon = ((((localNodeId.hashCode() ushr 16) and 0xFFFF).toDouble() / 65535.0) - 0.5) * 2.0

                fuzzedLat += stableOffsetLat * milesToDegreesLat
                fuzzedLon += stableOffsetLon * milesToDegreesLon
                Log.d(TAG, "Location fuzzer applied for transmitted GPS: stable offset by ±${primaryChan.precisionMiles} miles")
            }
        }

        // Build Telemetry message containing phone's position
        val telemetryBuilder = com.example.aethermesh.proto.Telemetry.newBuilder()
            .setLatitude(fuzzedLat.toFloat())
            .setLongitude(fuzzedLon.toFloat())
            .setBatteryLevel(100) // Dummy battery level
            .setNodeModel("Phone Inherited")
            .setUptimeSeconds(0)
            .setFirmwareVersion("")

        // Build MeshPacket wrapper
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(localNodeId.toInt()) // Set recipient to local node so it intercepts it
            .setPacketId(PacketIdGenerator.next())
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setTelemetry(telemetryBuilder)
            .build()

        // Write over BLE
        return bleManager.sendPacket(packet.toByteArray())
    }
}
