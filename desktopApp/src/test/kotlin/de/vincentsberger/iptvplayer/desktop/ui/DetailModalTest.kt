package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
import de.vincentsberger.iptvplayer.desktop.ScreenLayers
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import androidx.compose.runtime.CompositionLocalProvider

/** Detail-Fenster über der Startseite: öffnen, abspielen, schließen, Zurück aus dem Player. */
class DetailModalTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(app: AppState = testAppState(), tab: Tab = Tab.MOVIES): AppState {
        app.selectTab(tab)
        rule.setContent {
            AppTheme { ScreenLayers(app) { Text("player") } }
        }
        return app
    }

    private fun openDune(app: AppState) {
        app.open(app.section(Section.MOVIES).items, app.section(Section.MOVIES).items.indexOfFirst { it.name == "Dune" })
        rule.waitForIdle()
    }

    private fun button(text: String) = rule.onNode(hasText(text) and hasClickAction())

    private fun assertModalGone() {
        rule.mainClock.advanceTimeBy(400)
        rule.onAllNodesWithTag("detail-modal").assertCountEquals(0)
    }

    @Test
    fun modalIsAtMost820dpWide() {
        val app = testAppState()
        app.selectTab(Tab.MOVIES)
        rule.setContent { AppTheme { Box(Modifier.requiredSize(1600.dp, 1000.dp)) { ScreenLayers(app) { Text("player") } } } }
        openDune(app)
        assertEquals(820f, rule.onNodeWithTag("detail-modal").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun clickingAMovieOpensTheModalOverHome() {
        val app = show()
        rule.onAllNodesWithText("Dune").onFirst().performClick()
        assertEquals("Dune", assertIs<Screen.Movie>(app.screen).movie.name)
        rule.onNodeWithTag("detail-modal").assertExists()
        button("Filme").assertIsDisplayed() // Seitenleiste der Startseite bleibt darunter stehen
    }

    @Test
    fun playOnTheMovieDetailsStartsIt() {
        val app = show()
        openDune(app)
        button("Abspielen").performClick()
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("Dune", player.items[player.index].name)
    }

    @Test
    fun closeButtonClosesTheModal() {
        val app = show()
        openDune(app)
        rule.onNodeWithContentDescription("Schließen").performClick()
        assertEquals(Screen.Home, app.screen)
        assertModalGone()
    }

    @Test
    fun clickOnTheDimmedGroundClosesTheModal() {
        val app = show()
        openDune(app)
        rule.onNodeWithTag("detail-scrim").performMouseInput { click(Offset(5f, 5f)) }
        assertEquals(Screen.Home, app.screen)
        assertModalGone()
    }

    @Test
    fun clickInsideTheModalKeepsItOpen() {
        val app = show()
        openDune(app)
        rule.onNodeWithTag("detail-modal").performMouseInput { click(Offset(20f, height - 10f)) }
        assertIs<Screen.Movie>(app.screen)
    }

    @Test
    fun backFromPlayerShowsTheModalAgain() {
        val app = show()
        openDune(app)
        button("Abspielen").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithText("player").assertCountEquals(1)
        app.back()
        rule.mainClock.advanceTimeBy(600)
        assertIs<Screen.Movie>(app.screen)
        rule.onNodeWithTag("detail-modal").assertExists()
        rule.onAllNodesWithText("player").assertCountEquals(0)
    }

    @Test
    fun primaryActionIsFocusedOnOpen() {
        val app = show()
        openDune(app)
        button("Abspielen").assertIsFocused()
    }

    @Test
    fun filterOfTheGridSurvivesTheModal() {
        val app = show()
        rule.onNodeWithTag("browse-filter").performClick()
        rule.onNodeWithTag("browse-filter").performTextInput("du")
        rule.onAllNodesWithText("Dune").onFirst().performClick()
        rule.onNodeWithContentDescription("Schließen").performClick()
        assertModalGone()
        rule.onNodeWithTag("browse-filter").assertTextContains("du", substring = true)
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun clickSetsTheAnchorToTheTileCenter() {
        val app = testAppState().also { it.selectTab(Tab.MOVIES) }
        val anchor = DetailAnchor()
        rule.setContent { AppTheme { CompositionLocalProvider(LocalDetailAnchor provides anchor) { HomeScreen(app) } } }
        val tile = rule.onNodeWithTag("media-card:Dune")
        val center = tile.fetchSemanticsNode().boundsInWindow.center
        tile.performClick()
        val got = assertNotNull(anchor.tileCenter)
        assertEquals(center.x, got.x, 1f)
        assertEquals(center.y, got.y, 1f)
    }

    @Test
    fun hoverShowsNoFloatingCard() {
        show()
        val before = rule.onAllNodesWithText("Dune").fetchSemanticsNodes().size
        rule.onNodeWithTag("media-card:Dune").performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("Dune").assertCountEquals(before)
    }

    // ---------- Review: Fokus und Doppelklick ----------

    /** Wie der echte Player: fordert beim Erscheinen sofort den Tastaturfokus an. */
    private fun showWithFocusingPlayer(app: AppState = testAppState(), tab: Tab = Tab.MOVIES): AppState {
        app.selectTab(tab)
        rule.setContent {
            AppTheme {
                ScreenLayers(app) {
                    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.focusRequester(focus).focusable().testTag("player"),
                    )
                    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
                }
            }
        }
        return app
    }

    @Test
    fun playerGetsKeyboardFocusAfterPlayFromModal() {
        val app = showWithFocusingPlayer()
        openDune(app)
        button("Abspielen").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("player").assertIsFocused()
    }

    @Test
    fun closingReturnsFocusToTheTile() {
        show()
        rule.onNodeWithTag("media-card:Dune").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Schließen").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("media-card:Dune").assertIsFocused()
    }

    @Test
    fun doubleClickOnPlayStartsOnlyOnePlayer() {
        val app = show()
        openDune(app)
        rule.mainClock.autoAdvance = false
        button("Abspielen").performClick()
        rule.mainClock.advanceTimeBy(60)
        button("Abspielen").performClick() // zweiter Klick, während das Fenster ausblendet
        rule.mainClock.advanceTimeBy(600)
        app.back()
        assertIs<Screen.Movie>(app.screen, "ein Zurück führt aus dem (einzigen) Player ins Fenster")
    }
}
