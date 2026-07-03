package com.example.aethermesh.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
        startForeground(NOTIFICATION_ID, buildNotification("Initializing AetherMesh..."))

        // Listen to BLE Connection changes from repository
        val app = application as AetherMeshApplication
        serviceScope.launch {
            app.repository.isBleConnected.collectLatest { connected ->
                val text = if (connected) {
                    val deviceName = app.repository.bleManager.connectedDeviceName ?: "Node"
                    "Connected to $deviceName"
                } else {
                    val pairedMac = app.repository.bleManager.getPairedMac()
                    if (pairedMac != null) {
                        "Scanning/Reconnecting to paired node..."
                    } else {
                        "Ready to pair node"
                    }
                }
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

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherMesh")
            .setContentText(contentText)
            .setSmallIcon(com.example.aethermesh.R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AetherMesh Background Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BLE mesh connection active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
