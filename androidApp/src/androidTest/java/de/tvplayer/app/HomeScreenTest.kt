package de.tvplayer.app

import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.tvplayer.app.ui.AppTheme
import de.tvplayer.app.ui.HomeScreen
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Screen
import de.tvplayer.shared.Tab
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
import de.tvplayer.shared.data.TvException
import de.tvplayer.shared.testing.FakeHttp
import de.tvplayer.shared.testing.InMemoryKeyValueStore
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
class HomeScreenTest {

    @get:Rule val rule = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private lateinit var vm: AppState

    private fun show(state: AppState = testAppState(kv = kv)) {
        vm = state
        leaveTouchMode()
        rule.setContent { AppTheme { HomeScreen(vm) } }
        rule.settle()
    }

    private fun tab(t: Tab) = rule.onNodeWithTag("tab:${t.name}").select().also { rule.settle() }

    /** Taste auf der Fernbedienung: geht an das gerade fokussierte Element. */
    private fun press(key: Key): SemanticsNodeInteraction =
        rule.onNode(isFocused()).performKeyInput { pressKey(key) }.also { rule.waitForIdle() }

    // ---------- Live TV ----------

    @Test
    fun liveTvListsAllChannels() {
        show()
        listOf(0, 1, 2).forEach { rule.onNodeWithTag("channel:$it").assertExists() }
        rule.onNodeWithTag("channel:3").assertDoesNotExist()
        assertTrue(rule.hasText("Sport1"))
    }

    @Test
    fun selectingACategoryFiltersChannels() {
        show()
        rule.onNodeWithTag("category:2").select()
        rule.waitForIdle()
        assertEquals("2", vm.liveCategory)
        rule.onNodeWithTag("channel:0").assertExists()
        rule.onNodeWithTag("channel:1").assertDoesNotExist()
        assertFalse(rule.hasText("Das Erste"))
    }

    @Test
    fun clickingAChannelOpensThePlayer() {
        show()
        rule.onNodeWithTag("channel:1").select()
        val player = vm.screen as Screen.Player
        assertEquals("ZDF", player.items[player.index].name)
    }

    @Test
    fun remoteNavigatesFromCategoriesToChannelsAndStartsPlayback() {
        show()
        // Beim Start steht der Fokus auf der aktiven Kategorie „Alle Sender“.
        rule.onNodeWithTag("category:${AppState.LIVE_ALL}").assertIsFocused()

        press(Key.DirectionDown) // → „Nachrichten“: Fokus wählt die Kategorie
        rule.onNodeWithTag("category:1").assertIsFocused()
        assertEquals("1", vm.liveCategory)

        press(Key.DirectionRight) // → in die Senderliste (welcher Sender, hängt von der Geometrie ab)
        val key = vm.focusKey.orEmpty()
        assertTrue("Fokus in der Senderliste von „Nachrichten“: $key", key.startsWith("live:1:"))
        val idx = key.substringAfterLast(':').toInt()
        rule.onNodeWithTag("channel:$idx").assertIsFocused()

        press(if (idx == 0) Key.DirectionDown else Key.DirectionUp)
        rule.onNodeWithTag("channel:${1 - idx}").assertIsFocused()

        press(Key.DirectionCenter) // OK
        val player = vm.screen as Screen.Player
        assertEquals(listOf("Das Erste", "ZDF")[1 - idx], player.items[player.index].name)
    }

    @Test
    fun loadErrorShowsRetryWhichReloads() {
        var fail = true
        val http = FakeHttp { url -> if (fail) throw TvException("Server nicht erreichbar") else SampleData.respond(url) }
        show(testAppState(http = http, kv = kv))
        rule.onNodeWithText("Server nicht erreichbar").assertExists()

        fail = false
        rule.onNodeWithText("Erneut versuchen").select()
        rule.waitForIdle()
        rule.onNodeWithTag("channel:0").assertExists()
    }

    // ---------- Navigation und Filme/Serien ----------

