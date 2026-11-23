package de.vincentsberger.iptvplayer.shared

import kotlinx.coroutines.CoroutineScope
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.InMemoryPlaylistCache
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Start aus dem Zwischenspeicher oder dem Netz – und die Übersicht danach, die immer auf „Weiter“ wartet. */
class PlaylistOverviewTest {

    private val hour = 60 * 60 * 1000L
    private var now = 10 * 24 * hour
    private val kv = InMemoryKeyValueStore()
    private val caches = HashMap<String, InMemoryPlaylistCache>()

    private fun app(http: FakeHttp = SampleData.http(), scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) = testAppState(
        http = http, kv = kv, now = { now }, continueOverview = false, scope = scope,
        playlistCache = { id -> caches.getOrPut(id) { InMemoryPlaylistCache() } },
    )

    private fun listRequests(http: FakeHttp) = http.requests.count { FakeHttp.actionOf(it).startsWith("get_") && FakeHttp.actionOf(it) != "get_short_epg" }

    private fun AppState.row(s: Section) = overview!!.first { it.section == s }

    @Test
    fun firstStartLoadsFromNetworkAndShowsOverview() {
        val app = app()
        val rows = assertNotNull(app.overview)
        assertEquals(listOf(3, 3, 1), Section.entries.map { s -> rows.first { it.section == s }.count })
        assertTrue(rows.all { it.delta == null && it.warning == null })
        assertEquals(now, app.row(Section.LIVE).loadedAt)
    }

    @Test
    fun overviewWaitsForContinue() {
        val history = testAppState(kv = kv) // speichert einen gesehenen Sender
        history.startWithLastChannel = true
        history.saveProgress(history.section(Section.LIVE).items[1], 0, 0)

        val app = app()
        assertEquals(Screen.Home, app.screen, "Übersicht zuerst, Sender erst nach „Weiter“")
        app.continueFromOverview()
        assertNull(app.overview)
        assertIs<Screen.Player>(app.screen)
    }

    @Test
    fun freshCacheIsUsedWithoutHttp() {
        app()
        now += hour
        val http = SampleData.http()
        val app = app(http)
        assertEquals(0, listRequests(http))
        assertEquals(3, app.row(Section.MOVIES).count)
    }

    @Test
    fun staleCacheLoadsFromNetwork() {
        app()
        now += 25 * hour
        val http = SampleData.http()
        app(http)
        assertTrue(listRequests(http) > 0)
    }

    @Test
    fun clockBehindCacheCountsAsStale() {
        app()
        now -= hour
        val http = SampleData.http()
        app(http)
        assertTrue(listRequests(http) > 0)
    }

    @Test
    fun failedSectionKeepsCacheAndWarns() {
        app()
        now += 25 * hour
        val failing = FakeHttp { url -> if (FakeHttp.actionOf(url) == "get_vod_streams") error("Zeitüberschreitung") else SampleData.respond(url) }
        val app = app(failing)
        assertEquals(3, app.section(Section.MOVIES).items.size, "alter Stand statt leerer Liste")
        assertEquals(OverviewWarning.FAILED, app.row(Section.MOVIES).warning)

        now += hour
        val http = SampleData.http()
        app(http)
        assertTrue(listRequests(http) > 0, "nach einem Fehler nicht aus dem Zwischenspeicher starten")
    }

    @Test
    fun emptyAnswerKeepsCacheAndWarns() {
        app()
        now += 25 * hour
        val app = app(SampleData.http(mapOf("get_vod_streams" to "[]")))
        assertEquals(3, app.section(Section.MOVIES).items.size)
        assertEquals(OverviewWarning.EMPTY, app.row(Section.MOVIES).warning)
    }

