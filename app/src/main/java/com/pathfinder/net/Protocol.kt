package com.pathfinder.net

import com.pathfinder.transfer.TrailJson
import com.pathfinder.transfer.TrailPackage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Prioridade de envio: alertas e emergência passam na frente das posições. */
object Priority {
    const val ALERT = 0
    const val NORMAL = 1
    const val POSITION = 2
}

/**
 * Mensagem trocada entre aparelhos. O [id] único, a [origin] e o contador de
 * [hops] permitem descartar cópias e evitar loops quando a rede em malha
 * repassar mensagens (etapa 6).
 */
@Serializable
data class Envelope(
    val id: String = UUID.randomUUID().toString(),
    /** deviceId de quem criou a mensagem. */
    val origin: String,
    val originName: String,
    val createdAt: Long,
    val hops: Int = 0,
    val priority: Int = Priority.NORMAL,
    val body: Body,
)

@Serializable
sealed interface Body

/** Apresentação ao conectar. */
@Serializable
@SerialName("hello")
data class Hello(val role: String, val name: String) : Body

/** Coordenador → monitores: trilha completa. */
@Serializable
@SerialName("trail")
data class TrailShare(val trail: TrailPackage) : Body

/** Monitor → coordenador: posição e progresso, a cada intervalo configurado. */
@Serializable
@SerialName("position")
data class PositionUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val time: Long,
    val trailUuid: String,
    val trailName: String,
    val groupName: String,
    val monitorName: String,
    val nextIndex: Int,
    val totalPoints: Int,
    val lastReachedName: String? = null,
    val distanceToRouteMeters: Double? = null,
    val offRouteSince: Long? = null,
    val alarm: Boolean = false,
    val finished: Boolean = false,
) : Body

/** Monitor → coordenador: alarme de desvio disparou (ou repetiu). */
@Serializable
@SerialName("deviation")
data class DeviationAlert(
    val latitude: Double,
    val longitude: Double,
    val time: Long,
    val since: Long,
    val distanceMeters: Double,
    val trailName: String,
    val groupName: String,
    val monitorName: String,
    val repeat: Boolean,
) : Body

/** Monitor → coordenador: o grupo voltou à rota. */
@Serializable
@SerialName("back_on_route")
data class BackOnRoute(
    val time: Long,
    val since: Long,
    val maxDistanceMeters: Double,
    val groupName: String,
    val monitorName: String,
) : Body

/** Monitor → coordenador: ponto alcançado. */
@Serializable
@SerialName("point_reached")
data class PointReached(
    val time: Long,
    val index: Int,
    val pointName: String,
    val totalPoints: Int,
    val trailName: String,
    val groupName: String,
    val monitorName: String,
) : Body

/** Codificação das mensagens: JSON compactado com GZIP. */
object Wire {
    fun encode(envelope: Envelope): ByteArray {
        val json = TrailJson.json.encodeToString(Envelope.serializer(), envelope)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Envelope {
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8)
        return TrailJson.json.decodeFromString(Envelope.serializer(), json)
    }
}
