package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.data.TvException
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp.Companion.LOGIN_OK
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {

    private val kv = InMemoryKeyValueStore()
    private val storage = Storage(kv)
    private var now = 1_000L

    private val xtreamAccount = Account.Xtream("srv", "user", "pass")

    /** Ein kleiner Xtream-Server: zwei Sender (einer in unbekannter Kategorie), ein Film, eine Serie. */
    private val xtreamResponses = mapOf(
            "login" to LOGIN_OK,
            "get_live_categories" to """[{"category_id":"1","category_name":"News"}]""",
            "get_live_streams" to """[
                {"stream_id":1,"name":"ARD","category_id":"1"},
                {"stream_id":2,"name":"Arte","category_id":"99"}
            ]""",
            "get_vod_categories" to """[{"category_id":"3","category_name":"Kino"}]""",
            "get_vod_streams" to """[{"stream_id":7,"name":"Arrival","category_id":"3"}]""",
            "get_series_categories" to """[{"category_id":"5","category_name":"Drama"}]""",
            "get_series" to """[{"series_id":11,"name":"Babylon Berlin","category_id":"5"}]""",
            "get_series_info" to """{"episodes":{"1":[{"id":"100","title":"Geheimnisse"}]}}""",
    )
    private val xtreamHttp = FakeHttp.xtream(xtreamResponses)

    private fun TestScope.app(http: FakeHttp = xtreamHttp) =
        AppState(storage, scope = this, clock = { now }, newRepository = { Repository(it, http) })

    private fun movie(n: Int) = Item("$n", "Film $n", null, "3", "http://srv/movie/user/pass/$n.mp4", Section.MOVIES)

    // ---------- Start und Anmeldung ----------

    @Test
    fun startsAtLoginWithoutSavedAccount() = runTest {
        val app = app()
        assertEquals(Screen.Login, app.screen)
        advanceUntilIdle()
        assertEquals(0, xtreamHttp.requests.size)
    }

    @Test
    fun startupShowsLoadingUntilCurrentSectionIsThereAndPreloadsTheRest() = runTest {
        storage.saveAccount(xtreamAccount)
        val app = app()
        assertEquals(false, app.startupDone, "Ladebildschirm, solange Live TV lädt")
        assertTrue(Section.entries.all { app.sections[it]?.loading == true }, "alle Bereiche laden parallel vor")
        advanceUntilIdle()
        assertTrue(app.startupDone)
        assertTrue(Section.entries.all { app.sections[it]?.loading == false })
    }

    @Test
    fun screensHaveANavigationDepthForTransitions() {
        val item = movie(1)
        assertTrue(Screen.Login.depth < Screen.Home.depth)
        assertTrue(Screen.Home.depth < Screen.Series(item).depth)
        assertTrue(Screen.Series(item).depth < Screen.Player(listOf(item), 0).depth)
    }

    @Test
    fun savedAccountOpensHomeAndLoadsLiveTv() = runTest {
        storage.saveAccount(xtreamAccount)
        val app = app()
        assertEquals(Screen.Home, app.screen)
        assertTrue(app.section(Section.LIVE).loading)

        advanceUntilIdle()
        val live = app.section(Section.LIVE)
        assertTrue(live.loaded)
        assertEquals(listOf("ARD", "Arte"), live.items.map { it.name })
    }

    @Test
    fun itemsInUnknownCategoriesLandInSonstige() = runTest {
        storage.saveAccount(xtreamAccount)
        val app = app()
        advanceUntilIdle()
        assertEquals(listOf("News", "Sonstige"), app.section(Section.LIVE).categories.map { it.name })
    }

    @Test
    fun loginStoresAccountAndShowsHome() = runTest {
        val app = app()
        app.login(xtreamAccount)
        assertEquals(Screen.Home, app.screen)
        assertEquals(xtreamAccount.copy(server = "https://srv"), storage.loadAccount(), "gespeichert mit erreichter Adresse")
        assertEquals("Xtream Codes · user @ srv", app.accountLabel)
    }

    @Test
    fun failedLoginKeepsLoginScreenAndStoresNothing() = runTest {
        val app = app(FakeHttp.xtream(mapOf("login" to """{"user_info":{"auth":0}}""")))
        assertFailsWith<TvException> { app.login(xtreamAccount) }
        assertEquals(Screen.Login, app.screen)
        assertNull(storage.loadAccount())
    }

    @Test
    fun loadErrorIsShownAndRetriedOnNextVisit() = runTest {
        var fail = true
        val http = FakeHttp { url ->
            if (fail) throw TvException("Server nicht erreichbar")
            "#EXTINF:-1,ARD\nhttp://srv/live/1.ts"
        }
        storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        val app = app(http)
        advanceUntilIdle()
        assertEquals("Server nicht erreichbar", app.section(Section.LIVE).error)

        fail = false
        app.selectTab(Tab.LIVE)
        advanceUntilIdle()
        assertEquals(listOf("ARD"), app.section(Section.LIVE).items.map { it.name })
    }

    @Test
    fun logoutClearsEverything() = runTest {
        val app = app()
        app.login(xtreamAccount)
        advanceUntilIdle()
        app.toggleFavorite(movie(1))
        app.saveProgress(movie(1), 60_000, 600_000)

        app.logout()
        assertEquals(Screen.Login, app.screen)
        assertEquals(emptySet(), app.favorites)
        assertEquals(emptyList(), app.history)
        assertTrue(kv.values.isEmpty())
    }

    // ---------- Mehrere Anbieter ----------

    private val m3uAccount = Account.M3u("http://srv/list.m3u")

    /** Antwortet für Xtream wie [xtreamHttp], für die M3U-Liste mit einem Sender. */
    private val mixedHttp = FakeHttp { url ->
        if (url.endsWith("list.m3u")) "#EXTM3U\n#EXTINF:-1,Playlist-Sender\nhttp://srv/live/9.ts"
        else xtreamResponses[FakeHttp.actionOf(url)] ?: error("Unerwartete Anfrage: $url")
    }

    @Test
    fun secondProviderHasItsOwnFavoritesAndSwitchingBackRestoresThem() = runTest {
        val app = app(mixedHttp)
        app.login(xtreamAccount)
        advanceUntilIdle()
        app.toggleFavorite(movie(1))
        val first = app.activeAccountId!!

        app.addAccount()
        assertEquals(Screen.Login, app.screen)
        assertTrue(app.canCancelLogin)
        app.login(m3uAccount)
        advanceUntilIdle()
        assertEquals(Screen.Home, app.screen)
        assertEquals(2, app.accounts.size)
        assertEquals(emptySet(), app.favorites)
        assertEquals(listOf("Playlist-Sender"), app.section(Section.LIVE).items.map { it.name })

        app.switchAccount(first)
        advanceUntilIdle()
        assertEquals(setOf(movie(1).favKey), app.favorites)
        assertEquals("Xtream Codes · user @ srv", app.accountLabel)
        assertEquals(listOf("ARD", "Arte"), app.section(Section.LIVE).items.map { it.name })
    }

    @Test
    fun cancellingAddProviderReturnsHome() = runTest {
        val app = app()
        app.login(xtreamAccount)
        advanceUntilIdle()
        assertFalse(app.canCancelLogin)
        app.addAccount()
        app.back()
        assertEquals(Screen.Home, app.screen)
        assertEquals(1, app.accounts.size)
    }

    @Test
    fun removingOneOfTwoProvidersContinuesWithTheOther() = runTest {
        val app = app(mixedHttp)
        app.login(xtreamAccount)
        app.login(m3uAccount)
        advanceUntilIdle()
        app.toggleFavorite(movie(2))

        app.logout()
        advanceUntilIdle()
        assertEquals(Screen.Home, app.screen)
        assertEquals(listOf("Xtream Codes · user @ srv"), app.accounts.map { labelOf(it.account) })
        assertEquals("Xtream Codes · user @ srv", app.accountLabel)
        assertEquals(emptySet(), app.favorites)
    }

    // ---------- Favoriten, Suche, Live-Filter ----------

    @Test
    fun toggleFavoriteIsPersisted() = runTest {
        val app = app()
        val film = movie(1)
        app.toggleFavorite(film)
        assertEquals(setOf(film.favKey), storage.favorites())
        app.toggleFavorite(film)
        assertEquals(emptySet(), storage.favorites())
    }

    @Test
    fun searchNeedsTwoCharactersAndIgnoresCase() = runTest {
        storage.saveAccount(xtreamAccount)
        val app = app()
        app.selectTab(Tab.SEARCH) // lädt alle Bereiche
        advanceUntilIdle()

        app.query = "a"
        assertEquals(emptyMap(), app.searchResults)

        app.query = " AR "
        val results = app.searchResults
        assertEquals(listOf("ARD", "Arte"), results[Section.LIVE]?.map { it.name })
        assertEquals(listOf("Arrival"), results[Section.MOVIES]?.map { it.name })
        assertNull(results[Section.SERIES], "Bereiche ohne Treffer fehlen")
    }

    @Test
    fun liveChannelsFollowTheSelectedFilter() = runTest {
        storage.saveAccount(xtreamAccount)
        val app = app()
        advanceUntilIdle()
        val (ard, arte) = app.section(Section.LIVE).items

        assertEquals(listOf(ard, arte), app.liveChannels)

        app.liveCategory = "1"
        assertEquals(listOf(ard), app.liveChannels)

        app.toggleFavorite(arte)
        app.liveCategory = AppState.LIVE_FAV
        assertEquals(listOf(arte), app.liveChannels)

        app.saveProgress(ard, 0, 0)
        app.liveCategory = AppState.LIVE_RECENT
        assertEquals(listOf(ard), app.liveChannels)
    }

    // ---------- Verlauf ----------

    @Test
    fun saveProgressStoresPositionWithTimestamp() = runTest {
        val app = app()
        app.saveProgress(movie(1), 120_000, 600_000)
        assertEquals(HistoryEntry(movie(1), 120_000, 600_000, now), app.history.single())
        assertEquals(app.history, storage.history())
    }

    @Test
    fun liveAndFinishedItemsDoNotKeepAPosition() = runTest {
        val app = app()
        val live = Item("1", "ARD", null, "1", "http://srv/live/1.ts", Section.LIVE)
        app.saveProgress(live, 90_000, 0)
        app.saveProgress(movie(1), 590_000, 600_000) // > 95 %

        assertEquals(listOf(0L, 0L), app.history.map { it.position })
        assertEquals(emptyList(), app.continueWatching(Section.MOVIES))
    }

    @Test
    fun historyIsNewestFirstWithoutDuplicatesAndCappedAt50() = runTest {
        val app = app()
        repeat(55) { app.saveProgress(movie(it), 60_000, 600_000) }
        now = 2_000
        app.saveProgress(movie(54), 90_000, 600_000)

        assertEquals(50, app.history.size)
        assertEquals(movie(54), app.history.first().item)
        assertEquals(90_000, app.history.first().position)
        assertEquals(1, app.history.count { it.item == movie(54) })
    }

    @Test
    fun seriesFoldersAreNotStoredInHistory() = runTest {
        val app = app()
        app.saveProgress(Item("11", "Dark", null, "5", "", Section.SERIES), 60_000, 600_000)
        assertEquals(emptyList(), app.history)
    }

    // ---------- Öffnen und Navigation ----------

    @Test
    fun openResumesAtLastPosition() = runTest {
        val app = app()
        val list = listOf(movie(1), movie(2))
        app.saveProgress(movie(2), 120_000, 600_000)

        app.open(list, 1)
        assertEquals(Screen.Player(list, 1, 120_000), app.screen)
        assertEquals(0.2f, app.progressOf(movie(2)))
    }

    @Test
    fun openingASeriesLoadsItsSeasons() = runTest {
        val app = app()
        app.login(xtreamAccount)
        val series = Item("11", "Dark", null, "5", "", Section.SERIES)

        app.open(listOf(series), 0)
        assertIs<Screen.Series>(app.screen)
        assertTrue(app.seasonsLoading)

        advanceUntilIdle()
        assertEquals(listOf("Geheimnisse"), app.seasons.single().episodes.map { it.name })
        assertNull(app.seasonsError)
    }

    @Test
    fun backStopsAtTheRootScreen() = runTest {
        val app = app()
        app.login(xtreamAccount)
        app.open(listOf(movie(1)), 0)
        app.back()
        assertEquals(Screen.Home, app.screen)
        app.back()
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun openWithInvalidIndexDoesNothing() = runTest {
        val app = app()
        app.open(emptyList(), 0)
        assertEquals(Screen.Login, app.screen)
    }
}
