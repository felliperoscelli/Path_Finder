package com.pathfinder.net

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

enum class PeerStatus { FOUND, CONNECTING, CONNECTED }

/** Aparelho visto pela rede. */
data class Peer(
    val endpointId: String,
    val deviceId: String,
    val name: String,
    val role: String,
    val status: PeerStatus,
)

data class NetworkState(
    val running: Boolean = false,
    val peers: Map<String, Peer> = emptyMap(),
    val error: String? = null,
) {
    val connected: List<Peer> get() = peers.values.filter { it.status == PeerStatus.CONNECTED }
}

/** Mensagem recebida e de qual conexão direta ela veio. */
data class Received(val envelope: Envelope, val fromEndpoint: String)

/**
 * Comunicação offline entre celulares pela Nearby Connections
 * (estratégia P2P_CLUSTER: Wi-Fi Direct/Bluetooth, vários aparelhos).
 * Cada aparelho anuncia e procura ao mesmo tempo e aceita conexões
 * automaticamente, sem ação do usuário.
 */
class NearbyManager(
    context: Context,
    private val identity: DeviceIdentity,
    private val scope: CoroutineScope,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<Received>(extraBufferCapacity = 128)
    val incoming: SharedFlow<Received> = _incoming.asSharedFlow()

    /** IDs de mensagens já vistas, para descartar cópias. */
    private val seen = object : LinkedHashMap<String, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_SEEN
    }

    private var localName = ""
    private var localRole = ""

    val deviceId: String get() = identity.deviceId
    val name: String get() = localName

    fun start(name: String, role: String) {
        if (_state.value.running) return
        localName = name.ifBlank { "Sem nome" }.take(30).replace(SEP, " ")
        localRole = role
        _state.value = NetworkState(running = true)
        val endpointName = listOf(localRole, localName, identity.deviceId).joinToString(SEP)
        client.startAdvertising(endpointName, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail("Falha ao anunciar", it) }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail("Falha ao procurar aparelhos", it) }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _state.value = NetworkState()
    }

    /** Cria e envia uma mensagem nova deste aparelho para todos os conectados. */
    fun broadcast(body: Body, priority: Int, now: Long = System.currentTimeMillis()): Envelope {
        val envelope = Envelope(origin = identity.deviceId, originName = localName, createdAt = now, priority = priority, body = body)
        markSeen(envelope.id)
        send(envelope, _state.value.connected.map { it.endpointId })
        return envelope
    }

    /** Envia uma mensagem (própria ou repassada) para as conexões indicadas. */
    fun send(envelope: Envelope, endpointIds: List<String>) {
        if (endpointIds.isEmpty()) return
        val bytes = Wire.encode(envelope)
        val payload = if (bytes.size <= ConnectionsClient.MAX_BYTES_DATA_SIZE) {
            Payload.fromBytes(bytes)
        } else {
            Payload.fromStream(ByteArrayInputStream(bytes))
        }
        client.sendPayload(endpointIds, payload).addOnFailureListener { Log.w(TAG, "Falha ao enviar", it) }
    }

    private fun markSeen(id: String): Boolean = synchronized(seen) { seen.put(id, Unit) == null }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val peer = parsePeer(endpointId, info.endpointName, PeerStatus.FOUND) ?: return
            if (peer.deviceId == identity.deviceId) return
            val existing = _state.value.peers[endpointId]
            if (existing?.status == PeerStatus.CONNECTED || existing?.status == PeerStatus.CONNECTING) return
            putPeer(peer)
            // Só o aparelho de id menor pede a conexão, para os dois não pedirem ao
            // mesmo tempo; o outro tenta depois de um tempo, caso não seja visto.
            val delayMs = if (identity.deviceId < peer.deviceId) 0L else FALLBACK_REQUEST_MS
            scope.launch { delay(delayMs); requestIfIdle(endpointId) }
        }

        override fun onEndpointLost(endpointId: String) {
            val p = _state.value.peers[endpointId] ?: return
            if (p.status == PeerStatus.FOUND) removePeer(endpointId)
        }
    }

    private fun requestIfIdle(endpointId: String) {
        val p = _state.value.peers[endpointId] ?: return
        if (!_state.value.running || p.status != PeerStatus.FOUND) return
        putPeer(p.copy(status = PeerStatus.CONNECTING))
        val myName = listOf(localRole, localName, identity.deviceId).joinToString(SEP)
        client.requestConnection(myName, endpointId, lifecycle).addOnFailureListener { e ->
            val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
            if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
            _state.value.peers[endpointId]?.let { if (it.status == PeerStatus.CONNECTING) putPeer(it.copy(status = PeerStatus.FOUND)) }
            scope.launch { delay(RETRY_MS); requestIfIdle(endpointId) }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val peer = parsePeer(endpointId, info.endpointName, PeerStatus.CONNECTING)
            if (peer == null || peer.deviceId == identity.deviceId) {
                client.rejectConnection(endpointId)
                return
            }
            putPeer(peer)
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val p = _state.value.peers[endpointId] ?: return
            if (resolution.status.isSuccess) {
                putPeer(p.copy(status = PeerStatus.CONNECTED))
                send(
                    Envelope(origin = identity.deviceId, originName = localName, createdAt = System.currentTimeMillis(), body = Hello(localRole, localName)),
                    listOf(endpointId),
                )
            } else {
                putPeer(p.copy(status = PeerStatus.FOUND))
                scope.launch { delay(RETRY_MS); requestIfIdle(endpointId) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val p = _state.value.peers[endpointId] ?: return
            putPeer(p.copy(status = PeerStatus.FOUND))
            scope.launch { delay(RETRY_MS); requestIfIdle(endpointId) }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { deliver(endpointId, it) }
                Payload.Type.STREAM -> scope.launch(Dispatchers.IO) {
                    val bytes = payload.asStream()?.asInputStream()?.use { it.readBytes() } ?: return@launch
                    deliver(endpointId, bytes)
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun deliver(endpointId: String, bytes: ByteArray) {
        val envelope = try {
            Wire.decode(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Mensagem inválida de $endpointId", e)
            return
        }
        if (envelope.origin == identity.deviceId || !markSeen(envelope.id)) return
        val body = envelope.body
        if (body is Hello) {
            _state.value.peers[endpointId]?.let { putPeer(it.copy(name = body.name, role = body.role)) }
        }
        _incoming.tryEmit(Received(envelope, endpointId))
    }

    private fun parsePeer(endpointId: String, endpointName: String, status: PeerStatus): Peer? {
        val parts = endpointName.split(SEP)
        if (parts.size != 3) return null
        return Peer(endpointId, deviceId = parts[2], name = parts[1], role = parts[0], status = status)
    }

    private fun putPeer(peer: Peer) = _state.update { it.copy(peers = it.peers + (peer.endpointId to peer)) }

    private fun removePeer(endpointId: String) = _state.update { it.copy(peers = it.peers - endpointId) }

    private fun fail(message: String, e: Exception) {
        Log.w(TAG, message, e)
        _state.update { it.copy(error = "$message: ${e.message}") }
    }

    companion object {
        const val SERVICE_ID = "com.pathfinder.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val SEP = "|"
        private const val TAG = "NearbyManager"
        private const val MAX_SEEN = 2_000
        private const val FALLBACK_REQUEST_MS = 8_000L
        private const val RETRY_MS = 5_000L
    }
}
