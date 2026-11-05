package com.pathfinder.recording

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import com.pathfinder.geo.StopCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado da gravação em andamento, compartilhado entre o serviço e as telas. */
data class RecordingState(
    val trailId: Long? = null,
    val startedAt: Long = 0,
    val lastFix: Location? = null,
    val trackPoints: Int = 0,
    /** Preenchido quando o usuário fica parado: o app pergunta "Marcar ponto aqui?". */
    val stopSuggestion: StopCandidate? = null,
) {
    val isRecording: Boolean get() = trailId != null
}

object RecordingSession {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    internal fun update(transform: (RecordingState) -> RecordingState) = _state.update(transform)

    fun dismissStopSuggestion() = _state.update { it.copy(stopSuggestion = null) }

    /** Inicia (ou mantém) a gravação da trilha em um serviço em primeiro plano. */
    fun start(context: Context, trailId: Long) {
        val intent = Intent(context, TrailRecordingService::class.java)
            .setAction(TrailRecordingService.ACTION_START)
            .putExtra(TrailRecordingService.EXTRA_TRAIL_ID, trailId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        if (!state.value.isRecording) return
        context.startService(
            Intent(context, TrailRecordingService::class.java).setAction(TrailRecordingService.ACTION_STOP)
        )
    }
}
