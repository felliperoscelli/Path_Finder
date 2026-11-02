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
)

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

    fun reduce(state: BoardState, envelope: Envelope): BoardState {
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
            else -> state
        }
    }

    /** O coordenador marcou a emergência como vista (o alarme para). */
    fun acknowledge(state: BoardState, messageId: String): BoardState =
        state.copy(emergencies = state.emergencies.map { if (it.messageId == messageId) it.copy(acknowledged = true) else it })

    private fun List<RemoteAlert>.closeFor(device: String) =
        map { if (it.deviceId == device && it.active) it.copy(active = false) else it }
}
