package de.vincentsberger.iptvplayer.app

import de.vincentsberger.iptvplayer.shared.data.Account

import de.vincentsberger.iptvplayer.shared.data.Storage

import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.TvChrome
import de.vincentsberger.iptvplayer.app.ui.HomeScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.TvException
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule val rule = createComposeRule()

    private val kv = InMemoryKeyValueStore()
    private lateinit var vm: AppState

    /** Die meisten Tests prüfen Live TV – die App selbst startet auf der Startseite. */
    private fun show(state: AppState = testAppState(kv = kv), tab: Tab = Tab.LIVE) {
        vm = state
        vm.selectTab(tab)
        leaveTouchMode()
        rule.setContent { AppTheme { TvChrome { HomeScreen(vm) } } } // TvChrome: Optionen-Menü und Tastenleiste
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
        rule.onNodeWithTag("overview:continue").select() // nach dem Neuladen kommt die Übersicht
        rule.settle()
        rule.onNodeWithTag("channel:0").assertExists()
    }

    // ---------- Komfort: Zifferntasten, Ausblenden, Einstellungen ----------

    @Test
    fun typingAChannelNumberInTheListStartsThatChannel() {
        show()
        rule.onNodeWithTag("channel:0").requestFocus()
        rule.waitForIdle()
        press(Key.Three)
        rule.onNodeWithText("3_").assertExists()
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        val player = vm.screen as Screen.Player
        assertEquals("Sport1", player.items[player.index].name)
    }

    @Test
    fun longPressOnAProviderCategoryOffersHidingIt() {
        show()
        rule.onNodeWithTag("category:2").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.settle()
        assertTrue("OK halten blendet nicht mehr sofort aus, sondern öffnet das Menü", !vm.isHidden(Section.LIVE, "2"))
        rule.onNodeWithText("Kategorie ausblenden").assertExists()
        rule.onNodeWithTag("menu:0").performSemanticsAction(SemanticsActions.OnClick)
        rule.settle()
        assertTrue(vm.isHidden(Section.LIVE, "2"))
        rule.onNodeWithTag("category:2").assertDoesNotExist()
        // „Alle Sender“ & Co. lassen sich nicht ausblenden (tv-Surface meldet OnLongClick immer, daher Verhalten prüfen)
        rule.onNodeWithTag("category:${AppState.LIVE_ALL}").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.settle()
        assertEquals(setOf("LIVE:2"), vm.hidden)
        rule.onNodeWithTag("category:${AppState.LIVE_ALL}").assertExists()
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
    fun startScreenShowsAccountLineAndNewMovies() {
        val state = testAppState(http = dashboardHttp, kv = kv, now = { dashNow })
        state.toggleDashboardCategory(Section.MOVIES, "10")
        show(state, Tab.HOME)
        rule.onNodeWithText("Abo: läuft bis", substring = true).assertExists()
        rule.onNodeWithText("Neue Filme").assertExists()
        rule.onNodeWithTag("start:Neue Filme:showall").select()
        rule.settle()
        assertEquals(Tab.MOVIES, vm.tab)
        assertEquals(AppState.BROWSE_NEW, vm.browseCategory(Section.MOVIES))
    }

    @Test
    fun startScreenOffersCategoryChoiceWhenNothingChosen() {
        show(testAppState(http = dashboardHttp, kv = kv, now = { dashNow }), Tab.HOME)
        rule.onNodeWithText("Wähle, was dich interessiert").assertExists()
        rule.onNodeWithTag("start:choose").select()
        rule.settle()
        assertEquals(Tab.SETTINGS, vm.tab)
        // Zugeklappt, damit die Einstellungen darunter mit der Fernbedienung erreichbar bleiben
        rule.onNodeWithTag("dashcat:MOVIES:10").assertDoesNotExist()
        rule.onNodeWithTag("dashpick:MOVIES").performScrollTo().select()
        rule.settle()
        rule.onNodeWithTag("dashcat:MOVIES:10").performScrollTo().select()
        assertTrue(vm.dashboardConfigured)
    }

    @Test
    fun overviewShowsCountsAndContinues() {
        show(testAppState(kv = kv, continueOverview = false))
        rule.onNodeWithText("Playlist").assertExists()
        rule.onNodeWithText("3 Sender").assertExists()
        rule.onNodeWithTag("overview:reload").assertExists()
        rule.onNodeWithTag("overview:continue").assertIsFocused() // auf dem TV genügt ein OK
        rule.onNodeWithTag("overview:continue").select()
        rule.settle()
        assertEquals(null, vm.overview)
    }

    @Test
    fun countryFilterInSettingsNarrowsLiveTv() {
        val tagged = mapOf(
            "get_live_categories" to """[{"category_id":"1","category_name":"DE | Nachrichten"},{"category_id":"2","category_name":"AT | Sport"}]""",
            "get_live_streams" to """[{"stream_id":1,"name":"Das Erste","category_id":"1"},{"stream_id":2,"name":"ORF Sport","category_id":"2"}]""",
        )
        show(testAppState(http = SampleData.http(tagged), kv = kv))
        tab(Tab.SETTINGS)
        rule.onNodeWithTag("country:DE").select()
        assertEquals(setOf("DE"), vm.liveCountries)
        // Auswahl muss am Knopf selbst sichtbar sein – im Fokus sehen gewählte und nicht gewählte Knöpfe sonst gleich aus.
        rule.onNodeWithTag("country:DE").assert(hasText("✓", substring = true))

        tab(Tab.LIVE)
        rule.onNodeWithText("Gefiltert: DE", substring = true).assertExists()
        rule.onNodeWithTag("category:2").assertDoesNotExist()

        tab(Tab.SETTINGS)
        rule.onNodeWithTag("country:clear").select()
        assertEquals(emptySet<String>(), vm.liveCountries)
    }

    @Test
    fun settingsToggleSortingAndBringHiddenCategoriesBack() {
        val state = testAppState(kv = kv)
        state.toggleHidden(Section.LIVE, "2")
        show(state)
        tab(Tab.SETTINGS)
        rule.onNodeWithTag("setting:sort").select()
        assertTrue(vm.sortAz)
        rule.onNodeWithTag("unhide:LIVE:2").select()
        assertFalse(vm.isHidden(Section.LIVE, "2"))
    }

    // ---------- Navigation und Filme/Serien ----------

    @Test
    fun railSwitchesTabsAndLoadsSections() {
        show()
        tab(Tab.MOVIES)
        assertEquals(Tab.MOVIES, vm.tab)
        rule.onNodeWithTag("card:cat:10:0").assertExists()
        // Kategorien stehen als Liste links; das Grid zeigt nur die gewählte.
        rule.onNodeWithTag("browse-category:cat:11").assertExists()
        rule.onNodeWithTag("card:cat:11:0").assertDoesNotExist()

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
        rule.onNodeWithTag("browse-category:continue").assertExists()
        rule.onNodeWithTag("card:continue:0").assertExists()
    }

    @Test
    fun focusingACategoryShowsItsTitlesInTheGrid() {
        show()
        tab(Tab.MOVIES)
        rule.onNodeWithTag("browse-category:cat:11").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("card:cat:11:0").assertExists()
        rule.onNodeWithTag("card:cat:10:0").assertDoesNotExist()
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

        rule.onNodeWithText("Anbieter entfernen").select()
        assertEquals(Screen.Home, vm.screen)
        rule.onNodeWithText("Wirklich entfernen?").select()
        rule.waitForIdle()
        assertEquals(Screen.Login, vm.screen)
        assertTrue(kv.values.isEmpty())
    }

    @Test
    fun settingsListOtherProvidersAndSwitchToThem() {
        val other = Storage(kv).saveAccount(Account.Xtream("http://zweit.example", "anna", "pw"))
        Storage(kv).activeAccountId = null
        show() // testAppState meldet den Beispiel-Anbieter an und macht ihn aktiv
        tab(Tab.SETTINGS)
        rule.onNodeWithText("Xtream Codes · anna @ zweit.example").assertExists()
        rule.onNodeWithTag("account:add").assertExists()

        rule.onNodeWithTag("account:switch:$other").select()
        rule.waitForIdle()
        assertEquals(other, vm.activeAccountId)
        assertEquals("Xtream Codes · anna @ zweit.example", vm.accountLabel)
    }

    @Test
    fun longPressOnAChannelOffersFavoriteAndShowsFeedback() {
        show()
        rule.onNodeWithTag("channel:0").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.settle()
        rule.onNodeWithText("Zu Favoriten hinzufügen").performSemanticsAction(SemanticsActions.OnClick)
        rule.settle()
        assertEquals(1, vm.favorites.size)
        rule.onNodeWithTag("feedback").assertExists()
    }
}
