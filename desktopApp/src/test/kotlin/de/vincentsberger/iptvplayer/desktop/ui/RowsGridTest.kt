package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reihen blättern, Raster ohne Lücken, Suche als Raster (Review-Paket E: H5, M4). */
@OptIn(ExperimentalTestApi::class)
class RowsGridTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rowShowsPagersOnHoverAndPages() {
        val app = testAppState()
        val movies = app.section(Section.MOVIES).items
        rule.setContent {
            AppTheme { Box(Modifier.width(400.dp)) { ItemsRow("Filme", movies, emptySet(), { 0f }, { _, _ -> }, {}) } }
        }
        rule.onNodeWithContentDescription("Weiterblättern").assertDoesNotExist() // erst beim Überfahren
        rule.onRoot().performMouseInput { moveTo(center) }
        rule.onNodeWithContentDescription("Zurückblättern").assertDoesNotExist() // ganz links
        rule.onNodeWithContentDescription("Weiterblättern").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Zurückblättern").assertExists()
    }

    @Test
    fun searchShowsAGridInsteadOfRows() {
        val app = testAppState()
        app.selectTab(Tab.SEARCH)
        app.query = "Dune"
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onNodeWithTag("search-grid:Filme (1)").assertExists()
        rule.onNodeWithContentDescription("Weiterblättern").assertDoesNotExist()
    }

    @Test
    fun gridColumnsMatchTheCardWidth() {
        val app = testAppState()
        val movie = app.section(Section.MOVIES).items.first()
        val channel = app.section(Section.LIVE).items.first()
        assertEquals(GridCells.FixedSize(cardSize(movie).first), cardColumns(wide = false))
        assertEquals(GridCells.FixedSize(cardSize(channel).first), cardColumns(wide = true))
    }
}
