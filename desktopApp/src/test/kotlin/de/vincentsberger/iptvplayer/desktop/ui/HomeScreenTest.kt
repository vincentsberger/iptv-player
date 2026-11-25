package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.Key

import androidx.compose.ui.test.pressKey

import androidx.compose.ui.test.performKeyInput

import androidx.compose.ui.test.onRoot

import de.vincentsberger.iptvplayer.shared.data.Account

import de.vincentsberger.iptvplayer.shared.data.Storage

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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performTextInput
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.TvException
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class) // performMouseInput für Hover, performKeyInput für die Tastatur
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    /** Die meisten Tests prüfen Live TV – die App selbst startet auf der Startseite. */
    private fun show(app: AppState = testAppState(), tab: Tab = Tab.LIVE): AppState {
        app.selectTab(tab)
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
        // „3 Sender“ steht zweimal: links alle Sender, über der Liste die gewählte Kategorie („Alle Sender“).
        rule.onAllNodesWithText("3 Sender").assertCountEquals(2)
    }

    @Test
    fun categoryFiltersChannels() {
        show()
        nav("Sport").performClick()
        rule.onNodeWithText("Sport1").assertExists()
        rule.onNodeWithText("ZDF").assertDoesNotExist()
        rule.onNodeWithText("1 Sender").assertExists()
    }

    @Test
    fun selectingAChannelShowsInfoAndPlayOpensPlayer() {
        val app = show()
        rule.onNodeWithText("Wähle einen Sender").assertExists()
        rule.onNodeWithText("ZDF").performClick() // ein Klick spielt direkt ab
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("ZDF", player.items[player.index].name)
        rule.onNodeWithText("Keine Programminformationen").assertExists()
    }

    @Test
    fun liveTvWorksWithTheKeyboardAlone() {
        val app = show()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Keine Programminformationen").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals(1, player.index, "zweiter Sender nach zweimal ↓")
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
        rule.onNodeWithText("1 Sender").assertExists()
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
        nav("Weiter").performClick() // nach dem Neuladen kommt die Übersicht
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
    fun eyeIconOnHoverHidesACategoryAndSettingsBringItBack() {
        val app = show()
        rule.onNodeWithContentDescription("Kategorie ausblenden").assertDoesNotExist() // erst bei Hover
        nav("Sport").performMouseInput { moveTo(center) }
        rule.onNodeWithContentDescription("Kategorie ausblenden").performClick()
        nav("Sport").assertDoesNotExist()
        assertShown("Sport1", shown = false)

        nav("Einstellungen").performClick()
        rule.onNode(hasText("Einblenden") and hasClickAction()).performScrollTo().performClick() // Einstellungen sind lang
        assertEquals(emptySet(), app.hidden)
    }

    // ---------- Startseite: Abo und Neuheiten ----------

    private val day = 24 * 60 * 60 * 1000L
    private val dashNow = 1000 * day
    private val dashboardHttp = SampleData.http(mapOf(
        "login" to """{"user_info":{"auth":1,"status":"Active","exp_date":"${(dashNow + 30 * day) / 1000}","max_connections":"2","active_cons":"1"}}""",
        "get_vod_categories" to """[{"category_id":"10","category_name":"DE | Kino"}]""",
        "get_vod_streams" to """[{"stream_id":1,"name":"Frisch im Kino","category_id":"10","added":"${(dashNow - day) / 1000}"}]""",
    ))

    @Test
    fun startScreenShowsNewMovies() {
        val app = testAppState(http = dashboardHttp, now = { dashNow })
        app.toggleDashboardCategory(Section.MOVIES, "10")
        show(app, Tab.HOME)
        rule.onNodeWithText("Neue Filme").assertExists()
        assertShown("Frisch im Kino")
    }

    @Test
    fun startScreenOffersCategoryChoiceWhenNothingChosen() {
        val app = show(testAppState(http = dashboardHttp, now = { dashNow }), Tab.HOME)
        rule.onNodeWithText("Wähle, was dich interessiert").assertExists()
        nav("Kategorien wählen").performClick()
        assertEquals(Tab.SETTINGS, app.tab)
        rule.onNodeWithText("Neues aus diesen Kategorien").performScrollTo().assertExists()
    }

    @Test
    fun showAllOpensNewCategoryInMovies() {
        val app = testAppState(http = dashboardHttp, now = { dashNow })
        app.toggleDashboardCategory(Section.MOVIES, "10")
        show(app, Tab.HOME)
        nav("Alle anzeigen").performClick()
        assertEquals(Tab.MOVIES, app.tab)
        // Eintrag in der Kategorienliste + Überschrift der gewählten Kategorie
        assertEquals(2, rule.onAllNodesWithText("Neu (7 Tage)").fetchSemanticsNodes().size)
        assertShown("Frisch im Kino")
    }

    @Test
    fun liveListShowsProviderNumbers() {
        val http = SampleData.http(mapOf("get_live_streams" to """[{"stream_id":1,"name":"Das Erste","category_id":"1","num":101}]"""))
        show(testAppState(http = http))
        rule.onNodeWithText("101").assertExists()
    }

    @Test
    fun overviewShowsCountsAndContinues() {
        val app = show(testAppState(continueOverview = false))
        rule.onNodeWithText("Playlist").assertExists()
        rule.onNodeWithText("3 Sender").assertExists()
        nav("Neu laden").assertExists()
        nav("Weiter").performClick()
        assertEquals(null, app.overview)
        assertShown("Das Erste")
    }

    @Test
    fun overviewWarnsAboutFewerEntries() {
        val hour = 60 * 60 * 1000L
        var now = 1_000L
        val kv = InMemoryKeyValueStore()
        val cache = de.vincentsberger.iptvplayer.shared.testing.InMemoryPlaylistCache()
        testAppState(kv = kv, now = { now }, playlistCache = { cache })
        now += 25 * hour
        val fewer = """[{"stream_id":100,"name":"Arrival","category_id":"10"},{"stream_id":101,"name":"Dune","category_id":"10"}]"""
        show(testAppState(http = SampleData.http(mapOf("get_vod_streams" to fewer)), kv = kv, now = { now }, playlistCache = { cache }, continueOverview = false))
        rule.onNodeWithText("deutlich weniger als zuletzt", substring = true).assertExists()
    }

    @Test
    fun countryFilterInSettingsNarrowsLiveTv() {
        val tagged = mapOf(
            "get_live_categories" to """[{"category_id":"1","category_name":"DE | Nachrichten"},{"category_id":"2","category_name":"AT | Sport"}]""",
            "get_live_streams" to """[{"stream_id":1,"name":"Das Erste","category_id":"1"},{"stream_id":2,"name":"ORF Sport","category_id":"2"}]""",
        )
        val app = show(testAppState(http = SampleData.http(tagged)))
        nav("Einstellungen").performClick()
        rule.onNodeWithText("Länder in Live TV").assertExists()
        rule.onNodeWithTag("country:DE").performScrollTo().performClick() // Einstellungen scrollen, der Abschnitt liegt unten
        assertEquals(setOf("DE"), app.liveCountries)

        app.selectTab(Tab.LIVE)
        rule.onNodeWithText("Gefiltert: DE", substring = true).assertExists()
        assertShown("ORF Sport", shown = false)

        app.selectTab(Tab.SETTINGS)
        nav("Alle zeigen").performScrollTo().performClick()
        assertEquals(emptySet(), app.liveCountries)
    }

    @Test
    fun sortSwitchInSettingsSortsTheChannelList() {
        val app = show()
        nav("Einstellungen").performClick()
        rule.onNodeWithText("Listen alphabetisch sortieren").assertExists()
        rule.onNode(isToggleable() and hasText("Listen alphabetisch sortieren")).performScrollTo().performClick()
        assertEquals(true, app.sortAz)
        assertEquals(listOf("Das Erste", "Sport1", "ZDF"), app.section(de.vincentsberger.iptvplayer.shared.data.Section.LIVE).items.map { it.name })
    }

    @Test
    fun clickingAMovieOpensItsDetails() {
        val app = show()
        nav("Filme").performClick()
        rule.onAllNodesWithText("Dune").onFirst().performClick()
        assertEquals("Dune", assertIs<Screen.Movie>(app.screen).movie.name)
    }

    @Test
    fun playOnTheMovieDetailsStartsIt() {
        val app = testAppState()
        val dune = app.section(Section.MOVIES).items.first { it.name == "Dune" }
        rule.setContent { AppTheme { MovieScreen(app, dune) } }
        rule.onNodeWithText("Abspielen").performClick()
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
        rule.onNodeWithText("3 Sender · 3 Filme · 1 Serien").assertExists()

        nav("Anbieter entfernen").performClick()
        assertEquals(Screen.Home, app.screen, "erster Klick fragt nur nach")
        nav("Abbrechen").performClick()
        rule.onNodeWithText("Anbieter entfernen?").assertDoesNotExist()
        assertEquals(Screen.Home, app.screen, "Abbrechen lässt alles, wie es ist")
        nav("Anbieter entfernen").performClick()
        nav("Entfernen").performClick()
        assertEquals(Screen.Login, app.screen)
        assertTrue(kv.values.isEmpty())
    }

    @Test
    fun clearHistoryEmptiesContinueWatching() {
        val app = show()
        val movie = de.vincentsberger.iptvplayer.shared.data.Item("100", "Arrival", null, "10", "http://tv.example/movie/user/pass/100.mp4", Section.MOVIES)
        app.saveProgress(movie, 120_000, 600_000)
        nav("Filme").performClick()
        nav("Weiterschauen").assertExists()

        nav("Einstellungen").performClick()
        nav("Verlauf löschen").performClick()
        nav("Filme").performClick()
        rule.onAllNodesWithText("Weiterschauen").assertCountEquals(0)
    }

    @Test
    fun clearedHistoryCanBeRestored() {
        val app = show()
        val movie = de.vincentsberger.iptvplayer.shared.data.Item("100", "Arrival", null, "10", "http://tv.example/movie/user/pass/100.mp4", Section.MOVIES)
        app.saveProgress(movie, 120_000, 600_000)
        nav("Einstellungen").performClick()
        nav("Verlauf löschen").performClick()
        assertTrue(app.history.isEmpty())
        rule.onNodeWithText("Verlauf gelöscht.").assertExists()
        nav("Rückgängig").performClick()
        assertEquals(listOf("Arrival"), app.history.map { it.item.name })
        nav("Verlauf löschen").assertExists()
    }

    @Test
    fun tableOfContentsFollowsScrolling() {
        val tops = mapOf(
            SettingsPart.ACCOUNT to 0, SettingsPart.PLAYBACK to 400, SettingsPart.HOME to 800, SettingsPart.FILTER to 1200, SettingsPart.APP to 1800,
        )
        assertEquals(SettingsPart.ACCOUNT, currentSettingsPart(tops, 0))
        assertEquals(SettingsPart.PLAYBACK, currentSettingsPart(tops, 360))
        assertEquals(SettingsPart.HOME, currentSettingsPart(tops, 900))
        assertEquals(SettingsPart.ACCOUNT, currentSettingsPart(emptyMap(), 500))
    }

    @Test
    fun settingsAreGroupedWithATableOfContents() {
        val app = testAppState()
        app.selectTab(Tab.SETTINGS)
        rule.setContent { Box(Modifier.requiredSize(1440.dp, 900.dp)) { AppTheme { HomeScreen(app) } } }
        SettingsPart.entries.forEach { rule.onNodeWithTag("settings:${it.name}").assertExists() }
        rule.onNodeWithText("Komfort").assertDoesNotExist()
        rule.onNodeWithTag("settings-toc:APP").performClick()
        rule.onNodeWithText("Nach Updates suchen").assertExists()
    }

    @Test
    fun settingsListOtherProvidersSwitchAndAdd() {
        val kv = InMemoryKeyValueStore()
        val other = Storage(kv).saveAccount(Account.Xtream("http://zweit.example", "anna", "pw"))
        val app = show(testAppState(kv = kv)) // meldet den Beispiel-Anbieter an und macht ihn aktiv
        nav("Einstellungen").performClick()
        rule.onNodeWithText("Xtream Codes · anna @ zweit.example").assertExists()

        nav("Wechseln").performClick()
        rule.waitForIdle()
        assertEquals(other, app.activeAccountId)
        nav("Weiter").performClick() // Anbieterwechsel lädt neu und zeigt die Übersicht

        nav("Einstellungen").performClick()
        nav("Anbieter hinzufügen").performClick()
        rule.waitForIdle()
        assertEquals(Screen.Login, app.screen)
        assertTrue(app.canCancelLogin)
    }
}
