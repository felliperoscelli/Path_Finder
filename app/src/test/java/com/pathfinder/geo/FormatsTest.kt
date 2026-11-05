package com.pathfinder.geo

import com.pathfinder.data.settings.CoordinateFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    @Test fun utm() = assertEquals(
        "22K 751843 E 8036534 N",
        Formats.utm(Utm.fromLatLon(-17.7441, -48.6250)!!),
    )

    @Test fun azimuthRoundsAndWraps() {
        assertEquals("49°", Formats.azimuth(49.4466))
        assertEquals("0°", Formats.azimuth(359.7))
        assertEquals("—", Formats.azimuth(null))
    }

    @Test fun distance() {
        assertEquals("698 m", Formats.distance(697.94))
        assertEquals("1,25 km", Formats.distance(1250.0))
    }

    @Test fun declination() {
        assertEquals("21,4° O", Formats.declination(-21.43))
        assertEquals("3,0° L", Formats.declination(3.0))
    }

    @Test fun bothFormats() = assertEquals(
        "22K 1 E 2 N\n-17.744100, -48.625000",
        Formats.coordinate(CoordinateFormat.BOTH, -17.7441, -48.625, "22K 1 E 2 N"),
    )
}
