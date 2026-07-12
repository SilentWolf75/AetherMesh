package com.example.aethermesh

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aethermesh.ble.AetherMeshService
import com.example.aethermesh.theme.AetherMeshTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val TAG = "MainActivity"
    }

    override fun onResume() {
        super.onResume()
        (application as AetherMeshApplication).isActivityVisible = true
    }

    override fun onPause() {
        super.onPause()
        (application as AetherMeshApplication).isActivityVisible = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request BLE and Location permissions at startup
        checkAndRequestPermissions()

        // Service start is deferred until BLUETOOTH_CONNECT is granted (see onBlePermissionsReady)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("aethermesh_prefs", Context.MODE_PRIVATE) }
            var themeState by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "app_theme") {
                        themeState = sharedPrefs.getString("app_theme", "System") ?: "System"
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val darkTheme = when (themeState) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            AetherMeshTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Android 12 (API 31) and higher requires new BLE permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            // Location is still needed for displaying user positions on the map
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            // Android 11 and below requires Location permissions for BLE scanning
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Android 13 (API 33) and higher requires permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val listToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (listToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $listToRequest")
            ActivityCompat.requestPermissions(this, listToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "All permissions already granted.")
            onBlePermissionsReady()
        }
    }

    private fun onBlePermissionsReady() {
        startBackgroundService()
        (application as AetherMeshApplication).repository.bleManager.onPermissionsGranted()
    }

    private fun startBackgroundService() {
        val hasBleConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasBleConnect) {
            Log.w(TAG, "Postponing AetherMeshService start until BLUETOOTH_CONNECT is granted.")
            return
        }

        val serviceIntent = Intent(this, AetherMeshService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "AetherMeshService started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AetherMeshService: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedCount = grantResults.filter { it == PackageManager.PERMISSION_DENIED }.size
            if (deniedCount > 0) {
                Log.w(TAG, "$deniedCount permissions were denied by the user.")
            } else {
                Log.d(TAG, "All requested permissions granted by user.")
            }
            onBlePermissionsReady()
        }
    }
}
