package com.pathfinder.net

/** Regras da malha: por quanto tempo guardar cada mensagem e quais se substituem. */
object MeshPolicy {
    /** Limite de repasses: pistas de ~2 km com aparelhos a cada 150 m. */
    const val MAX_HOPS = 16

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** Tempo de vida da mensagem na rede (guardada para entregar depois). */
    fun ttlMillis(body: Body): Long = when (body) {
        is PositionUpdate -> 15 * MINUTE
        is PointReached -> 2 * HOUR
        is DeviationAlert, is BackOnRoute -> 6 * HOUR
        is TextMessage, is Emergency -> 6 * HOUR
        is TrailShare -> 24 * HOUR
    }

    /**
     * Mensagens com a mesma chave se substituem: só a mais nova é guardada e
     * repassada (ex.: só a última posição de cada monitor).
     */
    fun supersedeKey(envelope: Envelope): String? = when (val b = envelope.body) {
        is PositionUpdate -> "pos:${envelope.origin}"
        is DeviationAlert -> "dev:${envelope.origin}:${b.since}"
        is TrailShare -> "trail:${b.trail.uuid}"
        is BackOnRoute, is PointReached, is TextMessage, is Emergency -> null
    }

    /** Validade contada a partir do mais antigo entre criação e agora (tolera relógios adiantados). */
    fun expiresAt(envelope: Envelope, now: Long): Long = minOf(envelope.createdAt, now) + ttlMillis(envelope.body)
}
