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
import kotlin.test.assertTrue
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.data.Item

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
    fun gridCardsFillTheirCell() = runDesktopComposeUiTest(1440, 900) {
        val app = testAppState().also { it.selectTab(Tab.MOVIES) }
        setContent { AppTheme { HomeScreen(app) } }
        val cards = listOf("Arrival", "Dune").map { onNodeWithTag("media-card:$it").fetchSemanticsNode().boundsInRoot }
        cards.forEach { assertEquals(cards[0].width, it.width, 1f, "gleich breit") }
        cards.forEach { assertEquals(it.width * 1.5f, it.height, 1f, "Hochformat 2:3") }
        assertEquals(18f, cards[1].left - cards[0].right, 1f, "Lücke = Rasterabstand")
        assertTrue(cards[0].width > 150f, "Karte füllt die Spalte statt fester 150 dp")
    }

    @Test
    fun yearAndRatingSitOnTheCover() {
        val movie = Item("9", "Dune", "http://x/p.jpg", "sf", "http://x/9.mp4", Section.MOVIES, rating = 7.9)
        rule.setContent { AppTheme { Box(Modifier.width(200.dp)) { MediaCard(movie, false, 0f, {}, null, fillCell = true) } } }
        val card = rule.onNodeWithTag("media-card:Dune").fetchSemanticsNode().boundsInRoot
        val facts = rule.onNodeWithText("★ 7,9").fetchSemanticsNode().boundsInRoot
        assertTrue(card.contains(facts.topLeft) && card.contains(facts.bottomRight - androidx.compose.ui.geometry.Offset(1f, 1f)), "$facts liegt in $card")
    }

    @Test
    fun gridColumnsAdaptToTheWindow() {
        assertEquals(GridCells.Adaptive(150.dp), cardColumns(wide = false))
        assertEquals(GridCells.Adaptive(210.dp), cardColumns(wide = true))
    }
}
