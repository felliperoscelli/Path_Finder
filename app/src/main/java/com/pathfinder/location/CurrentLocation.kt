package com.pathfinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.recording.RecordingSession
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Posição atual para anexar a mensagens e emergências. */
object CurrentLocation {
    private const val FRESH_MS = 60_000L

    @SuppressLint("MissingPermission") // verificado abaixo
    suspend fun get(context: Context): Location? {
        val now = System.currentTimeMillis()
        // Navegação ou gravação em andamento já têm uma leitura recente.
        listOfNotNull(NavigationRuntime.state.value.lastFix, RecordingSession.state.value.lastFix)
            .firstOrNull { now - it.time < FRESH_MS }?.let { return it }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return runCatching {
            withTimeoutOrNull(10_000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }
                ?: client.lastLocation.await()
        }.getOrNull()
    }
}
