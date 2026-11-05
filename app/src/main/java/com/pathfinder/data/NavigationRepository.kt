package com.pathfinder.data

import androidx.room.withTransaction
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.DeviationEventEntity
import com.pathfinder.data.db.NavigationSessionEntity
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.geo.LatLon
import kotlinx.coroutines.flow.Flow

/** Pontos da trilha, percurso de criação e declinação: o que a navegação precisa. */
data class NavigationRoute(
    val trailUuid: String,
    val trailName: String,
    val points: List<LatLon>,
    val pointNames: List<String>,
    val creationTrack: List<LatLon>,
    val declinationDegrees: Double,
    val deviationDistanceMeters: Int,
    val deviationTimeSeconds: Int,
    val deadlineMinutes: Int?,
    val challengeMode: Boolean,
    /** Base para "voltar à base": a definida na trilha ou o ponto de partida. */
    val base: LatLon?,
)

/** Resumo de um percurso recebido pela rede. */
data class RemoteSummary(
    val groupName: String,
    val monitorName: String,
    val startedAt: Long,
    val endedAt: Long,
    val reachedTimes: Map<Int, Long>,
    val deviationAlerts: Int,
    val offRouteMillis: Long,
)

class NavigationRepository(private val db: AppDatabase) {
    private val dao = db.navigationDao()
    private val trackDao = db.trackPointDao()
    private val pointDao = db.trailPointDao()
    private val trailDao = db.trailDao()

    suspend fun loadRoute(trailId: Long): NavigationRoute? {
        val trail = trailDao.getById(trailId) ?: return null
        val points = pointDao.getForTrail(trailId)
        return NavigationRoute(
            trailUuid = trail.uuid,
            trailName = trail.name,
            points = points.map { LatLon(it.latitude, it.longitude) },
            pointNames = points.map { it.name },
            creationTrack = trackDao.getTrack(trailId, TrackSource.CREATION).map { LatLon(it.latitude, it.longitude) },
            declinationDegrees = trail.declinationDegrees ?: 0.0,
            deviationDistanceMeters = trail.deviationDistanceMeters,
            deviationTimeSeconds = trail.deviationTimeSeconds,
            deadlineMinutes = trail.deadlineMinutes,
            challengeMode = trail.challengeMode,
            base = if (trail.baseLatitude != null && trail.baseLongitude != null) {
                LatLon(trail.baseLatitude, trail.baseLongitude)
            } else {
                points.firstOrNull()?.let { LatLon(it.latitude, it.longitude) }
            },
        )
    }

    suspend fun startSession(trailId: Long, groupName: String, monitorName: String): Long =
        dao.insertSession(
            NavigationSessionEntity(
                trailId = trailId,
                groupName = groupName.trim(),
                monitorName = monitorName.trim(),
                startedAt = System.currentTimeMillis(),
            )
        )

    suspend fun getSession(id: Long) = dao.getSession(id)

    suspend fun getActiveSession() = dao.getActiveSession()

    fun observeSession(id: Long): Flow<NavigationSessionEntity?> = dao.observeSession(id)

    fun observeSessions(trailId: Long): Flow<List<NavigationSessionEntity>> = dao.observeSessions(trailId)

    fun observeTrack(sessionId: Long): Flow<List<TrackPointEntity>> = trackDao.observeSession(sessionId)

    fun observeDeviations(sessionId: Long): Flow<List<DeviationEventEntity>> = dao.observeDeviations(sessionId)

    suspend fun saveProgress(sessionId: Long, nextIndex: Int, reached: Set<Int>, reachedTimes: Map<Int, Long> = emptyMap()) {
        val s = dao.getSession(sessionId) ?: return
        val times = s.reachedTimeMap + reachedTimes.filterKeys { it !in s.reachedTimeMap }
        dao.updateSession(
            s.copy(
                nextPointIndex = nextIndex,
                reachedPoints = reached.sorted().joinToString(","),
                reachedTimes = NavigationSessionEntity.encodeTimes(times.filterKeys { it in reached }),
            )
        )
    }

    /** Encerra a sessão e calcula o tempo total fora da rota. Retorna a sessão final. */
    suspend fun finishSession(sessionId: Long): NavigationSessionEntity? {
        val s = dao.getSession(sessionId) ?: return null
        if (s.endedAt != null) return s
        val now = System.currentTimeMillis()
        val offRoute = dao.deviations(sessionId).sumOf { (it.endedAt ?: now) - it.startedAt }
        return s.copy(endedAt = now, offRouteMillis = offRoute).also { dao.updateSession(it) }
    }

    suspend fun finishedSessions(trailId: Long) = dao.finishedSessions(trailId)

    /** Guarda (ou atualiza) o resumo de um percurso feito em outro aparelho. */
    suspend fun saveRemoteSession(trailId: Long, device: String, summary: RemoteSummary) {
        val existing = dao.findRemote(device, summary.startedAt)
        val entity = NavigationSessionEntity(
            id = existing?.id ?: 0,
            trailId = trailId,
            groupName = summary.groupName,
            monitorName = summary.monitorName,
            startedAt = summary.startedAt,
            endedAt = summary.endedAt,
            nextPointIndex = summary.reachedTimes.keys.maxOrNull()?.plus(1) ?: 0,
            reachedPoints = summary.reachedTimes.keys.sorted().joinToString(","),
            deviationAlerts = summary.deviationAlerts,
            reachedTimes = NavigationSessionEntity.encodeTimes(summary.reachedTimes),
            offRouteMillis = summary.offRouteMillis,
            remoteDevice = device,
        )
        if (existing == null) dao.insertSession(entity) else dao.updateSession(entity)
    }

    suspend fun addTrackPoint(point: TrackPointEntity) {
        trackDao.insert(point)
    }

    /** Registra o alarme de desvio; retorna o id do evento. */
    suspend fun openDeviation(
        sessionId: Long,
        since: Long,
        alarmAt: Long,
        latitude: Double,
        longitude: Double,
        distanceMeters: Double,
    ): Long = db.withTransaction {
        dao.getSession(sessionId)?.let { dao.updateSession(it.copy(deviationAlerts = it.deviationAlerts + 1)) }
        dao.insertDeviation(
            DeviationEventEntity(
                sessionId = sessionId,
                startedAt = since,
                alarmAt = alarmAt,
                latitude = latitude,
                longitude = longitude,
                maxDistanceMeters = distanceMeters,
            )
        )
    }

    suspend fun closeDeviation(eventId: Long, endedAt: Long?, maxDistanceMeters: Double) {
        val e = dao.getDeviation(eventId) ?: return
        dao.updateDeviation(e.copy(endedAt = endedAt, maxDistanceMeters = maxOf(e.maxDistanceMeters, maxDistanceMeters)))
    }

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        trackDao.deleteSession(sessionId)
        dao.deleteSession(sessionId)
    }
}
