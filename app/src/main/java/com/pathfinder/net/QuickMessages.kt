package com.pathfinder.net

import com.pathfinder.data.settings.UserRole

/** Mensagem pronta; [urgent] vai com prioridade de alerta e toca o alarme do coordenador. */
data class QuickMessage(val text: String, val urgent: Boolean = false)

/** Alertas rápidos pré-programados (sem áudio, só texto). */
object QuickMessages {
    val monitor = listOf(
        QuickMessage("Grupo perdido", urgent = true),
        QuickMessage("Precisamos de ajuda", urgent = true),
        QuickMessage("Chegamos ao ponto"),
        QuickMessage("Tudo certo"),
    )

    val coordinator = listOf(
        QuickMessage("Recebido! Aguardem, resgate a caminho"),
        QuickMessage("Fiquem onde estão"),
        QuickMessage("Voltem ao último ponto alcançado"),
        QuickMessage("Tudo certo, continuem"),
    )

    fun forRole(role: UserRole) = if (role == UserRole.COORDINATOR) coordinator else monitor
}
