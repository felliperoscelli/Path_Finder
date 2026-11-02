package com.pathfinder.results

import com.pathfinder.data.db.NavigationSessionEntity

/** Resultado de um grupo numa trilha. */
data class GroupResult(
    val sessionId: Long,
    val groupName: String,
    val monitorName: String,
    /** Resumo recebido de outro aparelho (sem trajeto gravado aqui). */
    val remote: Boolean,
    val startedAt: Long,
    val reachedCount: Int,
    val totalPoints: Int,
    /** Alcançou o último ponto. */
    val completed: Boolean,
    /** Da partida (ponto 1) à chegada (último ponto); null se não completou. */
    val totalMillis: Long?,
    /** Tempo de cada trecho (ponto i → i+1); null se faltou um dos pontos. */
    val legMillis: List<Long?>,
    val deviationAlerts: Int,
    val offRouteMillis: Long,
    /** Classificação entre os que completaram (1 = mais rápido); null para os demais. */
    val rank: Int?,
)

object Results {
    /**
     * Calcula os resultados. Quem completou é classificado pelo tempo total;
     * os outros vêm depois, por número de pontos alcançados.
     */
    fun compute(sessions: List<NavigationSessionEntity>, totalPoints: Int): List<GroupResult> {
        val raw = sessions.filter { it.endedAt != null }.map { s ->
            val times = s.reachedTimeMap
            val start = times[0] ?: s.startedAt
            val finish = times[totalPoints - 1]
            GroupResult(
                sessionId = s.id,
                groupName = s.groupName,
                monitorName = s.monitorName,
                remote = s.remoteDevice != null,
                startedAt = s.startedAt,
                reachedCount = times.keys.count { it in 0 until totalPoints },
                totalPoints = totalPoints,
                completed = finish != null,
                totalMillis = finish?.let { (it - start).coerceAtLeast(0) },
                legMillis = (0 until (totalPoints - 1).coerceAtLeast(0)).map { i ->
                    val a = times[i]
                    val b = times[i + 1]
                    if (a != null && b != null) (b - a).coerceAtLeast(0) else null
                },
                deviationAlerts = s.deviationAlerts,
                offRouteMillis = s.offRouteMillis,
                rank = null,
            )
        }
        val finished = raw.filter { it.completed }.sortedBy { it.totalMillis }
            .mapIndexed { i, r -> r.copy(rank = i + 1) }
        val others = raw.filterNot { it.completed }
            .sortedWith(compareByDescending<GroupResult> { it.reachedCount }.thenBy { it.groupName })
        return finished + others
    }
}
