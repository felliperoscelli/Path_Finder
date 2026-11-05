package com.pathfinder.net

/** Mensagem guardada para repassar a aparelhos que ainda não a receberam. */
class StoredMessage(
    val id: String,
    val origin: String,
    val priority: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val supersedeKey: String?,
    /** Envelope já codificado, como deve ser enviado (com o contador de saltos atualizado). */
    val bytes: ByteArray,
    /** deviceIds que já têm a mensagem. */
    val deliveredTo: Set<String>,
)

/** Armazenamento das mensagens da malha ("armazenar e encaminhar"). */
interface MessageStore {
    suspend fun contains(id: String): Boolean
    suspend fun put(message: StoredMessage)
    suspend fun bySupersedeKey(key: String): List<StoredMessage>
    suspend fun delete(ids: List<String>)
    /** Mensagens válidas que [deviceId] ainda não recebeu. */
    suspend fun pendingFor(deviceId: String, now: Long): List<StoredMessage>
    suspend fun markDelivered(id: String, deviceId: String)
    /** Apaga as vencidas e, acima de [maxCount], as menos importantes e mais antigas. */
    suspend fun prune(now: Long, maxCount: Int)
    suspend fun count(): Int
}

/** Implementação em memória (testes). */
class InMemoryMessageStore : MessageStore {
    private val items = LinkedHashMap<String, StoredMessage>()

    override suspend fun contains(id: String) = synchronized(items) { id in items }

    override suspend fun put(message: StoredMessage) {
        synchronized(items) { items[message.id] = message }
    }

    override suspend fun bySupersedeKey(key: String) = synchronized(items) { items.values.filter { it.supersedeKey == key } }

    override suspend fun delete(ids: List<String>) {
        synchronized(items) { ids.forEach(items::remove) }
    }

    override suspend fun pendingFor(deviceId: String, now: Long) = synchronized(items) {
        items.values.filter { it.expiresAt > now && deviceId !in it.deliveredTo }
    }

    override suspend fun markDelivered(id: String, deviceId: String) {
        synchronized(items) {
            items[id]?.let { m ->
                items[id] = StoredMessage(m.id, m.origin, m.priority, m.createdAt, m.expiresAt, m.supersedeKey, m.bytes, m.deliveredTo + deviceId)
            }
        }
    }

    override suspend fun prune(now: Long, maxCount: Int) {
        synchronized(items) {
            items.values.removeAll { it.expiresAt <= now }
            val excess = items.size - maxCount
            if (excess > 0) {
                items.values.sortedWith(compareByDescending<StoredMessage> { it.priority }.thenBy { it.createdAt })
                    .take(excess).forEach { items.remove(it.id) }
            }
        }
    }

    override suspend fun count() = synchronized(items) { items.size }
}
