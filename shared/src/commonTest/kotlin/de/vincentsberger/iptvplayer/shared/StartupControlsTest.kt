package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.CacheMeta
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ladebildschirm: Live TV schon öffnen, Laden abbrechen, Gesamtfortschritt – und Angaben für die Übersicht. */
class StartupControlsTest {

    private val gate = CompletableDeferred<Unit>()

    /** Beispielserver, bei dem [action] erst nach [gate] antwortet. Die Bereiche laden nacheinander (Live, Filme, Serien). */
    private fun slow(action: String) = object : HttpSource {
        override suspend fun getText(url: String): String {
            if (FakeHttp.actionOf(url) == action) gate.await()
            return SampleData.respond(url)
        }
        override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = block(getText(url).lineSequence())
    }

    private val slowMovies = slow("get_vod_streams")

    private fun app(scope: CoroutineScope, http: HttpSource = slowMovies, setup: Storage.() -> Unit = {}): AppState {
        val storage = Storage(InMemoryKeyValueStore()).apply { saveAccount(SampleData.account); setup() }
        return AppState(storage, scope = scope, clock = { 1_000L }, newRepository = { a, c -> Repository(a, http, c, Dispatchers.Unconfined) })
    }

    @Test
    fun liveCanOpenEarlyWhileMoviesLoad() = runTest {
        val app = app(this)
        advanceUntilIdle()
        assertFalse(app.startupDone)
        assertTrue(app.canOpenLiveEarly)
        app.openLiveEarly()
        assertTrue(app.startupDone)
        assertEquals(Tab.LIVE, app.tab)
        assertNull(app.overview)
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(app.overview, "nach dem frühen Öffnen keine Übersicht mehr")
        assertEquals(3, app.section(Section.MOVIES).items.size)
    }

    @Test
    fun progressCountsFinishedSections() = runTest {
        val app = app(this, slow("get_series"))
        advanceUntilIdle()
        assertEquals(2f / 3, app.startupProgress)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1f, app.startupProgress)
    }

    @Test
    fun cancelKeepsFinishedSections() = runTest {
        val app = app(this)
        advanceUntilIdle()
        app.cancelStartup()
        advanceUntilIdle()
        assertTrue(app.startupDone)
        assertEquals("Laden abgebrochen", app.section(Section.MOVIES).error)
        assertEquals(3, app.section(Section.LIVE).items.size)
        val overview = assertNotNull(app.overview)
        assertEquals(OverviewWarning.CANCELLED, overview.first { it.section == Section.MOVIES }.warning)
        assertNull(overview.first { it.section == Section.LIVE }.warning)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Laden abgebrochen", app.section(Section.MOVIES).error, "abgebrochener Abruf schreibt nichts mehr")
    }

    @Test
    fun duplicatesAreCounted() = runTest {
        val twice = """[
            {"stream_id":1,"name":"Das Erste","category_id":"1"},
            {"stream_id":2,"name":"Das Erste","category_id":"1"},
            {"stream_id":3,"name":"ZDF","category_id":"1"}
        ]"""
        val app = app(this, SampleData.http(mapOf("get_live_streams" to twice)))
        advanceUntilIdle()
        assertEquals(1, app.collapsedDuplicates(Section.LIVE))
        assertEquals(0, app.collapsedDuplicates(Section.MOVIES))
    }

    @Test
    fun nameBadgesAreDetected() = runTest {
        val raw = """[{"stream_id":1,"name":"Das Erste ᴿᴬᵂ","category_id":"1"}]"""
        val app = app(this, SampleData.http(mapOf("get_live_streams" to raw)))
        advanceUntilIdle()
        assertTrue(app.hasNameBadges(Section.LIVE))
        assertFalse(app.hasNameBadges(Section.MOVIES))
    }

    @Test
    fun cancelledRowSaysCancelledWithoutStaleDate() = runTest {
        // Filme waren schon einmal geladen (Stand von gestern) – abgebrochen sind sie jetzt trotzdem leer.
        val app = app(this) { saveCacheMeta(Section.MOVIES, CacheMeta(loadedAt = 500L, count = 3, previousCount = null, failed = false)) }
        advanceUntilIdle()
        app.cancelStartup()
        val row = assertNotNull(app.overview).first { it.section == Section.MOVIES }
        assertEquals(OverviewWarning.CANCELLED, row.warning)
        assertNull(row.loadedAt, "kein alter Stand für einen leeren, abgebrochenen Bereich")
        assertEquals("abgebrochen – nicht geladen", overviewNote(row))
    }
}
