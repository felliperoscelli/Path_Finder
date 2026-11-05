package com.pathfinder.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Valores de referência gerados com pyproj (EPSG:326xx/327xx). */
class UtmTest {
    private fun check(lat: Double, lon: Double, zone: Int, band: Char, e: Double, n: Double) {
        val utm = Utm.fromLatLon(lat, lon)!!
        assertEquals(zone, utm.zone)
        assertEquals(band, utm.band)
        assertEquals(e, utm.easting, 0.01)
        assertEquals(n, utm.northing, 0.01)
    }

    @Test fun caldasNovas() = check(-17.7441, -48.6250, 22, 'K', 751843.004, 8036534.447)

    @Test fun paris() = check(48.8583701, 2.2944813, 31, 'U', 448250.599, 5411951.599)

    @Test fun equatorOnCentralMeridian() = check(0.0, 3.0, 31, 'N', 500000.0, 0.0)

    @Test fun sydney() = check(-33.8568, 151.2153, 56, 'H', 334900.570, 6252288.753)

    @Test fun norwayException() = check(60.0, 5.0, 32, 'V', 276979.926, 6658157.202)

    @Test fun outsideUtmRange() {
        assertNull(Utm.fromLatLon(85.0, 0.0))
        assertNull(Utm.fromLatLon(-81.0, 0.0))
    }
}
