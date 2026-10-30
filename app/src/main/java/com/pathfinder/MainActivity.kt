package com.pathfinder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.ui.PathFinderNavHost
import com.pathfinder.ui.theme.PathFinderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Trilha a abrir quando o app é aberto por uma notificação. */
    private val trailToOpen = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val openTrailId by trailToOpen.collectAsStateWithLifecycle()
            PathFinderTheme {
                PathFinderNavHost(
                    openTrailId = openTrailId,
                    onTrailOpened = { trailToOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_TRAIL_ID, -1L) ?: -1L
        if (id >= 0) trailToOpen.value = id
    }

    companion object {
        const val EXTRA_OPEN_TRAIL_ID = "openTrailId"
    }
}
