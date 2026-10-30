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
import com.pathfinder.ui.offline.OfflineMapsScreen
import com.pathfinder.ui.points.PointsScreen
import com.pathfinder.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val MAP = "map?trailId={trailId}"
    const val POINTS = "points/{trailId}"
    const val OFFLINE = "offline"
    const val SETTINGS = "settings"

    fun map(trailId: Long? = null) = if (trailId == null) "map" else "map?trailId=$trailId"
    fun points(trailId: Long) = "points/$trailId"
}

@Composable
fun PathFinderNavHost(openTrailId: Long? = null, onTrailOpened: () -> Unit = {}) {
    val nav = rememberNavController()

    LaunchedEffect(openTrailId) {
        if (openTrailId != null) {
            nav.navigate(Routes.map(openTrailId)) { popUpTo(Routes.HOME) }
            onTrailOpened()
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTrail = { nav.navigate(Routes.map(it)) },
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
            )
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
