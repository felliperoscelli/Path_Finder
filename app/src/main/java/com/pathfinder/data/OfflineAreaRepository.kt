package com.pathfinder.data

import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.OfflineAreaEntity
import kotlinx.coroutines.flow.Flow

class OfflineAreaRepository(db: AppDatabase) {
    private val dao = db.offlineAreaDao()

    fun observeAreas(): Flow<List<OfflineAreaEntity>> = dao.observeAll()

    suspend fun save(area: OfflineAreaEntity): Long = dao.insert(area)

    suspend fun delete(area: OfflineAreaEntity) = dao.delete(area)
}
