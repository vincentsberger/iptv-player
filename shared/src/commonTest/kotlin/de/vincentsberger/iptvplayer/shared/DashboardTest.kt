package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Startseite: neue Filme/Folgen aus gewählten Kategorien, nie Erwachseneninhalte, Abo-Daten. */
class DashboardTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1000 * day
    private val kv = InMemoryKeyValueStore()

    /** Unix-Sekunden „vor [days] Tagen“, wie der Anbieter sie liefert. */
    private fun ago(days: Double) = ((now - days * day) / 1000).toLong()

    private val responses = mapOf(
        "login" to """{"user_info":{"auth":1,"status":"Active","exp_date":"${(now + 30 * day) / 1000}","max_connections":"2","active_cons":"1"}}""",
        "get_vod_categories" to """[{"category_id":"10","category_name":"DE | Kino"},{"category_id":"11","category_name":"ES | Cine"},{"category_id":"12","category_name":"FOR ADULTS"}]""",
        "get_vod_streams" to """[
            {"stream_id":1,"name":"Neu A","category_id":"10","added":"${ago(1.0)}"},
            {"stream_id":2,"name":"Neu B","category_id":"10","added":"${ago(0.5)}"},
            {"stream_id":7,"name":"Uhr vor","category_id":"10","added":"${ago(-0.1)}"},
            {"stream_id":3,"name":"Alt","category_id":"10","added":"${ago(8.0)}"},
            {"stream_id":4,"name":"Spanisch","category_id":"11","added":"${ago(1.0)}"},
            {"stream_id":5,"name":"Heikel","category_id":"10","added":"${ago(1.0)}","is_adult":"1"},
            {"stream_id":6,"name":"Erwachsen","category_id":"12","added":"${ago(1.0)}"}
        ]""",
        "get_series_categories" to """[{"category_id":"20","category_name":"ENGLISH SERIES"}]""",
        "get_series" to """[{"series_id":200,"name":"Show","category_id":"20","last_modified":"${ago(2.0)}"}]""",
    )

    private fun app(overrides: Map<String, String> = emptyMap(), http: FakeHttp = SampleData.http(responses + overrides)) =
        testAppState(http = http, kv = kv, now = { now })

    private fun names(list: List<de.vincentsberger.iptvplayer.shared.data.Item>) = list.map { it.name }

    @Test
    fun nothingChosenMeansNoRowsButNotConfigured() {
        val app = app()
        assertTrue(app.supportsDashboard)
        assertFalse(app.dashboardConfigured)
        assertEquals(emptyList(), app.newMovies)
    }

    @Test
    fun newMoviesComeOnlyFromChosenCategoriesWithinSevenDays() {
        val app = app()
        app.toggleDashboardCategory(Section.MOVIES, "10")
        assertTrue(app.dashboardConfigured)
        assertEquals(listOf("Uhr vor", "Neu B", "Neu A"), names(app.newMovies), "neueste zuerst (auch bei vorgehender Uhr), 8 Tage alt fehlt, ES nicht gewählt")
    }

    @Test
    fun adultNeverAppears() {
        val app = app()
        app.toggleDashboardCategory(Section.MOVIES, "10")
        app.toggleDashboardCategory(Section.MOVIES, "12") // selbst wenn jemand sie gewählt hätte
        assertFalse("Heikel" in names(app.newMovies))
        assertFalse("Erwachsen" in names(app.newMovies))
        assertEquals(listOf("10", "11"), app.dashboardCategoryOptions(Section.MOVIES).map { it.id })
    }

    @Test
    fun newEpisodesUseLastModified() {
        val app = app()
        app.toggleDashboardCategory(Section.SERIES, "20")
        assertEquals(listOf("Show"), names(app.newEpisodes))
    }

    @Test
    fun choiceIsPerAccountAndSurvivesRestart() = runTest {
        app().toggleDashboardCategory(Section.MOVIES, "10")
        val app = app()
        assertEquals(setOf("MOVIES:10"), app.dashboardCategories)
        app.addAccount()
        app.login(Account.Xtream("other.example", "u", "p"))
        assertEquals(emptySet(), app.dashboardCategories)
    }

    @Test
    fun vanishedCategoryIsIgnored() {
        val app = app()
        app.toggleDashboardCategory(Section.MOVIES, "999")
        app.toggleDashboardCategory(Section.MOVIES, "10")
        assertEquals(listOf("Uhr vor", "Neu B", "Neu A"), names(app.newMovies))
    }

    @Test
    fun hiddenCategoryWins() {
        val app = app()
        app.toggleDashboardCategory(Section.MOVIES, "10")
        app.toggleHidden(Section.MOVIES, "10")
        assertEquals(emptyList(), app.newMovies)
    }

    @Test
    fun accountStatusIsFetchedAndKeptWhenOffline() {
        val first = app()
        val status = assertNotNull(first.accountStatus)
        assertEquals(now + 30 * day, status.info.expiresAt)
        assertEquals(2, status.info.maxConnections)
        assertFalse(first.accountStatusStale)

        val offline = FakeHttp { url -> if (FakeHttp.actionOf(url) == "login") error("kein Netz") else responses[FakeHttp.actionOf(url)] ?: SampleData.respond(url) }
        val again = testAppState(http = offline, kv = kv, now = { now }, continueOverview = false)
        assertEquals(status, again.accountStatus, "alter Stand bleibt")
        assertTrue(again.accountStatusStale)
        assertNotNull(again.overview, "Abo-Fehler stört den Start nicht")
    }

    @Test
    fun expiresSoonWithinSevenDays() {
        val soon = """{"user_info":{"auth":1,"status":"Active","exp_date":"${(now + 6 * day) / 1000}"}}"""
        assertTrue(app(mapOf("login" to soon)).accountExpiresSoon())
        assertFalse(app().accountExpiresSoon())
    }

    @Test
    fun m3uHasNoDashboard() {
        val storage = Storage(kv)
        storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        val app = testAppState(http = FakeHttp { "#EXTM3U\n#EXTINF:-1,ARD\nhttp://srv/live/1.ts" }, kv = kv, loggedIn = false, now = { now })
        assertFalse(app.supportsDashboard)
    }

    @Test
    fun showNewInBrowseSelectsTabAndCategory() {
        val app = app()
        app.showNewInBrowse(Section.SERIES)
        assertEquals(Tab.SERIES, app.tab)
        assertEquals(AppState.BROWSE_NEW, app.browseCategory(Section.SERIES))
    }
}
