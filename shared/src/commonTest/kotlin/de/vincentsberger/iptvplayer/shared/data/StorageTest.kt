package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTest {

    private val kv = InMemoryKeyValueStore()
    private val storage = Storage(kv)

    @Test
    fun trackLanguagesDefaultToProviderAndSurviveARoundTrip() {
        assertNull(storage.audioLanguage)
        assertNull(storage.subtitleLanguage)
        storage.audioLanguage = "en"
        storage.subtitleLanguage = Storage.SUBTITLES_OFF
        assertEquals("en", Storage(kv).audioLanguage)
        assertEquals(Storage.SUBTITLES_OFF, Storage(kv).subtitleLanguage)
        storage.audioLanguage = null
        assertNull(storage.audioLanguage)
    }

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
    fun secondProviderIsAddedAndBecomesActive() {
        val a = storage.saveAccount(Account.Xtream("http://srv", "user", "geheim"))
        val b = storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        assertEquals(listOf(a, b), storage.accounts().map { it.id })
        assertEquals(b, storage.activeAccountId)
        assertEquals(Account.M3u("http://srv/list.m3u"), storage.loadAccount())
    }

    @Test
    fun sameProviderKeepsItsIdAndPlaceEvenWithAnotherScheme() {
        val a = storage.saveAccount(Account.Xtream("srv:8080", "user", "alt"))
        storage.saveAccount(Account.M3u("http://other/list.m3u"))
        val again = storage.saveAccount(Account.Xtream("https://srv:8080", "user", "neu"))
        assertEquals(a, again)
        assertEquals(2, storage.accounts().size)
        assertEquals(Account.Xtream("https://srv:8080", "user", "neu"), storage.accounts().first().account)
    }

    @Test
    fun favoritesHistoryAndPlacesBelongToTheActiveProviderSettingsToAll() {
        val a = storage.saveAccount(Account.M3u("http://a/list.m3u"))
        storage.saveFavorites(setOf("LIVE:http://a/1"))
        storage.lastTab = "MOVIES"
        storage.sortAz = true
        val b = storage.saveAccount(Account.M3u("http://b/list.m3u"))
        assertEquals(emptySet(), storage.favorites())
        assertNull(storage.lastTab)
        assertEquals(true, storage.sortAz)

        storage.activeAccountId = a
        assertEquals(setOf("LIVE:http://a/1"), storage.favorites())
        assertEquals("MOVIES", storage.lastTab)
        assertEquals(b, storage.accounts().last().id)
    }

    @Test
    fun removingAProviderDeletesItsDataAndActivatesTheNext() {
        val a = storage.saveAccount(Account.M3u("http://a/list.m3u"))
        storage.saveFavorites(setOf("x"))
        val b = storage.saveAccount(Account.M3u("http://b/list.m3u"))
        storage.activeAccountId = a
        storage.removeAccount(a)
        assertEquals(b, storage.activeAccountId)
        assertTrue(kv.values.keys.none { it.startsWith("a:$a:") })

        storage.removeAccount(b)
        assertNull(storage.loadAccount())
        assertTrue(kv.values.isEmpty())
    }

    @Test
    fun singleProviderFromVersion12IsTakenOver() {
        kv.putString("type", "xtream"); kv.putString("server", "http://srv"); kv.putString("user", "u"); kv.putString("pass", "p")
        kv.putString("favorites", "[\"LIVE:1\"]"); kv.putString("lastCat:MOVIES", "7"); kv.putString("volume", "40")
        val migrated = Storage(kv)
        assertEquals(Account.Xtream("http://srv", "u", "p"), migrated.loadAccount())
        assertEquals(setOf("LIVE:1"), migrated.favorites())
        assertEquals("7", migrated.lastBrowseCategory(Section.MOVIES))
        assertEquals(40, migrated.volume)
        assertTrue(listOf("type", "server", "user", "pass", "favorites", "lastCat:MOVIES").none { it in kv.values })
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
        val id = storage.saveAccount(Account.M3u("http://srv/list.m3u"))
        kv.values["a:$id:history"] = """[
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
