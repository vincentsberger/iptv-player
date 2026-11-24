package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MediaInfoStoreTest {

    private val vodInfo = """{"info":{"plot":"Ein Raumschiff verschwindet."}}"""
    private val movie = Item("100", "Orbit 9", null, "10", "http://srv/movie/u/p/100.mp4", Section.MOVIES)
    private val series = Item("200", "Babylon Berlin", null, "20", "", Section.SERIES)

    private val session = Session()
    private val storage = Storage(InMemoryKeyValueStore())
    private val library = LibraryStore(storage, SettingsStore(storage) { 0L })
    private val media = MediaInfoStore(session, library, CoroutineScope(Dispatchers.Unconfined))

    private fun repo(http: HttpSource) = Repository(Account.Xtream("http://srv", "u", "p"), http, NoPlaylistCache, Dispatchers.Unconfined)

    @Test
    fun movieInfoIsFetchedOnce() {
        val http = SampleData.http(mapOf("get_vod_info" to vodInfo))
        session.repo = repo(http)
        media.requestMediaInfo(movie)
        media.requestMediaInfo(movie)
        assertEquals(1, http.requests.count { FakeHttp.actionOf(it) == "get_vod_info" })
        assertNotNull(media.mediaInfoFor(movie))
    }

    @Test
    fun loadSeasonsFillsSeasonsAndLabelsEpisodes() {
        session.repo = repo(SampleData.http())
        media.loadSeasons(series)
        assertFalse(media.seasonsLoading)
        assertEquals(listOf("1", "2"), media.seasons.map { it.number })
        val second = media.seasons.first().episodes[1]
        assertEquals("S1 · F2 · ${second.name}", media.episodeLabel(second))
    }

    @Test
    fun answerForPreviousAccountIsDropped() {
        val gate = CompletableDeferred<String>()
        val slow = object : HttpSource {
            override suspend fun getText(url: String): String = gate.await()
            override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = block(getText(url).lineSequence())
        }
        session.repo = repo(slow)
        media.requestMediaInfo(movie)
        session.repo = repo(SampleData.http()) // inzwischen anderer Anbieter
        gate.complete(vodInfo)
        assertNull(media.mediaInfoFor(movie))
    }
}
