package com.pathfinder.data

import androidx.room.withTransaction
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.DeclinationProvider
import com.pathfinder.geo.TrailCalculator
import kotlinx.coroutines.flow.Flow

/** Dados de um novo ponto, antes dos cálculos. */
data class NewPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val name: String,
    val notes: String,
    val photoPath: String?,
)

class TrailRepository(
    private val db: AppDatabase,
    private val declination: DeclinationProvider,
) {
    private val trailDao = db.trailDao()
    private val pointDao = db.trailPointDao()
    private val trackDao = db.trackPointDao()

    fun observeTrails(): Flow<List<TrailSummary>> = trailDao.observeSummaries()

    fun observeTrail(id: Long): Flow<TrailWithPoints?> = trailDao.observeWithPoints(id)

    suspend fun getTrail(id: Long): TrailEntity? = trailDao.getById(id)

    suspend fun createTrail(name: String, deviationDistanceMeters: Int, deviationTimeSeconds: Int): Long {
        val now = System.currentTimeMillis()
        return trailDao.insert(
            TrailEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                deviationDistanceMeters = deviationDistanceMeters,
                deviationTimeSeconds = deviationTimeSeconds,
            )
        )
    }

    suspend fun deleteTrail(id: Long) = trailDao.deleteById(id)

    /**
     * Declinação usada pela trilha. Fica fixa depois do primeiro ponto, para que
     * todos os azimutes magnéticos (e a ficha impressa) usem o mesmo valor.
     */
    fun declinationFor(trail: TrailEntity, latitude: Double, longitude: Double, altitude: Double?): Double =
        trail.declinationDegrees
            ?: declination.declinationAt(latitude, longitude, altitude ?: 0.0, System.currentTimeMillis())

    /** Acrescenta um ponto ao final da trilha e recalcula azimutes e distâncias. */
    suspend fun addPoint(trailId: Long, point: NewPoint): Long = db.withTransaction {
        val trail = requireNotNull(trailDao.getById(trailId)) { "Trilha $trailId não existe" }
        val now = System.currentTimeMillis()
        val decl = declinationFor(trail, point.latitude, point.longitude, point.altitude)
        val id = pointDao.insert(
            TrailPointEntity(
                trailId = trailId,
                sequence = pointDao.maxSequence(trailId) + 1,
                name = point.name.trim(),
                notes = point.notes.trim(),
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                photoPath = point.photoPath,
                createdAt = now,
            )
        )
        trailDao.update(trail.copy(declinationDegrees = decl, updatedAt = now))
        pointDao.update(TrailCalculator.recalculate(pointDao.getForTrail(trailId), decl))
        id
    }

    fun observeTrack(trailId: Long, source: TrackSource): Flow<List<TrackPointEntity>> =
        trackDao.observeTrack(trailId, source)

    suspend fun addTrackPoint(point: TrackPointEntity) {
        trackDao.insert(point)
    }
}
