package com.pathfinder.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialContentTest {
    @Test
    fun umaPaginaPorAssunto() {
        assertEquals(TutorialTopic.entries.toList(), TutorialContent.pages.map { it.topic })
    }

    @Test
    fun paginasTemTituloETexto() {
        TutorialContent.pages.forEach { page ->
            assertTrue(page.title, page.title.isNotBlank())
            assertTrue(page.title, page.items.isNotEmpty() && page.items.all { it.isNotBlank() })
        }
    }
}
