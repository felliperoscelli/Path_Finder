package com.pathfinder.ui.components

import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.map.MapLayer
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

/**
 * Botões redondos flutuantes sobre o mapa: camadas, bússola (toque = norte para cima) e,
 * se [onMyLocation] for informado, minha localização.
 */
@Composable
fun MapControls(
    mapView: MapView,
    modifier: Modifier = Modifier,
    onMyLocation: (() -> Unit)? = null,
) {
    var pickingLayer by remember { mutableStateOf(false) }
    val orientation = rememberMapOrientation(mapView)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapRoundButton(Icons.Filled.Layers, "Camadas do mapa") { pickingLayer = true }
        CompassButton(orientation) {
            mapView.controller.animateTo(mapView.mapCenter, mapView.zoomLevelDouble, 300L, 0f)
        }
        if (onMyLocation != null) {
            MapRoundButton(Icons.Filled.MyLocation, "Minha localização", tint = MaterialTheme.colorScheme.primary, onClick = onMyLocation)
        }
    }
    if (pickingLayer) MapLayerSheet(onDismiss = { pickingLayer = false })
}

@Composable
private fun MapRoundButton(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.padding(12.dp))
    }
}

/** Agulha vermelha aponta para o norte do mapa; gira junto quando o mapa é girado. */
@Composable
private fun CompassButton(orientation: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp),
    ) {
        val south = MaterialTheme.colorScheme.outline
        Canvas(Modifier.padding(10.dp).rotate(orientation)) {
            val c = Offset(size.width / 2, size.height / 2)
            val half = size.width * 0.18f
            val north = Path().apply {
                moveTo(c.x, 0f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            val southPath = Path().apply {
                moveTo(c.x, size.height); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            drawPath(north, Color(0xFFDB4437))
            drawPath(southPath, south)
        }
    }
}

/** Rotação atual do mapa em graus (sentido horário), atualizada a cada redesenho. */
@Composable
private fun rememberMapOrientation(mapView: MapView): Float {
    var orientation by remember(mapView) { mutableFloatStateOf(mapView.mapOrientation) }
    DisposableEffect(mapView) {
        val watcher = object : Overlay() {
            override fun draw(canvas: AndroidCanvas, projection: Projection) {
                val o = mapView.mapOrientation
                if (abs(o - orientation) > 0.5f) orientation = o
            }
        }
        mapView.overlays.add(watcher)
        onDispose { mapView.overlays.remove(watcher) }
    }
    return orientation
}

/** Escolha da camada de fundo, com miniaturas. A escolha vale para todos os mapas do app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLayerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as PathFinderApp).container.settingsRepository }
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings?.mapLayer ?: MapLayer.TOPO
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tipo de mapa", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapLayer.entries.forEach { layer ->
                    LayerOption(layer, selected = layer == current) {
                        scope.launch { repository.update { it.copy(mapLayer = layer) } }
                    }
                }
            }
            Text(current.description, style = MaterialTheme.typography.bodyMedium)
            if (!current.offlineDownload) {
                Text(
                    "Sem internet, o satélite mostra só os lugares que você já abriu nele com internet " +
                        "(ficam guardados por 1 ano). Antes do evento, abra a região em satélite com Wi-Fi " +
                        "e passeie pelo mapa nos zooms que vai usar. O download de áreas em \"Mapas offline\" " +
                        "baixa só as curvas de nível.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LayerOption(layer: MapLayer, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(shape).clickable(role = Role.RadioButton, onClick = onClick).padding(4.dp),
    ) {
        Surface(
            shape = shape,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(80.dp),
        ) {
            LayerThumbnail(layer)
        }
        Text(
            layer.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Miniatura desenhada (funciona sem internet), lembrando o aspecto de cada camada. */
@Composable
private fun LayerThumbnail(layer: MapLayer) {
    Canvas(Modifier.size(80.dp)) {
        val w = size.width
        val h = size.height
        when (layer) {
            MapLayer.TOPO -> {
                drawRect(Color(0xFFF4F1E4))
                drawCircle(Color(0xFFCFE3B4), radius = w * 0.32f, center = Offset(w * 0.25f, h * 0.8f))
                for (i in 1..5) {
                    drawOval(
                        Color(0xFFB0835A),
                        topLeft = Offset(w * (0.55f - i * 0.09f), h * (0.42f - i * 0.07f)),
                        size = Size(w * i * 0.2f, h * i * 0.14f),
                        style = Stroke(width = if (i == 5) 2.5f else 1.5f),
                    )
                }
                val river = Path().apply {
                    moveTo(0f, h * 0.15f)
                    cubicTo(w * 0.3f, h * 0.35f, w * 0.5f, h * 0.95f, w, h * 0.75f)
                }
                drawPath(river, Color(0xFF5B9BD5), style = Stroke(width = 4f))
            }
            MapLayer.SATELLITE -> {
                drawRect(Color(0xFF3E5A2E))
                drawCircle(Color(0xFF6E8B47), radius = w * 0.3f, center = Offset(w * 0.75f, h * 0.25f))
                drawCircle(Color(0xFFA89A6A), radius = w * 0.18f, center = Offset(w * 0.2f, h * 0.7f))
                drawCircle(Color(0xFF2C4521), radius = w * 0.22f, center = Offset(w * 0.3f, h * 0.2f))
                val road = Path().apply {
                    moveTo(0f, h * 0.45f)
                    cubicTo(w * 0.4f, h * 0.4f, w * 0.6f, h * 0.7f, w, h * 0.9f)
                }
                drawPath(road, Color(0xFFCDBB94), style = Stroke(width = 5f))
            }
        }
    }
}
