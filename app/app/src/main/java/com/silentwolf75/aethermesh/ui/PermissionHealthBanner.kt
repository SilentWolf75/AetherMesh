package com.silentwolf75.aethermesh.ui

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
import com.silentwolf75.aethermesh.data.AppUiPrefs
import com.silentwolf75.aethermesh.data.PermissionHealth
import com.silentwolf75.aethermesh.data.PermissionHealthPolicy
import com.silentwolf75.aethermesh.ui.main.AccentAmber
import com.silentwolf75.aethermesh.ui.main.SurfaceDark
import com.silentwolf75.aethermesh.ui.main.TextLight
import com.silentwolf75.aethermesh.ui.main.TextMuted

fun checkPermissionHealth(context: Context, bgAlertsEnabled: Boolean): PermissionHealth {
    val granted = { perm: String ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
    return PermissionHealthPolicy.evaluate(
        sdkInt = Build.VERSION.SDK_INT,
        bleScanGranted = granted(Manifest.permission.BLUETOOTH_SCAN),
        bleConnectGranted = granted(Manifest.permission.BLUETOOTH_CONNECT),
        fineLocationGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocationGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        bgAlertsEnabled = bgAlertsEnabled,
        notificationsGranted = granted(Manifest.permission.POST_NOTIFICATIONS)
    )
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

    val spanish = AppUiPrefs.isSpanish(appLanguage)
    val summary = PermissionHealthPolicy.summary(health, spanish)
    val why = PermissionHealthPolicy.why(health, spanish)

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
                contentDescription = PermissionHealthPolicy.contentDescription(spanish),
                tint = AccentAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = PermissionHealthPolicy.title(spanish),
                    color = TextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = summary, color = TextMuted, fontSize = 11.sp)
                if (why.isNotEmpty()) {
                    Text(text = why, color = TextMuted, fontSize = 10.sp)
                }
            }
        }
        TextButton(onClick = onOpenSettings) {
            Text(
                PermissionHealthPolicy.settingsLabel(spanish),
                color = AccentAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
