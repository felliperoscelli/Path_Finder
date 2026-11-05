package com.pathfinder.net

import androidx.room.withTransaction
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.MeshMessageEntity

/** Mensagens da malha no banco local: sobrevivem ao fechamento do app. */
class RoomMessageStore(private val db: AppDatabase) : MessageStore {
    private val dao = db.meshMessageDao()

    override suspend fun contains(id: String) = dao.exists(id) > 0

    override suspend fun put(message: StoredMessage) = dao.put(message.toEntity())

    override suspend fun bySupersedeKey(key: String) = dao.bySupersedeKey(key).map { it.toStored() }

    override suspend fun delete(ids: List<String>) {
        if (ids.isNotEmpty()) dao.delete(ids)
    }

    override suspend fun pendingFor(deviceId: String, now: Long) =
        dao.pendingCandidates(deviceId, now).map { it.toStored() }.filter { deviceId !in it.deliveredTo }

    override suspend fun markDelivered(id: String, deviceId: String) = db.withTransaction {
        val current = dao.deliveredTo(id) ?: return@withTransaction
        val set = split(current)
        if (deviceId !in set) dao.setDeliveredTo(id, join(set + deviceId))
    }

    override suspend fun prune(now: Long, maxCount: Int) = db.withTransaction {
        dao.deleteExpired(now)
        val excess = dao.count() - maxCount
        if (excess > 0) dao.delete(dao.leastImportant(excess))
    }

    override suspend fun count() = dao.count()

    private fun StoredMessage.toEntity() =
        MeshMessageEntity(id, origin, priority, createdAt, expiresAt, supersedeKey, bytes, join(deliveredTo))

    private fun MeshMessageEntity.toStored() =
        StoredMessage(id, origin, priority, createdAt, expiresAt, supersedeKey, payload, split(deliveredTo))

    private fun split(s: String) = s.split(',').filter { it.isNotEmpty() }.toSet()
    private fun join(set: Set<String>) = set.joinToString(",")
}
