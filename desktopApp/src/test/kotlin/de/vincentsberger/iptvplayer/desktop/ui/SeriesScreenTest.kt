package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SeriesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val series = Item("200", "Babylon Berlin", null, "20", "", Section.SERIES)
    private fun episode(id: String, name: String) =
        Item(id, name, null, "200", "http://tv.example/series/user/pass/$id.mp4", Section.SERIES)

    /** Öffnet die Serie wie aus der Übersicht (lädt die Staffeln) und zeigt die Serienansicht. */
    private fun show(app: AppState = testAppState()): AppState {
        app.open(listOf(series), 0)
        rule.setContent { AppTheme { SeriesScreen(app, series) } }
        return app
    }

    private fun button(text: String) = rule.onNode(hasText(text) and hasClickAction())
    private fun shown(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun showsSeasonsAndEpisodesOfTheFirstSeason() {
        show()
        rule.onNodeWithText("2 Staffeln · 3 Folgen").assertExists()
        button("Staffel 1").assertExists()
        button("Staffel 2").assertExists()
        assertTrue(shown("Pilot"))
        assertTrue(shown("Der Zug"))
        assertTrue(!shown("Neue Spur"))
    }

    @Test
    fun switchingSeasonShowsItsEpisodes() {
        show()
        button("Staffel 2").performClick()
        assertTrue(shown("Neue Spur"))
        assertTrue(!shown("Pilot"))
    }

    @Test
    fun playStartsWithFirstEpisode() {
        val app = show()
        button("Abspielen").performClick()
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("Pilot", player.items[player.index].name)
        assertEquals(listOf("Pilot", "Der Zug"), player.items.map { it.name }, "Player bekommt die ganze Staffel")
    }

    @Test
    fun continueWatchingJumpsToLastEpisodeAndItsSeason() {
        val app = testAppState()
        app.saveProgress(episode("2011", "Neue Spur"), 120_000, 600_000)
        show(app)

        rule.waitForIdle()
        assertTrue(shown("Neue Spur"), "Ansicht springt zur Staffel der letzten Folge")
        button("Weiterschauen").performClick()
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("Neue Spur", player.items[player.index].name)
        assertEquals(120_000, player.startPosition)
    }

    @Test
    fun rememberTogglesFavorite() {
        val app = show()
        button("Merken").performClick()
        button("Favorit").assertExists()
        assertTrue(series.favKey in app.favorites)
    }

    @Test
    fun backReturnsToHome() {
        val app = testAppState()
        show(app)
        rule.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun seriesWithoutEpisodesShowsHint() {
        val app = testAppState(de.vincentsberger.iptvplayer.shared.testing.SampleData.http(mapOf("get_series_info" to """{"episodes":{}}""")))
        show(app)
        rule.onNodeWithText("Keine Folgen").assertExists()
        rule.onNodeWithText("Für diese Serie sind keine Folgen verfügbar.").assertExists()
    }
}
