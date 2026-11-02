package com.pathfinder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.ui.OpenTrail
import com.pathfinder.ui.PathFinderNavHost
import com.pathfinder.ui.theme.PathFinderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    /** Trilha a abrir quando o app é aberto por uma notificação. */
    private val trailToOpen = MutableStateFlow<OpenTrail?>(null)
    /** Tela a abrir por uma notificação (rede, mensagens, painel). */
    private val screenToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val highContrastFlow = (application as PathFinderApp).container.settingsRepository.settings.map { it.highContrast }
        setContent {
            val openTrail by trailToOpen.collectAsStateWithLifecycle()
            val openScreen by screenToOpen.collectAsStateWithLifecycle()
            val highContrast by highContrastFlow.collectAsStateWithLifecycle(initialValue = false)
            PathFinderTheme(highContrast = highContrast) {
                PathFinderNavHost(
                    openTrail = openTrail,
                    onTrailOpened = { trailToOpen.value = null },
                    openScreen = openScreen,
                    onScreenOpened = { screenToOpen.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_SCREEN)?.let {
            screenToOpen.value = it
            return
        }
        val id = intent?.getLongExtra(EXTRA_OPEN_TRAIL_ID, -1L) ?: -1L
        if (id >= 0) trailToOpen.value = OpenTrail(id, intent?.getBooleanExtra(EXTRA_OPEN_NAVIGATION, false) == true)
    }

    companion object {
        const val EXTRA_OPEN_TRAIL_ID = "openTrailId"
        const val EXTRA_OPEN_NAVIGATION = "openNavigation"
        const val EXTRA_OPEN_SCREEN = "openScreen"
        const val SCREEN_NETWORK = "network"
        const val SCREEN_MESSAGES = "messages"
        const val SCREEN_DASHBOARD = "dashboard"
    }
}
