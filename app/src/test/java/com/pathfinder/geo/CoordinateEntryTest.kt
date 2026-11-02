package com.pathfinder.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CoordinateEntryTest {
    private fun assertLatLon(expectedLat: Double, expectedLon: Double, text: String, tol: Double = 1e-6) {
        val p = CoordinateParser.parseLatLon(text)
        assertNotNull("não leu: $text", p)
        assertEquals(text, expectedLat, p!!.latitude, tol)
        assertEquals(text, expectedLon, p.longitude, tol)
    }

    @Test fun decimalFormats() {
        assertLatLon(-17.7441, -48.625, "-17.7441, -48.6250")
        assertLatLon(-17.7441, -48.625, "-17.7441,-48.6250")
        assertLatLon(-17.7441, -48.625, "-17,7441 -48,6250")
        assertLatLon(-17.7441, -48.625, "-17,7441; -48,6250")
        assertLatLon(-17.7441, -48.625, "17.7441 S 48.6250 W")
        assertLatLon(-17.7441, -48.625, "S17.7441 O48.625")
    }

    @Test fun degreesMinutesSeconds() {
        val lat = -(17 + 44 / 60.0 + 38.8 / 3600)
        val lon = -(48 + 37 / 60.0 + 30.0 / 3600)
        assertLatLon(lat, lon, "17°44'38.8\"S 48°37'30.0\"W")
        assertLatLon(lat, lon, "17º 44' 38,8\" S, 48º 37' 30\" O")
        assertLatLon(lat, lon, "S 17 44 38.8 W 48 37 30")
        assertLatLon(lat, lon, "-17 44 38.8 -48 37 30")
        // Longitude primeiro, marcada: troca a ordem.
        assertLatLon(lat, lon, "48°37'30\"W 17°44'38.8\"S")
    }

    @Test fun degreesDecimalMinutes() {
        assertLatLon(-(17 + 44.647 / 60), -(48 + 37.5 / 60), "17°44.647'S 48°37.5'W")
    }

    @Test fun rejectsGarbage() {
        assertNull(CoordinateParser.parseLatLon("rua das flores 12"))
        assertNull(CoordinateParser.parseLatLon("-17.7441"))
        assertNull(CoordinateParser.parseLatLon("95, 10"))
        assertNull(CoordinateParser.parseLatLon("17 75 00 S 48 00 00 W")) // minutos ≥ 60
    }

    @Test fun utmInverseRoundTrip() {
        // Valores de referência do pyproj (mesmos do UtmTest).
        val p = Utm.toLatLon(22, southern = true, easting = 751843.004, northing = 8036534.447)!!
        assertEquals(-17.7441, p.latitude, 1e-7)
        assertEquals(-48.6250, p.longitude, 1e-7)
        val paris = Utm.toLatLon(31, southern = false, easting = 448250.599, northing = 5411951.599)!!
        assertEquals(48.8583701, paris.latitude, 1e-7)
        assertEquals(2.2944813, paris.longitude, 1e-7)
        for ((lat, lon) in listOf(-33.8568 to 151.2153, 0.5 to 3.0, -5.0 to -35.2, 60.0 to 5.0)) {
            val u = Utm.fromLatLon(lat, lon)!!
            val back = Utm.toLatLon(u.zone, u.isSouthern, u.easting, u.northing)!!
            assertEquals(lat, back.latitude, 1e-8)
            assertEquals(lon, back.longitude, 1e-8)
        }
        assertNull(Utm.toLatLon(61, true, 500000.0, 8000000.0))
        assertNull(Utm.toLatLon(22, true, 50.0, 8000000.0))
    }

    @Test fun timeOfDay() {
        assertEquals(17 * 60, TimeOfDay.parse("17:00"))
        assertEquals(17 * 60, TimeOfDay.parse("17h"))
        assertEquals(17 * 60 + 30, TimeOfDay.parse("17h30"))
        assertEquals(9 * 60 + 5, TimeOfDay.parse("0905"))
        assertNull(TimeOfDay.parse("25:00"))
        assertNull(TimeOfDay.parse("abc"))
        assertEquals("07:05", TimeOfDay.format(7 * 60 + 5))
        val zone = ZoneId.of("America/Sao_Paulo")
        // Início 02/11/2026 10:30 → 17:00 do mesmo dia; início 18:00 → 17:00 do dia seguinte.
        val start = 1_793_626_200_000L
        assertEquals(start + (6 * 60 + 30) * 60_000L, TimeOfDay.nextOccurrence(start, 17 * 60, zone))
        val evening = start + (7 * 60 + 30) * 60_000L
        assertEquals(start + (30 * 60 + 30) * 60_000L, TimeOfDay.nextOccurrence(evening, 17 * 60, zone))
    }
}
