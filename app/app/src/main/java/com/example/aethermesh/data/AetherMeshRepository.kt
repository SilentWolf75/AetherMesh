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

class AetherMeshRepository(private val context: Context) {

    companion object {
        private const val TAG = "MeshRepository"
        const val DEFAULT_CHANNEL = "General"
        private const val MAX_TEXT_CONTENT_LENGTH = 127
        // Encrypted payloads grow: base64(12B IV + plaintext + 16B GCM tag) must
        // fit the proto's 168-byte content field -> plaintext capped lower.
        private const val MAX_ENCRYPTED_CONTENT_LENGTH = 96
        private const val MAX_CHANNEL_LENGTH = 31
        private const val MESSAGE_ACK_TIMEOUT_MS = 15_000L
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
    private var lastSentRangePingId: Int = 0
    private var lastSentRangePingTime: Long = 0L
    private var rangeTestJob: Job? = null
    private val repositoryScope = CoroutineScope(Dispatchers.Default + Job())

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
            .setPacketId((1..100000).random())
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
            .setPacketId((1..100000).random())
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
                
                // Clean up duplicate old MAC-based node ID from the database if they differ
                if (oldNodeId != 0L && oldNodeId != senderId) {
                    dbHelper.deleteNode(oldNodeId)
                    Log.d(TAG, "Deleted old duplicate MAC-based node ID: 0x${oldNodeId.toString(16).uppercase()}")
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
                val localName = nodePrefs.getString("node_name", "")?.takeIf { it.isNotEmpty() } ?: bleManager.connectedDeviceName ?: "Wolf Base"
                val localShortName = nodePrefs.getString("node_short_name", "")?.takeIf { it.isNotEmpty() } ?: 
                    localName.replace("AetherMesh-", "").replace("Node ", "").replace(Regex("[^a-zA-Z0-9]"), "").take(4).uppercase().ifEmpty { String.format("%04X", (senderId and 0xFFFFL).toInt()) }
                dbHelper.updateNodeNameAndShortName(senderId, localName, localShortName)

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
                else -> null
            }
            if (newStatus != null) {
                Log.d(TAG, "DeliveryStatus for packet ${delivery.packetId}: $newStatus, reason=${delivery.reason}, retry=${delivery.retryCount}")
                dbHelper.updateMessageStatus(delivery.packetId, newStatus)
                refreshData()
            }
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
            currentMap[senderId] = RouteHopInfo(
                targetId = senderId,
                nextHopId = prevHopId,
                hops = hopsCount,
                lastRssi = packet.rxRssi,
                lastSnr = packet.rxSnr,
                timestamp = System.currentTimeMillis()
            )
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
                    val pongId = contentReceived.removePrefix("PONG_").toIntOrNull()
                    if (_isRangeTestActive.value && pongId != null && pongId == lastSentRangePingId) {
                        Log.d(TAG, "Range test PONG matched ping $pongId")
                        logRangeTestResult(success = true, rssi = packet.rxRssi, snr = packet.rxSnr)
                    }
                    return
                }
                if (contentReceived.startsWith("PING_")) {
                    return
                }

                val chatIdentifier = if (recipientId == 0xFFFFFFFFL) "CHANNEL_$targetChan" else "DM_$senderId"
                
