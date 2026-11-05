package com.pathfinder.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class LatLon(val latitude: Double, val longitude: Double)

/** Resultado do problema inverso: distância e azimute verdadeiro inicial. */
data class GeodesicResult(val distanceMeters: Double, val trueAzimuthDegrees: Double)

/** Cálculos geodésicos no elipsoide WGS84 (fórmulas de Vincenty). */
object Geodesy {
    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val B = A * (1 - F)
    private const val MEAN_RADIUS = 6_371_008.8

    /** Distância e azimute verdadeiro de [from] para [to]. */
    fun inverse(from: LatLon, to: LatLon): GeodesicResult {
        if (from == to) return GeodesicResult(0.0, 0.0)
        return vincenty(from, to) ?: haversine(from, to)
    }

    private fun vincenty(p1: LatLon, p2: LatLon): GeodesicResult? {
        val l = Math.toRadians(p2.longitude - p1.longitude)
        val u1 = atan((1 - F) * tan(Math.toRadians(p1.latitude)))
        val u2 = atan((1 - F) * tan(Math.toRadians(p2.latitude)))
        val sinU1 = sin(u1); val cosU1 = cos(u1)
        val sinU2 = sin(u2); val cosU2 = cos(u2)

        var lambda = l
        var iterations = 0
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var cos2Alpha: Double
        var cos2SigmaM: Double
        var sinLambda: Double
        var cosLambda: Double
        do {
            sinLambda = sin(lambda)
            cosLambda = cos(lambda)
            val x = cosU2 * sinLambda
            val y = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            sinSigma = sqrt(x * x + y * y)
            if (sinSigma == 0.0) return GeodesicResult(0.0, 0.0)
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cos2Alpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cos2Alpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cos2Alpha else 0.0
            val c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha))
            val previous = lambda
            lambda = l + (1 - c) * F * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - previous) < 1e-12) break
        } while (++iterations < 200)
        if (iterations >= 200) return null // pontos quase antípodas: não converge

        val uSq = cos2Alpha * (A * A - B * B) / (B * B)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        val distance = B * bigA * (sigma - deltaSigma)
        val azimuth = Math.toDegrees(atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
        return GeodesicResult(distance, normalizeDegrees(azimuth))
    }

    private fun haversine(p1: LatLon, p2: LatLon): GeodesicResult {
        val phi1 = Math.toRadians(p1.latitude)
        val phi2 = Math.toRadians(p2.latitude)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(p2.longitude - p1.longitude)
        val h = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        val distance = 2 * MEAN_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return GeodesicResult(distance, normalizeDegrees(Math.toDegrees(atan2(y, x))))
    }

    /** Distância em metros entre dois pontos. */
    fun distance(from: LatLon, to: LatLon): Double = inverse(from, to).distanceMeters
}

/** Normaliza um ângulo para o intervalo [0, 360). */
fun normalizeDegrees(degrees: Double): Double {
    val d = degrees % 360.0
    return if (d < 0) d + 360.0 else d
}

/**
 * Azimute magnético a partir do verdadeiro: verdadeiro menos a declinação
 * (declinação positiva para leste, negativa para oeste).
 */
fun magneticAzimuth(trueAzimuthDegrees: Double, declinationDegrees: Double): Double =
    normalizeDegrees(trueAzimuthDegrees - declinationDegrees)
