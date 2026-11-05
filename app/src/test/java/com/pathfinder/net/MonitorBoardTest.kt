package com.pathfinder.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorBoardTest {
    private fun env(device: String, body: Body, id: String = java.util.UUID.randomUUID().toString()) =
        Envelope(id = id, origin = device, originName = "x", createdAt = 0, body = body)

    private fun pos(t: Long, alarm: Boolean = false, next: Int = 1) =
        PositionUpdate(-17.7, -48.6, 5f, t, "u", "Pista", "Águias", "João", next, 5, null, 10.0, null, alarm)

    @Test fun positionsKeepTheNewest() {
        var s = MonitorBoard.reduce(BoardState(), env("d1", pos(100, next = 2)))
        s = MonitorBoard.reduce(s, env("d1", pos(50, next = 1))) // atrasada
        assertEquals(2, s.monitors.getValue("d1").nextIndex)
        assertEquals(100L, s.monitors.getValue("d1").updatedAt)
    }

    @Test fun deviationAlertRepeatsUpdateOneEntryAndBackOnRouteClosesIt() {
        val alert = DeviationAlert(-17.7, -48.6, 400, since = 100, distanceMeters = 45.0, trailName = "Pista",
            groupName = "Águias", monitorName = "João", repeat = false)
        var s = MonitorBoard.reduce(BoardState(), env("d1", pos(90)))
        s = MonitorBoard.reduce(s, env("d1", alert))
        s = MonitorBoard.reduce(s, env("d1", alert.copy(time = 460, distanceMeters = 60.0, repeat = true)))
        assertEquals(1, s.alerts.size)
        assertEquals(60.0, s.alerts.single().distanceMeters, 0.0)
        assertTrue(s.monitors.getValue("d1").alarm)
        assertEquals(1, s.activeAlerts.size)

        s = MonitorBoard.reduce(s, env("d1", BackOnRoute(500, 100, 60.0, "Águias", "João")))
        assertFalse(s.monitors.getValue("d1").alarm)
        assertTrue(s.activeAlerts.isEmpty())
        assertEquals(1, s.alerts.size) // continua no histórico
    }

    @Test fun alertFromUnknownMonitorCreatesIt() {
        val s = MonitorBoard.reduce(
            BoardState(),
            env("d2", DeviationAlert(-17.0, -48.0, 10, 5, 40.0, "Pista", "Lobos", "Ana", false)),
        )
        assertEquals("Lobos", s.monitors.getValue("d2").groupName)
        assertTrue(s.monitors.getValue("d2").alarm)
    }

    @Test fun pointReachedAdvancesProgress() {
        var s = MonitorBoard.reduce(BoardState(), env("d1", pos(100, next = 1)))
        s = MonitorBoard.reduce(s, env("d1", PointReached(120, 4, "Final", 5, "Pista", "Águias", "João")))
        val m = s.monitors.getValue("d1")
        assertEquals(5, m.nextIndex)
        assertEquals("Final", m.lastReachedName)
        assertTrue(m.finished)
    }

    @Test fun emergencyStaysOpenUntilAcknowledged() {
        var s = MonitorBoard.reduce(BoardState(), env("d1", pos(100)))
        val e = env("d1", Emergency(150, "MONITOR", "João", "Águias", "Pista", -17.8, -48.7, 5f), id = "e1")
        s = MonitorBoard.reduce(s, e)
        assertEquals(1, s.openEmergencies.size)
        assertEquals(-17.8, s.monitors.getValue("d1").latitude, 0.0) // posição atualizada
        s = MonitorBoard.acknowledge(s, "e1")
        assertTrue(s.openEmergencies.isEmpty())
        assertEquals(1, s.emergencies.size)
    }
}

class WatchdogTest {
    private val min = 60_000L
    private fun env(body: Body, at: Long) = Envelope(origin = "d1", originName = "x", createdAt = at, body = body)
    private fun pos(t: Long, battery: Int? = 80, deadline: Long? = null, finished: Boolean = false) =
        PositionUpdate(-17.7, -48.6, 5f, t, "u", "Pista", "Águias", "João", 1, 5,
            batteryPercent = battery, deadlineAt = deadline, finished = finished)

    @Test fun noSignalIsRaisedOnceAndClearsWithNews() {
        var s = MonitorBoard.reduce(BoardState(), env(pos(0), 0), now = 0)
        var (s2, raised) = MonitorBoard.watch(s, 5 * min, 10 * min)
        assertTrue(raised.isEmpty())
        val r = MonitorBoard.watch(s2, 11 * min, 10 * min)
        s2 = r.first
        assertEquals(listOf("d1" to WatchKind.NO_SIGNAL), r.second)
        assertTrue(MonitorBoard.watch(s2, 12 * min, 10 * min).second.isEmpty()) // não repete
        // Chega notícia (o relógio do monitor está 1 h atrasado: não importa).
        s = MonitorBoard.reduce(s2, env(pos(-60 * min), -60 * min), now = 13 * min)
        val after = MonitorBoard.watch(s, 14 * min, 10 * min)
        assertTrue(after.first.watch.isEmpty())
    }

    @Test fun overdueAndLowBattery() {
        val s = MonitorBoard.reduce(BoardState(), env(pos(0, battery = 12, deadline = 30 * min), 0), now = 0)
        val (w1, r1) = MonitorBoard.watch(s, 5 * min, 60 * min)
        assertEquals(listOf("d1" to WatchKind.LOW_BATTERY), r1)
        val (_, r2) = MonitorBoard.watch(w1, 31 * min, 60 * min)
        assertEquals(listOf("d1" to WatchKind.OVERDUE), r2)
        // Terminou a pista: não está atrasado.
        val done = MonitorBoard.reduce(BoardState(), env(pos(0, deadline = 30 * min, finished = true), 0), now = 0)
        assertTrue(MonitorBoard.watch(done, 31 * min, 60 * min).second.isEmpty())
    }

    @Test fun endedNavigationIsNotWatched() {
        var s = MonitorBoard.reduce(BoardState(), env(pos(0), 0), now = 0)
        s = MonitorBoard.reduce(
            s,
            env(NavigationEnded(5 * min, "u", "Pista", "Águias", "João", 0, mapOf(0 to 1L), 5, 0, 0), 5 * min),
            now = 5 * min,
        )
        assertTrue(s.monitors.getValue("d1").ended)
        assertTrue(MonitorBoard.watch(s, 100 * min, 10 * min).second.isEmpty())
    }
}
