package de.tvplayer.shared.data

import de.tvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTest {

    private val kv = InMemoryKeyValueStore()
    private val storage = Storage(kv)

    @Test
    fun noAccountWhenNothingIsStored() {
        assertNull(storage.loadAccount())
    }

    @Test
    fun accountsSurviveARoundTrip() {
        val xtream = Account.Xtream("http://srv", "user", "pass")
        storage.saveAccount(xtream)
        assertEquals(xtream, storage.loadAccount())

        val m3u = Account.M3u("http://srv/list.m3u")
        storage.saveAccount(m3u)
        assertEquals(m3u, storage.loadAccount())
    }

    @Test
    fun switchingToM3uRemovesOldCredentials() {
        storage.saveAccount(Account.Xtream("http://srv", "user", "geheim"))
        storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        assertEquals(setOf("type", "url"), kv.values.keys)
    }

    @Test
    fun favoritesSurviveARoundTripAndDefaultToEmpty() {
        assertEquals(emptySet(), storage.favorites())
        storage.saveFavorites(setOf("LIVE:http://a", "SERIES:42"))
        assertEquals(setOf("LIVE:http://a", "SERIES:42"), storage.favorites())
    }

    @Test
    fun brokenFavoritesAreIgnored() {
        kv.values["favorites"] = "{kein Array"
        assertEquals(emptySet(), storage.favorites())
    }

    @Test
    fun historySurvivesARoundTrip() {
        val list = listOf(
            HistoryEntry(Item("1", "Film", null, "c", "http://srv/movie/1.mp4", Section.MOVIES), 60_000, 7_200_000, 123),
            HistoryEntry(Item("2", "ARD", "http://logo", "n", "http://srv/live/2.ts", Section.LIVE), 0, 0, 456),
        )
        storage.saveHistory(list)
        assertEquals(list, storage.history())
    }

    @Test
    fun historyEntriesWithoutUrlOrWithUnknownSectionAreSkipped() {
        kv.values["history"] = """[
            {"id":"1","name":"ok","url":"http://a","section":"MOVIES","pos":5,"dur":10,"upd":1},
            {"id":"2","name":"ohne url","url":"","section":"MOVIES"},
            {"id":"3","name":"alt","url":"http://b","section":"RADIO"}
        ]"""
        assertEquals(listOf("ok"), storage.history().map { it.item.name })
    }

    @Test
    fun volumeDefaultsTo80() {
        assertEquals(80, storage.volume)
        storage.volume = 35
        assertEquals(35, storage.volume)
    }

    @Test
    fun clearRemovesEverything() {
        storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        storage.saveFavorites(setOf("x"))
        storage.clear()
        assertTrue(kv.values.isEmpty())
    }
}
