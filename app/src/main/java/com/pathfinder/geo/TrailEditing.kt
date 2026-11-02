package com.pathfinder.geo

import kotlin.math.cos
import kotlin.math.hypot

/** Operações de ordem dos pontos (puras; a persistência fica no repositório). */
object TrailEditing {

    /** Move o item [id] para a posição [newIndex] (limitada à lista). */
    fun <T> move(items: List<T>, id: (T) -> Long, itemId: Long, newIndex: Int): List<T> {
        val from = items.indexOfFirst { id(it) == itemId }
        if (from < 0) return items
        val result = items.toMutableList()
        val item = result.removeAt(from)
        result.add(newIndex.coerceIn(0, result.size), item)
        return result
    }

    /** Insere [item] na posição [index] (0 = nova partida, size = no final). */
    fun <T> insert(items: List<T>, item: T, index: Int): List<T> =
        items.toMutableList().apply { add(index.coerceIn(0, size), item) }

    /**
     * Sugere onde inserir um ponto novo em [p]: entre os dois pontos do trecho
     * mais próximo, se [p] cair ao lado de um trecho; senão, no final.
     */
    fun suggestInsertIndex(points: List<LatLon>, p: LatLon): Int {
        if (points.size < 2) return points.size
        // Projeção plana local (metros) em torno de p: suficiente para trilhas de poucos km.
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(p.latitude))
        fun xy(q: LatLon) = Pair((q.longitude - p.longitude) * mPerDegLon, (q.latitude - p.latitude) * mPerDegLat)

        val projected = points.map(::xy)
        val distanceToEnd = hypot(projected.last().first, projected.last().second)
        var bestIndex = points.size
        var bestDistance = distanceToEnd
        for (i in 0 until projected.size - 1) {
            val (ax, ay) = projected[i]
            val (bx, by) = projected[i + 1]
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            if (lengthSq == 0.0) continue
            // Parâmetro da projeção de p (origem) sobre o trecho A→B.
            val t = (-ax * dx - ay * dy) / lengthSq
            if (t <= 0.0 || t >= 1.0) continue
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i + 1
            }
        }
        return bestIndex
    }
}
