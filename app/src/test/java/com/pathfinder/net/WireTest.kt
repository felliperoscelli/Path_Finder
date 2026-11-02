package com.pathfinder.net

import com.pathfinder.transfer.PackagePoint
import com.pathfinder.transfer.TrailPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {
    private fun env(body: Body) = Envelope(origin = "dev1", originName = "Ana", createdAt = 1L, priority = Priority.ALERT, body = body)

    @Test fun roundTripEveryMessageType() {
        val bodies = listOf(
            Hello("COORDINATOR", "Ana"),
            PositionUpdate(-17.7, -48.6, 4f, 10, "u", "Pista", "Águias", "João", 2, 7, "Pedra", 12.0, null, false, false),
            DeviationAlert(-17.7, -48.6, 10, 5, 45.0, "Pista", "Águias", "João", repeat = true),
            BackOnRoute(20, 5, 52.0, "Águias", "João"),
            PointReached(30, 2, "Pedra", 7, "Pista", "Águias", "João"),
            TrailShare(TrailPackage(uuid = "u", name = "Pista", createdAt = 0, deviationDistanceMeters = 30,
                deviationTimeSeconds = 300, points = listOf(PackagePoint("A", "", 1.0, 2.0)))),
        )
        for (b in bodies) {
            val e = env(b)
            assertEquals(e, Wire.decode(Wire.encode(e)))
        }
    }

    @Test fun largeTrailCompressesBelowNearbyBytesLimit() {
        // 3 000 amostras de percurso: o JSON passa de 200 kB, comprimido cabe em menos.
        val track = (0 until 3000).map {
            com.pathfinder.transfer.PackageTrackPoint(-17.7 + it * 1e-5, -48.6 + it * 1e-5, 700.0, 1_000_000L + it * 2000)
        }
        val pkg = TrailPackage(uuid = "u", name = "Longa", createdAt = 0, deviationDistanceMeters = 30,
            deviationTimeSeconds = 300, points = emptyList(), track = track)
        val bytes = Wire.encode(env(TrailShare(pkg)))
        assertTrue("${bytes.size} bytes", bytes.size < 200_000)
        assertEquals(pkg, (Wire.decode(bytes).body as TrailShare).trail)
    }
}
