package com.pathfinder.navigation

import com.pathfinder.geo.LatLon
import com.pathfinder.navigation.NavigationEvent.BackOnRoute
import com.pathfinder.navigation.NavigationEvent.DeviationAlarm
import com.pathfinder.navigation.NavigationEvent.Finished
import com.pathfinder.navigation.NavigationEvent.PointReached
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineTest {
    private val m = 1.0 / 111_320.0 // ~1 m em graus perto do equador

    // Pista reta para o leste: P0 (0 m), P1 (500 m), P2 (1000 m).
    private val p0 = LatLon(0.0, 0.0)
    private val p1 = LatLon(0.0, 500 * m)
    private val p2 = LatLon(0.0, 1000 * m)
    private val config = NavigationConfig(
        deviationDistanceMeters = 30.0,
        deviationTimeMillis = 5 * 60_000,
        arrivalRadiusMeters = 15.0,
    )

    private fun engine(track: List<LatLon> = emptyList()) = NavigationEngine(listOf(p0, p1, p2), track, -21.0, config)

    private fun NavigationEngine.at(eastM: Double, northM: Double, tSec: Long, acc: Float = 5f) =
        onFix(northM * m, eastM * m, acc, tSec * 1000)

    @Test fun reachesPointsInOrderAndFinishes() {
        val e = engine()
        assertEquals(listOf(PointReached(0)), e.at(5.0, 0.0, 0))
        assertEquals(1, e.status.nextIndex)
        assertEquals(495.0, e.status.distanceToNextMeters!!, 1.0)
        assertEquals(90.0, e.status.trueBearingToNext!!, 0.1)
        assertEquals(111.0, e.status.magneticBearingToNext!!, 0.1) // 90° − (−21°)
        assertTrue(e.at(250.0, 0.0, 200).isEmpty())
        assertEquals(listOf(PointReached(1)), e.at(490.0, 0.0, 400))
        assertEquals(listOf(PointReached(2), Finished), e.at(995.0, 3.0, 800))
        assertTrue(e.status.finished)
    }

    @Test fun arrivingAheadSkipsPoints() {
        val e = engine()
        e.at(0.0, 0.0, 0)
        assertEquals(listOf(PointReached(2), Finished), e.at(1000.0, 0.0, 60))
        assertEquals(setOf(0, 2), e.status.reached)
    }

    @Test fun noDeviationAlarmBeforeReachingTheStart() {
        val e = engine()
        for (t in 0..900L step 10) assertTrue(e.at(-300.0, 200.0, t).isEmpty())
        assertFalse(e.status.monitoring)
    }

    @Test fun alarmAfterFiveMinutesOffRouteThenBackOnRoute() {
        val e = engine()
        e.at(0.0, 0.0, 0) // partida
        // 45 m ao norte da rota durante 4:50: ainda sem alarme.
        for (t in 10..300L step 10) {
            val events = e.at(200.0, 45.0, t)
            if (t < 310) assertTrue("t=$t", events.isEmpty())
        }
        assertEquals(10_000L, e.status.offRouteSince)
        val alarm = e.at(200.0, 45.0, 310)
        assertTrue(alarm.single() is DeviationAlarm)
        assertFalse((alarm.single() as DeviationAlarm).repeat)
        assertTrue(e.status.alarmActive)
        // Repete a cada minuto.
        assertTrue(e.at(200.0, 50.0, 340).isEmpty())
        assertTrue((e.at(200.0, 50.0, 370).single() as DeviationAlarm).repeat)
        // Volta à rota.
        val back = e.at(220.0, 5.0, 400).single() as BackOnRoute
        assertEquals(10_000L, back.since)
        assertEquals(50.0, back.maxDistanceMeters, 0.5)
        assertNull(e.status.offRouteSince)
        assertFalse(e.status.alarmActive)
    }

    @Test fun briefExcursionsResetTheTimer() {
        val e = engine()
        e.at(0.0, 0.0, 0)
        for (t in 10..1200L step 10) {
            // Alterna 4 min fora e uma leitura na rota.
            val north = if (t % 250 == 0L) 0.0 else 45.0
            assertTrue("t=$t", e.at(100.0 + t / 10.0, north, t).none { it is DeviationAlarm })
        }
    }

    @Test fun silenceStopsRepeatsUntilBackOnRoute() {
        val e = engine()
        e.at(0.0, 0.0, 0)
        for (t in 10..310L step 10) e.at(200.0, 45.0, t)
        e.silence()
        for (t in 320..600L step 10) assertTrue(e.at(200.0, 45.0, t).isEmpty())
        assertTrue(e.status.silenced)
        assertTrue(e.at(200.0, 0.0, 610).single() is BackOnRoute)
        assertFalse(e.status.silenced)
    }

    @Test fun inaccurateFixesAreIgnoredForDeviation() {
        val e = engine()
        e.at(0.0, 0.0, 0)
        for (t in 10..900L step 10) assertTrue(e.at(200.0, 45.0, t, acc = 80f).isEmpty())
        assertNull(e.status.offRouteSince)
    }

    @Test fun recordedTrackCountsAsRoute() {
        // O coordenador contornou um lago: andou 60 m ao norte entre 150 e 350 m.
        val detour = listOf(p0, LatLon(60 * m, 150 * m), LatLon(60 * m, 350 * m), p1)
        val e = engine(track = detour)
        e.at(0.0, 0.0, 0)
        for (t in 10..900L step 10) assertTrue(e.at(250.0, 58.0, t).isEmpty())
        assertEquals(2.0, e.status.distanceToRouteMeters!!, 0.5)
    }

    @Test fun skipAndBack() {
        val e = engine()
        e.at(0.0, 0.0, 0)
        e.skip()
        assertEquals(2, e.status.nextIndex)
        e.back(); e.back(); e.back()
        assertEquals(0, e.status.nextIndex)
    }
}

