package com.pathfinder.net

import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.util.Log
import com.pathfinder.data.ChatRepository
import com.pathfinder.data.NavigationRepository
import com.pathfinder.data.RemoteSummary
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.ChatMessageEntity
import com.pathfinder.data.settings.AppSettings
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.data.settings.UserRole
import com.pathfinder.diagnostics.Diagnostics
import com.pathfinder.navigation.AlertPlayer
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.recording.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Alguém para quem dá para mandar mensagem individual. */
data class Contact(
    val deviceId: String,
    val name: String,
    val role: String?,
    val groupName: String?,
) {
    val label: String
        get() = buildString {
            append(groupName?.let { "$it · $name" } ?: name)
            UserRole.entries.firstOrNull { it.name == role }?.let { append(" (${it.label})") }
        }
}

/**
 * Liga a rede em malha ao app: trata o que chega (importa trilhas, atualiza o
 * painel, guarda mensagens e, no coordenador, toca o alarme para desvios,
 * alertas urgentes e emergências) e envia as mensagens deste aparelho.
 */
class NetworkHub(
    private val context: Context,
    val nearby: NearbyManager,
    val mesh: MeshCore,
    private val trails: TrailRepository,
    private val navigation: NavigationRepository,
    private val chat: ChatRepository,
    settings: SettingsRepository,
    private val diagnostics: Diagnostics,
    private val scope: CoroutineScope,
) {
    private val _board = MutableStateFlow(BoardState())
    val board: StateFlow<BoardState> = _board.asStateFlow()

    private val alerts = AlertPlayer(context)
    @Volatile private var me = AppSettings()
    private val role get() = me.role
    /** Desvios (aparelho, início) cujo alarme o coordenador silenciou. */
    private val silenced = mutableSetOf<Pair<String, Long>>()

    /** Remetentes conhecidos por mensagens (nome, perfil, grupo). */
    private val learned = MutableStateFlow<Map<String, Contact>>(emptyMap())

    /** Contatos para mensagens individuais: aparelhos conectados, monitores e remetentes. */
    val contacts: StateFlow<List<Contact>> = combine(nearby.state, _board, learned) { net, board, known ->
        val all = LinkedHashMap<String, Contact>()
        known.values.forEach { all[it.deviceId] = it }
        board.monitors.values.forEach {
            all[it.deviceId] = Contact(it.deviceId, it.monitorName, UserRole.MONITOR.name, it.groupName)
        }
        net.peers.values.forEach { p ->
            all[p.deviceId] = all[p.deviceId]?.copy(role = p.role) ?: Contact(p.deviceId, p.name, p.role, null)
        }
        all.remove(nearby.deviceId)
        all.values.sortedBy { it.label }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            settings.settings.collect {
                me = it
                mesh.localName = it.userName
                mesh.eventCode = EventCode.normalize(it.eventCode)
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
        // Emergência sem resposta: o alarme do coordenador repete até alguém marcar "ciente".
        scope.launch {
            while (true) {
                delay(EMERGENCY_REPEAT_MS)
                if (role == UserRole.COORDINATOR && _board.value.openEmergencies.isNotEmpty()) alerts.alarm()
            }
        }
        // Vigia: grupos sem notícias, atrasados na hora limite ou com bateria baixa.
        scope.launch {
            while (true) {
                delay(WATCH_INTERVAL_MS)
                checkWatch(System.currentTimeMillis())
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

    /** Mensagem de texto para todos ([to] = null) ou para um aparelho. */
    suspend fun sendText(text: String, to: String?, urgent: Boolean = false, location: Location? = null) {
        val body = TextMessage(
            text = text.trim(),
            senderRole = role.name,
            senderGroup = myGroup(),
            urgent = urgent,
            latitude = location?.latitude,
            longitude = location?.longitude,
        )
        val envelope = mesh.originate(body, if (urgent) Priority.ALERT else Priority.NORMAL, to)
        chat.save(envelope.toChat(outgoing = true))
    }

    /** Botão de emergência: alerta prioritário para todos, com a posição. */
    suspend fun sendEmergency(location: Location?) {
        val nav = NavigationRuntime.state.value
        val body = Emergency(
            time = System.currentTimeMillis(),
            senderRole = role.name,
            monitorName = me.userName.ifBlank { "Sem nome" },
            groupName = myGroup(),
            trailName = null,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
        ).let { if (nav.isActive) it.copy(groupName = nav.groupName) else it }
        val envelope = mesh.originate(body, Priority.ALERT)
        chat.save(envelope.toChat(outgoing = true))
    }

    /** Coordenador pede que um grupo ([to]) ou todos (null) voltem à base. */
    suspend fun sendReturnToBase(to: String?, reason: String = "Pedido do coordenador") {
        val envelope = mesh.originate(ReturnToBase(reason), Priority.ALERT, to)
        chat.save(envelope.toChat(outgoing = true))
    }

    /** Silencia o alarme dos desvios ativos neste momento. */
    fun silenceAlerts() {
        _board.value.activeAlerts.forEach { silenced += it.deviceId to it.since }
        alerts.stop()
    }

    /** Coordenador viu a emergência: para o alarme repetido. */
    fun acknowledgeEmergency(messageId: String) {
        _board.update { MonitorBoard.acknowledge(it, messageId) }
        cancel(Notifications.ID_EMERGENCY_BASE + (messageId.hashCode() and 0xFFFF))
        if (_board.value.openEmergencies.isEmpty()) alerts.stop()
    }

    private fun myGroup(): String? {
        val nav = NavigationRuntime.state.value
        return when {
            nav.isActive -> nav.groupName
            role == UserRole.MONITOR -> me.groupName.ifBlank { null }
            else -> null
        }
    }

    private suspend fun handle(envelope: Envelope) {
        when (val body = envelope.body) {
            is TrailShare -> if (role != UserRole.REPEATER) importTrail(body, envelope.originName)
            is TextMessage -> onText(envelope, body)
            is Emergency -> onEmergency(envelope, body)
            is ReturnToBase -> onReturnToBase(envelope, body)
            else -> {
                _board.update { MonitorBoard.reduce(it, envelope, System.currentTimeMillis()) }
                if (body is NavigationEnded) saveRemoteResult(envelope.origin, body)
                if (role == UserRole.COORDINATOR) alertCoordinator(envelope.origin, body)
            }
        }
    }

    private suspend fun onText(envelope: Envelope, body: TextMessage) {
        learn(envelope.origin, envelope.originName, body.senderRole, body.senderGroup)
        if (role == UserRole.REPEATER) return
        chat.save(envelope.toChat(outgoing = false))
        val from = body.senderGroup?.let { "$it · ${envelope.originName}" } ?: envelope.originName
        if (body.urgent && role == UserRole.COORDINATOR) {
            alerts.alarm()
            diagnostics.onAlarm("mensagem urgente de $from: ${body.text}")
        }
        notify(
            Notifications.ID_MESSAGE_BASE + (envelope.origin.hashCode() and 0xFFFF),
            Notifications.message(context, if (envelope.to != null) "$from (para você)" else from, body.text, body.urgent),
        )
    }

    private suspend fun onReturnToBase(envelope: Envelope, body: ReturnToBase) {
        if (role == UserRole.REPEATER) return
        chat.save(envelope.toChat(outgoing = false))
        if (NavigationRuntime.state.value.isActive) {
            NavigationRuntime.returnToBase(context, "${envelope.originName.ifBlank { "Coordenador" }}: voltem à base. ${body.reason}".trim())
        }
    }

    /** Guarda o resumo de um grupo que terminou, para o resultado do evento. */
    private suspend fun saveRemoteResult(device: String, body: NavigationEnded) {
        if (role == UserRole.REPEATER) return
        val trail = trails.getTrailByUuid(body.trailUuid) ?: return
        navigation.saveRemoteSession(
            trail.id, device,
            RemoteSummary(
                groupName = body.groupName,
                monitorName = body.monitorName,
                startedAt = body.startedAt,
                endedAt = body.time,
                reachedTimes = body.reachedTimes,
                deviationAlerts = body.deviationAlerts,
                offRouteMillis = body.offRouteMillis,
            ),
        )
    }

    private fun checkWatch(now: Long) {
        val (state, raised) = MonitorBoard.watch(_board.value, now, me.noSignalMinutes * 60_000L)
        // Situações que acabaram: some a notificação.
        _board.value.watch.forEach { (device, kinds) ->
            (kinds - state.watch[device].orEmpty()).forEach { cancel(watchNotificationId(device, it)) }
        }
        _board.value = state
        if (role != UserRole.COORDINATOR) return
        raised.forEach { (device, kind) ->
            val m = state.monitors[device] ?: return@forEach
            val who = "${m.groupName} · ${m.monitorName}"
            val (title, text, alarm) = when (kind) {
                WatchKind.NO_SIGNAL -> Triple(
                    "Sem notícias: $who",
                    "Nenhuma mensagem há mais de ${me.noSignalMinutes} min. Última posição ${"%.6f, %.6f".format(java.util.Locale.US, m.latitude, m.longitude)}.",
                    true,
                )
                WatchKind.OVERDUE -> Triple("Hora limite: $who", "O grupo ainda não terminou a pista.", true)
                WatchKind.LOW_BATTERY -> Triple("Bateria baixa: $who", "Celular do monitor com ${m.batteryPercent}%.", false)
            }
            if (alarm) alerts.alarm()
            diagnostics.onAlarm(title)
            notify(watchNotificationId(device, kind), Notifications.remoteAlert(context, title, text))
        }
    }

    private fun watchNotificationId(device: String, kind: WatchKind) =
        Notifications.ID_WATCH_BASE + (device.hashCode() and 0xFFF) * 4 + kind.ordinal

    private suspend fun onEmergency(envelope: Envelope, body: Emergency) {
        learn(envelope.origin, body.monitorName, body.senderRole, body.groupName)
        if (role == UserRole.REPEATER) return
        _board.update { MonitorBoard.reduce(it, envelope) }
        chat.save(envelope.toChat(outgoing = false))
        val who = body.groupName?.let { "$it · ${body.monitorName}" } ?: body.monitorName
        diagnostics.onAlarm("emergência de $who (${envelope.hops} saltos)")
        if (role == UserRole.COORDINATOR) alerts.alarm()
        val where = if (body.latitude != null && body.longitude != null) {
            "Posição ${"%.6f, %.6f".format(java.util.Locale.US, body.latitude, body.longitude)}"
        } else {
            "Sem posição"
        }
        notify(
            Notifications.ID_EMERGENCY_BASE + (envelope.id.hashCode() and 0xFFFF),
            Notifications.emergency(context, "EMERGÊNCIA: $who", where),
        )
    }

    private fun learn(device: String, name: String, role: String?, group: String?) {
        learned.update { it + (device to Contact(device, name, role, group)) }
    }

    private fun alertCoordinator(device: String, body: Body) {
        when (body) {
            is DeviationAlert -> {
                if ((device to body.since) !in silenced) alerts.alarm()
                diagnostics.onAlarm("desvio recebido de ${body.groupName}")
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
                if (_board.value.activeAlerts.isEmpty() && _board.value.openEmergencies.isEmpty()) alerts.stop()
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

    private fun Envelope.toChat(outgoing: Boolean): ChatMessageEntity = when (val b = body) {
        is TextMessage -> ChatMessageEntity(
            id = id, fromDevice = origin, fromName = originName.ifBlank { "Sem nome" }, fromRole = b.senderRole,
            fromGroup = b.senderGroup, toDevice = to, text = b.text, urgent = b.urgent,
            latitude = b.latitude, longitude = b.longitude, createdAt = createdAt, outgoing = outgoing, read = outgoing,
        )
        is Emergency -> ChatMessageEntity(
            id = id, fromDevice = origin, fromName = b.monitorName, fromRole = b.senderRole, fromGroup = b.groupName,
            toDevice = to, text = "EMERGÊNCIA", urgent = true, emergency = true,
            latitude = b.latitude, longitude = b.longitude, createdAt = createdAt, outgoing = outgoing, read = outgoing,
        )
        is ReturnToBase -> ChatMessageEntity(
            id = id, fromDevice = origin, fromName = originName.ifBlank { "Coordenador" }, fromRole = UserRole.COORDINATOR.name,
            toDevice = to, text = "Voltem à base" + (b.reason.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""),
            urgent = true, createdAt = createdAt, outgoing = outgoing, read = outgoing,
        )
        else -> error("Não é mensagem de chat")
    }

    private fun notificationId(device: String) = Notifications.ID_REMOTE_ALERT_BASE + (device.hashCode() and 0xFFFF)

    private fun notify(id: Int, n: android.app.Notification) =
        context.getSystemService(NotificationManager::class.java).notify(id, n)

    private fun cancel(id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)

    private companion object {
        const val PRUNE_INTERVAL_MS = 5 * 60_000L
        const val EMERGENCY_REPEAT_MS = 30_000L
        const val WATCH_INTERVAL_MS = 30_000L
    }
}
