package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStoreTest {

    private val storage = Storage(InMemoryKeyValueStore())
    private val library = LibraryStore(storage, SettingsStore(storage) { 0L })
    private val history = HistoryStore(storage, library) { 0L }
    private val dashboard = DashboardStore(Session(), storage, library, { 0L }, CoroutineScope(Dispatchers.Unconfined))
    private val catalog = CatalogStore(storage, library, history, dashboard)

    private fun drama(id: String, cat: String) = Item(id, "Film $id", null, cat, "http://srv/movie/u/p/$id.mp4", Section.MOVIES, genre = "Drama")

    @Test
    fun genreRowsComeFromTheVisibleItems() {
        val items = listOf("a1", "a2", "a3").map { drama(it, "1") } + listOf("b1", "b2", "b3").map { drama(it, "2") }
        library.sections[Section.MOVIES] = SectionState(
            categories = listOf(Category("1", "Kino"), Category("2", "Archiv")), items = items, byCategory = items.groupBy { it.categoryId },
        )
        library.toggleHidden(Section.MOVIES, "2")
        val (genre, titles) = catalog.genreRows(Section.MOVIES).single()
        assertEquals("Drama", genre)
        assertEquals(listOf("a1", "a2", "a3"), titles.map { it.id })
    }
}
