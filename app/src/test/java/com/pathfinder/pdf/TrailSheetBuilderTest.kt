package com.pathfinder.pdf

import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.geo.TrailCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TrailSheetBuilderTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    // 02/11/2026 10:30 em Brasília
    private val time = 1_793_626_200_000L

    private val trail = TrailEntity(
        id = 1, name = "Pista Caldas", createdAt = time, updatedAt = time,
        deviationDistanceMeters = 30, deviationTimeSeconds = 300, declinationDegrees = -21.4,
    )
    private val points = TrailCalculator.recalculate(
        listOf(
            TrailPointEntity(id = 1, trailId = 1, sequence = 0, name = "Largada", latitude = -17.7441, longitude = -48.6250, createdAt = 0),
            TrailPointEntity(id = 2, trailId = 1, sequence = 1, name = "Pedra", notes = "Ao lado do ipê", latitude = -17.7400, longitude = -48.6200, createdAt = 0),
        ),
        declinationDegrees = -21.4,
    )

    @Test fun fullSheet() {
        val sheet = TrailSheetBuilder.build(trail, points, SheetOptions(SheetMode.FULL, CoordinateFormat.UTM), time, zone)
        assertEquals("Pista Caldas", sheet.title)
        assertTrue(sheet.headerLines.contains("Data: 02/11/2026"))
        assertTrue(sheet.headerLines.any { it.startsWith("Declinação magnética usada: 21,4° O") })
        assertTrue(sheet.headerLines.contains("2 pontos · distância total 698 m"))
        assertEquals(
            listOf("Nº", "Nome", "Coordenadas", "Az. verdadeiro", "Az. magnético", "Distância", "Observações"),
            sheet.columns.map { it.title },
        )
        assertEquals(listOf("1", "Largada", "22K 751843 E 8036534 N", "Partida", "", "", ""), sheet.rows[0])
        assertEquals("49°", sheet.rows[1][3])
        assertEquals("71°", sheet.rows[1][4]) // 49,4° + 21,4°
        assertEquals("698 m", sheet.rows[1][5])
        assertEquals("Ao lado do ipê", sheet.rows[1][6])
        assertFalse(sheet.writableRows)
        assertEquals("Gerado pelo Path Finder em 02/11/2026 10:30", sheet.footer)
    }

    @Test fun coordinatesOnlyHidesAnswers() {
        val sheet = TrailSheetBuilder.build(
            trail, points, SheetOptions(SheetMode.COORDINATES_ONLY, CoordinateFormat.LAT_LON, includeNotes = false), time, zone,
        )
        assertEquals(6, sheet.columns.size)
        assertEquals(listOf("2", "Pedra", "-17.740000, -48.620000", "", "", ""), sheet.rows[1])
        assertTrue(sheet.headerLines.contains("2 pontos"))
        assertFalse(sheet.headerLines.any { it.contains("distância total") })
        assertTrue(sheet.writableRows)
    }
}
