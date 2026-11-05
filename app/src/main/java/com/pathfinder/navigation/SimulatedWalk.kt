package com.pathfinder.navigation

import com.pathfinder.geo.Geodesy
import com.pathfinder.geo.LatLon
import kotlin.math.cos
import kotlin.math.sin

/** Uma leitura simulada: posição e instante relativo ao início (ms). */
data class SimFix(val position: LatLon, val offsetMillis: Long, val accuracyMeters: Float = 5f)

/**
 * Caminhada simulada pela trilha, para ensaiar sem sair do lugar.
 * Anda em linha reta de ponto a ponto a [speedMps]. Com [deviateOnLeg], no
 * meio desse trecho o grupo se afasta [deviationMeters] da rota por
 * [deviationMillis] (tempo simulado) e depois volta.
 */
object SimulatedWalk {
    fun generate(
        points: List<LatLon>,
        speedMps: Double = 1.3,
        stepMillis: Long = 2_000,
        deviateOnLeg: Int? = null,
        deviationMeters: Double = 60.0,
        deviationMillis: Long = 6 * 60_000,
    ): List<SimFix> {
        if (points.isEmpty()) return emptyList()
        val out = mutableListOf(SimFix(points.first(), 0))
        var t = 0L
        val stepMeters = speedMps * stepMillis / 1000
        for (leg in 0 until points.size - 1) {
            val a = points[leg]
            val b = points[leg + 1]
            val g = Geodesy.inverse(a, b)
            val steps = (g.distanceMeters / stepMeters).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                t += stepMillis
                out += SimFix(interpolate(a, b, s.toDouble() / steps), t)
                if (leg == deviateOnLeg && s == steps / 2) {
                    // Sai para o lado (perpendicular ao trecho), fica longe e volta.
                    val here = interpolate(a, b, s.toDouble() / steps)
                    val off = offset(here, g.trueAzimuthDegrees + 90, deviationMeters)
                    val end = t + deviationMillis
                    while (t < end) {
                        t += stepMillis
                        out += SimFix(off, t)
                    }
                }
            }
        }
        return out
    }

    private fun interpolate(a: LatLon, b: LatLon, f: Double) =
        LatLon(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)

    private fun offset(p: LatLon, bearingDeg: Double, meters: Double): LatLon {
        val r = Math.toRadians(bearingDeg)
        val dLat = meters * cos(r) / 111_320.0
        val dLon = meters * sin(r) / (111_320.0 * cos(Math.toRadians(p.latitude)))
        return LatLon(p.latitude + dLat, p.longitude + dLon)
    }
}
