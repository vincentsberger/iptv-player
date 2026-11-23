package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pause-Info: kompakt sofort, ausführlich nach 5 s; Infos zu Film bzw. Serie aus dem App-Zustand. */
class PauseInfoTest {

    private var now = 0L
    private val pause = PauseInfo { now }

    @Test
    fun compactImmediatelyFullAfterFiveSeconds() {
        pause.onPaused()
        assertEquals(PauseStage.COMPACT, pause.stage)
        now += 4_999; pause.tick()
        assertEquals(PauseStage.COMPACT, pause.stage)
        now += 1; pause.tick()
        assertEquals(PauseStage.FULL, pause.stage)
    }

    @Test
    fun resumeHides() {
        pause.onPaused(); now += 6_000; pause.tick()
        pause.onResumed()
        assertEquals(PauseStage.NONE, pause.stage)
    }

    @Test
    fun pauseAgainStartsCompact() {
        pause.onPaused(); now += 3_000; pause.onResumed()
        pause.onPaused(); now += 3_000; pause.tick()
        assertEquals(PauseStage.COMPACT, pause.stage, "die 5 s beginnen neu")
    }

    @Test
    fun dismissFullGoesBackToCompact() {
        pause.onPaused(); now += 6_000; pause.tick()
        pause.dismissFull()
        assertEquals(PauseStage.COMPACT, pause.stage)
        now += 1_000; pause.tick()
        assertEquals(PauseStage.COMPACT, pause.stage, "nicht sofort wieder ausführlich")
        now += 5_000; pause.tick()
        assertEquals(PauseStage.FULL, pause.stage)
    }

    private val responses = mapOf(
        "get_vod_info" to """{"info":{"releasedate":"2016-11-10","rating":"7.9","genre":"Science Fiction","plot":"Außerirdische landen."}}""",
        "get_series_info" to """{"info":{"releaseDate":"2017-10-13","rating":"8.4","genre":"Krimi","plot":"Berlin 1929"},
            "episodes":{"1":[{"id":"2001","episode_num":1,"title":"Pilot"}],"2":[{"id":"2011","episode_num":1,"title":"Neue Spur"},{"id":"2012","episode_num":2,"title":"Der Zug"}]}}""",
    )

    @Test
    fun movieInfoIsRequestedAndKept() {
        val app = testAppState(http = SampleData.http(responses))
        val arrival = app.section(Section.MOVIES).items.first { it.name == "Arrival" }
        assertNull(app.mediaInfoFor(arrival))
        app.requestMediaInfo(arrival)
        assertEquals("2016", app.mediaInfoFor(arrival)?.year)
    }

    @Test
    fun episodeInfoComesFromSeries() {
        val app = testAppState(http = SampleData.http(responses))
        val series = app.section(Section.SERIES).items.single()
        app.open(listOf(series), 0) // Staffeln laden
        val episode = app.seasons[1].episodes[1]
        app.requestMediaInfo(episode)
        assertEquals("Berlin 1929", app.mediaInfoFor(episode)?.plot)
        assertEquals("Babylon Berlin", app.seriesNameOf(episode))
    }

    @Test
    fun episodeLabelFromSeasons() {
        val app = testAppState(http = SampleData.http(responses))
        app.open(listOf(app.section(Section.SERIES).items.single()), 0)
        assertEquals("S2 · F2 · Der Zug", app.episodeLabel(app.seasons[1].episodes[1]))
    }
}
