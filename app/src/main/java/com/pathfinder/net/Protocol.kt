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
 * repassa mensagens.
 */
@Serializable
data class Envelope(
    val id: String = UUID.randomUUID().toString(),
    /** deviceId de quem criou a mensagem. */
    val origin: String,
    val originName: String,
    val createdAt: Long,
    /** Quantas vezes a mensagem já foi repassada (0 = veio direto da origem). */
    val hops: Int = 0,
    val priority: Int = Priority.NORMAL,
    /** deviceId do destinatário, ou null para todos. Os outros aparelhos só repassam. */
    val to: String? = null,
    /** Código do evento (normalizado); mensagens de outro evento são descartadas. */
    val event: String = "",
    val body: Body,
)

@Serializable
sealed interface Body

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
    /** Bateria do celular do monitor (%). */
    val batteryPercent: Int? = null,
    /** Hora limite desta navegação (epoch ms), se a trilha tiver uma. */
    val deadlineAt: Long? = null,
    /** O grupo está voltando à base. */
    val returningToBase: Boolean = false,
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

/**
 * Mensagem de texto (geral ou individual, conforme [Envelope.to]).
 * [urgent] = alerta rápido como "Grupo perdido": vai com prioridade e toca
 * o alarme do coordenador.
 */
@Serializable
@SerialName("text")
data class TextMessage(
    val text: String,
    val senderRole: String,
    val senderGroup: String? = null,
    val urgent: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
) : Body

/** Botão de emergência: alerta prioritário com a posição de quem pediu. */
@Serializable
@SerialName("emergency")
data class Emergency(
    val time: Long,
    val senderRole: String,
    val monitorName: String,
    val groupName: String? = null,
    val trailName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
) : Body

/**
 * Monitor → coordenador: a navegação terminou. Leva o resumo para o
 * resultado do evento (tempos de chegada a cada ponto, alertas, tempo fora
 * da rota).
 */
@Serializable
@SerialName("navigation_ended")
data class NavigationEnded(
    val time: Long,
    val trailUuid: String,
    val trailName: String,
    val groupName: String,
    val monitorName: String,
    val startedAt: Long,
    /** Índice do ponto → hora em que foi alcançado. */
    val reachedTimes: Map<Int, Long>,
    val totalPoints: Int,
    val deviationAlerts: Int,
    val offRouteMillis: Long,
) : Body

/** Coordenador → monitor(es): voltar à base (o app do monitor passa a guiar até lá). */
@Serializable
@SerialName("return_to_base")
data class ReturnToBase(val reason: String = "") : Body

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
