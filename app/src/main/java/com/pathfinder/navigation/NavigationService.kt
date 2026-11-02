package com.pathfinder.navigation

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
import com.pathfinder.data.NavigationRoute
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.geo.Formats
import com.pathfinder.net.BackOnRoute
import com.pathfinder.geo.TimeOfDay
import com.pathfinder.net.DeviationAlert
import com.pathfinder.net.NavigationEnded
import com.pathfinder.net.PointReached
import com.pathfinder.net.PositionUpdate
import com.pathfinder.net.Priority as MessagePriority
import com.pathfinder.recording.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano da navegação do monitor: acompanha o GPS, avisa
 * ao alcançar cada ponto, dispara o alarme de desvio (som + vibração) e grava
 * o trajeto real do grupo, mesmo com a tela desligada.
 */
class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as PathFinderApp).container }
    private val repo by lazy { container.navigationRepository }
    private val hub by lazy { container.networkHub }
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var alerts: AlertPlayer

    private var sessionId = NO_SESSION
    private var trailId = NO_SESSION
    private var route: NavigationRoute? = null
    private var engine: NavigationEngine? = null
    private var openDeviationId: Long? = null
    private var lastSaved: Location? = null
    private var groupName = ""
    private var monitorName = ""
    private var positionIntervalMs = 30_000L
    private var lastPublishedAt = 0L
    private var lastLocation: Location? = null
    private var startedAt = 0L
    private val reachedTimes = mutableMapOf<Int, Long>()
    private var deadline: DeadlineTracker? = null
    private var challengeMode = false
    private var ticker: kotlinx.coroutines.Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        alerts = AlertPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when {
            action == ACTION_START -> start(intent.getLongExtra(EXTRA_SESSION_ID, NO_SESSION))
            action == ACTION_STOP -> finish()
            // Reinício pelo sistema (processo encerrado): o último comando é reenviado
            // sem a sessão carregada. Retoma a sessão ativa do banco e ignora o comando.
            engine == null -> resumeActiveSession()
            action == ACTION_SKIP -> engine?.let { it.skip(); onProgressChanged() }
            action == ACTION_BACK -> engine?.let { it.back(); onProgressChanged() }
            action == ACTION_SILENCE -> engine?.let {
                it.silence()
                alerts.stop()
                publish()
            }
            action == ACTION_RETURN_BASE -> returnToBase(intent.getStringExtra(EXTRA_REASON))
            action == ACTION_RESUME_TRAIL -> engine?.let {
                it.stopReturn()
                cancel(Notifications.ID_DEADLINE)
                publish()
                lastLocation?.let(::publishPosition)
                updateNotification()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun resumeActiveSession() {
        goForeground("Navegação", "Retomando…")
        if (sessionId != NO_SESSION) return // já carregando
        scope.launch {
            val active = repo.getActiveSession()
            if (active != null) start(active.id) else stopEverything()
        }
    }

    private fun start(id: Long) {
        goForeground("Navegação", "Iniciando…")
        if (id == NO_SESSION || !hasLocationPermission()) {
            stopEverything()
            return
        }
        if (id == sessionId) return
        if (sessionId != NO_SESSION) fused.removeLocationUpdates(callback)
        sessionId = id
        scope.launch {
            val session = repo.getSession(id)
            val r = session?.let { repo.loadRoute(it.trailId) }
            if (session == null || session.endedAt != null || r == null || r.points.isEmpty()) {
                stopEverything()
                return@launch
            }
            val settings = container.settingsRepository.current()
            trailId = session.trailId
            groupName = session.groupName
            monitorName = session.monitorName
            positionIntervalMs = settings.positionIntervalSeconds * 1000L
            lastPublishedAt = 0L
            startedAt = session.startedAt
            reachedTimes.clear()
            reachedTimes += session.reachedTimeMap
            challengeMode = r.challengeMode
            deadline = r.deadlineMinutes?.let { DeadlineTracker(TimeOfDay.nextOccurrence(session.startedAt, it)) }
            route = r
            engine = NavigationEngine(
                points = r.points,
                track = r.creationTrack,
                declinationDegrees = r.declinationDegrees,
                config = NavigationConfig(
                    deviationDistanceMeters = r.deviationDistanceMeters.toDouble(),
                    deviationTimeMillis = r.deviationTimeSeconds * 1000L,
                    arrivalRadiusMeters = settings.arrivalRadiusMeters.toDouble(),
                ),
                startIndex = session.nextPointIndex,
                reached = session.reachedSet,
            )
            NavigationRuntime.update {
                NavigationState(
                    sessionId = id,
                    trailId = trailId,
                    groupName = session.groupName,
                    status = engine?.status,
                    challengeMode = challengeMode,
                    deadlineAt = deadline?.deadlineAt,
                    batteryPercent = batteryPercent(),
                )
            }
            requestUpdates()
            updateNotification()
            // Hora limite e bateria são conferidas mesmo sem leitura de GPS.
            ticker?.cancel()
            ticker = scope.launch {
                while (true) {
                    tick(System.currentTimeMillis())
                    kotlinx.coroutines.delay(TICK_MS)
                }
            }
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
        val e = engine ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val events = e.onFix(location.latitude, location.longitude, accuracy, location.time)
        NavigationRuntime.update { it.copy(status = e.status, lastFix = location) }
        saveTrack(location, accuracy)
        lastLocation = location
        events.forEach { handle(it, location) }
        if (location.time - lastPublishedAt >= positionIntervalMs) publishPosition(location)
        updateNotification()
    }

    private fun handle(event: NavigationEvent, location: Location) {
        val id = sessionId
        when (event) {
            is NavigationEvent.PointReached -> {
                alerts.chime()
                reachedTimes[event.index] = location.time
                onProgressChanged()
                route?.let { r ->
                    hub.publish(
                        PointReached(location.time, event.index, r.pointNames[event.index], r.points.size, r.trailName, groupName, monitorName),
                        MessagePriority.NORMAL,
                    )
                }
            }
            NavigationEvent.Finished -> Unit // o aviso de chegada já foi dado
            NavigationEvent.ArrivedAtBase -> {
                alerts.chime()
                notify(Notifications.ID_DEADLINE, Notifications.deadline(this, trailId, "Chegaram à base", "Encerre a navegação quando todos estiverem reunidos.", withReturnAction = false))
                publishPosition(location)
            }
            is NavigationEvent.DeviationAlarm -> {
                alerts.alarm()
                if (!event.repeat) {
                    scope.launch {
                        openDeviationId = repo.openDeviation(
                            id, event.since, location.time, event.latitude, event.longitude, event.distanceMeters,
                        )
                    }
                }
                val minutes = (location.time - event.since) / 60_000
                notify(
                    Notifications.ID_DEVIATION,
                    Notifications.deviation(
                        this, trailId,
                        "Há $minutes min a ${Formats.distance(event.distanceMeters)} da rota. Volte para a trilha.",
                    ),
                )
                hub.publish(
                    DeviationAlert(
                        event.latitude, event.longitude, location.time, event.since, event.distanceMeters,
                        route?.trailName.orEmpty(), groupName, monitorName, event.repeat,
                    ),
                    MessagePriority.ALERT,
                )
            }
            is NavigationEvent.BackOnRoute -> {
                alerts.stop()
                cancel(Notifications.ID_DEVIATION)
                val eventId = openDeviationId
                openDeviationId = null
                if (eventId != null) {
                    scope.launch { repo.closeDeviation(eventId, location.time, event.maxDistanceMeters) }
                }
                hub.publish(BackOnRoute(location.time, event.since, event.maxDistanceMeters, groupName, monitorName), MessagePriority.ALERT)
                publishPosition(location)
            }
        }
    }

    private fun onProgressChanged() {
        val e = engine ?: return
        val s = e.status
        val times = reachedTimes.toMap()
        scope.launch { repo.saveProgress(sessionId, s.nextIndex, s.reached, times) }
        publish()
        lastLocation?.let(::publishPosition)
        updateNotification()
    }

    /** Envia a posição e o progresso ao coordenador pela rede local. */
    private fun publishPosition(location: Location) {
        val r = route ?: return
        val s = engine?.status ?: return
        lastPublishedAt = location.time
        val lastReached = s.reached.maxOrNull()
        hub.publish(
            PositionUpdate(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                time = location.time,
                trailUuid = r.trailUuid,
                trailName = r.trailName,
                groupName = groupName,
                monitorName = monitorName,
                nextIndex = s.nextIndex,
                totalPoints = r.points.size,
                lastReachedName = lastReached?.let { r.pointNames.getOrNull(it) },
                distanceToRouteMeters = s.distanceToRouteMeters,
                offRouteSince = s.offRouteSince,
                alarm = s.alarmActive,
                finished = s.finished,
                batteryPercent = batteryPercent(),
                deadlineAt = deadline?.deadlineAt,
                returningToBase = s.returningToBase,
            ),
            MessagePriority.POSITION,
        )
    }

    /** Voltar à base: pelo botão do monitor, pela hora limite ou a pedido do coordenador. */
    private fun returnToBase(reason: String?) {
        val e = engine ?: return
        val base = route?.base ?: return
        e.startReturn(base)
        publish()
        if (reason != null) {
            alerts.alarm()
            notify(Notifications.ID_DEADLINE, Notifications.deadline(this, trailId, "Voltar à base", reason, withReturnAction = false))
        }
        lastLocation?.let(::publishPosition)
        updateNotification()
    }

    private fun tick(now: Long) {
        NavigationRuntime.update { it.copy(batteryPercent = batteryPercent()) }
        val d = deadline ?: return
        when (d.check(now)) {
            DeadlineEvent.WARNING -> {
                alerts.chime()
                notify(
                    Notifications.ID_DEADLINE,
                    Notifications.deadline(
                        this, trailId, "Faltam ${d.minutesLeft(now)} min para a hora limite",
                        "Hora limite: ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(d.deadlineAt))}. Planejem a volta.",
                        withReturnAction = true,
                    ),
                )
            }
            DeadlineEvent.REACHED -> {
                alerts.alarm()
                NavigationRuntime.update { it.copy(deadlineReached = true) }
                notify(
                    Notifications.ID_DEADLINE,
                    Notifications.deadline(this, trailId, "Hora limite!", "Voltem à base agora.", withReturnAction = true),
                )
            }
            null -> Unit
        }
    }

    private fun batteryPercent(): Int? =
        getSystemService(android.os.BatteryManager::class.java)
            ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

    private fun publish() {
        val e = engine ?: return
        NavigationRuntime.update { it.copy(status = e.status) }
    }

    /** Uma amostra a cada [MIN_TRACK_DISTANCE_M] ou [MAX_TRACK_GAP_MS]. */
    private fun saveTrack(location: Location, accuracy: Float?) {
        if (accuracy != null && accuracy > MAX_TRACK_ACCURACY_M) return
        val last = lastSaved
        if (last != null && location.distanceTo(last) < MIN_TRACK_DISTANCE_M && location.time - last.time < MAX_TRACK_GAP_MS) return
        lastSaved = location
        val point = TrackPointEntity(
            trailId = trailId,
            source = TrackSource.NAVIGATION,
            sessionId = sessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = accuracy,
            timestamp = location.time,
        )
        scope.launch { repo.addTrackPoint(point) }
    }

    private fun updateNotification() {
        val r = route ?: return
        val s = engine?.status ?: return
        val detail = when {
            s.returningToBase -> if (s.arrivedAtBase) "Na base" else
                "Voltando à base: ${s.distanceToNextMeters?.let(Formats::distance) ?: "…"}" +
                    (s.magneticBearingToNext?.let { " · az. mag. ${Formats.azimuth(it)}" } ?: "")
            s.finished -> "Pista concluída!"
            challengeMode -> "Próximo: ${s.nextIndex + 1}. ${r.pointNames.getOrNull(s.nextIndex).orEmpty()} (modo desafio)"
            else -> {
                val name = r.pointNames.getOrNull(s.nextIndex) ?: ""
                val dist = s.distanceToNextMeters?.let { " a ${Formats.distance(it)}" } ?: ""
                val az = s.magneticBearingToNext?.let { " · az. mag. ${Formats.azimuth(it)}" } ?: ""
                "Próximo: ${s.nextIndex + 1}. $name$dist$az"
            }
        }
        notify(Notifications.ID_NAVIGATION, Notifications.navigation(this, trailId, "Navegando \"${r.trailName}\"", detail))
    }

    private fun finish() {
        val id = sessionId
        val eventId = openDeviationId
        val r = route
        scope.launch {
            if (eventId != null) repo.closeDeviation(eventId, null, 0.0)
            val ended = if (id != NO_SESSION) repo.finishSession(id) else null
            // Resumo para o resultado no coordenador.
            if (ended != null && r != null) {
                hub.publish(
                    NavigationEnded(
                        time = ended.endedAt ?: System.currentTimeMillis(),
                        trailUuid = r.trailUuid,
                        trailName = r.trailName,
                        groupName = groupName,
                        monitorName = monitorName,
                        startedAt = ended.startedAt,
                        reachedTimes = ended.reachedTimeMap,
                        totalPoints = r.points.size,
                        deviationAlerts = ended.deviationAlerts,
                        offRouteMillis = ended.offRouteMillis,
                    ),
                    MessagePriority.NORMAL,
                )
            }
            stopEverything()
        }
    }

    private fun stopEverything() {
        ticker?.cancel()
        ticker = null
        cancel(Notifications.ID_DEADLINE)
        fused.removeLocationUpdates(callback)
        alerts.stop()
        sessionId = NO_SESSION
        engine = null
        NavigationRuntime.update { NavigationState() }
        cancel(Notifications.ID_DEVIATION)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(title: String, detail: String) {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_NAVIGATION,
            Notifications.navigation(this, trailId.coerceAtLeast(0), title, detail),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
    }

    private fun notify(id: Int, notification: android.app.Notification) =
        getSystemService(NotificationManager::class.java).notify(id, notification)

    private fun cancel(id: Int) = getSystemService(NotificationManager::class.java).cancel(id)

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        alerts.stop()
        NavigationRuntime.update { NavigationState() }
        scope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.pathfinder.navigation.START"
        const val ACTION_STOP = "com.pathfinder.navigation.STOP"
        const val ACTION_SKIP = "com.pathfinder.navigation.SKIP"
        const val ACTION_BACK = "com.pathfinder.navigation.BACK"
        const val ACTION_SILENCE = "com.pathfinder.navigation.SILENCE"
        const val ACTION_RETURN_BASE = "com.pathfinder.navigation.RETURN_BASE"
        const val ACTION_RESUME_TRAIL = "com.pathfinder.navigation.RESUME_TRAIL"
        const val EXTRA_REASON = "reason"
        private const val TICK_MS = 30_000L
        const val EXTRA_SESSION_ID = "sessionId"
        private const val NO_SESSION = -1L
        private const val UPDATE_INTERVAL_MS = 2_000L
        private const val MAX_TRACK_ACCURACY_M = 50f
        private const val MIN_TRACK_DISTANCE_M = 5f
        private const val MAX_TRACK_GAP_MS = 30_000L
    }
}
