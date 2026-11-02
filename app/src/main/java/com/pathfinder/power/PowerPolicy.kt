package com.pathfinder.power

import com.pathfinder.geo.Geodesy
import com.pathfinder.geo.LatLon

/** Economia de bateria escolhida nas configurações. */
enum class PowerMode(val label: String) {
    AUTO("Automática"),
    ALWAYS("Sempre"),
    OFF("Desligada"),
}

/** Como o GPS e o envio de posição devem trabalhar agora. */
data class PowerPlan(
    val gpsIntervalMillis: Long,
    val publishIntervalMillis: Long,
    /** Economia ativa (mostrada na tela). */
    val saving: Boolean,
    /** Motivo, para a tela e o diagnóstico. */
    val reason: String,
)

/**
 * Regras de economia de bateria na navegação:
 *
 * - carregando ou economia desligada: GPS a cada 2 s e posição no intervalo configurado;
 * - parado há 2 min: GPS a cada 10 s (volta a 2 s quando o grupo anda);
 * - economia (sempre, ou automática com bateria ≤ 30%): GPS a cada 5 s e
 *   posição com o dobro do intervalo (mínimo 60 s);
 * - automática com bateria ≤ 10%: GPS a cada 10 s e posição com o triplo (mínimo 120 s).
 *
 * Com o desvio medido em minutos, leituras a cada 5–10 s continuam suficientes.
 */
object PowerPolicy {
    const val NORMAL_GPS_MS = 2_000L
    const val SAVING_GPS_MS = 5_000L
    const val SLOW_GPS_MS = 10_000L
    const val STATIONARY_AFTER_MS = 2 * 60_000L
    const val SAVING_BATTERY = 30
    const val CRITICAL_BATTERY = 10

    fun plan(mode: PowerMode, batteryPercent: Int?, charging: Boolean, stationaryForMillis: Long, basePublishMillis: Long): PowerPlan {
        val stationary = stationaryForMillis >= STATIONARY_AFTER_MS
        if (mode == PowerMode.OFF || charging) {
            return PowerPlan(NORMAL_GPS_MS, basePublishMillis, saving = false, reason = if (charging) "carregando" else "economia desligada")
        }
        val battery = batteryPercent ?: 100
        return when {
            mode == PowerMode.AUTO && battery <= CRITICAL_BATTERY -> PowerPlan(
                SLOW_GPS_MS, maxOf(basePublishMillis * 3, 120_000L), saving = true, reason = "bateria crítica ($battery%)",
            )
            mode == PowerMode.ALWAYS || battery <= SAVING_BATTERY -> PowerPlan(
                if (stationary) SLOW_GPS_MS else SAVING_GPS_MS,
                maxOf(basePublishMillis * 2, 60_000L),
                saving = true,
                reason = if (mode == PowerMode.ALWAYS) "economia ligada" else "bateria baixa ($battery%)",
            )
            stationary -> PowerPlan(SLOW_GPS_MS, basePublishMillis, saving = false, reason = "parado")
            else -> PowerPlan(NORMAL_GPS_MS, basePublishMillis, saving = false, reason = "normal")
        }
    }
}

/** Detecta há quanto tempo o grupo está parado (sem se afastar mais que [radiusMeters]). */
class MotionTracker(private val radiusMeters: Double = 15.0) {
    private var anchor: LatLon? = null
    private var since = 0L

    fun onFix(position: LatLon, time: Long) {
        val a = anchor
        if (a == null || Geodesy.distance(a, position) > radiusMeters) {
            anchor = position
            since = time
        }
    }

    fun stationaryFor(now: Long): Long = if (anchor == null) 0 else (now - since).coerceAtLeast(0)
}
