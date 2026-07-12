package com.example.aethermesh.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.aethermesh.AetherMeshApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AetherMeshService : Service() {

    companion object {
        private const val TAG = "AetherMeshService"
        private const val CHANNEL_ID = "aethermesh_bg_service"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        androidx.core.app.ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(statusText(initializing = true)), types
        )

        val app = application as AetherMeshApplication
        serviceScope.launch {
            combine(
                app.repository.isBleConnected,
                app.repository.bleReconnectGaveUp,
                app.repository.bleConnectionPhase,
                app.repository.isDeviceAuthenticated
            ) { connected, gaveUp, phase, authenticated ->
                statusText(
                    connected = connected,
                    gaveUp = gaveUp,
                    phase = phase,
                    authenticated = authenticated
                )
            }.collectLatest { text ->
                updateNotification(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun spanish(): Boolean {
        val prefs = getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
        return prefs.getString("app_language", "English") == "Spanish"
    }

    private fun bluetoothEnabled(): Boolean {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter != null && adapter.isEnabled
    }

    private fun statusText(
        initializing: Boolean = false,
        connected: Boolean = false,
        gaveUp: Boolean = false,
        phase: BleConnectionPhase = BleConnectionPhase.Disconnected,
        authenticated: Boolean = true
    ): String {
        val es = spanish()
        if (initializing) {
            return if (es) "Iniciando AetherMesh…" else "Initializing AetherMesh…"
        }
        if (!bluetoothEnabled()) {
            return if (es) "Bluetooth desactivado" else "Bluetooth is off"
        }
        val app = application as AetherMeshApplication
        if (connected) {
            val deviceName = app.repository.bleManager.connectedDeviceName
                ?: if (es) "Nodo" else "Node"
            return if (!authenticated) {
                if (es) "Conectado — desbloquear $deviceName" else "Connected — unlock $deviceName"
            } else {
                if (es) "Conectado a $deviceName" else "Connected to $deviceName"
            }
        }
        if (gaveUp) {
            return if (es) "Reconexión agotada — abre la app" else "Reconnect gave up — open the app"
        }
        val pairedMac = app.repository.bleManager.getPairedMac()
        return when {
            pairedMac != null &&
                (phase == BleConnectionPhase.Reconnecting || phase == BleConnectionPhase.Connecting) ->
                if (es) "Reconectando al nodo emparejado…" else "Reconnecting to paired node…"
            pairedMac != null ->
                if (es) "Buscando nodo emparejado…" else "Scanning for paired node…"
            else ->
                if (es) "Listo para emparejar un nodo" else "Ready to pair a node"
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = Intent(this, com.example.aethermesh.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherMesh")
            .setContentText(contentText)
            .setSmallIcon(com.example.aethermesh.R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val es = spanish()
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (es) "Conexión en segundo plano" else "AetherMesh Background Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = if (es)
                    "Mantiene activa la conexión BLE de la malla en segundo plano"
                else
                    "Keeps BLE mesh connection active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