                val finalContent = if (isEncrypted) {
                    val passcode = dbHelper.getChatKey(chatIdentifier)
                    if (!passcode.isNullOrEmpty()) {
                        decryptAES(contentReceived, passcode)
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
            }
            MeshPacket.PayloadCase.TELEMETRY -> {
                val telemetry = packet.telemetry
                var lat = telemetry.latitude
                var lon = telemetry.longitude
                
                // Retrieve the primary channel to check for location fuzzer privacy settings
                val primaryChan = dbHelper.getChannelsList().firstOrNull { it.isPrimary }
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
                    voltage = telemetry.batteryVoltage
                )
                // Append to telemetry history for battery/voltage graphs.
                dbHelper.insertTelemetrySample(senderId, telemetry.batteryLevel, telemetry.batteryVoltage, telemetry.isCharging)
                retryQueuedDirectMessages(senderId)
                refreshData()
            }
            MeshPacket.PayloadCase.ACK -> {
                val ackedId = packet.ack.ackedPacketId
                Log.d(TAG, "ACK received for packet: $ackedId")
                dbHelper.updateMessageStatus(ackedId, "DELIVERED")
                if (_isRangeTestActive.value && ackedId == lastSentRangePingId) {
                    Log.d(TAG, "Range test ACK matched packet $ackedId")
                    logRangeTestResult(
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

    fun sendMessage(recipientId: Long, content: String, channel: String = _selectedChannel.value): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false
        val boundedChannel = channel.take(MAX_CHANNEL_LENGTH)
        val generatedPacketId = (1..100000).random()

        val chatIdentifier = if (recipientId == 0xFFFFFFFFL) "CHANNEL_$boundedChannel" else "DM_$recipientId"
        val passcode = dbHelper.getChatKey(chatIdentifier)
        val isEncrypted = !passcode.isNullOrEmpty()
        val boundedContent = content.take(if (isEncrypted) MAX_ENCRYPTED_CONTENT_LENGTH else MAX_TEXT_CONTENT_LENGTH)

        // Encrypt FIRST and refuse to send on failure — never fall back to
        // transmitting plaintext on a chat the user believes is encrypted.
        val contentToSend = if (isEncrypted) {
            encryptAES(boundedContent, passcode!!) ?: run {
                Log.e(TAG, "Encryption failed; message NOT sent.")
                return false
            }
        } else {
            boundedContent
        }

        // Insert into local DB as sent by local user (store plaintext locally)
        val localNodeId = bleManager.connectedNodeId
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

    fun sendNodeConfig(name: String, shortName: String, sf: Int, bw: Float, txPower: Int, region: Int, role: Int, telemetryInterval: Int = 60, screenTimeout: Int = 30, powerSaveMode: Boolean = false): Boolean {
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

        // Build MeshPacket wrapper
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(localNodeId.toInt())
            .setPacketId((1..100000).random())
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
        powerSaveMode: Boolean = false
    ): Boolean {
        if (!bleManager.isConnected || !_isDeviceAuthenticated.value) return false

        val localNodeId = bleManager.connectedNodeId

        val configBuilder = com.example.aethermesh.proto.NodeConfig.newBuilder()
            .setNodeName(name)
            .setConfigPassword(password)
            .setLoraSf(sf)
            .setLoraBw(bw)
            .setLoraTxPower(txPower)
            .setRegion(region)
            .setNodeRole(role)
            .setTelemetryInterval(telemetryInterval)
            .setScreenTimeoutSecs(screenTimeout)
            .setPowerSaveMode(powerSaveMode)

        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(nodeId.toInt())
            .setPacketId((1..100000).random())
            .setHopLimit(4)
            .setWantAck(true)
            .setPrevHopId(localNodeId.toInt())
            .setConfig(configBuilder)
            .build()

        return bleManager.sendPacket(packet.toByteArray())
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
        refreshData()
    }

    fun clearAllNodes() {
        dbHelper.clearAllNodes()
        if (_isRangeTestActive.value) {
            stopRangeTest()
        }
        _rangeTestLogs.value = emptyList()
        refreshData()
    }

    // AES-256 E2EE Cryptography Helper
    private fun deriveKey(passcode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(passcode.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    // AES-256-GCM with a random 12-byte IV prepended to the ciphertext.
    // Returns null on failure — callers must refuse to send, never fall back
    // to plaintext.
    fun encryptAES(plainText: String, passcode: String): String? {
        return try {
            val keySpec = deriveKey(passcode)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv))
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            null
        }
    }

    fun decryptAES(cipherText: String, passcode: String): String {
        val keySpec = deriveKey(passcode)
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
        lastSentRangePingId = 0
        lastSentRangePingTime = 0L

        rangeTestJob = repositoryScope.launch {
            while (_isRangeTestActive.value) {
                sendRangePing(targetId)
                delay(intervalSeconds * 1000L)
            }
        }
        Log.d(TAG, "Range Test started targeting node 0x${targetId.toString(16).uppercase()} every ${intervalSeconds}s.")
    }

    fun loadRangeTestLogs(targetId: Long) {
        rangeTestTargetId = targetId
        _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
    }

    fun stopRangeTest() {
        rangeTestJob?.cancel()
        rangeTestJob = null
        _isRangeTestActive.value = false
        lastSentRangePingId = 0
        lastSentRangePingTime = 0L
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

        if (lastSentRangePingId != 0) {
            logRangeTestResult(success = false, rssi = -140f, snr = -20f)
        }

        val localNodeId = bleManager.connectedNodeId
        if (localNodeId == targetId) {
            Log.w(TAG, "Range test target 0x${targetId.toString(16)} is the BLE-connected node; " +
                    "connect to a different node to test this target.")
        }

        val generatedPacketId = (1..100000).random()
        lastSentRangePingId = generatedPacketId
        lastSentRangePingTime = System.currentTimeMillis()

        val textBuilder = TextMessage.newBuilder()
            .setContent("PING_$generatedPacketId")
            .setChannel("")
            .setIsEncrypted(false)

        // want_ack = false by design: pings are scored via the target's PONG reply
        // (which the target retries itself). ACK-tracking a ping would make the
        // connected node retransmit it, colliding with the inbound PONGs.
        val packet = MeshPacket.newBuilder()
            .setSenderId(localNodeId.toInt())
            .setRecipientId(targetId.toInt())
            .setPacketId(generatedPacketId)
            .setHopLimit(4)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setText(textBuilder)
            .build()

        if (bleManager.sendPacket(packet.toByteArray())) {
            Log.d(TAG, "Range test ping sent: packetId=$generatedPacketId")
        } else {
            Log.w(TAG, "Range test ping failed to send over BLE.")
            lastSentRangePingId = 0
            lastSentRangePingTime = 0L
        }
    }

    fun logRangeTestResult(success: Boolean, rssi: Float, snr: Float, remoteRssi: Float? = null, remoteSnr: Float? = null) {
        val targetId = rangeTestTargetId
        if (targetId == 0L) return

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

        dbHelper.insertRangeTestLog(targetId, lat, lon, rssi, snr, success, remoteRssi, remoteSnr, speedMps, gpsAccuracyM)
        _rangeTestLogs.value = dbHelper.getRangeTestLogs(targetId)
        lastSentRangePingId = 0
        lastSentRangePingTime = 0L
    }

    fun clearRangeTestLogs(targetId: Long) {
        dbHelper.clearRangeTestLogs(targetId)
        _rangeTestLogs.value = emptyList()
        if (_isRangeTestActive.value && rangeTestTargetId == targetId) {
            lastSentRangePingId = 0
            lastSentRangePingTime = 0L
        }
    }

    fun getAllRangeTestLogs(): List<RangeTestLog> {
        return dbHelper.getAllRangeTestLogs()
    }

    /** Latest phone GPS fix (fresh only while a range test is running). */
    fun lastPhoneFix(): android.location.Location? = lastPhoneLocation

    fun getTelemetryHistory(nodeId: Long) = dbHelper.getTelemetryHistory(nodeId)

    fun getChannelsList(): List<ChannelConfig> {
        return dbHelper.getChannelsList()
    }

    fun insertChannel(channel: ChannelConfig): Long {
        val id = dbHelper.insertChannel(channel)
        refreshChannelsList()
        return id
    }

    fun updateChannel(channel: ChannelConfig) {
        dbHelper.updateChannel(channel)
        refreshChannelsList()
    }

    fun deleteChannel(id: Long) {
        dbHelper.deleteChannel(id)
        refreshChannelsList()
    }

    fun generateRandomPsk(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun refreshChannelsList() {
        val list = dbHelper.getChannelsList().map { it.name }
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

        val localNodeId = bleManager.connectedNodeId
        
        var fuzzedLat = lat
        var fuzzedLon = lon

        // Retrieve the primary channel to check for location fuzzer privacy settings
        val primaryChan = dbHelper.getChannelsList().firstOrNull { it.isPrimary }
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
            .setPacketId((1..100000).random())
            .setHopLimit(1)
            .setWantAck(false)
            .setPrevHopId(localNodeId.toInt())
            .setTelemetry(telemetryBuilder)
            .build()

        // Write over BLE
        return bleManager.sendPacket(packet.toByteArray())
    }
}
