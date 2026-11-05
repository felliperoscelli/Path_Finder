package com.pathfinder.navigation

/** Avisos da hora limite da pista. */
enum class DeadlineEvent { WARNING, REACHED }

/**
 * Hora limite: avisa uma vez [warnBeforeMillis] antes e uma vez na hora.
 * Se a navegação começar depois do aviso (ou da hora), só o que ainda
 * faz sentido é avisado.
 */
class DeadlineTracker(
    val deadlineAt: Long,
    private val warnBeforeMillis: Long = 30 * 60_000L,
) {
    private var warned = false
    private var reached = false

    fun check(now: Long): DeadlineEvent? = when {
        reached -> null
        now >= deadlineAt -> { reached = true; warned = true; DeadlineEvent.REACHED }
        !warned && now >= deadlineAt - warnBeforeMillis -> { warned = true; DeadlineEvent.WARNING }
        else -> null
    }

    fun minutesLeft(now: Long): Long = ((deadlineAt - now) / 60_000).coerceAtLeast(0)
}
