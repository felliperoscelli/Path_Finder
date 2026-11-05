package com.pathfinder.data

import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(db: AppDatabase) {
    private val dao = db.chatDao()

    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()
    fun observeUnread(): Flow<Int> = dao.observeUnread()
    fun observeLatestUnread(): Flow<ChatMessageEntity?> = dao.observeLatestUnread()

    suspend fun save(message: ChatMessageEntity) = dao.insert(message)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun clear() = dao.clear()
}
