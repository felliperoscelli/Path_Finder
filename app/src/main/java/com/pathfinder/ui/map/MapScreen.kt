package com.pathfinder.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.centerOnMe
import com.pathfinder.ui.components.rememberLocationPermission
import com.pathfinder.ui.components.rememberMyLocationOverlay
import com.pathfinder.ui.components.rememberTopoMapView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    trailId: Long?,
    onBack: () -> Unit,
    viewModel: MapViewModel = viewModel(key = "map-$trailId", factory = MapViewModel.factory(trailId)),
) {
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val mapView = rememberTopoMapView()
    val permission = rememberLocationPermission()
    val myLocation = rememberMyLocationOverlay(mapView, permission.granted)
    val trailLayer = remember(mapView) { TrailLayer(mapView) }
    var framedTrail by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    LaunchedEffect(trail) {
        val points = trail?.orderedPoints ?: return@LaunchedEffect
        trailLayer.show(points)
        if (!framedTrail && points.isNotEmpty()) {
            trailLayer.zoomToTrail()
            framedTrail = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.trail?.name ?: "Mapa") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (!trail?.points.isNullOrEmpty()) {
                        IconButton(onClick = { trailLayer.zoomToTrail() }) {
                            Icon(Icons.Filled.CenterFocusStrong, "Ver trilha inteira")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when {
                    !permission.granted -> permission.request()
                    myLocation?.centerOnMe(mapView) != true -> scope.launch {
                        snackbar.showSnackbar("Aguardando sinal do GPS…")
                    }
                }
            }) { Icon(Icons.Filled.MyLocation, "Minha localização") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TopoMap(mapView, Modifier.fillMaxSize())
            val t = trail
            if (t != null && t.points.isEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Trilha sem pontos", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Limite de desvio: ${t.trail.deviationDistanceMeters} m por " +
                                "${t.trail.deviationTimeSeconds / 60} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