    @Test
    fun railSwitchesTabsAndLoadsSections() {
        show()
        tab(Tab.MOVIES)
        assertEquals(Tab.MOVIES, vm.tab)
        rule.onNodeWithTag("card:cat:10:0").assertExists()
        rule.onNodeWithTag("card:cat:11:0").assertExists()

        tab(Tab.SERIES)
        rule.onNodeWithTag("card:cat:20:0").assertExists()
        rule.onNodeWithTag("card:cat:10:0").assertDoesNotExist()
    }

    @Test
    fun firstMovieGetsFocusAndOkStartsIt() {
        show()
        tab(Tab.MOVIES)
        rule.onNodeWithTag("card:cat:10:0").assertIsFocused()
        press(Key.DirectionRight)
        rule.onNodeWithTag("card:cat:10:1").assertIsFocused()
        press(Key.Enter)
        val player = vm.screen as Screen.Player
        assertEquals("Dune", player.items[player.index].name)
    }

    @Test
    fun openingASeriesShowsItsSeasons() {
        show()
        tab(Tab.SERIES)
        rule.onNodeWithTag("card:cat:20:0").select()
        val screen = vm.screen as Screen.Series
        assertEquals("Babylon Berlin", screen.series.name)
        assertEquals(2, vm.seasons.size)
    }

    @Test
    fun continueWatchingRowAppearsAfterProgress() {
        val state = testAppState(kv = kv)
        val arrival = Item("100", "Arrival", null, "10", "http://tv.example/movie/user/pass/100.mp4", Section.MOVIES)
        state.saveProgress(arrival, 120_000, 600_000)
        show(state)
        tab(Tab.MOVIES)
        rule.onNodeWithText("Weiterschauen").assertExists()
        rule.onNodeWithTag("card:continue:0").assertExists()
    }

    // ---------- Suche, Favoriten, Einstellungen ----------

    @Test
    fun searchNeedsTwoLettersAndFindsAcrossSections() {
        show()
        tab(Tab.SEARCH)
        rule.onNodeWithText("Was möchtest du sehen?").assertExists()

        rule.onNodeWithTag("search:field").performTextInput("ar")
        rule.waitForIdle()
        rule.onNodeWithText("Filme (1)").assertExists()
        rule.onNodeWithTag("card:search:MOVIES:0").assertExists()
    }

    @Test
    fun remoteLeavesTheSearchFieldWithArrowKeys() {
        show()
        tab(Tab.SEARCH)
        rule.onNodeWithTag("search:field").performTextInput("ar")   // fokussiert das Feld und tippt
        rule.onNodeWithTag("search:field").assertIsFocused()

        press(Key.DirectionDown)                                       // ▼ zu den Treffern
        rule.onNodeWithTag("card:search:MOVIES:0").assertIsFocused()

        rule.onNodeWithTag("search:field").performTextInput("r")
        press(Key.DirectionLeft)                                       // ◀ zurück in die Seitenleiste
        rule.onNodeWithTag("search:field").assertIsNotFocused()
    }

    @Test
    fun searchWithoutHitsSaysSo() {
        show()
        tab(Tab.SEARCH)
        rule.onNodeWithTag("search:field").performTextInput("xyz")
        rule.onNodeWithText("Keine Treffer").assertExists()
    }

    @Test
    fun favoritesTabShowsMarkedItems() {
        show()
        tab(Tab.FAVORITES)
        rule.onNodeWithText("Noch keine Favoriten").assertExists()

        rule.runOnIdle { vm.toggleFavorite(vm.section(Section.LIVE).items.first { it.name == "ZDF" }) }
        rule.waitForIdle()
        rule.onNodeWithTag("card:favs:LIVE:0").assertExists()
        rule.onNodeWithTag("card:favs:MOVIES:0").assertDoesNotExist()
    }

    @Test
    fun settingsShowAccountAndLogoutNeedsConfirmation() {
        show()
        tab(Tab.SETTINGS)
        rule.onNodeWithText("Xtream Codes · user @ tv.example").assertExists()

        rule.onNodeWithText("Abmelden").select()
        assertEquals(Screen.Home, vm.screen)
        rule.onNodeWithText("Wirklich abmelden?").select()
        rule.waitForIdle()
        assertEquals(Screen.Login, vm.screen)
        assertTrue(kv.values.isEmpty())
    }
}
