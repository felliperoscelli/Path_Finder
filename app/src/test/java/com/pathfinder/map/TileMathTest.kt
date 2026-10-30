package com.pathfinder.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    @Test
    fun `zoom 0 has a single tile covering the world`() {
        val world = GeoBounds(north = 85.0, south = -85.0, east = 180.0, west = -180.0)
        assertEquals(1L, TileMath.tilesAtZoom(world, 0))
        assertEquals(4L, TileMath.tilesAtZoom(world, 1))
    }

    @Test
    fun `tile indices match the slippy map reference`() {
        // Caldas Novas (GO) no zoom 13, conferido com a fórmula de referência do OSM.
        assertEquals(2989, TileMath.tileX(-48.6250, 13))
        assertEquals(4506, TileMath.tileY(-17.7441, 13))
    }

    @Test
    fun `tile count sums every zoom level`() {
        val area = GeoBounds(north = -17.70, south = -17.78, east = -48.58, west = -48.67)
        val expected = (10..14).sumOf { TileMath.tilesAtZoom(area, it) }
        assertEquals(expected, TileMath.tileCount(area, 10, 14))
        assertTrue(TileMath.tilesAtZoom(area, 14) > TileMath.tilesAtZoom(area, 13))
    }

    @Test
    fun `a 2 km event area up to zoom 17 fits the download limit`() {
        val area = GeoBounds(north = -17.735, south = -17.755, east = -48.615, west = -48.635)
        assertTrue(TileMath.tileCount(area, 10, TopoMap.MAX_ZOOM) <= OfflineMapManager.MAX_TILES_PER_AREA)
    }

    @Test
    fun `bounds intersection`() {
        val a = GeoBounds(north = 1.0, south = 0.0, east = 1.0, west = 0.0)
        assertTrue(a.intersects(GeoBounds(north = 1.5, south = 0.5, east = 1.5, west = 0.5)))
        assertFalse(a.intersects(GeoBounds(north = 3.0, south = 2.0, east = 3.0, west = 2.0)))
    }
}
