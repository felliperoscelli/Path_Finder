package com.pathfinder.geo

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** Coordenada UTM (WGS84). */
data class UtmCoordinate(
    val zone: Int,
    /** Letra da banda de latitude (C–X, sem I e O). */
    val band: Char,
    val easting: Double,
    val northing: Double,
) {
    val isSouthern: Boolean get() = band < 'N'
}

/**
 * Conversão latitude/longitude (WGS84) → UTM pela série de Krüger,
 * com precisão submilimétrica dentro da zona.
 */
object Utm {
    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500_000.0
    private const val FALSE_NORTHING_SOUTH = 10_000_000.0
    private const val BANDS = "CDEFGHJKLMNPQRSTUVWXX"

    private val n = F / (2 - F)
    private val rectifyingRadius = A / (1 + n) * (1 + n * n / 4 + n * n * n * n / 64)
    private val alpha = doubleArrayOf(
        n / 2 - 2.0 / 3 * n * n + 5.0 / 16 * n * n * n,
        13.0 / 48 * n * n - 3.0 / 5 * n * n * n,
        61.0 / 240 * n * n * n,
    )
    private val twoSqrtNOverOnePlusN = 2 * sqrt(n) / (1 + n)
    private val beta = doubleArrayOf(
        n / 2 - 2.0 / 3 * n * n + 37.0 / 96 * n * n * n,
        1.0 / 48 * n * n + 1.0 / 15 * n * n * n,
        17.0 / 480 * n * n * n,
    )
    private val delta = doubleArrayOf(
        2 * n - 2.0 / 3 * n * n - 2 * n * n * n,
        7.0 / 3 * n * n - 8.0 / 5 * n * n * n,
        56.0 / 15 * n * n * n,
    )

    /**
     * UTM → latitude/longitude (série de Krüger inversa). [southern] indica o
     * hemisfério (no Brasil, quase sempre sul). Retorna null para valores fora
     * da faixa válida.
     */
    fun toLatLon(zone: Int, southern: Boolean, easting: Double, northing: Double): LatLon? {
        if (zone !in 1..60 || easting !in 100_000.0..900_000.0 || northing !in 0.0..10_000_000.0) return null
        val xi = (northing - if (southern) FALSE_NORTHING_SOUTH else 0.0) / (K0 * rectifyingRadius)
        val eta = (easting - FALSE_EASTING) / (K0 * rectifyingRadius)
        var xiP = xi
        var etaP = eta
        for (j in 1..3) {
            val b = beta[j - 1]
            xiP -= b * sin(2 * j * xi) * cosh(2 * j * eta)
            etaP -= b * cos(2 * j * xi) * sinh(2 * j * eta)
        }
        val chi = kotlin.math.asin(sin(xiP) / cosh(etaP))
        var phi = chi
        for (j in 1..3) phi += delta[j - 1] * sin(2 * j * chi)
        val centralMeridian = (zone - 1) * 6 - 180 + 3.0
        val lon = centralMeridian + Math.toDegrees(kotlin.math.atan2(sinh(etaP), cos(xiP)))
        return LatLon(Math.toDegrees(phi), lon)
    }

    /** Retorna null fora da faixa coberta pelo UTM (latitude entre −80° e 84°). */
    fun fromLatLon(latitude: Double, longitude: Double): UtmCoordinate? {
        if (latitude < -80.0 || latitude > 84.0) return null
        val lon = normalizeLongitude(longitude)
        val zone = zoneFor(latitude, lon)
        val band = BANDS[floor((latitude + 80) / 8).toInt().coerceIn(0, BANDS.length - 1)]
        val centralMeridian = (zone - 1) * 6 - 180 + 3.0

        val phi = Math.toRadians(latitude)
        val dLambda = Math.toRadians(lon - centralMeridian)
        val sinPhi = sin(phi)
        val t = sinh(atanh(sinPhi) - twoSqrtNOverOnePlusN * atanh(twoSqrtNOverOnePlusN * sinPhi))
        val xi = atan(t / cos(dLambda))
        val eta = atanh(sin(dLambda) / sqrt(1 + t * t))

        var e = eta
        var nn = xi
        for (j in 1..3) {
            val a = alpha[j - 1]
            e += a * cos(2 * j * xi) * sinh(2 * j * eta)
            nn += a * sin(2 * j * xi) * cosh(2 * j * eta)
        }
        val easting = FALSE_EASTING + K0 * rectifyingRadius * e
        var northing = K0 * rectifyingRadius * nn
        if (latitude < 0) northing += FALSE_NORTHING_SOUTH
        return UtmCoordinate(zone, band, easting, northing)
    }

    /** Zona UTM, incluindo as exceções da Noruega e de Svalbard. */
    fun zoneFor(latitude: Double, longitude: Double): Int {
        val lon = normalizeLongitude(longitude)
        if (latitude in 56.0..<64.0 && lon in 3.0..<12.0) return 32
        if (latitude in 72.0..84.0 && lon >= 0) {
            when {
                lon < 9 -> return 31
                lon < 21 -> return 33
                lon < 33 -> return 35
                lon < 42 -> return 37
            }
        }
        return (floor((lon + 180) / 6).toInt() % 60) + 1
    }

    private fun normalizeLongitude(lon: Double): Double {
        var l = (lon + 180) % 360
        if (l < 0) l += 360
        return l - 180
    }

    private fun atanh(x: Double) = 0.5 * kotlin.math.ln((1 + x) / (1 - x))
}
