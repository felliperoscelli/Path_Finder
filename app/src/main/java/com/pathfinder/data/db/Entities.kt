package com.pathfinder.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * Uma pista (trilha) de orientação criada pelo coordenador.
 * Cada trilha guarda seus próprios limites de desvio (Módulo 2).
 */
@Entity(tableName = "trails", indices = [Index(value = ["uuid"], unique = true)])
data class TrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificador global: a mesma trilha tem o mesmo uuid em todos os aparelhos. */
    @ColumnInfo(defaultValue = "")
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /** Distância máxima da rota antes de disparar o alerta de desvio, em metros. */
    val deviationDistanceMeters: Int,
    /** Tempo contínuo fora da rota antes de disparar o alerta, em segundos. */
    val deviationTimeSeconds: Int,
    /** Declinação magnética usada nos cálculos (graus; negativo = oeste). */
    val declinationDegrees: Double? = null,
    /** Declinação digitada pelo coordenador (ex.: a da carta), em vez da calculada. */
    @ColumnInfo(defaultValue = "0")
    val declinationManual: Boolean = false,
    /** Hora limite do dia, em minutos desde 0h (ex.: 17h = 1020), ou null. */
    val deadlineMinutes: Int? = null,
    /** Modo desafio: o monitor não vê distância nem direção dos pontos. */
    @ColumnInfo(defaultValue = "0")
    val challengeMode: Boolean = false,
    /** Base para onde os grupos voltam; null = ponto de partida. */
    val baseLatitude: Double? = null,
    val baseLongitude: Double? = null,
)

/** Se o ponto é a base da trilha (definida, ou a partida quando não há base). */
fun TrailEntity.isBase(point: TrailPointEntity): Boolean =
    if (baseLatitude != null && baseLongitude != null) {
        baseLatitude == point.latitude && baseLongitude == point.longitude
    } else {
        point.sequence == 0
    }

/** Ponto de controle da trilha, na ordem em que deve ser percorrido. */
@Entity(
    tableName = "trail_points",
    foreignKeys = [
        ForeignKey(
            entity = TrailEntity::class,
            parentColumns = ["id"],
            childColumns = ["trailId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trailId"), Index(value = ["trailId", "sequence"])],
)
data class TrailPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    /** Posição do ponto na trilha (0 = partida). */
    val sequence: Int,
    val name: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val utmZone: Int? = null,
    /** Letra da banda de latitude UTM (ex.: 'K'). */
    val utmBand: String? = null,
    val utmEasting: Double? = null,
    val utmNorthing: Double? = null,
    /** Azimute verdadeiro a partir do ponto anterior, em graus. */
    val trueAzimuthDegrees: Double? = null,
    /** Azimute magnético a partir do ponto anterior, em graus. */
    val magneticAzimuthDegrees: Double? = null,
    /** Distância até o ponto anterior, em metros. */
    val distanceFromPreviousMeters: Double? = null,
    val photoPath: String? = null,
    val createdAt: Long,
)

/** Origem de um trajeto gravado pelo GPS. */
enum class TrackSource {
    /** Percurso gravado pelo coordenador ao criar a trilha. */
    CREATION,

    /** Trajeto real percorrido pelo grupo durante a navegação. */
    NAVIGATION,
}

/** Amostra de GPS de um trajeto gravado. */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrailEntity::class,
            parentColumns = ["id"],
            childColumns = ["trailId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trailId", "source", "timestamp"]), Index(value = ["sessionId", "timestamp"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val source: TrackSource,
    /** Sessão de navegação, quando [source] é NAVIGATION. */
    val sessionId: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long,
)

/** Uma vez em que um grupo percorreu a trilha, para acompanhamento e revisão. */
@Entity(
    tableName = "navigation_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TrailEntity::class,
            parentColumns = ["id"],
            childColumns = ["trailId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trailId")],
)
data class NavigationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val groupName: String,
    val monitorName: String,
    val startedAt: Long,
    /** null enquanto a navegação está em andamento. */
    val endedAt: Long? = null,
    /** Próximo ponto a alcançar (igual ao número de pontos quando terminou). */
    val nextPointIndex: Int = 0,
    /** Índices dos pontos alcançados, separados por vírgula. */
    val reachedPoints: String = "",
    val deviationAlerts: Int = 0,
    /** "índice:hora" de cada ponto alcançado, separados por vírgula. */
    @ColumnInfo(defaultValue = "")
    val reachedTimes: String = "",
    /** Tempo total fora da rota (soma dos desvios com alarme). */
    @ColumnInfo(defaultValue = "0")
    val offRouteMillis: Long = 0,
    /** Percurso de outro aparelho (resumo recebido pela rede); null = feito neste aparelho. */
    val remoteDevice: String? = null,
) {
    val reachedSet: Set<Int>
        get() = reachedPoints.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Índice do ponto → hora em que foi alcançado. */
    val reachedTimeMap: Map<Int, Long>
        get() = reachedTimes.split(',').mapNotNull { pair ->
            val (i, t) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val index = i.toIntOrNull() ?: return@mapNotNull null
            val time = t.toLongOrNull() ?: return@mapNotNull null
            index to time
        }.toMap()

    companion object {
        fun encodeTimes(times: Map<Int, Long>) = times.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}

/** Período em que o grupo ficou fora da rota e o alarme disparou. */
@Entity(
    tableName = "deviation_events",
    foreignKeys = [
        ForeignKey(
            entity = NavigationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class DeviationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Quando o grupo saiu da rota. */
    val startedAt: Long,
    /** Quando o alarme disparou. */
    val alarmAt: Long,
    /** Quando voltou à rota (null se não voltou). */
    val endedAt: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceMeters: Double,
)

/** Mensagem da rede em malha guardada para repassar ("armazenar e encaminhar"). */
@Entity(tableName = "mesh_messages", indices = [Index("supersedeKey"), Index("expiresAt")])
class MeshMessageEntity(
    @PrimaryKey val id: String,
    val origin: String,
    val priority: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val supersedeKey: String?,
    /** Envelope codificado, pronto para enviar. */
    val payload: ByteArray,
    /** deviceIds que já têm a mensagem, separados por vírgula. */
    val deliveredTo: String,
)

/** Mensagem de texto ou emergência, enviada ou recebida (histórico do chat). */
@Entity(tableName = "chat_messages", indices = [Index("createdAt")])
data class ChatMessageEntity(
    /** Mesmo id do envelope da rede. */
    @PrimaryKey val id: String,
    val fromDevice: String,
    val fromName: String,
    val fromRole: String,
    val fromGroup: String? = null,
    /** Destinatário (deviceId) ou null para todos. */
    val toDevice: String? = null,
    val text: String,
    val urgent: Boolean = false,
    val emergency: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long,
    val outgoing: Boolean,
    val read: Boolean,
)

/** Área de mapa baixada para uso offline. */
@Entity(tableName = "offline_areas")
data class OfflineAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val createdAt: Long,
)

/** Trilha com a quantidade de pontos, para listagens. */
data class TrailSummary(
    @Embedded val trail: TrailEntity,
    @ColumnInfo(name = "pointCount") val pointCount: Int,
)

/** Trilha com todos os pontos, ordenados pela sequência. */
data class TrailWithPoints(
    @Embedded val trail: TrailEntity,
    @Relation(parentColumn = "id", entityColumn = "trailId")
    val points: List<TrailPointEntity>,
) {
    val orderedPoints: List<TrailPointEntity> get() = points.sortedBy { it.sequence }
}
