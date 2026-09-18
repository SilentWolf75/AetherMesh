package com.silentwolf75.aethermesh.ble

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Brings the mesh service back after the phone restarts or the app is
 * updated, so messages keep arriving without the user opening the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE)
        val savedMac = prefs.getString(BleConnectPolicy.PREF_PAIRED_MAC, null)
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

        when (BootReconnectPolicy.decide(savedMac, hasPermission)) {
            BootReconnectPolicy.Decision.START -> {
                Log.i(TAG, "${intent.action}: starting the mesh service to reconnect")
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AetherMeshService::class.java)
                    )
                } catch (e: RuntimeException) {
                    // Some OEM builds refuse background starts even here; the
                    // app reconnects as usual the next time it is opened.
                    Log.w(TAG, "Could not start the mesh service after ${intent.action}: ${e.message}")
                }
            }
            BootReconnectPolicy.Decision.NO_PERMISSION ->
                Log.w(TAG, "Not reconnecting after ${intent.action}: Bluetooth permission is missing")
            BootReconnectPolicy.Decision.NO_NODE -> Unit
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
