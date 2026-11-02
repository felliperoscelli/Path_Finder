package com.pathfinder.navigation

import com.pathfinder.geo.Geodesy
import com.pathfinder.geo.LatLon
import com.pathfinder.geo.Planar
import com.pathfinder.geo.magneticAzimuth

data class NavigationConfig(
    /** Distância máxima da rota antes de contar como desvio. */
    val deviationDistanceMeters: Double,
    /** Tempo contínuo fora da rota até disparar o alarme. */
    val deviationTimeMillis: Long,
    /** Raio em que o ponto conta como alcançado. */
    val arrivalRadiusMeters: Double,
    /** Leituras mais imprecisas que isso não contam para o desvio. */
    val maxAccuracyMeters: Float = 50f,
    /** Intervalo entre repetições do alarme enquanto continuar fora da rota. */
    val alarmRepeatMillis: Long = 60_000,
)

/** Situação atual da navegação, recalculada a cada leitura do GPS. */
data class NavigationStatus(
    /** Índice do próximo ponto; igual ao número de pontos quando a pista acabou. */
    val nextIndex: Int,
    val reached: Set<Int>,
    val distanceToNextMeters: Double? = null,
    val trueBearingToNext: Double? = null,
    val magneticBearingToNext: Double? = null,
    val distanceToRouteMeters: Double? = null,
    /** Início do desvio atual, ou null se estiver na rota. */
    val offRouteSince: Long? = null,
    /** Alarme disparado e ainda fora da rota. */
    val alarmActive: Boolean = false,
    val silenced: Boolean = false,
    /** Se o desvio está sendo vigiado (só depois de chegar à partida). */
    val monitoring: Boolean = false,
    val finished: Boolean = false,
)

sealed interface NavigationEvent {
    data class PointReached(val index: Int) : NavigationEvent

    /** [repeat] = lembrete enquanto continua fora da rota. */
    data class DeviationAlarm(
        val distanceMeters: Double,
        val since: Long,
        val latitude: Double,
        val longitude: Double,
        val repeat: Boolean,
    ) : NavigationEvent

    data class BackOnRoute(val since: Long, val maxDistanceMeters: Double) : NavigationEvent
    data object Finished : NavigationEvent
}

/**
 * Regras da navegação do monitor (sem dependência do Android):
 *
 * - A rota é a linha reta entre os pontos (como o grupo anda pela bússola) e,
 *   se houver, o percurso gravado na criação da trilha. Vale a menor distância
 *   entre as duas, para não acusar desvio quando o grupo contorna um obstáculo
 *   pelo mesmo caminho do coordenador.
 * - Um ponto é alcançado ao entrar no raio de chegada. Chegar a um ponto à
 *   frente pula os anteriores.
 * - O desvio só é vigiado depois de chegar à partida (ou pular para depois dela)
 *   e até a pista acabar. O alarme dispara após o tempo configurado
 *   continuamente fora da rota e se repete até voltar ou ser silenciado.
 */
class NavigationEngine(
    private val points: List<LatLon>,
    track: List<LatLon>,
    private val declinationDegrees: Double,
    private val config: NavigationConfig,
    startIndex: Int = 0,
    reached: Set<Int> = emptySet(),
) {
    private val track = track
    private var nextIndex = startIndex.coerceIn(0, points.size)
    private val reachedSet = reached.toMutableSet()
    private var offRouteSince: Long? = null
    private var maxOffDistance = 0.0
    private var alarmFired = false
    private var lastAlarmAt = 0L
    private var silenced = false
    private var lastStatus = NavigationStatus(nextIndex, reachedSet.toSet())

    val status: NavigationStatus get() = lastStatus

    fun onFix(latitude: Double, longitude: Double, accuracyMeters: Float?, timeMillis: Long): List<NavigationEvent> {
        val events = mutableListOf<NavigationEvent>()
        val here = LatLon(latitude, longitude)
        val wasFinished = nextIndex >= points.size

        // Chegada: o ponto mais à frente dentro do raio.
        val arrived = (points.size - 1 downTo nextIndex).firstOrNull {
            Geodesy.distance(here, points[it]) <= config.arrivalRadiusMeters
        }
        if (arrived != null) {
            reachedSet += arrived
            nextIndex = arrived + 1
            events += NavigationEvent.PointReached(arrived)
            if (nextIndex >= points.size && !wasFinished) events += NavigationEvent.Finished
        }

        val finished = nextIndex >= points.size
        val monitoring = nextIndex > 0 && !finished
        val routeDistance = distanceToRoute(here)
        val usable = accuracyMeters == null || accuracyMeters <= config.maxAccuracyMeters

        if (!monitoring) {
            endDeviation(events)
        } else if (usable && routeDistance != null) {
            if (routeDistance > config.deviationDistanceMeters) {
                val since = offRouteSince ?: timeMillis.also { offRouteSince = it; maxOffDistance = 0.0 }
                maxOffDistance = maxOf(maxOffDistance, routeDistance)
                val elapsed = timeMillis - since
                val due = !alarmFired || (!silenced && timeMillis - lastAlarmAt >= config.alarmRepeatMillis)
                if (elapsed >= config.deviationTimeMillis && due) {
                    events += NavigationEvent.DeviationAlarm(routeDistance, since, latitude, longitude, repeat = alarmFired)
                    alarmFired = true
                    lastAlarmAt = timeMillis
                }
            } else {
                endDeviation(events)
            }
        }

        val target = points.getOrNull(nextIndex)
        val leg = target?.let { Geodesy.inverse(here, it) }
        lastStatus = NavigationStatus(
            nextIndex = nextIndex,
            reached = reachedSet.toSet(),
            distanceToNextMeters = leg?.distanceMeters,
            trueBearingToNext = leg?.trueAzimuthDegrees,
            magneticBearingToNext = leg?.let { magneticAzimuth(it.trueAzimuthDegrees, declinationDegrees) },
            distanceToRouteMeters = routeDistance,
            offRouteSince = offRouteSince,
            alarmActive = alarmFired && offRouteSince != null,
            silenced = silenced,
            monitoring = monitoring,
            finished = finished,
        )
        return events
    }

    /** Pula o próximo ponto (ex.: ponto destruído ou inacessível). */
    fun skip() = setNext(nextIndex + 1)

    /** Volta a mirar o ponto anterior. */
    fun back() = setNext(nextIndex - 1)

    /** Silencia as repetições do alarme até o grupo voltar à rota. */
    fun silence() {
        silenced = true
        lastStatus = lastStatus.copy(silenced = true)
    }

    private fun setNext(index: Int) {
        nextIndex = index.coerceIn(0, points.size)
        lastStatus = lastStatus.copy(nextIndex = nextIndex, finished = nextIndex >= points.size, distanceToNextMeters = null)
    }

    private fun endDeviation(events: MutableList<NavigationEvent>) {
        val since = offRouteSince ?: return
        if (alarmFired) events += NavigationEvent.BackOnRoute(since, maxOffDistance)
        offRouteSince = null
        alarmFired = false
        silenced = false
        maxOffDistance = 0.0
    }

    private fun distanceToRoute(p: LatLon): Double? {
        val legs = Planar.distanceToPolyline(p, points)
        val walked = Planar.distanceToPolyline(p, track)
        return listOfNotNull(legs, walked).minOrNull()
    }
}
