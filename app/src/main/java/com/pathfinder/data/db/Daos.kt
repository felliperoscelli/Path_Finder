package com.pathfinder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailDao {
    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM trail_points p WHERE p.trailId = t.id) AS pointCount
        FROM trails t ORDER BY t.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<TrailSummary>>

    @Query("SELECT * FROM trails WHERE id = :id")
    suspend fun getById(id: Long): TrailEntity?

    @Transaction
    @Query("SELECT * FROM trails WHERE id = :id")
    fun observeWithPoints(id: Long): Flow<TrailWithPoints?>

    @Insert
    suspend fun insert(trail: TrailEntity): Long

    @Update
    suspend fun update(trail: TrailEntity)

    @Query("DELETE FROM trails WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TrailPointDao {
    @Query("SELECT * FROM trail_points WHERE trailId = :trailId ORDER BY sequence")
    suspend fun getForTrail(trailId: Long): List<TrailPointEntity>

    @Query("SELECT * FROM trail_points WHERE id = :id")
    suspend fun getById(id: Long): TrailPointEntity?

    @Insert
    suspend fun insert(point: TrailPointEntity): Long

    @Update
    suspend fun update(points: List<TrailPointEntity>)

    @Delete
    suspend fun delete(point: TrailPointEntity)
}

@Dao
interface TrackPointDao {
    @Query("SELECT * FROM track_points WHERE trailId = :trailId AND source = :source ORDER BY timestamp")
    fun observeTrack(trailId: Long, source: TrackSource): Flow<List<TrackPointEntity>>

    @Insert
    suspend fun insert(point: TrackPointEntity): Long

    @Query("DELETE FROM track_points WHERE trailId = :trailId AND source = :source")
    suspend fun clear(trailId: Long, source: TrackSource)
}

@Dao
interface OfflineAreaDao {
    @Query("SELECT * FROM offline_areas ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OfflineAreaEntity>>

    @Insert
    suspend fun insert(area: OfflineAreaEntity): Long

    @Delete
    suspend fun delete(area: OfflineAreaEntity)
}
