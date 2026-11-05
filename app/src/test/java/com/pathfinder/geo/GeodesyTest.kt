package com.pathfinder.geo

import com.pathfinder.data.db.TrailPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Valores de referência gerados com pyproj.Geod (WGS84). */
class GeodesyTest {
    private val start = LatLon(-17.7441, -48.6250)

    @Test fun shortLegNorthEast() {
        val r = Geodesy.inverse(start, LatLon(-17.7400, -48.6200))
        assertEquals(697.9406, r.distanceMeters, 0.001)
        assertEquals(49.446603, r.trueAzimuthDegrees, 1e-5)
    }

    @Test fun dueSouth() {
        val r = Geodesy.inverse(start, LatLon(-17.7500, -48.6250))
        assertEquals(652.9974, r.distanceMeters, 0.001)
        assertEquals(180.0, r.trueAzimuthDegrees, 1e-6)
    }

    @Test fun oneDegreeAlongEquator() {
        val r = Geodesy.inverse(LatLon(0.0, 0.0), LatLon(0.0, 1.0))
        assertEquals(111319.4908, r.distanceMeters, 0.001)
        assertEquals(90.0, r.trueAzimuthDegrees, 1e-6)
    }

    @Test fun samePoint() = assertEquals(0.0, Geodesy.distance(start, start), 0.0)

    @Test fun magneticAzimuthWithWestDeclination() {
        // Declinação oeste (negativa): o azimute magnético fica maior que o verdadeiro.
        assertEquals(71.0, magneticAzimuth(50.0, -21.0), 1e-9)
        assertEquals(1.0, magneticAzimuth(340.0, -21.0), 1e-9)
        assertEquals(355.0, magneticAzimuth(5.0, 10.0), 1e-9)
    }

    @Test fun recalculateRenumbersAndFillsLegs() {
        fun p(id: Long, seq: Int, lat: Double, lon: Double) =
            TrailPointEntity(id = id, trailId = 1, sequence = seq, name = "P$id", latitude = lat, longitude = lon, createdAt = 0)
        val result = TrailCalculator.recalculate(
            listOf(p(2, 7, -17.7400, -48.6200), p(1, 3, -17.7441, -48.6250)),
            declinationDegrees = -21.0,
        )
        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.sequence })
        assertNull(result[0].trueAzimuthDegrees)
        assertNull(result[0].distanceFromPreviousMeters)
        assertEquals(22, result[0].utmZone)
        assertEquals("K", result[0].utmBand)
        assertEquals(697.9406, result[1].distanceFromPreviousMeters!!, 0.001)
        assertEquals(49.446603, result[1].trueAzimuthDegrees!!, 1e-5)
        assertEquals(70.446603, result[1].magneticAzimuthDegrees!!, 1e-5)
    }
}
