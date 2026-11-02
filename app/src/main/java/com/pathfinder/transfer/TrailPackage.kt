package com.pathfinder.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Trilha para compartilhar entre aparelhos (rede ou arquivo .json).
 * Leva só os dados de origem; UTM, azimutes e distâncias são recalculados
 * ao importar. Fotos não são enviadas.
 */
@Serializable
data class TrailPackage(
    val format: String = FORMAT,
    val version: Int = 1,
    val uuid: String,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val deviationDistanceMeters: Int,
    val deviationTimeSeconds: Int,
    val declinationDegrees: Double? = null,
    val points: List<PackagePoint>,
    /** Percurso gravado na criação (usado como rota alternativa no alerta de desvio). */
    val track: List<PackageTrackPoint> = emptyList(),
) {
    companion object {
        const val FORMAT = "pathfinder-trail"
    }
}

@Serializable
data class PackagePoint(
    val name: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
)

@Serializable
data class PackageTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val timestamp: Long,
)

object TrailJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val pretty = Json(json) { prettyPrint = true }

    fun encode(pkg: TrailPackage): String = pretty.encodeToString(TrailPackage.serializer(), pkg)

    /** Lê um arquivo .json do Path Finder; lança [IllegalArgumentException] se não for um. */
    fun decode(text: String): TrailPackage {
        val pkg = try {
            json.decodeFromString(TrailPackage.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo não é uma trilha do Path Finder", e)
        }
        require(pkg.format == TrailPackage.FORMAT) { "Formato desconhecido: ${pkg.format}" }
        return pkg
    }
}
