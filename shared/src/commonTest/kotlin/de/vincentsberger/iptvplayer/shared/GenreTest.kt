package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GenreTest {
    @Test
    fun genresBecomeCategoriesWithEnoughTitles() = runTest {
        val series = (1..4).joinToString(",") { """{"series_id":$it,"name":"Serie $it","category_id":"20","genre":"Drama, Krimi"}""" } +
            """,{"series_id":9,"name":"Einzeln","category_id":"20","genre":"Western"}"""
        val app = testAppState(http = SampleData.http(mapOf(
            "get_series_categories" to """[{"category_id":"20","category_name":"Serien"}]""",
            "get_series" to "[$series]",
        )))
        app.ensureLoaded(Section.SERIES)
        val genres = app.genreCategories(Section.SERIES)
        assertEquals(listOf("Genre · Drama", "Genre · Krimi"), genres.map { it.name }, "Western hat zu wenige Titel")
        assertEquals(4, app.browseItems(Section.SERIES, genres.first().id).size)
        assertEquals(genres, app.browseCategories(Section.SERIES).takeLast(2))
    }

    @Test
    fun splitAndNormalise() {
        assertEquals(listOf("Drama", "Krimi", "Thriller"), splitGenres("DRAMA, krimi / Thriller, Drama"))
    }

    @Test
    fun seriesFactsFromTheListAlone() {
        val item = de.vincentsberger.iptvplayer.shared.data.Item(
            "1", "DE - Dark (2017)", null, "20", "", de.vincentsberger.iptvplayer.shared.data.Section.SERIES,
            rating = 8.7, genre = "Drama, Krimi, Mystery, Sci-Fi",
        )
        assertEquals("2017 · ★ 8,7 · Drama, Krimi, Mystery", seriesFacts(item))
    }
}
