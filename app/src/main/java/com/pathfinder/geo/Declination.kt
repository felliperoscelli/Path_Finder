package com.pathfinder.geo

import android.hardware.GeomagneticField

/** Fornece a declinação magnética (graus; positivo = leste, negativo = oeste). */
fun interface DeclinationProvider {
    fun declinationAt(latitude: Double, longitude: Double, altitudeMeters: Double, timeMillis: Long): Double
}

/** Declinação pelo modelo geomagnético mundial embutido no Android. */
object AndroidDeclinationProvider : DeclinationProvider {
    override fun declinationAt(latitude: Double, longitude: Double, altitudeMeters: Double, timeMillis: Long): Double =
        GeomagneticField(latitude.toFloat(), longitude.toFloat(), altitudeMeters.toFloat(), timeMillis)
            .declination.toDouble()
}
