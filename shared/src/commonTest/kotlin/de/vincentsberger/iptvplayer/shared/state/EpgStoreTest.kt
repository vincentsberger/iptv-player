package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class EpgStoreTest {

    private val epgJson = """{"epg_listings":[{"title":"${Base64.encode("Tagesschau".encodeToByteArray())}",""" +
        """"start":"2026-11-16 20:00:00","end":"2026-11-16 20:15:00"}]}"""

    private val channel = Item("1", "Kanal Eins", null, "1", "http://srv/live/u/p/1.ts", Section.LIVE)
    private val session = Session()
    private val epg = EpgStore(session, CoroutineScope(Dispatchers.Unconfined), { 0L }, { "1970-01-01 00:00:01" })

    private fun repo(http: HttpSource) = Repository(Account.Xtream("http://srv", "u", "p"), http, NoPlaylistCache, Dispatchers.Unconfined)

    @Test
    fun requestEpgFillsNowFor() {
        session.repo = repo(SampleData.http(mapOf("get_short_epg" to epgJson)))
        epg.requestEpg(channel)
        assertEquals("Tagesschau", epg.nowFor(channel)?.title)
    }

    @Test
    fun channelWithoutEpgIdIsNeverAsked() {
        val http = SampleData.http(mapOf("get_short_epg" to epgJson))
        session.repo = repo(http)
        epg.requestEpg(channel.copy(hasEpg = false))
        assertTrue(http.requests.none { FakeHttp.actionOf(it) == "get_short_epg" })
    }

    @Test
    fun answerForPreviousAccountIsDropped() {
        val gate = CompletableDeferred<String>()
        val slow = object : HttpSource {
            override suspend fun getText(url: String): String = gate.await()
            override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = block(getText(url).lineSequence())
        }
        session.repo = repo(slow)
        epg.requestProgramme(channel)
        session.repo = repo(SampleData.http()) // inzwischen anderer Anbieter
        gate.complete(epgJson)
        assertTrue(epg.programmes.isEmpty())
    }
}
