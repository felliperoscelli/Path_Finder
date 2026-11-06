package com.pathfinder.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OverzoomTest {
    @Test
    fun umZoomAcimaUsaUmQuartoDoTilePai() {
        // Tile (18, 101, 203): pai é (17, 50, 101); x ímpar → metade direita, y ímpar → metade de baixo.
        assertEquals(OverzoomSource(50, 101, 128, 128, 128), Overzoom.source(18, 101, 203, 17, 256))
        assertEquals(OverzoomSource(50, 101, 0, 0, 128), Overzoom.source(18, 100, 202, 17, 256))
    }

    @Test
    fun doisZoomsAcimaUsaUmDezesseisAvos() {
        // 19 → 17: o tile vira 4×4 pedaços de 64 px; (x % 4, y % 4) = (3, 2).
        assertEquals(OverzoomSource(10, 20, 192, 128, 64), Overzoom.source(19, 43, 82, 17, 256))
    }

    @Test
    fun tileDe512Pixels() {
        assertEquals(OverzoomSource(1, 1, 256, 0, 256), Overzoom.source(5, 3, 2, 4, 512))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zoomDeOrigemPrecisaSerMenor() {
        Overzoom.source(17, 1, 1, 17, 256)
    }
}
