package com.pathfinder.geo

import com.pathfinder.data.db.TrailPointEntity

/** Grandezas de um ponto em relação ao ponto anterior. */
data class LegMetrics(
    val distanceMeters: Double,
    val trueAzimuthDegrees: Double,
    val magneticAzimuthDegrees: Double,
)

object TrailCalculator {

    fun leg(previous: LatLon, current: LatLon, declinationDegrees: Double): LegMetrics {
        val g = Geodesy.inverse(previous, current)
        return LegMetrics(
            distanceMeters = g.distanceMeters,
            trueAzimuthDegrees = g.trueAzimuthDegrees,
            magneticAzimuthDegrees = magneticAzimuth(g.trueAzimuthDegrees, declinationDegrees),
        )
    }

    /**
     * Recalcula a trilha inteira: renumera a sequência (0, 1, 2…) mantendo a
     * ordem atual e preenche UTM, distância e azimutes a partir do ponto
     * anterior. O primeiro ponto não tem azimute nem distância.
     */
    fun recalculate(points: List<TrailPointEntity>, declinationDegrees: Double): List<TrailPointEntity> {
        val ordered = points.sortedBy { it.sequence }
        return ordered.mapIndexed { index, p ->
            val utm = Utm.fromLatLon(p.latitude, p.longitude)
            val leg = if (index == 0) {
                null
            } else {
                val prev = ordered[index - 1]
                leg(LatLon(prev.latitude, prev.longitude), LatLon(p.latitude, p.longitude), declinationDegrees)
            }
            p.copy(
                sequence = index,
                utmZone = utm?.zone,
                utmBand = utm?.band?.toString(),
                utmEasting = utm?.easting,
                utmNorthing = utm?.northing,
                distanceFromPreviousMeters = leg?.distanceMeters,
                trueAzimuthDegrees = leg?.trueAzimuthDegrees,
                magneticAzimuthDegrees = leg?.magneticAzimuthDegrees,
            )
        }
    }
}
