package com.pathfinder.geo

import com.pathfinder.data.settings.CoordinateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatação de coordenadas, azimutes e distâncias para exibição. */
object Formats {
    private val ptBr = Locale("pt", "BR")

    fun latLon(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    fun utm(utm: UtmCoordinate): String =
        "${utm.zone}${utm.band} ${utm.easting.roundToInt()} E ${utm.northing.roundToInt()} N"

    fun utm(zone: Int?, band: String?, easting: Double?, northing: Double?): String? =
        if (zone == null || band == null || easting == null || northing == null) null
        else utm(UtmCoordinate(zone, band.first(), easting, northing))

    /** Coordenada no formato escolhido; "Ambos" gera duas linhas. */
    fun coordinate(format: CoordinateFormat, latitude: Double, longitude: Double, utm: String?): String =
        when (format) {
            CoordinateFormat.LAT_LON -> latLon(latitude, longitude)
            CoordinateFormat.UTM -> utm ?: latLon(latitude, longitude)
            CoordinateFormat.BOTH -> listOfNotNull(utm, latLon(latitude, longitude)).joinToString("\n")
        }

    /** Azimute arredondado ao grau, como lido numa bússola. */
    fun azimuth(degrees: Double?): String =
        degrees?.let { "${normalizeDegrees(it).roundToInt() % 360}°" } ?: "—"

    fun distance(meters: Double?): String = when {
        meters == null -> "—"
        meters < 1000 -> "${meters.roundToInt()} m"
        else -> String.format(ptBr, "%.2f km", meters / 1000)
    }

    /** Ex.: "21,4° O" ou "3,0° L". */
    fun declination(degrees: Double?): String {
        if (degrees == null) return "—"
        val side = if (degrees < 0) "O" else "L"
        return String.format(ptBr, "%.1f° %s", abs(degrees), side)
    }
}
