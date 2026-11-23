package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Filme öffnen zuerst die Detailseite; abgespielt wird von dort (oder aus „Weiterschauen“) mit play(). */
class MovieDetailsTest {

    private val vodInfo = """{"info":{"plot":"Linguistin trifft Außerirdische","director":"Denis Villeneuve",
        "cast":"Amy Adams, Jeremy Renner , Forest Whitaker","country":"USA","o_name":"Arrival","name":"Arrival",
        "backdrop_path":["https://img.example/arrival-bg.jpg"],"rating":8,"releasedate":"2016-11-24"}}"""

    @Test
    fun choosingAMovieOpensItsDetailsAndLoadsTheInfo() {
        val app = testAppState(http = SampleData.http(mapOf("get_vod_info" to vodInfo)))
        val movies = app.section(Section.MOVIES).items
        app.open(movies, 0)
        val screen = assertIs<Screen.Movie>(app.screen)
        assertEquals("Arrival", screen.movie.name)
        assertEquals("Denis Villeneuve", app.mediaInfoFor(screen.movie)?.director)
    }

    @Test
    fun playStartsWhereYouStoppedOrFromTheBeginning() {
        val app = testAppState()
        val movies = app.section(Section.MOVIES).items
        app.saveProgress(movies[0], position = 600_000, duration = 6_000_000)
        app.play(movies, 0)
        assertEquals(600_000, assertIs<Screen.Player>(app.screen).startPosition)
        app.back()
        app.play(movies, 0, fromStart = true)
        assertEquals(0, assertIs<Screen.Player>(app.screen).startPosition)
    }

    @Test
    fun backFromTheDetailsReturnsHome() {
        val app = testAppState()
        app.open(app.section(Section.MOVIES).items, 1)
        app.back()
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun liveChannelsStillPlayDirectly() {
        val app = testAppState()
        app.open(app.section(Section.LIVE).items, 0)
        assertIs<Screen.Player>(app.screen)
    }
}

class MovieDetailsTextTest {
    private fun info(year: String? = "2016", rating: Double? = 7.9, secs: Int? = 6960, age: Int? = 12, genres: List<String> = listOf("Drama", "Sci-Fi"), country: String? = "USA") =
        de.vincentsberger.iptvplayer.shared.data.MediaInfo(year, rating, secs, age, genres, plot = null, country = country)

    @Test
    fun factsInFixedOrderWithoutGaps() {
        assertEquals(listOf("2016", "★ 7,9", "1 h 56 min", "FSK 12", "Drama, Sci-Fi", "USA"), movieFacts(info()))
        assertEquals(listOf("2016", "Drama, Sci-Fi"), movieFacts(info(rating = null, secs = null, age = null, country = null)))
        assertEquals(emptyList(), movieFacts(null))
    }

    @Test
    fun playLabelShowsWhereItContinues() {
        assertEquals("Abspielen", playLabel(0))
        assertEquals("Weiterschauen ab 12:05", playLabel(725_000))
        assertEquals("Weiterschauen ab 1:12:30", playLabel(4_350_000))
    }
}
