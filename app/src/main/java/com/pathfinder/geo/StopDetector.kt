package com.pathfinder.geo

/** Posição média calculada enquanto o usuário esteve parado. */
data class StopCandidate(val latitude: Double, val longitude: Double, val altitude: Double?, val sinceMillis: Long)

/**
 * Detecta quando o usuário fica parado por [stopMillis]. Considera "parado"
 * enquanto todas as leituras ficam dentro de um raio da primeira (ampliado
 * pela imprecisão do GPS). Dispara uma vez por parada; volta a armar quando
 * o usuário se afasta. A posição sugerida é a média das leituras, mais
 * precisa que uma leitura isolada.
 */
class StopDetector(
    private val stopMillis: Long,
    private val radiusMeters: Double = 10.0,
    private val maxAccuracyRadius: Double = 25.0,
) {
    private var anchor: LatLon? = null
    private var anchorTime = 0L
    private var fired = false
    private var sumLat = 0.0
    private var sumLon = 0.0
    private var sumAlt = 0.0
    private var altCount = 0
    private var count = 0

    fun onFix(latitude: Double, longitude: Double, altitude: Double?, accuracyMeters: Float?, timeMillis: Long): StopCandidate? {
        val here = LatLon(latitude, longitude)
        val a = anchor
        val radius = maxOf(radiusMeters, minOf(accuracyMeters?.toDouble() ?: 0.0, maxAccuracyRadius))
        if (a == null || Geodesy.distance(a, here) > radius) {
            reset(here, timeMillis)
        }
        sumLat += latitude
        sumLon += longitude
        count++
        if (altitude != null) {
            sumAlt += altitude
            altCount++
        }
        if (!fired && timeMillis - anchorTime >= stopMillis) {
            fired = true
            return StopCandidate(
                latitude = sumLat / count,
                longitude = sumLon / count,
                altitude = if (altCount > 0) sumAlt / altCount else null,
                sinceMillis = anchorTime,
            )
        }
        return null
    }

    private fun reset(anchor: LatLon, time: Long) {
        this.anchor = anchor
        anchorTime = time
        fired = false
        sumLat = 0.0; sumLon = 0.0; sumAlt = 0.0
        count = 0; altCount = 0
    }
}
