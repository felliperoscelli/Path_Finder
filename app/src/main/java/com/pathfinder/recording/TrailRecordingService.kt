package com.pathfinder.recording

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pathfinder.PathFinderApp
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.geo.StopDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Serviço em primeiro plano que grava o percurso pelo GPS (Fused Location
 * Provider) enquanto o coordenador cria a trilha, inclusive com a tela
 * desligada, e avisa quando ele fica parado para sugerir um ponto.
 */
class TrailRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as PathFinderApp).container }
    private lateinit var fused: FusedLocationProviderClient

    private var trailId = NO_TRAIL
    private var trailName = ""
    private var stopSeconds = 0
    private var detector: StopDetector? = null
    private var lastSaved: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_TRAIL_ID, NO_TRAIL))
            ACTION_STOP -> stopRecording()
            else -> if (trailId == NO_TRAIL) stopSelf()
        }
        // Se o sistema matar o processo, o Android recria o serviço com o
        // mesmo pedido e a gravação continua.
        return START_REDELIVER_INTENT
    }

    private fun start(id: Long) {
        // startForeground precisa ser chamado logo, mesmo se formos parar em seguida.
        goForeground(id, "Aguardando GPS…")
        if (id == NO_TRAIL || !hasLocationPermission()) {
            stopRecording()
            return
        }
        if (id == trailId) return
        if (trailId != NO_TRAIL) fused.removeLocationUpdates(callback)

        trailId = id
        lastSaved = null
        RecordingSession.update {
            RecordingState(trailId = id, startedAt = System.currentTimeMillis())
        }
        scope.launch {
            trailName = container.trailRepository.getTrail(id)?.name ?: "Trilha"
            stopSeconds = container.settingsRepository.current().stopDetectionSeconds
            detector = StopDetector(stopSeconds * 1000L)
            goForeground(id, "Aguardando GPS…")
            requestUpdates()
        }
    }

    @Suppress("MissingPermission") // verificado em hasLocationPermission()
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun onLocation(location: Location) {
        val id = trailId.takeIf { it != NO_TRAIL } ?: return
        RecordingSession.update { it.copy(lastFix = location) }
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return

        if (shouldSave(location)) {
            lastSaved = location
            scope.launch {
                container.trailRepository.addTrackPoint(
                    TrackPointEntity(
                        trailId = id,
                        source = TrackSource.CREATION,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        accuracyMeters = accuracy,
                        timestamp = location.time,
                    )
                )
            }
            val count = RecordingSession.state.value.trackPoints + 1
            RecordingSession.update { it.copy(trackPoints = count) }
            val detail = "GPS ±${accuracy?.roundToInt() ?: "?"} m · $count amostras"
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_RECORDING, Notifications.recording(this, id, trailName, detail))
        }

        val stop = detector?.onFix(
            location.latitude,
            location.longitude,
            if (location.hasAltitude()) location.altitude else null,
            accuracy,
            location.time,
        )
        if (stop != null) {
            RecordingSession.update { it.copy(stopSuggestion = stop) }
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_STOP, Notifications.stopDetected(this, id, stopSeconds))
        }
    }

    /** Guarda uma amostra a cada [MIN_TRACK_DISTANCE_M] ou [MAX_TRACK_GAP_MS]. */
    private fun shouldSave(location: Location): Boolean {
        val last = lastSaved ?: return true
        return location.distanceTo(last) >= MIN_TRACK_DISTANCE_M || location.time - last.time >= MAX_TRACK_GAP_MS
    }

    private fun goForeground(id: Long, detail: String) {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RECORDING,
            Notifications.recording(this, id, trailName.ifEmpty { "Trilha" }, detail),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
    }

    private fun stopRecording() {
        fused.removeLocationUpdates(callback)
        trailId = NO_TRAIL
        RecordingSession.update { RecordingState() }
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_STOP)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        RecordingSession.update { RecordingState() }
        scope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.pathfinder.recording.START"
        const val ACTION_STOP = "com.pathfinder.recording.STOP"
        const val EXTRA_TRAIL_ID = "trailId"
        private const val NO_TRAIL = -1L
        private const val UPDATE_INTERVAL_MS = 2_000L
        private const val MAX_ACCURACY_M = 50f
        private const val MIN_TRACK_DISTANCE_M = 5f
        private const val MAX_TRACK_GAP_MS = 30_000L
    }
}
