package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStoreTest {

    private val storage = Storage(InMemoryKeyValueStore())
    private val library = LibraryStore(storage, SettingsStore(storage) { 0L })
    private var now = 1L
    private fun store() = HistoryStore(storage, library) { now }

    private fun movie(id: String) = Item(id, "Film $id", null, "10", "http://srv/movie/u/p/$id.mp4", Section.MOVIES)

    @Test
    fun progressIsSavedNewestFirst() {
        val s = store()
        s.saveProgress(movie("1"), 60_000, 600_000)
        now = 2L
        s.saveProgress(movie("2"), 90_000, 600_000)
        assertEquals(listOf("2", "1"), s.history.map { it.item.id })
        assertEquals(listOf("2", "1"), store().history.map { it.item.id })
    }

    @Test
    fun favoriteOfACollapsedDuplicateMovesToTheKeptEntry() {
        val s = store()
        val kept = movie("1")
        val duplicate = movie("1b")
        s.toggleFavorite(duplicate)
        s.keepFavoritesOfDuplicates(mapOf(kept.favKey to listOf(duplicate)))
        assertTrue(kept.favKey in s.favorites)
    }
}
