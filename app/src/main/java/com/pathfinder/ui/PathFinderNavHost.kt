package com.pathfinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pathfinder.ui.home.HomeScreen
import com.pathfinder.ui.map.MapScreen
import com.pathfinder.ui.navigate.NavigateScreen
import com.pathfinder.ui.navigate.SessionReviewScreen
import com.pathfinder.ui.offline.OfflineMapsScreen
import com.pathfinder.ui.points.PointsScreen
import com.pathfinder.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val MAP = "map?trailId={trailId}"
    const val POINTS = "points/{trailId}"
    const val NAVIGATE = "navigate/{trailId}"
    const val SESSION = "session/{sessionId}"
    const val OFFLINE = "offline"
    const val SETTINGS = "settings"

    fun map(trailId: Long? = null) = if (trailId == null) "map" else "map?trailId=$trailId"
    fun points(trailId: Long) = "points/$trailId"
    fun navigate(trailId: Long) = "navigate/$trailId"
    fun session(sessionId: Long) = "session/$sessionId"
}

/** Pedido para abrir uma trilha vindo de uma notificação. */
data class OpenTrail(val trailId: Long, val navigation: Boolean)

@Composable
fun PathFinderNavHost(openTrail: OpenTrail? = null, onTrailOpened: () -> Unit = {}) {
    val nav = rememberNavController()

    LaunchedEffect(openTrail) {
        if (openTrail != null) {
            val route = if (openTrail.navigation) Routes.navigate(openTrail.trailId) else Routes.map(openTrail.trailId)
            nav.navigate(route) { popUpTo(Routes.HOME) }
            onTrailOpened()
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTrail = { nav.navigate(Routes.map(it)) },
                onNavigateTrail = { nav.navigate(Routes.navigate(it)) },
                onOpenMap = { nav.navigate(Routes.map()) },
                onOpenOfflineMaps = { nav.navigate(Routes.OFFLINE) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.MAP,
            arguments = listOf(navArgument("trailId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val trailId = entry.arguments?.getLong("trailId")?.takeIf { it >= 0 }
            MapScreen(
                trailId = trailId,
                onBack = { nav.popBackStack() },
                onOpenPoints = { nav.navigate(Routes.points(it)) },
                onNavigate = { nav.navigate(Routes.navigate(it)) },
            )
        }
        composable(
            Routes.NAVIGATE,
            arguments = listOf(navArgument("trailId") { type = NavType.LongType }),
        ) { entry ->
            NavigateScreen(
                trailId = entry.arguments!!.getLong("trailId"),
                onBack = { nav.popBackStack() },
                onOpenSession = { nav.navigate(Routes.session(it)) },
            )
        }
        composable(
            Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            SessionReviewScreen(sessionId = entry.arguments!!.getLong("sessionId"), onBack = { nav.popBackStack() })
        }
        composable(
            Routes.POINTS,
            arguments = listOf(navArgument("trailId") { type = NavType.LongType }),
        ) { entry ->
            PointsScreen(trailId = entry.arguments!!.getLong("trailId"), onBack = { nav.popBackStack() })
        }
        composable(Routes.OFFLINE) {
            OfflineMapsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
