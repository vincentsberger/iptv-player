package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryStoreTest {

    private val storage = Storage(InMemoryKeyValueStore())
    private val settings = SettingsStore(storage) { 0L }
    private val library = LibraryStore(storage, settings).apply {
        val news = Category("1", "DE | Zeitgeschehen")
        val sport = Category("2", "AT | Abendsport")
        val items = listOf(
            Item("a", "Kanal A", null, "1", "http://x/a", Section.LIVE),
            Item("b", "Kanal B", null, "2", "http://x/b", Section.LIVE),
        )
        sections[Section.LIVE] = SectionState(categories = listOf(news, sport), items = items, byCategory = items.groupBy { it.categoryId })
    }

    private fun visibleIds() = library.section(Section.LIVE).categories.map { it.id }

    @Test
    fun hiddenCategoryDisappearsFromTheView() {
        library.toggleHidden(Section.LIVE, "1")
        assertEquals(listOf("2"), visibleIds())
    }

    @Test
    fun countryFilterKeepsOnlyChosenTags() {
        library.toggleCountry(Section.LIVE, "DE")
        assertEquals(listOf("1"), visibleIds())
        assertEquals(listOf("1", "2"), library.sectionWithoutCountryFilter(Section.LIVE).categories.map { it.id })
    }

    @Test
    fun sortAzFollowsSettings() {
        settings.sortAz = true
        assertEquals(listOf("2", "1"), visibleIds()) // „Abendsport“ vor „Zeitgeschehen“ – ohne Kürzel verglichen
    }
}
