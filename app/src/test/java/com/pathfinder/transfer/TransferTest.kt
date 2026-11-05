package com.pathfinder.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferTest {
    private val pkg = TrailPackage(
        uuid = "abc-123",
        name = "Pista \"Ipê & Cia\" <teste>",
        notes = "Levar água",
        createdAt = 1_793_626_200_000L,
        deviationDistanceMeters = 40,
        deviationTimeSeconds = 240,
        declinationDegrees = -21.4,
        points = listOf(
            PackagePoint("Largada", "", -17.7441, -48.6250, 700.5),
            PackagePoint("Pedra & tronco", "Atrás do ipê", -17.7400, -48.6200),
        ),
        track = listOf(
            PackageTrackPoint(-17.7441, -48.6250, 700.0, 1_793_626_200_000L),
            PackageTrackPoint(-17.7420, -48.6230, null, 1_793_626_260_000L),
        ),
    )

    @Test fun jsonRoundTrip() {
        val text = TrailJson.encode(pkg)
        assertTrue(text.contains("\"format\": \"pathfinder-trail\""))
        assertEquals(pkg, TrailJson.decode(text))
    }

    @Test fun jsonIgnoresUnknownFieldsFromNewerVersions() {
        val text = TrailJson.encode(pkg).replaceFirst("{", "{\"futureField\": 1,")
        assertEquals(pkg, TrailJson.decode(text))
    }

    @Test fun rejectsOtherJson() {
        assertThrows(IllegalArgumentException::class.java) { TrailJson.decode("""{"hello": 1}""") }
        assertThrows(IllegalArgumentException::class.java) {
            TrailJson.decode(TrailJson.encode(pkg).replace("pathfinder-trail", "other"))
        }
    }

    @Test fun gpxRoundTrip() {
        val xml = Gpx.write(pkg)
        assertTrue(xml.contains("&quot;Ipê &amp; Cia&quot; &lt;teste&gt;"))
        val back = Gpx.read(xml.toByteArray(), "arquivo", 30, 300)
        assertEquals(pkg.name, back.name)
        assertEquals(pkg.notes, back.notes)
        assertEquals(pkg.createdAt, back.createdAt)
        assertEquals(pkg.points, back.points)
        assertEquals(pkg.track.map { it.copy(altitude = it.altitude) }, back.track)
        assertEquals(30, back.deviationDistanceMeters)
        assertNull(back.declinationDegrees) // recalculada ao importar
        assertTrue(back.uuid != pkg.uuid)
    }

    @Test fun gpxFromOtherAppsWithWaypointsOnly() {
        val xml = """<?xml version="1.0"?>
            <gpx:gpx xmlns:gpx="http://www.topografix.com/GPX/1/1" version="1.1">
              <gpx:wpt lat="-17.70" lon="-48.60"><gpx:name>A</gpx:name></gpx:wpt>
              <gpx:wpt lat="-17.71" lon="-48.61"/>
            </gpx:gpx>""".trimIndent()
        val back = Gpx.read(xml.toByteArray(), "minha_trilha", 30, 300)
        assertEquals("minha_trilha", back.name)
        assertEquals(listOf("A", "Ponto 2"), back.points.map { it.name })
        assertEquals(-48.61, back.points[1].longitude, 0.0)
    }

    @Test fun gpxRejectsDoctypeAndGarbage() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx><wpt lat="1" lon="1"><name>&x;</name></wpt></gpx>"""
        assertThrows(IllegalArgumentException::class.java) { Gpx.read(xxe.toByteArray(), "x", 30, 300) }
        assertThrows(IllegalArgumentException::class.java) { Gpx.read("não é xml".toByteArray(), "x", 30, 300) }
        assertThrows(IllegalArgumentException::class.java) { Gpx.read("<gpx/>".toByteArray(), "x", 30, 300) }
    }
}
