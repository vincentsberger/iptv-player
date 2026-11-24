package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicatesTest {
    private val live = mapOf(
        "get_live_categories" to """[{"category_id":"1","category_name":"UK | General"},{"category_id":"2","category_name":"UK | Sport"}]""",
        "get_live_streams" to """[{"stream_id":1,"name":"UK: BBC ONE","category_id":"1"},
            {"stream_id":2,"name":"UK: BBC ONE ","category_id":"1"},
            {"stream_id":3,"name":"uk: bbc one","category_id":"1"},
            {"stream_id":4,"name":"UK: BBC ONE","category_id":"2"},
            {"stream_id":5,"name":"UK: ITV","category_id":"1"}]""",
    )

    @Test
    fun sameNameSameCategoryBecomesOneWithSources() {
        val app = testAppState(http = SampleData.http(live))
        val items = app.section(Section.LIVE).items
        assertEquals(listOf("1", "4", "5"), items.map { it.id }, "andere Kategorie bleibt eigener Eintrag")
        assertEquals(listOf("1", "2", "3"), app.sourcesOf(items.first()).map { it.id })
        assertEquals(listOf("5"), app.sourcesOf(items.last()).map { it.id })
    }

    @Test
    fun favoriteOfADuplicateIsKept() {
        val kv = InMemoryKeyValueStore()
        val first = testAppState(http = SampleData.http(mapOf(
            "get_live_categories" to """[{"category_id":"1","category_name":"UK | General"}]""",
            "get_live_streams" to """[{"stream_id":2,"name":"UK: BBC ONE","category_id":"1"}]""",
        )), kv = kv)
        first.toggleFavorite(first.section(Section.LIVE).items.single()) // Favorit auf Stream 2
        val app = testAppState(http = SampleData.http(live), kv = kv)  // jetzt kommt Stream 1 zuerst
        val shown = app.section(Section.LIVE).items.first()
        assertTrue(shown.favKey in app.favorites, "angezeigter Eintrag ist Favorit")
    }
}
