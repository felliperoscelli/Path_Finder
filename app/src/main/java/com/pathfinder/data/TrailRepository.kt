package com.pathfinder.data

import androidx.room.withTransaction
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import kotlinx.coroutines.flow.Flow

class TrailRepository(private val db: AppDatabase) {
    private val trailDao = db.trailDao()
    private val pointDao = db.trailPointDao()

    fun observeTrails(): Flow<List<TrailSummary>> = trailDao.observeSummaries()

    fun observeTrail(id: Long): Flow<TrailWithPoints?> = trailDao.observeWithPoints(id)

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

    /** Acrescenta um ponto ao final da trilha. */
    suspend fun appendPoint(point: TrailPointEntity): Long = db.withTransaction {
        val sequence = pointDao.maxSequence(point.trailId) + 1
        val id = pointDao.insert(point.copy(sequence = sequence))
        touch(point.trailId)
        id
    }

    private suspend fun touch(trailId: Long) {
        trailDao.getById(trailId)?.let { trailDao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }
}
