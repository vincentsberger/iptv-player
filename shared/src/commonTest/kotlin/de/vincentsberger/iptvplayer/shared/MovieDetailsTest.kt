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
