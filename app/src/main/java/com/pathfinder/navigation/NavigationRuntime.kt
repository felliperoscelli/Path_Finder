package com.pathfinder.navigation

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado da navegação em andamento, compartilhado entre o serviço e as telas. */
data class NavigationState(
    val sessionId: Long? = null,
    val trailId: Long? = null,
    val groupName: String = "",
    val status: NavigationStatus? = null,
    val lastFix: Location? = null,
) {
    val isActive: Boolean get() = sessionId != null
}

object NavigationRuntime {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    internal fun update(transform: (NavigationState) -> NavigationState) = _state.update(transform)

    fun start(context: Context, sessionId: Long) {
        val intent = Intent(context, NavigationService::class.java)
            .setAction(NavigationService.ACTION_START)
            .putExtra(NavigationService.EXTRA_SESSION_ID, sessionId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) = send(context, NavigationService.ACTION_STOP)
    fun skip(context: Context) = send(context, NavigationService.ACTION_SKIP)
    fun back(context: Context) = send(context, NavigationService.ACTION_BACK)
    fun silence(context: Context) = send(context, NavigationService.ACTION_SILENCE)

    private fun send(context: Context, action: String) {
        if (!state.value.isActive) return
        context.startService(Intent(context, NavigationService::class.java).setAction(action))
    }
}
