package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Filme/Serien-Übersicht: Titelbild, Genre-Reihen, ähnliche Titel, Versionen. */
class BrowseRowsTest {

    private fun movies(json: String) = testAppState(http = SampleData.http(mapOf("get_vod_streams" to json)))

    private val catalog = """[
        {"stream_id":1,"name":"Kaltfront","category_id":"10","added":"1700000300","genre":"Thriller, Drama"},
        {"stream_id":2,"name":"Eisland","category_id":"10","added":"1700000100","genre":"Thriller"},
        {"stream_id":3,"name":"Funkstille","category_id":"11","added":"1700000200","genre":"Thriller"},
        {"stream_id":4,"name":"Ufer","category_id":"11","added":"1700000900","is_adult":"1","genre":"Drama"},
        {"stream_id":5,"name":"Ohne Genre","category_id":"11"},
        {"stream_id":6,"name":"Nachbar","category_id":"11"}
    ]"""

    private fun AppState.movie(name: String): Item = section(Section.MOVIES).items.first { it.name == name }

    @Test
    fun heroIsNewestVisibleTitle() {
        assertEquals("Kaltfront", movies(catalog).heroItem(Section.MOVIES)?.name, "Erwachseneninhalt nie im Titelbild")
        assertNull(testAppState().heroItem(Section.MOVIES), "ohne addedAt kein Titelbild")
    }

    @Test
    fun genreRowsLimitAndName() {
        val rows = movies(catalog).genreRows(Section.MOVIES)
        assertEquals("Thriller", rows.first().first)
        assertEquals(listOf("Kaltfront", "Eisland", "Funkstille"), rows.first().second.map { it.name })
        assertTrue(rows.none { it.first.startsWith("Genre") })
    }

    @Test
    fun similarBySharedGenre() {
        val app = movies(catalog)
        assertEquals(listOf("Eisland", "Funkstille"), app.similarTitles(app.movie("Kaltfront"), null).map { it.name }.filter { it != "Ufer" })
    }

    @Test
    fun similarUsesDetailGenresWhenListHasNone() {
        val app = movies(catalog)
        val info = MediaInfo(null, null, null, null, listOf("Thriller"), null)
        assertTrue(app.similarTitles(app.movie("Ohne Genre"), info).map { it.name }.containsAll(listOf("Kaltfront", "Eisland")))
    }

    @Test
    fun similarFallsBackToCategory() {
        val app = movies(catalog)
        assertEquals(listOf("Funkstille", "Ufer", "Nachbar"), app.similarTitles(app.movie("Ohne Genre"), null).map { it.name })
    }

    @Test
    fun similarEmptyWithoutMatch() {
        val app = movies("""[{"stream_id":1,"name":"Allein","category_id":"10"}]""")
        assertEquals(emptyList(), app.similarTitles(app.movie("Allein"), null))
    }

    @Test
    fun versionLabels() {
        fun item(name: String) = Item("1", name, null, "1", "u", Section.MOVIES)
        assertEquals("4K · HEVC", versionLabel(item("Kaltfront 4K HEVC")))
        assertEquals("Standard", versionLabel(item("Kaltfront")))
    }
}