class ReturnAndDeadlineTest {
    private val m = 1.0 / 111_320.0
    private val config = NavigationConfig(30.0, 5 * 60_000, 15.0)
    private val points = listOf(LatLon(0.0, 0.0), LatLon(0.0, 500 * m), LatLon(0.0, 1000 * m))

    @Test fun returnToBaseGuidesHomeWithoutDeviationAlarm() {
        val e = NavigationEngine(points, emptyList(), -21.0, config)
        e.onFix(0.0, 0.0, 5f, 0)
        e.onFix(0.0, 300 * m, 5f, 60_000)
        e.startReturn(LatLon(0.0, 0.0))
        // Longe da rota por muito tempo: sem alarme enquanto volta.
        for (t in 1..20L) assertTrue(e.onFix(200 * m, 300 * m, 5f, 60_000 + t * 60_000).isEmpty())
        val s = e.status
        assertTrue(s.returningToBase)
        assertEquals(360.6, s.distanceToNextMeters!!, 1.0)
        assertEquals(236.3, s.trueBearingToNext!!, 0.5) // sudoeste
        assertEquals(listOf(NavigationEvent.ArrivedAtBase), e.onFix(5 * m, 3 * m, 5f, 2_000_000))
        assertTrue(e.status.arrivedAtBase)
        assertTrue(e.onFix(0.0, 0.0, 5f, 2_100_000).isEmpty()) // avisa uma vez só
        e.stopReturn()
        assertFalse(e.status.returningToBase)
        assertEquals(1, e.status.nextIndex)
    }

    @Test fun deadlineWarnsOnceThenReaches() {
        val d = DeadlineTracker(deadlineAt = 100 * 60_000L)
        assertNull(d.check(60 * 60_000L))
        assertEquals(DeadlineEvent.WARNING, d.check(70 * 60_000L))
        assertNull(d.check(80 * 60_000L))
        assertEquals(DeadlineEvent.REACHED, d.check(100 * 60_000L))
        assertNull(d.check(120 * 60_000L))
        // Começou depois da hora: só "chegou a hora".
        assertEquals(DeadlineEvent.REACHED, DeadlineTracker(10L).check(20L))
    }
}
