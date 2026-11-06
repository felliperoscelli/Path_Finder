package com.pathfinder.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Tutorial "Como usar o app": uma página por assunto, arrastando para o lado ou pelos botões. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onBack: () -> Unit) {
    val pages = TutorialContent.pages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Como usar o app") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
        bottomBar = {
            val page = pager.currentPage
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    enabled = page > 0,
                ) { Text("Anterior") }
                Dots(count = pages.size, current = page, modifier = Modifier.weight(1f))
                if (page < pages.lastIndex) {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(page + 1) } }) { Text("Próximo") }
                } else {
                    Button(onClick = onBack) { Text("Concluir") }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding)) { index ->
            PageContent(pages[index])
        }
    }
}

@Composable
private fun PageContent(page: TutorialPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(page.topic.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Text(page.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        page.items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top = 8.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Página ${current + 1} de $count" },
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(if (i == current) 9.dp else 6.dp).background(color, CircleShape))
        }
    }
}

private val TutorialTopic.icon: ImageVector
    get() = when (this) {
        TutorialTopic.WELCOME -> Icons.Filled.WavingHand
        TutorialTopic.MAP -> Icons.Filled.Map
        TutorialTopic.OFFLINE -> Icons.Filled.Download
        TutorialTopic.CREATE -> Icons.Filled.Route
        TutorialTopic.EDIT -> Icons.Filled.Edit
        TutorialTopic.NAVIGATE -> Icons.Filled.Explore
        TutorialTopic.GROUP -> Icons.Filled.Groups
        TutorialTopic.COORDINATOR -> Icons.Filled.Sos
        TutorialTopic.TIPS -> Icons.Filled.Lightbulb
    }
