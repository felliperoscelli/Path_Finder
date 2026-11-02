package com.pathfinder.results

import com.pathfinder.data.db.NavigationSessionEntity
import com.pathfinder.data.db.TrailEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ResultsTest {
    private val min = 60_000L

    private fun session(id: Long, group: String, times: Map<Int, Long>, alerts: Int = 0, remote: String? = null) =
        NavigationSessionEntity(
            id = id, trailId = 1, groupName = group, monitorName = "M$id", startedAt = 0, endedAt = 100 * min,
            deviationAlerts = alerts, reachedTimes = NavigationSessionEntity.encodeTimes(times), remoteDevice = remote,
        )

    @Test fun ranksCompletedByTotalTimeThenOthersByPoints() {
        val sessions = listOf(
            session(1, "Águias", mapOf(0 to 5 * min, 1 to 20 * min, 2 to 45 * min)), // 40 min
            session(2, "Lobos", mapOf(0 to 2 * min, 1 to 12 * min, 2 to 32 * min), alerts = 2, remote = "dev"), // 30 min
            session(3, "Corujas", mapOf(0 to 3 * min, 1 to 30 * min)), // não completou
            session(4, "Falcões", emptyMap()),
            session(5, "Andamento", mapOf(0 to 1L)).copy(endedAt = null), // ainda navegando: fora
        )
        val r = Results.compute(sessions, totalPoints = 3)
        assertEquals(listOf("Lobos", "Águias", "Corujas", "Falcões"), r.map { it.groupName })
        assertEquals(listOf(1, 2, null, null), r.map { it.rank })
        assertEquals(30 * min, r[0].totalMillis)
        assertEquals(listOf(10 * min, 20 * min), r[0].legMillis)
        assertEquals(listOf(27 * min, null), r[2].legMillis)
        assertNull(r[2].totalMillis)
        assertFalse(r[2].completed)
        assertEquals(2, r[2].reachedCount)
        assertEquals(true, r[0].remote)
    }

    @Test fun reachedTimesEncoding() {
        val s = session(1, "A", mapOf(2 to 30L, 0 to 10L))
        assertEquals("0:10,2:30", s.reachedTimes)
        assertEquals(mapOf(0 to 10L, 2 to 30L), s.reachedTimeMap)
    }

    @Test fun sheetHasRankingAndLegTables() {
        val trail = TrailEntity(name = "Pista", createdAt = 0, updatedAt = 0, deviationDistanceMeters = 30, deviationTimeSeconds = 300)
        val names = (1..10).map { "P$it" }
        val results = Results.compute(listOf(session(1, "Águias", (0..9).associateWith { it * min })), 10)
        val sheet = ResultsSheetBuilder.build(trail, names, results)
        assertEquals(listOf("1", "Águias", "M1", "10/10", "9min00s", "0", "—"), sheet.rows.single())
        // 9 trechos → duas tabelas (8 + 1) e a lista de pontos.
        assertEquals(3, sheet.extraTables.size)
        assertEquals("9→10", sheet.extraTables[1].columns.last().title)
        assertEquals("1min00s", sheet.extraTables[0].rows.single()[1])
    }
}
