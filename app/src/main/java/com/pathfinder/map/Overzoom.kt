package com.pathfinder.map

/**
 * Pedaço de um tile de zoom menor que, ampliado, cobre um tile de zoom maior: [x]/[y] são o
 * tile de origem e [left]/[top]/[size] o quadrado dentro dele, em pixels.
 */
data class OverzoomSource(val x: Int, val y: Int, val left: Int, val top: Int, val size: Int)

/** Conta do zoom acima do máximo do fornecedor (ou de um tile que não foi baixado). */
object Overzoom {
    /**
     * Onde fica o tile ([zoom], [x], [y]) dentro do tile de [sourceZoom] que o contém.
     * Cada zoom a mais divide o tile em 2×2, então o pedaço tem `tileSize / 2^diferença` pixels.
     */
    fun source(zoom: Int, x: Int, y: Int, sourceZoom: Int, tileSize: Int): OverzoomSource {
        val diff = zoom - sourceZoom
        require(diff > 0) { "O zoom de origem deve ser menor" }
        val size = tileSize shr diff
        val mask = (1 shl diff) - 1
        return OverzoomSource(x shr diff, y shr diff, (x and mask) * size, (y and mask) * size, size)
    }
}
