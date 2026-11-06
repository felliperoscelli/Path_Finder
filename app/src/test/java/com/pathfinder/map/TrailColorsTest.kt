package com.pathfinder.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailColorsTest {
    @Test
    fun corSegueAOrdemDeCriacao() {
        val colors = TrailColors.assign(listOf(7L, 3L, 12L))
        assertEquals(TrailColors.PALETTE[0], colors[3L])
        assertEquals(TrailColors.PALETTE[1], colors[7L])
        assertEquals(TrailColors.PALETTE[2], colors[12L])
    }

    @Test
    fun oitoPrimeirasTrilhasTemCoresDiferentes() {
        val colors = TrailColors.assign((1L..8L).toList())
        assertEquals(8, colors.values.toSet().size)
    }

    @Test
    fun depoisDaOitavaAsCoresRecomecam() {
        val colors = TrailColors.assign((1L..9L).toList())
        assertEquals(colors[1L], colors[9L])
        assertNotEquals(colors[1L], colors[2L])
    }

    @Test
    fun semVerdeNemAzulDaPosicao() {
        // Verde = próximo ponto da navegação; azul = GPS e percurso gravado.
        val green = 0xFF2E7D32.toInt()
        val blue = 0xFF1E88E5.toInt()
        assertTrue(green !in TrailColors.PALETTE && blue !in TrailColors.PALETTE)
    }
}
