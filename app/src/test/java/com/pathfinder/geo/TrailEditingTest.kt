package com.pathfinder.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailEditingTest {
    private val ids = listOf(1L, 2L, 3L, 4L)

    @Test fun moveDownUpAndClamp() {
        assertEquals(listOf(2L, 1L, 3L, 4L), TrailEditing.move(ids, { it }, 1L, 1))
        assertEquals(listOf(4L, 1L, 2L, 3L), TrailEditing.move(ids, { it }, 4L, 0))
        assertEquals(listOf(2L, 3L, 4L, 1L), TrailEditing.move(ids, { it }, 1L, 99))
        assertEquals(ids, TrailEditing.move(ids, { it }, 42L, 0))
    }

    @Test fun renumberedShowsOnlyThePointsThatChange() {
        val names = listOf("Base", "Pedra", "Árvore", "Ponte")
        // Árvore (3º) vira o 2º; Pedra passa a ser o 3º.
        assertEquals(listOf(1 to "Árvore", 2 to "Pedra"), TrailEditing.renumbered(names, 2, 1))
        assertEquals(listOf(0 to "Pedra", 1 to "Árvore", 2 to "Base"), TrailEditing.renumbered(names, 0, 2))
        assertEquals(emptyList<Pair<Int, String>>(), TrailEditing.renumbered(names, 1, 1))
        assertEquals(listOf(1 to "Árvore", 2 to "Ponte", 3 to "Pedra"), TrailEditing.renumbered(names, 1, 99))
        // Mesma ordem que move().
        val moved = TrailEditing.move(ids, { it }, 3L, 1)
        TrailEditing.renumbered(ids, 2, 1).forEach { (i, id) -> assertEquals(moved[i], id) }
    }

    @Test fun insertAtStartMiddleEnd() {
        assertEquals(listOf(9L, 1L, 2L, 3L, 4L), TrailEditing.insert(ids, 9L, 0))
        assertEquals(listOf(1L, 2L, 9L, 3L, 4L), TrailEditing.insert(ids, 9L, 2))
        assertEquals(listOf(1L, 2L, 3L, 4L, 9L), TrailEditing.insert(ids, 9L, Int.MAX_VALUE))
    }

    // Trilha em "L": A(0,0) → B 1 km a leste → C 1 km ao norte de B.
    private val m = 1.0 / 111_320.0
    private val a = LatLon(0.0, 0.0)
    private val b = LatLon(0.0, 1000 * m)
    private val c = LatLon(1000 * m, 1000 * m)

    @Test fun suggestsSegmentNextToThePoint() {
        // 50 m ao sul do meio de A→B: entra entre A e B.
        assertEquals(1, TrailEditing.suggestInsertIndex(listOf(a, b, c), LatLon(-50 * m, 500 * m)))
        // 30 m a leste do meio de B→C: entra entre B e C.
        assertEquals(2, TrailEditing.suggestInsertIndex(listOf(a, b, c), LatLon(500 * m, 1030 * m)))
    }

    @Test fun pointBeyondTheEndGoesLast() {
        assertEquals(3, TrailEditing.suggestInsertIndex(listOf(a, b, c), LatLon(1200 * m, 1000 * m)))
    }

    @Test fun shortTrails() {
        assertEquals(0, TrailEditing.suggestInsertIndex(emptyList(), a))
        assertEquals(1, TrailEditing.suggestInsertIndex(listOf(a), b))
    }
}
