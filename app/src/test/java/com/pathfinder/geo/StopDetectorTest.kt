package com.pathfinder.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StopDetectorTest {
    private val lat = -17.7441
    private val lon = -48.6250
    private val meter = 1.0 / 111_000 // ~1 m em latitude

    @Test fun firesOnceAfterStoppingThenRearmsAfterMoving() {
        val d = StopDetector(stopMillis = 25_000)
        // Pequenas oscilações de GPS (±3 m) durante 30 s.
        var fired = 0
        for (t in 0..30 step 2) {
            val jitter = if (t % 4 == 0) 3 * meter else -3 * meter
            if (d.onFix(lat + jitter, lon, 800.0, 5f, t * 1000L) != null) fired++
        }
        assertEquals(1, fired)

        // Anda 50 m e para de novo: deve disparar outra vez.
        val far = lat + 50 * meter
        var second: StopCandidate? = null
        for (t in 40..70 step 2) {
            second = d.onFix(far, lon, 800.0, 5f, t * 1000L) ?: second
        }
        assertNotNull(second)
        assertEquals(far, second!!.latitude, 1e-9)
    }

    @Test fun walkingNeverFires() {
        val d = StopDetector(stopMillis = 25_000)
        for (t in 0..120 step 2) {
            // ~1,5 m/s
            assertNull(d.onFix(lat + t * 1.5 * meter, lon, null, 5f, t * 1000L))
        }
    }

    @Test fun suggestedPositionIsTheAverage() {
        val d = StopDetector(stopMillis = 4_000)
        d.onFix(lat + 2 * meter, lon, 100.0, 5f, 0)
        d.onFix(lat - 2 * meter, lon, 102.0, 5f, 2_000)
        val c = d.onFix(lat, lon, null, 5f, 4_000)!!
        assertEquals(lat, c.latitude, 1e-9)
        assertEquals(101.0, c.altitude!!, 1e-9)
    }
}
