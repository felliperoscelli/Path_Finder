package com.pathfinder.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Permissões que a Nearby Connections exige em cada versão do Android. */
private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

/** As obrigatórias e, se possível, notificações (opcional: avisos com a tela desligada). */
fun nearbyPermissions(): Array<String> = buildList {
    addAll(requiredPermissions())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.hasNearbyPermissions() = requiredPermissions()
    .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

class NearbyPermissionState(val granted: Boolean, val request: () -> Unit)

/** Pede as permissões da rede local; [onGranted] roda quando todas foram concedidas. */
@Composable
fun rememberNearbyPermissions(onGranted: () -> Unit): NearbyPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasNearbyPermissions()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasNearbyPermissions()
        if (granted) onGranted()
    }
    return NearbyPermissionState(granted) { launcher.launch(nearbyPermissions()) }
}
