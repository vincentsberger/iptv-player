package de.tvplayer.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Screen
import de.tvplayer.shared.data.Section
import de.tvplayer.shared.data.TvException
import de.tvplayer.shared.testing.FakeHttp
import de.tvplayer.shared.testing.InMemoryKeyValueStore
import de.tvplayer.shared.testing.SampleData
import de.tvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class) // performMouseInput für Hover
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(app: AppState = testAppState()): AppState {
        rule.setContent { AppTheme { HomeScreen(app) } }
        return app
    }

    /** Eintrag in der Seitenleiste oder einer Kategorieliste (klickbar, exakter Text). */
    private fun nav(text: String) = rule.onNode(hasText(text) and hasClickAction())

    /** Karten zeigen den Titel ohne Bild zweimal (Karte + Unterschrift); es zählt, ob es ihn gibt. */
    private fun assertShown(text: String, shown: Boolean = true) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty().let { assertEquals(shown, it, text) }

    // ---------- Live TV ----------

    @Test
    fun liveTvListsChannelsAndCategories() {
        show()
        listOf("Das Erste", "ZDF", "Sport1").forEach { rule.onNodeWithText(it).assertExists() }
        nav("Nachrichten").assertExists()
        rule.onNodeWithText("3 Sender · Doppelklick zum Ansehen").assertExists()
    }

    @Test
    fun categoryFiltersChannels() {
        show()
        nav("Sport").performClick()
        rule.onNodeWithText("Sport1").assertExists()
        rule.onNodeWithText("ZDF").assertDoesNotExist()
        rule.onNodeWithText("1 Sender · Doppelklick zum Ansehen").assertExists()
    }

    @Test
    fun selectingAChannelShowsInfoAndPlayOpensPlayer() {
        val app = show()
        rule.onNodeWithText("Wähle einen Sender").assertExists()
        rule.onNodeWithText("ZDF").performClick()
        rule.onNodeWithText("Keine Programminformationen").assertExists()

        rule.onNode(hasText("Ansehen") and hasClickAction()).performClick()
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("ZDF", player.items[player.index].name)
    }

    @Test
    fun rememberingAChannelPutsItIntoFavorites() {
        val app = show()
        rule.onNodeWithText("Das Erste").performClick()
        nav("Merken").performClick()
        nav("Favorit").assertExists()
        assertEquals(1, app.favorites.size)

        // "Favoriten" gibt es zweimal: [0] Seitenleiste, [1] Kategorie im Live TV
        rule.onAllNodes(hasText("Favoriten") and hasClickAction())[1].performClick()
        rule.onNodeWithText("ZDF").assertDoesNotExist()
        rule.onNodeWithText("1 Sender · Doppelklick zum Ansehen").assertExists()
    }

    @Test
    fun loadErrorOffersRetry() {
        var fail = true
        val http = FakeHttp { url ->
            if (fail && FakeHttp.actionOf(url) == "get_live_streams") throw TvException("Server nicht erreichbar")
            SampleData.respond(url)
        }
        show(testAppState(http))
        rule.onNodeWithText("Das hat nicht geklappt").assertExists()
        rule.onNodeWithText("Server nicht erreichbar").assertExists()

        fail = false
        nav("Erneut versuchen").performClick()
        rule.onNodeWithText("Das Erste").assertExists()
    }

    // ---------- Filme und Serien ----------

    @Test
    fun moviesStartInFirstCategoryAndCanShowAll() {
        show()
        nav("Filme").performClick()
        assertShown("Arrival")
        assertShown("Dune")
        assertShown("Ziemlich beste Freunde", shown = false)

        nav("Alle Filme").performClick()
        assertShown("Ziemlich beste Freunde")
    }

    @Test
    fun listFilterNarrowsResults() {
        show()
        nav("Filme").performClick()
        rule.onNodeWithTag("browse-filter").performTextInput("dun")
        assertShown("Dune")
        assertShown("Arrival", shown = false)
        rule.onNodeWithText("1 Titel").assertExists()
    }

    @Test
    fun starOnHoverRemembersMovieForFavoritesCategory() {
        val app = show()
        nav("Filme").performClick()
        rule.onNodeWithContentDescription("Als Favorit merken").assertDoesNotExist() // Stern erst bei Hover

        rule.onAllNodesWithText("Dune").onFirst().performMouseInput { moveTo(center) }
        rule.onNodeWithContentDescription("Als Favorit merken").performClick()
        assertEquals(1, app.favorites.size)

        // [0] Seitenleiste, [1] Kategorie „Favoriten“ in der Filmliste
        rule.onAllNodes(hasText("Favoriten") and hasClickAction())[1].performClick()
        assertShown("Dune")
        assertShown("Arrival", shown = false)
    }

    @Test
    fun clickingAMovieOpensThePlayer() {
        val app = show()
        nav("Filme").performClick()
        rule.onAllNodesWithText("Dune").onFirst().performClick()
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("Dune", player.items[player.index].name)
    }

    @Test
    fun clickingASeriesOpensTheSeriesScreen() {
        val app = show()
        nav("Serien").performClick()
        rule.onAllNodesWithText("Babylon Berlin").onFirst().performClick()
        assertEquals("Babylon Berlin", assertIs<Screen.Series>(app.screen).series.name)
    }

    // ---------- Suche, Favoriten, Einstellungen ----------

    @Test
    fun searchNeedsTwoLettersAndGroupsResults() {
        show()
        nav("Suche").performClick()
        rule.onNodeWithText("Was möchtest du sehen?").assertExists()

        rule.onNodeWithTag("search-field").performTextInput("ar")
        rule.onNodeWithText("Filme (1)").assertExists()
        assertShown("Arrival")

        rule.onNodeWithTag("search-field").performTextInput("xyz")
        rule.onNodeWithText("Keine Treffer").assertExists()
    }

    @Test
    fun favoritesPageIsEmptyAtFirst() {
        show()
        rule.onAllNodes(hasText("Favoriten") and hasClickAction())[0].performClick()
        rule.onNodeWithText("Noch keine Favoriten").assertExists()
    }

    @Test
    fun settingsShowAccountAndLogoutNeedsConfirmation() {
        val kv = InMemoryKeyValueStore()
        val app = show(testAppState(kv = kv))
        nav("Einstellungen").performClick()
        rule.onNodeWithText("Xtream Codes · user @ tv.example").assertExists()
        rule.onNodeWithText("3 Sender · 0 Filme · 0 Serien").assertExists()

        nav("Abmelden").performClick()
        assertEquals(Screen.Home, app.screen, "erster Klick fragt nur nach")
        nav("Wirklich abmelden?").performClick()
        assertEquals(Screen.Login, app.screen)
        assertTrue(kv.values.isEmpty())
    }

    @Test
    fun clearHistoryEmptiesContinueWatching() {
        val app = show()
        val movie = de.tvplayer.shared.data.Item("100", "Arrival", null, "10", "http://tv.example/movie/user/pass/100.mp4", Section.MOVIES)
        app.saveProgress(movie, 120_000, 600_000)
        nav("Filme").performClick()
        nav("Weiterschauen").assertExists()

        nav("Einstellungen").performClick()
        nav("Verlauf löschen").performClick()
        nav("Filme").performClick()
        rule.onAllNodesWithText("Weiterschauen").assertCountEquals(0)
    }
}
