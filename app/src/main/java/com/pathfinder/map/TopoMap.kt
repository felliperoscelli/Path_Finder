package com.pathfinder.map

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import java.io.File

/** Configuração do mapa topográfico (OpenTopoMap) e do cache de tiles do osmdroid. */
object TopoMap {
    /** Zoom máximo servido pelo OpenTopoMap. */
    const val MAX_ZOOM = 17
    const val MIN_ZOOM = 2

    /** Centro padrão: Caldas Novas (GO). */
    val DEFAULT_CENTER = GeoPoint(-17.7441, -48.6250)
    const val DEFAULT_ZOOM = 13.0

    /**
     * Camada OpenTopoMap (curvas de nível). A política permite o download
     * de áreas para uso offline, com no máximo 2 conexões simultâneas para
     * respeitar o servidor.
     */
    val OPEN_TOPO_MAP = XYTileSource(
        "OpenTopoMap",
        0,
        MAX_ZOOM,
        256,
        ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/",
        ),
        "Dados: © OpenStreetMap, SRTM | Estilo: © OpenTopoMap (CC-BY-SA)",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    )

    private const val CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024
    private const val CACHE_TRIM_BYTES = 1800L * 1024 * 1024
    private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /**
     * Deve ser chamado uma vez no início do app, antes de criar qualquer MapView.
     * O cache fica no armazenamento interno do app (não precisa de permissão)
     * e os tiles são considerados válidos por um ano, para que as áreas
     * baixadas continuem disponíveis sem internet.
     */
    fun configure(context: Context) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        val base = File(context.filesDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
        config.tileFileSystemCacheMaxBytes = CACHE_MAX_BYTES
        config.tileFileSystemCacheTrimBytes = CACHE_TRIM_BYTES
        config.expirationOverrideDuration = ONE_YEAR_MS
    }
}
