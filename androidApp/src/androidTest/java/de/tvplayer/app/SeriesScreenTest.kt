package de.tvplayer.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.tvplayer.app.ui.AppTheme
import de.tvplayer.app.ui.SeriesScreen
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Screen
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
import de.tvplayer.shared.testing.SampleData
import de.tvplayer.shared.testing.testAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SeriesScreenTest {

    @get:Rule val rule = createComposeRule()

    private val series = Item("200", "Babylon Berlin", null, "20", "", Section.SERIES)
    private lateinit var vm: AppState

    /** Öffnet die Serie wie aus der Liste heraus (lädt die Staffeln) und zeigt die Serienansicht. */
    private fun show(state: AppState = testAppState(), before: (AppState) -> Unit = {}) {
        vm = state
        before(vm)
        vm.open(listOf(series), 0)
        leaveTouchMode()
        rule.setContent { AppTheme { SeriesScreen(vm, series) } }
        rule.settle()
    }

    private fun press(key: Key) {
        rule.onNode(isFocused()).performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun showsSeasonsAndEpisodesOfTheFirstSeason() {
        show()
        rule.onNodeWithText("2 Staffeln · 3 Folgen").assertExists()
        assertTrue(rule.hasText("Pilot"))
        assertTrue(rule.hasText("Der Zug"))
        assertFalse(rule.hasText("Neue Spur"))
    }

    @Test
    fun choosingAnotherSeasonSwitchesEpisodes() {
        show()
        rule.onNodeWithText("Staffel 2").select()
        rule.waitForIdle()
        assertTrue(rule.hasText("Neue Spur"))
        assertFalse(rule.hasText("Pilot"))
    }

    @Test
    fun clickingAnEpisodeStartsItWithTheSeasonAsPlaylist() {
        show()
        rule.onNodeWithTag("episode:1").select()
        val player = vm.screen as Screen.Player
        assertEquals(listOf("Pilot", "Der Zug"), player.items.map { it.name })
        assertEquals(1, player.index)
        assertEquals(0L, player.startPosition)
    }

    @Test
    fun remoteStartsOnFirstEpisodeAndMovesRight() {
        show()
        rule.onNodeWithTag("episode:0").assertIsFocused()
        press(Key.DirectionRight)
        rule.onNodeWithTag("episode:1").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, (vm.screen as Screen.Player).index)
    }

    @Test
    fun jumpsToLastWatchedEpisodeAndResumesThere() {
        // „Neue Spur“ (Staffel 2) wurde schon angeschaut
        val episode = Item("2011", "Neue Spur", null, "200", "http://tv.example/series/user/pass/2011.mp4", Section.SERIES)
        show { it.saveProgress(episode, 120_000, 600_000) }
        assertTrue(rule.hasText("Neue Spur"))
        assertFalse(rule.hasText("Pilot"))
        rule.onNodeWithText("Weiterschauen").assertExists()
        rule.onNodeWithTag("episode:0").assertIsFocused()

        press(Key.Enter)
        val player = vm.screen as Screen.Player
        assertEquals("Neue Spur", player.items[player.index].name)
        assertEquals(120_000L, player.startPosition)
    }

    @Test
    fun seriesWithoutEpisodesShowsMessage() {
        show(testAppState(http = SampleData.http(mapOf("get_series_info" to """{"episodes":{}}"""))))
        rule.onNodeWithText("Für diese Serie sind keine Folgen verfügbar.").assertExists()
    }
}
