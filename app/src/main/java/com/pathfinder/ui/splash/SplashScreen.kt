package com.pathfinder.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tempo total da abertura em Compose. */
private const val SPLASH_MILLIS = 1_200L

/**
 * Tamanho da bússola igual ao da abertura do sistema (ícone sem fundo: 288 dp), para a
 * passagem de uma para a outra não dar salto.
 */
private val LOGO_SIZE = 288.dp

/** Abertura: bússola no centro sobre o verde do ícone e "Path Finder" aparecendo embaixo. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val finished by rememberUpdatedState(onFinished)
    val logoScale = remember { Animatable(1f) }
    val textAlpha = remember { Animatable(0f) }
    val textScale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1.06f, tween(400, easing = FastOutSlowInEasing)); logoScale.animateTo(1f, tween(300)) }
        launch { textAlpha.animateTo(1f, tween(500, delayMillis = 150)) }
        launch { textScale.animateTo(1f, tween(500, delayMillis = 150, easing = FastOutSlowInEasing)) }
        delay(SPLASH_MILLIS)
        finished()
    }
    Box(Modifier.fillMaxSize().background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(LOGO_SIZE).graphicsLayer { scaleX = logoScale.value; scaleY = logoScale.value },
        )
        // Abaixo do anel da bússola (raio de 32/108 do desenho), sem empurrar a logo do centro.
        Text(
            "Path Finder",
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .offset(y = LOGO_SIZE * 0.42f)
                .graphicsLayer { alpha = textAlpha.value; scaleX = textScale.value; scaleY = textScale.value },
        )
    }
}
