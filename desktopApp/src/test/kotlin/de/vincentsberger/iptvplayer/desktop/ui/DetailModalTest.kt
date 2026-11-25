package de.vincentsberger.iptvplayer.desktop.ui

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
}
