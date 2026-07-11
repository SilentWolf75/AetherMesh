package com.example.aethermesh.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

@SuppressLint("MissingPermission")
class BleConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BleConnManager"
        private const val PREF_PAIRED_MAC = "paired_mac"
        
        val SERVICE_UUID: UUID = UUID.fromString("a75e0001-8b01-4475-bf7d-9477b83e7953")
        val TX_CHAR_UUID: UUID = UUID.fromString("a75e0002-8b01-4475-bf7d-9477b83e7953")
        val RX_CHAR_UUID: UUID = UUID.fromString("a75e0003-8b01-4475-bf7d-9477b83e7953")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val prefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
    private var userWantsDisconnect = false
    
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    
    var isConnected = false
        private set
        
    var connectedDeviceName: String? = null
        private set
        
    var connectedNodeId: Long = 0

    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var connectionTimeoutRunnable: Runnable? = null
    private var reconnectAttempt = 0
    private var mtuTimeoutRunnable: Runnable? = null
    private var pendingAutoConnectMac: String? = null

    init {
        // Auto-connect is deferred until BLUETOOTH_CONNECT is granted (Android 12+).
        val savedMac = prefs.getString(PREF_PAIRED_MAC, null)
        if (savedMac != null) {
            pendingAutoConnectMac = savedMac
            if (hasBleConnectPermission()) {
                scheduleAutoConnect(savedMac)
            } else {
                Log.d(TAG, "Auto-connect to $savedMac deferred until BLUETOOTH_CONNECT is granted.")
            }
        }
    }

    /** Call after runtime BLE permissions are granted (e.g. from MainActivity). */
    fun onPermissionsGranted() {
        val mac = pendingAutoConnectMac ?: prefs.getString(PREF_PAIRED_MAC, null) ?: return
        pendingAutoConnectMac = null
        if (!isConnected) {
            scheduleAutoConnect(mac)
        }
    }

    private fun scheduleAutoConnect(macAddress: String) {
        Log.d(TAG, "Scheduling auto-connect to saved MAC: $macAddress")
        handler.postDelayed({
            if (!isConnected) {
                connect(macAddress)
            }
        }, 1500)
    }

    private fun hasBleConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasBleScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String? {
        if (!hasBleConnectPermission()) return null
        return try {
            device.name
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to read device name: ${e.message}")
            null
        }
    }
    
    // Callbacks
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onPacketReceived: ((ByteArray) -> Unit)? = null
    var onDeviceDiscovered: ((String, String) -> Unit)? = null // Device Name, MAC Address

    // Scan for AetherMesh devices.
    // We scan WITHOUT a hardware ScanFilter (offloaded 128-bit-UUID filtering is unreliable
    // on many phones) and instead identify our nodes in the callback by the advertised
    // service UUID. This is also name-independent, so nodes with a custom name still appear.
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        if (!hasBleScanPermission()) {
            Log.w(TAG, "BLE scan skipped: BLUETOOTH_SCAN (or location) permission not granted.")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val scanRecord = result.scanRecord
                val deviceName = scanRecord?.deviceName ?: safeDeviceName(result.device)
                val advertisesOurService =
                    scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                val nameMatches = deviceName?.startsWith("AetherMesh-") == true

                if (advertisesOurService || nameMatches) {
                    // Fall back to a generic label if the advert has no readable name yet.
                    val label = deviceName ?: "AetherMesh Node"
                    onDeviceDiscovered?.invoke(label, result.device.address)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
            }
        }

        Log.d(TAG, "Starting BLE scanning (unfiltered, matching by service UUID)...")
        // null filter list = report all advertisements; we filter in the callback.
        scanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        if (bluetoothAdapter == null || scanCallback == null) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        Log.d(TAG, "Stopping BLE scanning.")
        scanner.stopScan(scanCallback)
        scanCallback = null
    }

    // Connect to a node by MAC address
    fun connect(macAddress: String) {
        if (bluetoothAdapter == null) return
        if (!hasBleConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted; deferring connect to $macAddress")
            pendingAutoConnectMac = macAddress
            return
        }

        // Cancel any pending reconnect attempts
        reconnectRunnable?.let {
            handler.removeCallbacks(it)
            reconnectRunnable = null
        }
        
        // Stop scanning to dedicate all radio bandwidth to the connection handshake
        stopScan()
        
        val currentGatt = bluetoothGatt
        if (currentGatt != null) {
            val currentMac = currentGatt.device.address
            if (currentMac == macAddress) {
                Log.d(TAG, "Already connected/connecting to device: $macAddress. Skipping reconnect.")
                return
            }
            Log.d(TAG, "Disconnecting and closing existing connection to $currentMac before connecting to $macAddress")
            
            // Set userWantsDisconnect temporarily to prevent disconnect callback of the old device 
            // from triggering unexpected reconnect loops.
            userWantsDisconnect = true
            
            try {
                currentGatt.disconnect()
                currentGatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing previous GATT: ${e.message}")
            }
            bluetoothGatt = null
            isConnected = false
            
            // Allow stack to settle down before opening connection to new device
            handler.postDelayed({
                performConnect(macAddress)
            }, 500)
        } else {
            performConnect(macAddress)
        }
    }

    private fun performConnect(macAddress: String) {
        if (!hasBleConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted; deferring performConnect to $macAddress")
            pendingAutoConnectMac = macAddress
            return
        }

        userWantsDisconnect = false
        prefs.edit().putString(PREF_PAIRED_MAC, macAddress).apply()

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        val label = safeDeviceName(device) ?: "Unknown"
        Log.d(TAG, "Connecting to device: $label (${device.address})")

        // Explicitly use TRANSPORT_LE to avoid fallback connection delays/drops on dual-mode devices
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        connectionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectionTimeoutRunnable = Runnable {
            val stalledGatt = bluetoothGatt
            if (stalledGatt != null && !isConnected) {
                Log.w(TAG, "Connection handshake timed out for $macAddress")
                try {
                    stalledGatt.disconnect()
                    stalledGatt.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing timed-out GATT: ${e.message}")
                }
                if (bluetoothGatt == stalledGatt) bluetoothGatt = null
                onConnectionStateChanged?.invoke(false)
                scheduleReconnect(macAddress)
            }
        }
        handler.postDelayed(connectionTimeoutRunnable!!, 15_000)
    }

    private fun scheduleReconnect(macAddress: String) {
        if (userWantsDisconnect || suppressReconnect) return
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        val delay = ReconnectPolicy.delayMs(reconnectAttempt, macAddress.hashCode() + reconnectAttempt)
        reconnectAttempt++
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delay}ms")
        reconnectRunnable = Runnable {
            reconnectRunnable = null
            if (!isConnected && !userWantsDisconnect && !suppressReconnect) connect(macAddress)
        }
        handler.postDelayed(reconnectRunnable!!, delay)
    }

    // Set during a bootloader DFU update: our auto-reconnect must not fight the
    // Nordic DFU library for the device. Unlike disconnect(), this keeps the
    // paired MAC so normal reconnection resumes afterward.
    @Volatile
    var suppressReconnect = false

    fun detachForDfu() {
        suppressReconnect = true
        reconnectRunnable?.let {
            handler.removeCallbacks(it)
            reconnectRunnable = null
        }
        connectionTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            connectionTimeoutRunnable = null
        }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        connectedDeviceName = null
        onConnectionStateChanged?.invoke(false)
    }

    fun resumeAfterDfu() {
        suppressReconnect = false
        reconnectAttempt = 0
        val savedMac = prefs.getString(PREF_PAIRED_MAC, null)
        if (savedMac != null && !isConnected) {
            // Give the freshly flashed node a moment to boot before connecting
            handler.postDelayed({
                if (!isConnected && !userWantsDisconnect) {
                    connect(savedMac)
                }
            }, 4000)
        }
    }

    fun disconnect() {
        userWantsDisconnect = true
        reconnectAttempt = 0
        
        // Cancel any pending reconnect attempts
        reconnectRunnable?.let {
            handler.removeCallbacks(it)
            reconnectRunnable = null
        }
        connectionTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            connectionTimeoutRunnable = null
        }
        
        // Clear paired MAC when user explicitly disconnects so we don't auto-connect next time
        prefs.edit().remove(PREF_PAIRED_MAC).apply()
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        connectedDeviceName = null
        connectedNodeId = 0
        onConnectionStateChanged?.invoke(false)
    }

    fun getPairedMac(): String? {
        return prefs.getString(PREF_PAIRED_MAC, null)
    }

    fun parseNodeIdFromMac(mac: String): Long {
        return try {
            val parts = mac.split(":")
            if (parts.size >= 4) {
                val b0 = parts[0].toInt(16)
                val b1 = parts[1].toInt(16)
                val b2 = parts[2].toInt(16)
                val b3 = parts[3].toInt(16)
                
                (b3.toLong() and 0xFFL shl 24) or
                (b2.toLong() and 0xFFL shl 16) or
                (b1.toLong() and 0xFFL shl 8) or
                (b0.toLong() and 0xFFL)
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing node ID from MAC $mac: ${e.message}")
            0L
        }
    }

    // Write flow control: even WRITE_TYPE_NO_RESPONSE writes must wait for
    // onCharacteristicWrite before the stack accepts the next one. Blind
    // retry-with-sleep throttled OTA to a crawl; gating on the callback lets
    // writes stream at the radio's actual pace. The lock also protects the
    // characteristic's shared value from concurrent senders (OTA stream vs
    // periodic telemetry/GPS pushes).
    private val writeLock = Object()

    @Volatile
    private var writeInFlight = false

    fun sendPacket(packetBytes: ByteArray, timeoutMs: Long = 1000): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = txCharacteristic ?: return false

        synchronized(writeLock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (writeInFlight) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) {
                    // Callback never came (stack hiccup / disconnect race) -
                    // recover instead of deadlocking
                    Log.w(TAG, "sendPacket: write-complete callback timeout; forcing gate open")
                    writeInFlight = false
                    break
                }
                try {
                    writeLock.wait(left)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }

            char.value = packetBytes
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            val ok = gatt.writeCharacteristic(char)
            if (ok) writeInFlight = true
            return ok
        }
    }

    internal fun onWriteCompleted() {
        synchronized(writeLock) {
            writeInFlight = false
            writeLock.notifyAll()
        }
    }

    // GATT Callbacks
    private val gattCallback = object : BluetoothGattCallback() {
        
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt != bluetoothGatt) {
                Log.d(TAG, "onConnectionStateChange: Stale GATT callback (device: ${gatt.device.address}). Ignoring.")
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing stale GATT: ${e.message}")
                }
                return
            }
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server. Requesting MTU 256...")
                val discoverRunnable = object : Runnable {
                    override fun run() {
                        if (mtuTimeoutRunnable != null) {
                            mtuTimeoutRunnable = null
                            Log.d(TAG, "MTU change timeout/unsupported. Discovering services now...")
                            gatt.discoverServices()
                        }
                    }
                }
                mtuTimeoutRunnable = discoverRunnable
                handler.postDelayed(discoverRunnable, 800)
                gatt.requestMtu(256)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server. Closing GATT to release system resources...")
                mtuTimeoutRunnable?.let {
                    handler.removeCallbacks(it)
                    mtuTimeoutRunnable = null
                }
                connectionTimeoutRunnable?.let {
                    handler.removeCallbacks(it)
                    connectionTimeoutRunnable = null
                }
                isConnected = false
                connectedDeviceName = null
                connectedNodeId = 0
                
                try {
                    gatt.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT: ${e.message}")
                }
                if (gatt == bluetoothGatt) {
                    bluetoothGatt = null
                }
                
                handler.post { onConnectionStateChanged?.invoke(false) }

                // Auto-reconnect if it's not a user-initiated disconnect (and not
                // suspended for a bootloader DFU transfer)
                if (!userWantsDisconnect && !suppressReconnect) {
                    val savedMac = prefs.getString(PREF_PAIRED_MAC, null)
                    if (savedMac != null) {
                        scheduleReconnect(savedMac)
                    }
                }
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt != bluetoothGatt) {
                Log.d(TAG, "onMtuChanged: Stale GATT callback. Ignoring.")
                return
            }
            Log.d(TAG, "MTU changed to: $mtu, status: $status")
            
            mtuTimeoutRunnable?.let {
                handler.removeCallbacks(it)
                mtuTimeoutRunnable = null
                Log.d(TAG, "MTU handshake completed. Discovering services...")
                gatt.discoverServices()
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt != bluetoothGatt) {
                Log.d(TAG, "onServicesDiscovered: Stale GATT callback. Ignoring.")
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    
                    // Enable notifications on RX Characteristic immediately
                    val rxChar = rxCharacteristic
                    if (rxChar != null) {
                        gatt.setCharacteristicNotification(rxChar, true)
                        
                        val descriptor = rxChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            Log.d(TAG, "Writing CCCD descriptor to enable notifications...")
                            gatt.writeDescriptor(descriptor)
                        } else {
                            finalizeConnection(gatt)
                        }
                    } else {
                        finalizeConnection(gatt)
                    }
                } else {
                    Log.e(TAG, "AetherMesh service NOT found on device.")
                    gatt.disconnect()
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // Stack is ready for the next write - release the send gate
            onWriteCompleted()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt != bluetoothGatt) return
            Log.d(TAG, "onDescriptorWrite callback received: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                finalizeConnection(gatt)
            } else {
                Log.e(TAG, "Notification setup failed: $status")
                gatt.disconnect()
            }
        }

        private fun finalizeConnection(gatt: BluetoothGatt) {
            if (isConnected) return // Prevent double-triggering finalization
            
            isConnected = true
            reconnectAttempt = 0
            connectionTimeoutRunnable?.let {
                handler.removeCallbacks(it)
                connectionTimeoutRunnable = null
            }
            connectedDeviceName = safeDeviceName(gatt.device)
            
            // Save MAC address to preferences for auto-reconnection
            prefs.edit().putString(PREF_PAIRED_MAC, gatt.device.address).apply()
            
            // Extract Node ID from device name (AetherMesh-XXXX) or fallback/verify with MAC address
            try {
                val hexPart = connectedDeviceName?.substringAfter("AetherMesh-") ?: ""
                if (hexPart.isNotEmpty() && hexPart != connectedDeviceName) {
                    val name16Id = hexPart.toLong(16)
                    val mac32Id = parseNodeIdFromMac(gatt.device.address)
                    if ((mac32Id and 0xFFFFL) == name16Id) {
                        connectedNodeId = mac32Id
                    } else {
                        connectedNodeId = name16Id
                    }
                } else {
                    connectedNodeId = parseNodeIdFromMac(gatt.device.address)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing node ID: ${e.message}")
                connectedNodeId = parseNodeIdFromMac(gatt.device.address)
            }
            
            Log.d(TAG, "BLE services configured. Node ID: 0x${connectedNodeId.toString(16).uppercase()}")
            handler.post { onConnectionStateChanged?.invoke(true) }
            
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt != bluetoothGatt) {
                Log.d(TAG, "onCharacteristicChanged: Stale GATT callback. Ignoring.")
                return
            }
            if (characteristic.uuid == RX_CHAR_UUID) {
                val data = characteristic.value
                Log.d(TAG, "Packet received from BLE: ${data.size} bytes")
                handler.post { onPacketReceived?.invoke(data) }
            }
        }
    }

    fun getConnectedDeviceAddress(): String? {
        return bluetoothGatt?.device?.address
    }
}
