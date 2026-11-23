package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.InMemoryPlaylistCache
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Zwischenspeicher lesen/schreiben (große Dateien, gzip, DPAPI) darf den UI-Thread nicht blockieren. */
class RepositoryDispatcherTest {

    @Test
    fun cacheWorkRunsOffCallerThread() {
        val threads = mutableListOf<String>()
        val cache = object : PlaylistCacheStore by InMemoryPlaylistCache() {
            override fun writeText(key: String, text: String) { threads += Thread.currentThread().name }
        }
        @Suppress("OPT_IN_USAGE")
        runBlocking(newSingleThreadContext("ui")) { Repository(SampleData.account, SampleData.http(), cache).load(Section.LIVE) }
        assertTrue(threads.isNotEmpty() && threads.none { it == "ui" }, threads.toString())
    }
}
