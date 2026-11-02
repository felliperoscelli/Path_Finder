package com.pathfinder.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue

/** Envio de bytes para uma conexão direta (Nearby Connections, ou falso nos testes). */
fun interface MeshTransport {
    /** Retorna quando o envio terminou; false se falhou. */
    suspend fun send(endpointId: String, bytes: ByteArray): Boolean
}

data class MeshStats(
    val stored: Int = 0,
    val originated: Int = 0,
    val received: Int = 0,
    val relayed: Int = 0,
    val sent: Int = 0,
)

/**
 * Rede em malha sobre conexões diretas:
 *
 * - cada mensagem nova recebida é entregue a este aparelho e repassada às
 *   outras conexões, com o contador de saltos +1 (até [MeshPolicy.MAX_HOPS]);
 * - cópias repetidas (mesmo id) são descartadas, o que evita loops;
 * - mensagens são guardadas até vencer e enviadas a cada aparelho que se
 *   conectar e ainda não as tiver ("armazenar e encaminhar");
 * - cada conexão tem sua fila: alertas saem antes de mensagens normais, e
 *   estas antes das posições;
 * - posições antigas são substituídas pela mais nova do mesmo monitor.
 */
class MeshCore(
    private val myDeviceId: String,
    private val transport: MeshTransport,
    private val store: MessageStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxStored: Int = 2_000,
) {
    /** Nome deste aparelho nas mensagens criadas aqui. */
    @Volatile var localName: String = ""

    private val _delivered = MutableSharedFlow<Envelope>(extraBufferCapacity = 256)
    /** Mensagens para este aparelho (novas, sem cópias). */
    val delivered: SharedFlow<Envelope> = _delivered.asSharedFlow()

    private val _stats = MutableStateFlow(MeshStats())
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    private val lock = Mutex()
    private val seen = object : LinkedHashMap<String, Unit>(512, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 5_000
    }
    private val peers = HashMap<String, PeerQueue>() // endpointId → fila

    val connectedDevices: Int get() = synchronized(peers) { peers.size }

    /** Cria uma mensagem deste aparelho, guarda e envia a quem estiver conectado. */
    suspend fun originate(body: Body, priority: Int, to: String? = null): Envelope {
        val envelope = Envelope(
            origin = myDeviceId,
            originName = localName,
            createdAt = clock(),
            priority = priority,
            to = to,
            body = body,
        )
        lock.withLock { seen[envelope.id] = Unit }
        _stats.update { it.copy(originated = it.originated + 1) }
        storeAndSpread(envelope, Wire.encode(envelope), deliveredTo = setOf(myDeviceId), except = null)
        return envelope
    }

    /** Bytes recebidos da conexão [endpointId] (aparelho [fromDevice]). */
    suspend fun onReceived(endpointId: String, fromDevice: String, bytes: ByteArray) {
        val envelope = try {
            Wire.decode(bytes)
        } catch (_: Exception) {
            return
        }
        val isNew = lock.withLock {
            if (envelope.id in seen) false else { seen[envelope.id] = Unit; true }
        } && !store.contains(envelope.id)
        if (!isNew) {
            // O remetente já tem a mensagem: não precisa reenviar para ele.
            store.markDelivered(envelope.id, fromDevice)
            return
        }
        if (envelope.origin == myDeviceId) return
        _stats.update { it.copy(received = it.received + 1) }

        if (envelope.to == null || envelope.to == myDeviceId) _delivered.emit(envelope)
        if (envelope.to == myDeviceId) return // chegou ao destino: não repassa

        if (envelope.hops + 1 > MeshPolicy.MAX_HOPS) return
        val now = clock()
        if (MeshPolicy.expiresAt(envelope, now) <= now) return
        val forwarded = envelope.copy(hops = envelope.hops + 1)
        val relayed = storeAndSpread(
            forwarded,
            Wire.encode(forwarded),
            deliveredTo = setOf(myDeviceId, envelope.origin, fromDevice),
            except = endpointId,
        )
        if (relayed) _stats.update { it.copy(relayed = it.relayed + 1) }
    }

    /** Nova conexão: envia tudo o que está guardado e o aparelho ainda não tem. */
    suspend fun onPeerConnected(endpointId: String, deviceId: String) {
        val queue = synchronized(peers) {
            peers.remove(endpointId)?.close()
            PeerQueue(endpointId, deviceId).also { peers[endpointId] = it }
        }
        store.pendingFor(deviceId, clock()).forEach(queue::offer)
    }

    fun onPeerDisconnected(endpointId: String) {
        synchronized(peers) { peers.remove(endpointId) }?.close()
    }

    /** Apaga mensagens vencidas e limita o tamanho do armazenamento. */
    suspend fun prune() {
        store.prune(clock(), maxStored)
        refreshStoredCount()
    }

    /**
     * Guarda a mensagem (substituindo versões mais antigas da mesma chave) e a
     * coloca na fila das conexões que ainda não a têm. Retorna false se a
     * mensagem era mais antiga que uma já guardada (e foi descartada).
     */
    private suspend fun storeAndSpread(envelope: Envelope, bytes: ByteArray, deliveredTo: Set<String>, except: String?): Boolean {
        val now = clock()
        val key = MeshPolicy.supersedeKey(envelope)
        if (key != null) {
            val existing = store.bySupersedeKey(key)
            if (existing.any { it.createdAt > envelope.createdAt }) return false
            store.delete(existing.map { it.id })
        }
        val message = StoredMessage(
            id = envelope.id,
            origin = envelope.origin,
            priority = envelope.priority,
            createdAt = envelope.createdAt,
            expiresAt = MeshPolicy.expiresAt(envelope, now),
            supersedeKey = key,
            bytes = bytes,
            deliveredTo = deliveredTo,
        )
        store.put(message)
        refreshStoredCount()
        val targets = synchronized(peers) { peers.values.filter { it.endpointId != except && it.deviceId !in deliveredTo } }
        targets.forEach { it.offer(message) }
        return true
    }

    private suspend fun refreshStoredCount() {
        val n = store.count()
        _stats.update { it.copy(stored = n) }
    }

    /** Fila de envio de uma conexão, por prioridade e depois por ordem de criação. */
    private inner class PeerQueue(val endpointId: String, val deviceId: String) {
        private val pending = PriorityQueue(compareBy<StoredMessage>({ it.priority }, { it.createdAt }))
        private val queuedIds = HashSet<String>()
        private val signal = Channel<Unit>(Channel.CONFLATED)
        private val worker: Job = scope.launch { for (u in signal) drain() }

        fun offer(message: StoredMessage) {
            synchronized(this) {
                if (!queuedIds.add(message.id)) return
                pending.add(message)
            }
            signal.trySend(Unit)
        }

        private suspend fun drain() {
            while (true) {
                val next = synchronized(this) { pending.poll()?.also { queuedIds.remove(it.id) } } ?: return
                if (next.expiresAt <= clock()) continue
                if (transport.send(endpointId, next.bytes)) {
                    store.markDelivered(next.id, deviceId)
                    _stats.update { it.copy(sent = it.sent + 1) }
                }
                // Se falhar, a mensagem continua guardada e vai na próxima conexão.
            }
        }

        fun close() {
            signal.close()
            worker.cancel()
        }
    }
}
