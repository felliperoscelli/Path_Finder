package com.pathfinder.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.map.GeoBounds
import com.pathfinder.map.MapLayer
import com.pathfinder.map.SmoothTileProvider
import com.pathfinder.map.TopoMap
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Zoom máximo de exibição; acima de [TopoMap.MAX_ZOOM] os tiles são ampliados. */
private const val MAX_DISPLAY_ZOOM = 19.0

/**
 * Cria um [MapView] ligado ao ciclo de vida da tela, com a camada escolhida pelo usuário
 * (ou [fixedLayer], se indicada), giro com dois dedos e a atribuição do fornecedor.
 */
@Composable
fun rememberTopoMapView(fixedLayer: MapLayer? = null): MapView {
    val context = LocalContext.current
    val chosen by remember { (context.applicationContext as PathFinderApp).container.settingsRepository.settings }
        .collectAsStateWithLifecycle(initialValue = null)
    val layer = fixedLayer ?: chosen?.mapLayer
    val mapView = remember {
        val initial = (fixedLayer ?: MapLayer.TOPO).tileSource
        // Provedor que amplia com suavização acima do zoom 17 (sem "quadriculado").
        val provider = SmoothTileProvider(context.applicationContext, initial)
        MapView(context, provider).apply {
            setTileSource(initial)
            provider.onRefreshed = ::invalidate
            // Parado num zoom acima do 17: garante os tiles do 17 que a ampliação usa.
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean { ensureOverzoomParents(this@apply, provider); return false }
                override fun onZoom(event: ZoomEvent?): Boolean { ensureOverzoomParents(this@apply, provider); return false }
            }, 300))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = TopoMap.MIN_ZOOM.toDouble()
            maxZoomLevel = MAX_DISPLAY_ZOOM
            controller.setZoom(TopoMap.DEFAULT_ZOOM)
            controller.setCenter(TopoMap.DEFAULT_CENTER)
            overlays.add(ScaleBarOverlay(this).apply { setAlignBottom(true) })
            overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
            // Atribuição do fornecedor logo acima da barra de escala, no canto esquerdo.
            val density = context.resources.displayMetrics.density
            overlays.add(
                CopyrightOverlay(context).apply {
                    setAlignBottom(true)
                    setAlignRight(false)
                    setTextSize(10)
                    setOffset((8 * density).toInt(), (30 * density).toInt())
                },
            )
        }
    }
    LaunchedEffect(mapView, layer) {
        val current = layer ?: return@LaunchedEffect
        val source = current.tileSource
        if (mapView.tileProvider.tileSource.name() != source.name()) mapView.setTileSource(source)
        mapView.overlays.filterIsInstance<CopyrightOverlay>().forEach {
            it.setTextColor(if (current.darkBackground) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY)
        }
        mapView.invalidate()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(mapView) {
        onDispose { mapView.onDetach() }
    }
    return mapView
}

private fun ensureOverzoomParents(mapView: MapView, provider: SmoothTileProvider) {
    val box = mapView.boundingBox
    if (box.latNorth > box.latSouth && box.lonEast > box.lonWest) {
        provider.ensureParents(GeoBounds(box.latNorth, box.latSouth, box.lonEast, box.lonWest), mapView.zoomLevelDouble)
    }
}

/** Exibe um [MapView] criado por [rememberTopoMapView]. */
@Composable
fun TopoMap(mapView: MapView, modifier: Modifier = Modifier, update: (MapView) -> Unit = {}) {
    AndroidView(factory = { mapView }, modifier = modifier, update = update)
}

/** Avisa [onChange] sempre que a área visível do mapa mudar. */
@Composable
fun VisibleBoundsListener(mapView: MapView, onChange: (GeoBounds, zoom: Double) -> Unit) {
    val currentOnChange by rememberUpdatedState(onChange)
    DisposableEffect(mapView) {
        fun emit() {
            val box = mapView.boundingBox
            if (box.latNorth > box.latSouth && box.lonEast > box.lonWest) {
                currentOnChange(GeoBounds(box.latNorth, box.latSouth, box.lonEast, box.lonWest), mapView.zoomLevelDouble)
            }
        }
        val listener = DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { emit(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { emit(); return false }
        }, 250)
        mapView.addMapListener(listener)
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> emit() }
        onDispose { mapView.removeMapListener(listener) }
    }
}

/**
 * Camada "minha localização". Só é ativada quando [enabled] (permissão concedida).
 * Retorna a camada para que a tela possa centralizar o mapa na posição atual.
 */
@Composable
fun rememberMyLocationOverlay(mapView: MapView, enabled: Boolean): MyLocationNewOverlay? {
    val context = LocalContext.current
    val overlay = remember(mapView, enabled) {
        if (enabled) {
            MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
                setDirectionArrow(MapIcons.myLocation(context), MapIcons.myLocationDirection(context))
                setPersonAnchor(0.5f, 0.5f)
                setDirectionAnchor(0.5f, 0.5f)
            }
        } else {
            null
        }
    }
    DisposableEffect(overlay) {
        if (overlay != null) {
            overlay.enableMyLocation()
            mapView.overlays.add(overlay)
            mapView.invalidate()
        }
        onDispose {
            if (overlay != null) {
                overlay.disableMyLocation()
                overlay.onDetach(mapView)
                mapView.overlays.remove(overlay)
            }
        }
    }
    return overlay
}

/** Centraliza o mapa na última posição conhecida e passa a segui-la. */
fun MyLocationNewOverlay.centerOnMe(mapView: MapView): Boolean {
    val me = myLocation ?: return false
    mapView.controller.animateTo(me, maxOf(mapView.zoomLevelDouble, 16.0), null)
    enableFollowLocation()
    return true
}

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Estado da permissão de localização, com função para solicitá-la. */
class LocationPermissionState(val granted: Boolean, val request: () -> Unit)

@Composable
fun rememberLocationPermission(): LocationPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.any { it } || context.hasLocationPermission() }
    return LocationPermissionState(granted) {
        launcher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
}
