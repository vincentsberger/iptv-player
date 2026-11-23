package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryPlaylistCache
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Listen aus dem Zwischenspeicher statt aus dem Netz – und nur gute Antworten landen darin. */
class RepositoryCacheTest {

    private val cache = InMemoryPlaylistCache()

    private fun repo(http: FakeHttp = SampleData.http(), account: Account = SampleData.account) = Repository(account, http, cache)

    @Test
    fun networkLoadWritesCache() = runTest {
        repo().load(Section.LIVE)
        assertTrue("get_live_categories.json" in cache.entries)
        assertTrue("get_live_streams.json" in cache.entries)
    }

    @Test
    fun cacheLoadMakesNoHttpCall() = runTest {
        val first = repo().load(Section.LIVE)
        val http = SampleData.http()
        val again = repo(http).load(Section.LIVE, fromCache = true)
        assertEquals(emptyList(), http.requests)
        assertEquals(first.items, again.items)
        assertEquals(first.categories, again.categories)
        assertTrue(again.fromCache)
        assertFalse(first.fromCache)
    }

    @Test
    fun missingCacheFallsBackToNetwork() = runTest {
        val loaded = repo().load(Section.LIVE, fromCache = true)
        assertEquals(3, loaded.items.size)
        assertFalse(loaded.fromCache)
    }

    @Test
    fun emptyAnswerDoesNotOverwriteCache() = runTest {
        repo().load(Section.LIVE)
        val before = cache.entries.getValue("get_live_streams.json")
        val empty = repo(SampleData.http(mapOf("get_live_streams" to "[]"))).load(Section.LIVE)
        assertEquals(0, empty.items.size)
        assertEquals(before, cache.entries.getValue("get_live_streams.json"))
        assertEquals(3, repo(SampleData.http()).load(Section.LIVE, fromCache = true).items.size, "alter Stand bleibt lesbar")
    }

    @Test
    fun m3uIsCachedAsLines() = runTest {
        val playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"News\",ARD\nhttp://srv/live/1.ts"
        val account = Account.M3u("http://srv/list.m3u")
        val first = repo(FakeHttp { playlist }, account).load(Section.LIVE)
        val http = FakeHttp { error("kein Netz erwartet") }
        val again = repo(http, account).load(Section.LIVE, fromCache = true)
        assertEquals(first.items, again.items)
        assertTrue(again.fromCache)
        assertEquals(emptyList(), http.requests)
    }

    @Test
    fun credentialsNeverInKeys() = runTest {
        Section.entries.forEach { repo().load(it) }
        val account = SampleData.account
        cache.entries.keys.forEach { key ->
            assertFalse(account.username in key || account.password in key || account.server in key, key)
        }
    }
}
