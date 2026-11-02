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
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE read = 0")
    fun observeUnread(): Flow<Int>

    @Query("SELECT * FROM chat_messages WHERE outgoing = 0 AND read = 0 ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestUnread(): Flow<ChatMessageEntity?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface MeshMessageDao {
    @Query("SELECT COUNT(*) FROM mesh_messages WHERE id = :id")
    suspend fun exists(id: String): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun put(message: MeshMessageEntity)

    @Query("SELECT * FROM mesh_messages WHERE supersedeKey = :key")
    suspend fun bySupersedeKey(key: String): List<MeshMessageEntity>

    @Query("DELETE FROM mesh_messages WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    /** Válidas que ainda não foram entregues ao aparelho (o filtro exato é feito em código). */
    @Query("SELECT * FROM mesh_messages WHERE expiresAt > :now AND deliveredTo NOT LIKE '%' || :deviceId || '%'")
    suspend fun pendingCandidates(deviceId: String, now: Long): List<MeshMessageEntity>

    @Query("SELECT deliveredTo FROM mesh_messages WHERE id = :id")
    suspend fun deliveredTo(id: String): String?

    @Query("UPDATE mesh_messages SET deliveredTo = :deliveredTo WHERE id = :id")
    suspend fun setDeliveredTo(id: String, deliveredTo: String)

    @Query("DELETE FROM mesh_messages WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM mesh_messages")
    suspend fun count(): Int

    /** As [n] menos importantes (maior número de prioridade) e mais antigas. */
    @Query("SELECT id FROM mesh_messages ORDER BY priority DESC, createdAt ASC LIMIT :n")
    suspend fun leastImportant(n: Int): List<String>
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

    @Query("SELECT * FROM navigation_sessions WHERE remoteDevice = :device AND startedAt = :startedAt LIMIT 1")
    suspend fun findRemote(device: String, startedAt: Long): NavigationSessionEntity?

    @Query("SELECT * FROM navigation_sessions WHERE trailId = :trailId AND endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun finishedSessions(trailId: Long): List<NavigationSessionEntity>

    @Query("SELECT * FROM navigation_sessions WHERE endedAt IS NULL AND remoteDevice IS NULL ORDER BY startedAt DESC LIMIT 1")
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

    @Query("SELECT * FROM deviation_events WHERE sessionId = :sessionId")
    suspend fun deviations(sessionId: Long): List<DeviationEventEntity>
}
