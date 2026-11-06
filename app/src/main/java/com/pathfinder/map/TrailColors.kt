package com.pathfinder.map

/**
 * Cores das trilhas quando várias aparecem no mesmo mapa. Sem verde (próximo ponto) e sem azul
 * (posição do GPS e percurso gravado), para não confundir.
 */
object TrailColors {
    /** Vermelho, roxo, laranja, rosa, marrom, índigo, ciano escuro e cinza (ARGB). */
    val PALETTE = intArrayOf(
        0xFFC62828.toInt(),
        0xFF6A1B9A.toInt(),
        0xFFEF6C00.toInt(),
        0xFFAD1457.toInt(),
        0xFF5D4037.toInt(),
        0xFF283593.toInt(),
        0xFF00838F.toInt(),
        0xFF424242.toInt(),
    )

    /**
     * Cor de cada trilha pela ordem de criação (id): a cor não muda quando outra trilha é
     * mostrada ou escondida, e as 8 primeiras nunca repetem.
     */
    fun assign(trailIds: Collection<Long>): Map<Long, Int> =
        trailIds.sorted().withIndex().associate { (i, id) -> id to PALETTE[i % PALETTE.size] }
}
