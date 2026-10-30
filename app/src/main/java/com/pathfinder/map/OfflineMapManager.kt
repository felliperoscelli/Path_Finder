package com.pathfinder.map

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.BoundingBox
import kotlin.coroutines.resume

/** Estado de um download de área de mapa. */
sealed interface DownloadState {
    data class Progress(val done: Int, val total: Int, val zoom: Int) : DownloadState
    data class Finished(val total: Int) : DownloadState
    data class Failed(val errors: Int) : DownloadState
}

/**
 * Baixa tiles do OpenTopoMap para o cache local do osmdroid, permitindo
 * usar o mapa sem internet durante o evento.
 */
@Suppress("DEPRECATION") // CacheManagerTask do osmdroid é um AsyncTask
class OfflineMapManager(private val context: Context) {

    /**
     * Inicia o download de [bounds] entre os zooms indicados. O download é
     * cancelado se o coletor do Flow for cancelado.
     */
    fun download(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Flow<DownloadState> = callbackFlow {
        val writer = SqlTileWriter()
        val manager = CacheManager(TopoMap.OPEN_TOPO_MAP, writer, minZoom, maxZoom)
        var total = 0
        val callback = object : CacheManager.CacheManagerCallback {
            override fun setPossibleTilesInArea(possible: Int) {
                total = possible
            }

            override fun downloadStarted() {
                trySend(DownloadState.Progress(0, total, minZoom))
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                trySend(DownloadState.Progress(progress, total, currentZoomLevel))
            }

            override fun onTaskComplete() {
                trySend(DownloadState.Finished(total))
                close()
            }

            override fun onTaskFailed(errors: Int) {
                trySend(DownloadState.Failed(errors))
                close()
            }
        }
        val task = manager.downloadAreaAsyncNoUI(context, bounds.toBoundingBox(), minZoom, maxZoom, callback)
        awaitClose {
            task.cancel(true)
            writer.onDetach()
        }
    }

    /** Remove do cache os tiles de uma área. */
    suspend fun deleteTiles(bounds: GeoBounds, minZoom: Int, maxZoom: Int) {
        val writer = SqlTileWriter()
        try {
            suspendCancellableCoroutine { cont ->
                val manager = CacheManager(TopoMap.OPEN_TOPO_MAP, writer, minZoom, maxZoom)
                val task = CacheManager.CacheManagerTask(
                    manager, manager.cleaningAction, bounds.toBoundingBox(), minZoom, maxZoom
                )
                task.addCallback(object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() = cont.resume(Unit)
                    override fun onTaskFailed(errors: Int) = cont.resume(Unit)
                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
                    override fun downloadStarted() = Unit
                    override fun setPossibleTilesInArea(total: Int) = Unit
                })
                manager.execute(task)
                cont.invokeOnCancellation { task.cancel(true) }
            }
        } finally {
            writer.onDetach()
        }
    }

    private fun GeoBounds.toBoundingBox() = BoundingBox(north, east, south, west)

    companion object {
        /** Limite de tiles por área, para não sobrecarregar o servidor nem o aparelho. */
        const val MAX_TILES_PER_AREA = 20_000L

        /** Tamanho médio aproximado de um tile do OpenTopoMap, para estimativa. */
        const val AVERAGE_TILE_BYTES = 25_000L
    }
}
