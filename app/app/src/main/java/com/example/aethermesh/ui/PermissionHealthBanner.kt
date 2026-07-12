package com.example.aethermesh.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aethermesh.ui.main.AccentAmber
import com.example.aethermesh.ui.main.SurfaceDark
import com.example.aethermesh.ui.main.TextLight
import com.example.aethermesh.ui.main.TextMuted

data class PermissionHealth(
    val missingBle: Boolean,
    val missingLocation: Boolean,
    val missingNotifications: Boolean
) {
    val hasAnyIssue: Boolean
        get() = missingBle || missingLocation || missingNotifications
}

fun checkPermissionHealth(context: Context, bgAlertsEnabled: Boolean): PermissionHealth {
    val missingBle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
    }
    val missingLocation =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
    val missingNotifications = bgAlertsEnabled && Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    return PermissionHealth(missingBle, missingLocation, missingNotifications)
}

@Composable
fun PermissionHealthBanner(
    appLanguage: String,
    bgAlertsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick = SystemClock.elapsedRealtime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val health = remember(refreshTick, bgAlertsEnabled) {
        checkPermissionHealth(context, bgAlertsEnabled)
    }
    if (!health.hasAnyIssue) return

    val spanish = appLanguage == "Spanish"
    val parts = buildList {
        if (health.missingBle) add(if (spanish) "Bluetooth" else "Bluetooth")
        if (health.missingLocation) add(if (spanish) "ubicación" else "location")
        if (health.missingNotifications) add(if (spanish) "notificaciones" else "notifications")
    }
    val summary = if (spanish) {
        "Faltan permisos: ${parts.joinToString(", ")}. Algunas funciones no funcionarán."
    } else {
        "Missing permissions: ${parts.joinToString(", ")}. Some features won't work."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .border(BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)), RoundedCornerShape(0.dp))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = if (spanish) "Permisos" else "Permissions",
                tint = AccentAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (spanish) "Permisos necesarios" else "Permissions needed",
                    color = TextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = summary, color = TextMuted, fontSize = 11.sp)
            }
        }
        TextButton(onClick = onOpenSettings) {
            Text(
                if (spanish) "Ajustes" else "Settings",
                color = AccentAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
