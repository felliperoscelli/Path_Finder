package com.pathfinder.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Retângulo geográfico em graus decimais. */
data class GeoBounds(val north: Double, val south: Double, val east: Double, val west: Double) {
    init {
        require(north >= south) { "north deve ser >= south" }
        require(east >= west) { "east deve ser >= west" }
    }

    fun intersects(other: GeoBounds): Boolean =
        south <= other.north && north >= other.south && west <= other.east && east >= other.west
}

/** Cálculos de tiles no esquema Web Mercator (XYZ / "slippy map"). */
object TileMath {
    private const val MAX_LAT = 85.05112878

    fun tileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val rad = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    /** Quantidade de tiles necessários para cobrir [bounds] em um nível de zoom. */
    fun tilesAtZoom(bounds: GeoBounds, zoom: Int): Long {
        val cols = tileX(bounds.east, zoom) - tileX(bounds.west, zoom) + 1
        val rows = tileY(bounds.south, zoom) - tileY(bounds.north, zoom) + 1
        return cols.toLong() * rows.toLong()
    }

    /** Quantidade total de tiles entre [minZoom] e [maxZoom] (inclusive). */
    fun tileCount(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long =
        (minZoom..maxZoom).sumOf { tilesAtZoom(bounds, it) }
}
