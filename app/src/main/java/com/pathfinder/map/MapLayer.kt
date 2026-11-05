package com.pathfinder.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

/**
 * Camadas de fundo que o usuário pode escolher no mapa.
 *
 * [offlineDownload] indica se os termos do fornecedor permitem baixar áreas inteiras para uso
 * offline. Camadas sem essa permissão só guardam no aparelho os tiles já vistos com internet.
 */
enum class MapLayer(
    val label: String,
    val description: String,
    val offlineDownload: Boolean,
) {
    TOPO("Curvas de nível", "Relevo, trilhas e rios (OpenTopoMap)", offlineDownload = true),
    SATELLITE("Satélite", "Imagem de satélite: mata, campo e clareiras (Sentinel-2)", offlineDownload = false),
    ;

    val tileSource: OnlineTileSourceBase
        get() = when (this) {
            TOPO -> TopoMap.OPEN_TOPO_MAP
            SATELLITE -> SatelliteMap.TILE_SOURCE
        }

    /** Atribuição completa do fornecedor (no mapa aparece a versão curta da camada). */
    val attribution: String
        get() = when (this) {
            TOPO -> "Dados: © OpenStreetMap, SRTM | Estilo: © OpenTopoMap (CC-BY-SA)"
            SATELLITE -> SatelliteMap.ATTRIBUTION
        }

    /** Imagem de satélite é escura: a atribuição sobre ela vai em branco. */
    val darkBackground: Boolean get() = this == SATELLITE
}

/**
 * Imagem de satélite Sentinel-2 cloudless (EOX), mosaico sem nuvens com ~10 m por pixel.
 *
 * Termos (cloudless.eox.at, conferidos em nov/2026): o serviço público é gratuito para uso não
 * comercial (CC BY-NC-SA 4.0), com atribuição visível. Guardar no aparelho os tiles vistos é
 * permitido; o download de áreas para uso offline não é previsto no serviço gratuito, por isso
 * esta camada não entra em [OfflineMapManager].
 */
object SatelliteMap {
    const val LAYER = "s2cloudless-2024_3857"
    const val BASE_URL = "https://tiles.maps.eox.at/wmts/1.0.0/"

    /**
     * A imagem tem ~10 m/pixel (detalhe real até o zoom 14), mas o servidor entrega tiles
     * suavizados até o 17; ampliar no aparelho a partir de zooms menores fica quadriculado.
     */
    const val MAX_ZOOM = 17

    /** Atribuição completa exigida pela EOX (mostrada no seletor de camadas). */
    const val ATTRIBUTION =
        "EOxCloudless 2024 (cloudless.eox.at) por EOX IT Services GmbH " +
            "(contém dados Copernicus Sentinel modificados 2024)"

    /** Versão curta, escrita sobre o mapa; a EOX permite a completa em outro lugar em telas pequenas. */
    const val SHORT_ATTRIBUTION = "EOxCloudless 2024 por EOX · Copernicus Sentinel 2024"

    /** Endereço WMTS de um tile: a ordem é zoom/linha/coluna (y antes de x). */
    fun tileUrl(zoom: Int, x: Int, y: Int): String =
        "$BASE_URL$LAYER/default/g/$zoom/$y/$x.jpg"

    /** Criado sob demanda para que [tileUrl] possa ser testado sem Android. */
    val TILE_SOURCE: OnlineTileSourceBase by lazy { source() }

    private fun source(): OnlineTileSourceBase = object : OnlineTileSourceBase(
        "EOX-S2-2024",
        TopoMap.MIN_ZOOM,
        MAX_ZOOM,
        256,
        ".jpg",
        arrayOf(BASE_URL),
        SHORT_ATTRIBUTION,
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    ) {
        override fun getTileURLString(index: Long): String =
            tileUrl(MapTileIndex.getZoom(index), MapTileIndex.getX(index), MapTileIndex.getY(index))
    }
}