    @Test
    fun fewerThan90PercentWarns() {
        app()
        now += 25 * hour
        val fewer = """[{"stream_id":100,"name":"Arrival","category_id":"10"},{"stream_id":101,"name":"Dune","category_id":"10"}]"""
        val app = app(SampleData.http(mapOf("get_vod_streams" to fewer)))
        assertEquals(-1, app.row(Section.MOVIES).delta)
        assertEquals(OverviewWarning.FEWER, app.row(Section.MOVIES).warning)
        assertNull(app.row(Section.LIVE).warning)
        assertEquals(0, app.row(Section.LIVE).delta)
    }

    @Test
    fun reloadFromNetworkShowsOverviewAgain() {
        val http = SampleData.http()
        val app = app(http)
        app.continueFromOverview()
        val before = listRequests(http)
        app.reloadFromNetwork()
        assertTrue(listRequests(http) > before)
        assertNotNull(app.overview)
    }

    @Test
    fun resumeAfterMoreThanAnHourRestarts() {
        val app = app()
        app.continueFromOverview()
        app.onAppResumed(59 * 60 * 1000L)
        assertNull(app.overview)
        app.onAppResumed(61 * 60 * 1000L)
        assertNotNull(app.overview)
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun accountsDoNotShareCache() = runTest {
        app()
        val http = SampleData.http()
        val app = app(http)
        app.continueFromOverview()
        app.addAccount()
        app.login(Account.Xtream("other.example", "u", "p"))
        assertTrue(listRequests(http) > 0, "zweiter Anbieter hat keinen Zwischenspeicher")
        assertEquals(2, caches.size)
    }

    @Test
    fun logoutClearsCache() {
        val app = app()
        val id = Storage(kv).activeAccountId!!
        assertTrue(caches.getValue(id).entries.isNotEmpty())
        app.logout()
        assertTrue(caches.getValue(id).entries.isEmpty())
    }

    @Test
    fun networkFailureWithoutCacheFileDoesNotRetry() = runTest {
        app()
        caches.values.forEach { it.entries.clear() } // Stand bekannt, Datei aber weg (z. B. gelöscht)
        now += 25 * hour
        val failing = FakeHttp { url -> if (FakeHttp.actionOf(url) == "get_vod_streams") error("Zeitüberschreitung") else SampleData.respond(url) }
        val app = app(failing, scope = this) // asynchron wie in der App, nicht Unconfined
        advanceUntilIdle()
        assertEquals(1, failing.requests.count { FakeHttp.actionOf(it) == "get_vod_streams" }, "nicht zweimal aufs Timeout warten")
        assertNotNull(app.sections[Section.MOVIES]?.error)
    }

    @Test
    fun staleLoadOfPreviousAccountDoesNotWriteMeta() = runTest {
        val storage = Storage(kv)
        val other = storage.saveAccount(Account.Xtream("http://b.example", "u", "p"))
        storage.saveAccount(SampleData.account) // A aktiv
        val gate = CompletableDeferred<Unit>()
        val fiveMovies = (1..5).joinToString(",", "[", "]") { """{"stream_id":$it,"name":"A$it","category_id":"10"}""" }
        val http = object : HttpSource {
            override suspend fun getText(url: String): String {
                if ("tv.example" in url && FakeHttp.actionOf(url) == "get_vod_streams") { gate.await(); return fiveMovies }
                return SampleData.respond(url)
            }
            override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = block(getText(url).lineSequence())
        }
        val app = AppState(
            storage, scope = this, clock = { now },
            newRepository = { a, c -> Repository(a, http, c, Dispatchers.Unconfined) },
            playlistCache = { id -> caches.getOrPut(id) { InMemoryPlaylistCache() } },
        )
        advanceUntilIdle() // A lädt, Filme hängen
        app.switchAccount(other)
        advanceUntilIdle() // B fertig
        gate.complete(Unit)
        advanceUntilIdle() // der alte Filme-Abruf von A kommt jetzt erst an
        assertEquals(3, storage.cacheMeta(Section.MOVIES)?.count, "Stand von B nicht mit A-Daten überschreiben")
        assertEquals(3, app.sections[Section.MOVIES]?.items?.size)
    }
}
