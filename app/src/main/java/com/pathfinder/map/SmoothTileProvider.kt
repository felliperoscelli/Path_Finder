package com.pathfinder.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.ReusableBitmapDrawable
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileFileStorageProviderBase
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.TileSystem
import org.osmdroid.views.Projection
import kotlin.math.floor

/**
 * Provedor de tiles do osmdroid que amplia com suavização. Acima do zoom máximo do fornecedor
 * (17 nas curvas de nível e no satélite) não existe imagem com mais detalhe: o osmdroid amplia
 * o tile do zoom 17, mas sem filtro, e o mapa fica "quadriculado". Aqui a ampliação é filtrada
 * (fica suave, como uma foto ampliada).
 */
class SmoothTileProvider(context: Context, source: ITileSource) : MapTileProviderBasic(context, source) {
    override fun createApproximater(
        assets: MapTileFileStorageProviderBase?,
        cache: MapTileFileStorageProviderBase?,
        archive: MapTileFileStorageProviderBase?,
    ): MapTileApproximater = SmoothApproximater().apply { listOfNotNull(assets, cache, archive).forEach(::addProvider) }

    /**
     * Ao mudar o zoom, o osmdroid copia os tiles da memória para o zoom novo, ampliados sem
     * filtro; acima do zoom máximo essas cópias nunca mais são trocadas (era o "quadriculado").
     * Lá em cima a cópia é pulada e o tile vem do [SmoothApproximater].
     */
    override fun rescaleCache(projection: Projection, newZoomLevel: Double, oldZoomLevel: Double, viewPort: Rect) {
        if (TileSystem.getInputTileZoomLevel(newZoomLevel) > tileSource.maximumZoomLevel) return
        super.rescaleCache(projection, newZoomLevel, oldZoomLevel, viewPort)
    }

    /** Tiles do zoom máximo pedidos por [ensureParents] que ainda não chegaram. */
    private val pendingParents = mutableSetOf<Long>()
    private var pendingSource: String? = null

    /** Chamado quando as ampliações foram refeitas (para redesenhar o mapa). */
    var onRefreshed: () -> Unit = {}

    init {
        // Quando um tile pedido chega, as ampliações feitas sem ele (de zooms mais baixos,
        // com emendas visíveis) são descartadas e refeitas a partir dele.
        tileRequestCompleteHandlers.add(Handler(Looper.getMainLooper()) {
            val writer = tileWriter
            if (pendingParents.isNotEmpty() && writer != null) {
                val arrived = pendingParents.filter { writer.exists(tileSource, it) }
                if (arrived.isNotEmpty()) {
                    pendingParents.removeAll(arrived.toSet())
                    tileCache.clear()
                    onRefreshed()
                }
            }
            true
        })
    }

    /**
     * Acima do zoom máximo a ampliação só fica certa se o tile do zoom máximo (17) já estiver no
     * celular; ao pular direto do 13 para o 19, por exemplo, ele nunca foi baixado. Pede os que
     * faltam na área visível (o download segue as regras normais do fornecedor).
     */
    fun ensureParents(bounds: GeoBounds, mapZoom: Double) {
        val source = tileSource
        // Trocou de camada: os pedidos da outra não interessam mais.
        if (source.name() != pendingSource) {
            pendingParents.clear()
            pendingSource = source.name()
        }
        val max = source.maximumZoomLevel
        if (floor(mapZoom).toInt() <= max) return
        val writer = tileWriter ?: return
        val xs = TileMath.tileX(bounds.west, max)..TileMath.tileX(bounds.east, max)
        val ys = TileMath.tileY(bounds.north, max)..TileMath.tileY(bounds.south, max)
        if (xs.count() * ys.count() > MAX_PARENT_REQUESTS) return
        for (x in xs) for (y in ys) {
            val index = MapTileIndex.getTileIndex(max, x, y)
            if (index in pendingParents || writer.exists(source, index)) continue
            pendingParents += index
            getMapTile(index)
        }
    }

    private companion object {
        /** A tela inteira no zoom 18+ cabe em poucos tiles do 17; mais que isso é engano. */
        const val MAX_PARENT_REQUESTS = 16
    }
}

/** Igual ao aproximador do osmdroid (procura o tile nos zooms de baixo), mas desenha com filtro. */
private class SmoothApproximater : MapTileApproximater() {
    private val sources = mutableListOf<MapTileModuleProviderBase>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    override fun addProvider(provider: MapTileModuleProviderBase) {
        super.addProvider(provider)
        sources += provider
    }

    override fun approximateTileFromLowerZoom(pMapTileIndex: Long): Bitmap? {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        for (diff in 1..MAX_ZOOM_DIFF) {
            val sourceZoom = zoom - diff
            if (sourceZoom < 0) return null
            for (provider in sources) {
                if (sourceZoom < provider.minimumZoomLevel || sourceZoom > provider.maximumZoomLevel) continue
                scaled(provider, pMapTileIndex, sourceZoom)?.let { return it }
            }
        }
        return null
    }

    private fun scaled(provider: MapTileModuleProviderBase, index: Long, sourceZoom: Int): Bitmap? {
        val zoom = MapTileIndex.getZoom(index)
        val x = MapTileIndex.getX(index)
        val y = MapTileIndex.getY(index)
        // O tamanho em pixels só é conhecido com o tile na mão; a posição do tile de origem, não.
        val parent = Overzoom.source(zoom, x, y, sourceZoom, 1 shl 20)
        val drawable = provider.tileLoader.loadTileIfReachable(MapTileIndex.getTileIndex(sourceZoom, parent.x, parent.y))
            as? BitmapDrawable ?: return null
        val reusable = drawable as? ReusableBitmapDrawable
        reusable?.beginUsingDrawable()
        try {
            if (reusable != null && !reusable.isBitmapValid) return null
            val src = drawable.bitmap ?: return null
            val tileSize = src.width
            val part = Overzoom.source(zoom, x, y, sourceZoom, tileSize)
            if (part.size < 1) return null
            val out = getTileBitmap(tileSize) ?: return null
            out.eraseColor(Color.TRANSPARENT)
            // Amplia o tile de origem inteiro com a mesma transformação para todos os pedaços:
            // recortar cada pedaço antes deixava um degrau de meio pixel na emenda dos tiles.
            val scale = tileSize.toFloat() / part.size
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(-part.left * scale, -part.top * scale)
            }
            Canvas(out).drawBitmap(src, matrix, paint)
            return out
        } finally {
            reusable?.finishUsingDrawable()
        }
    }

    private companion object {
        /** Até 2^6 = 64 vezes: além disso o pedaço de origem fica com poucos pixels. */
        const val MAX_ZOOM_DIFF = 6
    }
}
