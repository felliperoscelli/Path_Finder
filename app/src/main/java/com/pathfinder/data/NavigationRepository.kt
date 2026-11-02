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
    val trailName: String,
    val points: List<LatLon>,
    val pointNames: List<String>,
    val creationTrack: List<LatLon>,
    val declinationDegrees: Double,
    val deviationDistanceMeters: Int,
    val deviationTimeSeconds: Int,
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
            trailName = trail.name,
            points = points.map { LatLon(it.latitude, it.longitude) },
            pointNames = points.map { it.name },
            creationTrack = trackDao.getTrack(trailId, TrackSource.CREATION).map { LatLon(it.latitude, it.longitude) },
            declinationDegrees = trail.declinationDegrees ?: 0.0,
            deviationDistanceMeters = trail.deviationDistanceMeters,
            deviationTimeSeconds = trail.deviationTimeSeconds,
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

    suspend fun saveProgress(sessionId: Long, nextIndex: Int, reached: Set<Int>) {
        val s = dao.getSession(sessionId) ?: return
        dao.updateSession(s.copy(nextPointIndex = nextIndex, reachedPoints = reached.sorted().joinToString(",")))
    }

    suspend fun finishSession(sessionId: Long) {
        val s = dao.getSession(sessionId) ?: return
        if (s.endedAt == null) dao.updateSession(s.copy(endedAt = System.currentTimeMillis()))
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
