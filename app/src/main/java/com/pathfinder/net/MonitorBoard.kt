package com.pathfinder.net

/** Última situação conhecida de um monitor, vista pelo coordenador. */
data class MonitorInfo(
    val deviceId: String,
    val monitorName: String,
    val groupName: String,
    val trailName: String,
    val latitude: Double,
    val longitude: Double,
    val trailUuid: String? = null,
    val accuracyMeters: Float? = null,
    val nextIndex: Int = 0,
    val totalPoints: Int = 0,
    val lastReachedName: String? = null,
    val distanceToRouteMeters: Double? = null,
    val offRouteSince: Long? = null,
    val alarm: Boolean = false,
    val finished: Boolean = false,
    /** Hora da informação no aparelho do monitor. */
    val updatedAt: Long,
    /** Quando o coordenador recebeu a última notícia (relógio dele: imune a relógio errado no monitor). */
    val receivedAt: Long = updatedAt,
    val batteryPercent: Int? = null,
    val deadlineAt: Long? = null,
    val returningToBase: Boolean = false,
    /** O monitor encerrou a navegação. */
    val ended: Boolean = false,
)

/** Situações vigiadas pelo coordenador, além dos alertas enviados pelos monitores. */
enum class WatchKind {
    /** Sem notícias há mais que o limite configurado. */
    NO_SIGNAL,

    /** Passou da hora limite e o grupo não terminou. */
    OVERDUE,

    /** Bateria do celular do monitor baixa. */
    LOW_BATTERY,
}

/** Alerta de desvio recebido de um monitor. */
data class RemoteAlert(
    val messageId: String,
    val deviceId: String,
    val groupName: String,
    val monitorName: String,
    val trailName: String,
    val latitude: Double,
    val longitude: Double,
    val since: Long,
    val time: Long,
    val distanceMeters: Double,
    val active: Boolean = true,
)

/** Pedido de emergência recebido; fica ativo até o coordenador marcar "ciente". */
data class EmergencyCall(
    val messageId: String,
    val deviceId: String,
    val name: String,
    val groupName: String?,
    val trailName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val time: Long,
    val acknowledged: Boolean = false,
)

data class BoardState(
    val monitors: Map<String, MonitorInfo> = emptyMap(),
    /** Mais recentes primeiro. */
    val alerts: List<RemoteAlert> = emptyList(),
    /** Mais recentes primeiro. */
    val emergencies: List<EmergencyCall> = emptyList(),
    /** Situações ativas por aparelho (ver [MonitorBoard.watch]). */
    val watch: Map<String, Set<WatchKind>> = emptyMap(),
) {
    val activeAlerts: List<RemoteAlert> get() = alerts.filter { it.active }
    val openEmergencies: List<EmergencyCall> get() = emergencies.filterNot { it.acknowledged }
}

/**
 * Atualiza o painel do coordenador a partir das mensagens recebidas
 * (função pura). Mensagens antigas que chegarem fora de ordem não
 * sobrescrevem dados mais novos.
 */
object MonitorBoard {
    private const val MAX_ALERTS = 100
    const val LOW_BATTERY_PERCENT = 15

    /**
     * Confere as situações vigiadas. Retorna o novo estado e as que acabaram
     * de começar (para tocar o alarme uma vez).
     */
    fun watch(state: BoardState, now: Long, noSignalMillis: Long): Pair<BoardState, List<Pair<String, WatchKind>>> {
        val raised = mutableListOf<Pair<String, WatchKind>>()
        val watch = state.monitors.values.filterNot { it.ended }.associate { m ->
            val kinds = buildSet {
                if (now - m.receivedAt > noSignalMillis) add(WatchKind.NO_SIGNAL)
                if (m.deadlineAt != null && now >= m.deadlineAt && !m.finished) add(WatchKind.OVERDUE)
                if (m.batteryPercent != null && m.batteryPercent <= LOW_BATTERY_PERCENT) add(WatchKind.LOW_BATTERY)
            }
            val before = state.watch[m.deviceId].orEmpty()
            (kinds - before).forEach { raised += m.deviceId to it }
            m.deviceId to kinds
        }.filterValues { it.isNotEmpty() }
        return state.copy(watch = watch) to raised
    }

