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

    @Query("SELECT * FROM trails WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): TrailEntity?

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

    @Insert
    suspend fun insertAll(points: List<TrailPointEntity>)

    @Query("DELETE FROM trail_points WHERE trailId = :trailId")
    suspend fun deleteForTrail(trailId: Long)
}

@Dao
interface TrackPointDao {
    @Query("SELECT * FROM track_points WHERE trailId = :trailId AND source = :source ORDER BY timestamp")
    fun observeTrack(trailId: Long, source: TrackSource): Flow<List<TrackPointEntity>>

    @Insert
    suspend fun insert(point: TrackPointEntity): Long

    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp")
    fun observeSession(sessionId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE trailId = :trailId AND source = :source ORDER BY timestamp")
    suspend fun getTrack(trailId: Long, source: TrackSource): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

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

@Dao
interface NavigationDao {
    @Insert
    suspend fun insertSession(session: NavigationSessionEntity): Long

    @Update
    suspend fun updateSession(session: NavigationSessionEntity)

    @Query("SELECT * FROM navigation_sessions WHERE id = :id")
    suspend fun getSession(id: Long): NavigationSessionEntity?

    @Query("SELECT * FROM navigation_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<NavigationSessionEntity?>

    @Query("SELECT * FROM navigation_sessions WHERE trailId = :trailId ORDER BY startedAt DESC")
    fun observeSessions(trailId: Long): Flow<List<NavigationSessionEntity>>

    @Query("SELECT * FROM navigation_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): NavigationSessionEntity?

    @Query("DELETE FROM navigation_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertDeviation(event: DeviationEventEntity): Long

    @Update
    suspend fun updateDeviation(event: DeviationEventEntity)

    @Query("SELECT * FROM deviation_events WHERE id = :id")
    suspend fun getDeviation(id: Long): DeviationEventEntity?

    @Query("SELECT * FROM deviation_events WHERE sessionId = :sessionId ORDER BY startedAt")
    fun observeDeviations(sessionId: Long): Flow<List<DeviationEventEntity>>
}
