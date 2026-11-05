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
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
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

/** Eventos da camada de transporte, consumidos pela malha ([MeshCore]). */
sealed interface TransportEvent {
    data class Connected(val endpointId: String, val deviceId: String) : TransportEvent
    data class Disconnected(val endpointId: String) : TransportEvent
    class Bytes(val endpointId: String, val deviceId: String, val bytes: ByteArray) : TransportEvent
}

/**
 * Transporte offline entre celulares pela Nearby Connections
 * (estratégia P2P_CLUSTER: Wi-Fi Direct/Bluetooth, vários aparelhos).
 * Cada aparelho anuncia e procura ao mesmo tempo e aceita conexões
 * automaticamente, sem ação do usuário. O repasse e o armazenamento das
 * mensagens ficam na malha ([MeshCore]).
 */
class NearbyManager(
    context: Context,
    private val identity: DeviceIdentity,
    private val scope: CoroutineScope,
) : MeshTransport {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    /** Envios aguardando confirmação, por id do payload. */
    private val inFlight = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    private var localName = ""
    private var localRole = ""

    val deviceId: String get() = identity.deviceId
    val name: String get() = localName

    /** serviceId da Nearby derivado do código do evento: eventos diferentes nem se enxergam. */
    private var serviceId = EventCode.serviceId("")

    /** Código do evento com que a rede está ligada. */
    var eventCode: String = ""
        private set

    fun start(name: String, role: String, event: String) {
        if (_state.value.running) return
        localName = name.ifBlank { "Sem nome" }.take(30).replace(SEP, " ")
        localRole = role
        eventCode = EventCode.normalize(event)
        serviceId = EventCode.serviceId(eventCode)
        _state.value = NetworkState(running = true)
        val endpointName = listOf(localRole, localName, identity.deviceId).joinToString(SEP)
        client.startAdvertising(endpointName, serviceId, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail("Falha ao anunciar", it) }
        client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail("Falha ao procurar aparelhos", it) }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _state.value.connected.forEach { _events.tryEmit(TransportEvent.Disconnected(it.endpointId)) }
        inFlight.values.forEach { it.complete(false) }
        inFlight.clear()
        _state.value = NetworkState()
    }

    /** Envia bytes a uma conexão e espera a confirmação de entrega. */
    override suspend fun send(endpointId: String, bytes: ByteArray): Boolean {
        val payload = if (bytes.size <= ConnectionsClient.MAX_BYTES_DATA_SIZE) {
            Payload.fromBytes(bytes)
        } else {
            Payload.fromStream(ByteArrayInputStream(bytes))
        }
        val done = CompletableDeferred<Boolean>()
        inFlight[payload.id] = done
        client.sendPayload(endpointId, payload).addOnFailureListener {
            Log.w(TAG, "Falha ao enviar para $endpointId", it)
            done.complete(false)
        }
        val timeout = if (payload.type == Payload.Type.STREAM) STREAM_TIMEOUT_MS else BYTES_TIMEOUT_MS
        return try {
            withTimeoutOrNull(timeout) { done.await() } ?: false
        } finally {
            inFlight.remove(payload.id)
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != serviceId) return
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
                _events.tryEmit(TransportEvent.Connected(endpointId, p.deviceId))
            } else {
                putPeer(p.copy(status = PeerStatus.FOUND))
                scope.launch { delay(RETRY_MS); requestIfIdle(endpointId) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val p = _state.value.peers[endpointId] ?: return
            _events.tryEmit(TransportEvent.Disconnected(endpointId))
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

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val ok = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> true
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> false
                else -> return
            }
            inFlight[update.payloadId]?.complete(ok)
        }
    }

    private fun deliver(endpointId: String, bytes: ByteArray) {
        val peer = _state.value.peers[endpointId] ?: return
        _events.tryEmit(TransportEvent.Bytes(endpointId, peer.deviceId, bytes))
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
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val SEP = "|"
        private const val TAG = "NearbyManager"
        private const val BYTES_TIMEOUT_MS = 30_000L
        private const val STREAM_TIMEOUT_MS = 120_000L
        private const val FALLBACK_REQUEST_MS = 8_000L
        private const val RETRY_MS = 5_000L
    }
}