    fun reduce(state: BoardState, envelope: Envelope, now: Long = envelope.createdAt): BoardState =
        reduceBody(state, envelope).let { s ->
            // Qualquer notícia do aparelho conta para "sem sinal".
            val m = s.monitors[envelope.origin] ?: return@let s
            s.copy(monitors = s.monitors + (envelope.origin to m.copy(receivedAt = maxOf(m.receivedAt, now))))
        }

    private fun reduceBody(state: BoardState, envelope: Envelope): BoardState {
        val device = envelope.origin
        val current = state.monitors[device]
        return when (val b = envelope.body) {
            is PositionUpdate -> {
                if (current != null && current.updatedAt > b.time) return state
                val info = MonitorInfo(
                    deviceId = device,
                    monitorName = b.monitorName,
                    groupName = b.groupName,
                    trailName = b.trailName,
                    latitude = b.latitude,
                    longitude = b.longitude,
                    trailUuid = b.trailUuid,
                    accuracyMeters = b.accuracyMeters,
                    nextIndex = b.nextIndex,
                    totalPoints = b.totalPoints,
                    lastReachedName = b.lastReachedName,
                    distanceToRouteMeters = b.distanceToRouteMeters,
                    offRouteSince = b.offRouteSince,
                    alarm = b.alarm,
                    finished = b.finished,
                    updatedAt = b.time,
                    receivedAt = current?.receivedAt ?: 0,
                    batteryPercent = b.batteryPercent,
                    deadlineAt = b.deadlineAt,
                    returningToBase = b.returningToBase,
                )
                val alerts = if (!b.alarm) state.alerts.closeFor(device) else state.alerts
                state.copy(monitors = state.monitors + (device to info), alerts = alerts)
            }
            is DeviationAlert -> {
                val info = (current ?: MonitorInfo(device, b.monitorName, b.groupName, b.trailName, b.latitude, b.longitude, updatedAt = b.time))
                    .let { if (it.updatedAt > b.time) it else it.copy(latitude = b.latitude, longitude = b.longitude, updatedAt = b.time) }
                    .copy(alarm = true, offRouteSince = b.since, distanceToRouteMeters = b.distanceMeters)
                val alert = RemoteAlert(
                    envelope.id, device, b.groupName, b.monitorName, b.trailName,
                    b.latitude, b.longitude, b.since, b.time, b.distanceMeters,
                )
                // Uma entrada por desvio: repetições atualizam a mesma.
                val others = state.alerts.filterNot { it.deviceId == device && it.since == b.since }
                state.copy(
                    monitors = state.monitors + (device to info),
                    alerts = (listOf(alert) + others).take(MAX_ALERTS),
                )
            }
            is BackOnRoute -> state.copy(
                monitors = current?.let { state.monitors + (device to it.copy(alarm = false, offRouteSince = null)) } ?: state.monitors,
                alerts = state.alerts.closeFor(device),
            )
            is PointReached -> {
                if (current == null || current.updatedAt > b.time) return state
                state.copy(
                    monitors = state.monitors + (
                        device to current.copy(
                            nextIndex = b.index + 1,
                            totalPoints = b.totalPoints,
                            lastReachedName = b.pointName,
                            finished = b.index + 1 >= b.totalPoints,
                            updatedAt = b.time,
                        )
                    ),
                )
            }
            is Emergency -> {
                val call = EmergencyCall(
                    envelope.id, device, b.monitorName, b.groupName, b.trailName, b.latitude, b.longitude, b.time,
                )
                val monitors = if (current != null && b.latitude != null && b.longitude != null && current.updatedAt <= b.time) {
                    state.monitors + (device to current.copy(latitude = b.latitude, longitude = b.longitude, updatedAt = b.time))
                } else {
                    state.monitors
                }
                state.copy(monitors = monitors, emergencies = (listOf(call) + state.emergencies).take(MAX_ALERTS))
            }
            is NavigationEnded -> state.copy(
                monitors = current?.let {
                    state.monitors + (device to it.copy(ended = true, alarm = false, offRouteSince = null))
                } ?: state.monitors,
                alerts = state.alerts.closeFor(device),
                watch = state.watch - device,
            )
            else -> state
        }
    }

    /** O coordenador marcou a emergência como vista (o alarme para). */
    fun acknowledge(state: BoardState, messageId: String): BoardState =
        state.copy(emergencies = state.emergencies.map { if (it.messageId == messageId) it.copy(acknowledged = true) else it })

    private fun List<RemoteAlert>.closeFor(device: String) =
        map { if (it.deviceId == device && it.active) it.copy(active = false) else it }
}
