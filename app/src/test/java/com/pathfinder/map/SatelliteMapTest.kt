package com.pathfinder.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteMapTest {

    @Test
    fun `wmts url puts the row before the column`() {
        // Caldas Novas (GO) no zoom 13: coluna (x) 2989; linha (y) vem antes no endereço WMTS.
        val x = TileMath.tileX(-48.6250, 13)
        val y = TileMath.tileY(-17.7441, 13)
        assertEquals(
            "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2024_3857/default/g/13/$y/$x.jpg",
            SatelliteMap.tileUrl(13, x, y),
        )
    }

    @Test
    fun `only the topographic layer can be downloaded for offline use`() {
        assertTrue(MapLayer.TOPO.offlineDownload)
        assertFalse(MapLayer.SATELLITE.offlineDownload)
    }
}
