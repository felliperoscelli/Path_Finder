package com.pathfinder.geo

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Distâncias em projeção plana local (metros) em torno de um ponto de
 * referência. Erro desprezível nas escalas de uma pista (alguns km).
 */
object Planar {
    private const val M_PER_DEG_LAT = 111_320.0

    /** Menor distância de [p] ao segmento [a]–[b], em metros. */
    fun distanceToSegment(p: LatLon, a: LatLon, b: LatLon): Double {
        val mPerDegLon = M_PER_DEG_LAT * cos(Math.toRadians(p.latitude))
        val ax = (a.longitude - p.longitude) * mPerDegLon
        val ay = (a.latitude - p.latitude) * M_PER_DEG_LAT
        val bx = (b.longitude - p.longitude) * mPerDegLon
        val by = (b.latitude - p.latitude) * M_PER_DEG_LAT
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    /** Menor distância de [p] a uma linha formada por [line] (ou ao ponto, se houver só um). */
    fun distanceToPolyline(p: LatLon, line: List<LatLon>): Double? = when (line.size) {
        0 -> null
        1 -> distanceToSegment(p, line[0], line[0])
        else -> (0 until line.size - 1).minOf { distanceToSegment(p, line[it], line[it + 1]) }
    }
}
