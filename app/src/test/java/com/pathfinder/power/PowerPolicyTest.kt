package com.pathfinder.power

import com.pathfinder.geo.LatLon
import com.pathfinder.navigation.NavigationConfig
import com.pathfinder.navigation.NavigationEngine
import com.pathfinder.navigation.NavigationEvent
import com.pathfinder.navigation.SimulatedWalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPolicyTest {
    private val min = 60_000L
    private val base = 30_000L

    @Test fun normalAndStationary() {
        val p = PowerPolicy.plan(PowerMode.AUTO, 80, false, 0, base)
        assertEquals(2_000L, p.gpsIntervalMillis)
        assertEquals(base, p.publishIntervalMillis)
        assertFalse(p.saving)
        assertEquals(10_000L, PowerPolicy.plan(PowerMode.AUTO, 80, false, 3 * min, base).gpsIntervalMillis)
    }

    @Test fun lowAndCriticalBattery() {
        val low = PowerPolicy.plan(PowerMode.AUTO, 25, false, 0, base)
        assertTrue(low.saving)
        assertEquals(5_000L, low.gpsIntervalMillis)
        assertEquals(60_000L, low.publishIntervalMillis)
        val critical = PowerPolicy.plan(PowerMode.AUTO, 8, false, 0, base)
        assertEquals(10_000L, critical.gpsIntervalMillis)
        assertEquals(120_000L, critical.publishIntervalMillis)
    }

    @Test fun chargingOrOffIgnoresBattery() {
        assertFalse(PowerPolicy.plan(PowerMode.AUTO, 5, true, 10 * min, base).saving)
        assertEquals(2_000L, PowerPolicy.plan(PowerMode.OFF, 5, false, 10 * min, base).gpsIntervalMillis)
        assertTrue(PowerPolicy.plan(PowerMode.ALWAYS, 90, false, 0, base).saving)
    }

    @Test fun motionTracker() {
        val m = 1.0 / 111_320.0
        val t = MotionTracker()
        t.onFix(LatLon(0.0, 0.0), 0)
        t.onFix(LatLon(5 * m, 0.0), 60_000) // oscilação de GPS
        assertEquals(90_000L, t.stationaryFor(90_000))
        t.onFix(LatLon(40 * m, 0.0), 100_000) // andou
        assertEquals(0L, t.stationaryFor(100_000))
    }

    @Test fun simulatedWalkReachesAllPointsAndTriggersDeviation() {
        val m = 1.0 / 111_320.0
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.0, 300 * m), LatLon(300 * m, 300 * m))
        val walk = SimulatedWalk.generate(points, deviateOnLeg = 0, deviationMillis = 6 * min)
        val engine = NavigationEngine(points, emptyList(), -21.0, NavigationConfig(30.0, 5 * min, 15.0))
        val events = walk.flatMap { engine.onFix(it.position.latitude, it.position.longitude, it.accuracyMeters, it.offsetMillis) }
        assertEquals(listOf(0, 1, 2), events.filterIsInstance<NavigationEvent.PointReached>().map { it.index })
        assertTrue(events.any { it is NavigationEvent.DeviationAlarm })
        assertTrue(events.any { it is NavigationEvent.BackOnRoute })
        assertTrue(engine.status.finished)
        // Sem desvio: nenhum alarme.
        val clean = NavigationEngine(points, emptyList(), -21.0, NavigationConfig(30.0, 5 * min, 15.0))
        val cleanEvents = SimulatedWalk.generate(points).flatMap { clean.onFix(it.position.latitude, it.position.longitude, 5f, it.offsetMillis) }
        assertTrue(cleanEvents.none { it is NavigationEvent.DeviationAlarm })
    }
}

class DiagnosticsMathTest {
    private val min = 60_000L

    @Test fun drainUsesOnlyTheLatestDischargingRun() {
        val samples = listOf(
            com.pathfinder.diagnostics.BatterySample(0, 50, charging = true),
            com.pathfinder.diagnostics.BatterySample(10 * min, 90, charging = false),
            com.pathfinder.diagnostics.BatterySample(40 * min, 84, charging = false),
            com.pathfinder.diagnostics.BatterySample(70 * min, 78, charging = false),
        )
        assertEquals(12.0, com.pathfinder.diagnostics.DiagnosticsMath.drainPerHour(samples)!!, 0.01)
        assertEquals(6.5, com.pathfinder.diagnostics.DiagnosticsMath.hoursLeft(78, 12.0)!!, 0.01)
        // Menos de 15 min de dados: sem estimativa.
        assertEquals(null, com.pathfinder.diagnostics.DiagnosticsMath.drainPerHour(samples.take(2)))
    }

    @Test fun percentiles() {
        val v = listOf(3f, 5f, 4f, 20f, 6f)
        assertEquals(5f, com.pathfinder.diagnostics.DiagnosticsMath.percentile(v, 50))
        assertEquals(20f, com.pathfinder.diagnostics.DiagnosticsMath.percentile(v, 100))
        assertEquals(null, com.pathfinder.diagnostics.DiagnosticsMath.percentile(emptyList(), 50))
    }
}
