package com.pathfinder.net

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.data.settings.UserRole
import com.pathfinder.navigation.AlertPlayer
import com.pathfinder.recording.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Liga a rede em malha ao app: trata o que chega (importa trilhas, atualiza o
 * painel de monitores e, no coordenador, toca o alarme quando um grupo sai
 * da rota) e envia as mensagens deste aparelho.
 */
class NetworkHub(
    private val context: Context,
    val nearby: NearbyManager,
    val mesh: MeshCore,
    private val trails: TrailRepository,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _board = MutableStateFlow(BoardState())
    val board: StateFlow<BoardState> = _board.asStateFlow()

    private val alerts = AlertPlayer(context)
    private var role = UserRole.COORDINATOR
    /** Desvios (aparelho, início) cujo alarme o coordenador silenciou. */
    private val silenced = mutableSetOf<Pair<String, Long>>()

    init {
        scope.launch {
            settings.settings.collect {
                role = it.role
                mesh.localName = it.userName
            }
        }
        scope.launch { mesh.delivered.collect(::handle) }
        scope.launch {
            nearby.events.collect { e ->
                when (e) {
                    is TransportEvent.Connected -> mesh.onPeerConnected(e.endpointId, e.deviceId)
                    is TransportEvent.Disconnected -> mesh.onPeerDisconnected(e.endpointId)
                    is TransportEvent.Bytes -> mesh.onReceived(e.endpointId, e.deviceId, e.bytes)
                }
            }
        }
        scope.launch {
            while (true) {
                mesh.prune()
                delay(PRUNE_INTERVAL_MS)
            }
        }
    }

    /**
     * Envia uma mensagem deste aparelho pela malha. Se não houver ninguém
     * conectado, ela fica guardada e sai quando alguém se conectar.
     */
    fun publish(body: Body, priority: Int) {
        scope.launch { mesh.originate(body, priority) }
    }

    /**
     * Envia a trilha a todos; retorna quantos aparelhos estão conectados agora
     * (os outros recebem quando se conectarem, direto ou por repasse).
     */
    suspend fun shareTrail(trailId: Long): Int {
        val pkg = trails.exportPackage(trailId) ?: return 0
        mesh.originate(TrailShare(pkg), Priority.NORMAL)
        return nearby.state.value.connected.size
    }

    /** Silencia o alarme dos desvios ativos neste momento. */
    fun silenceAlerts() {
        _board.value.activeAlerts.forEach { silenced += it.deviceId to it.since }
        alerts.stop()
    }

    private suspend fun handle(envelope: Envelope) {
        when (val body = envelope.body) {
            is TrailShare -> if (role != UserRole.REPEATER) importTrail(body, envelope.originName)
            else -> {
                _board.update { MonitorBoard.reduce(it, envelope) }
                if (role == UserRole.COORDINATOR) alertCoordinator(envelope.origin, body)
            }
        }
    }

    private fun alertCoordinator(device: String, body: Body) {
        when (body) {
            is DeviationAlert -> {
                if ((device to body.since) !in silenced) alerts.alarm()
                notify(
                    notificationId(device),
                    Notifications.remoteAlert(
                        context,
                        "${body.groupName} fora da rota!",
                        "${body.monitorName} · ${(body.time - body.since) / 60_000} min, " +
                            "${body.distanceMeters.toInt()} m da rota · ${body.trailName}",
                    ),
                )
            }
            is BackOnRoute -> {
                silenced.removeAll { it.first == device }
                cancel(notificationId(device))
                if (_board.value.activeAlerts.isEmpty()) alerts.stop()
            }
            else -> Unit
        }
    }

    private suspend fun importTrail(share: TrailShare, from: String) {
        try {
            val result = trails.importPackage(share.trail)
            notify(
                Notifications.ID_TRAIL_RECEIVED,
                Notifications.trailReceived(
                    context, result.trailId,
                    if (result.replaced) "Trilha atualizada: ${share.trail.name}" else "Trilha recebida: ${share.trail.name}",
                    "Enviada por $from · ${share.trail.points.size} pontos",
                ),
            )
        } catch (e: Exception) {
            Log.w("NetworkHub", "Falha ao importar trilha", e)
        }
    }

    private companion object {
        const val PRUNE_INTERVAL_MS = 5 * 60_000L
    }

    private fun notificationId(device: String) = Notifications.ID_REMOTE_ALERT_BASE + (device.hashCode() and 0xFFFF)

    private fun notify(id: Int, n: android.app.Notification) =
        context.getSystemService(NotificationManager::class.java).notify(id, n)

    private fun cancel(id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)
}
