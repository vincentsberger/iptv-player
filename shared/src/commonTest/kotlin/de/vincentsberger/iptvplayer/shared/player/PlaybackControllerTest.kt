package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.FakePlaybackEngine
import de.vincentsberger.iptvplayer.shared.testing.Play
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private class Setup(val app: AppState, val engine: FakePlaybackEngine, val controller: PlaybackController, val items: List<Item>)

    private fun TestScope.setup(
        section: Section = Section.LIVE,
        startIndex: Int = 0,
        startPosition: Long = 0,
        app: AppState = testAppState(),
        resolveUrl: (Item) -> String = { it.url },
        pick: (List<Item>) -> List<Item> = { it },
    ): Setup {
        val items = pick(app.section(section).items.let { l -> if (section == Section.SERIES) episodes(app) else l })
        val engine = FakePlaybackEngine { currentTime }
        val controller = PlaybackController(app, engine, items, startIndex, startPosition, { currentTime }, { currentTime }, resolveUrl)
        return Setup(app, engine, controller, items)
    }

    /** Drei Folgen einer Serie: Filme des Beispielservers als Serie ausgegeben (Folgen tragen die Serien-ID als Kategorie). */
    private fun episodes(app: AppState) = app.section(Section.MOVIES).items.map { it.copy(section = Section.SERIES, categoryId = "200") }

    @Test
    fun startCurrentPlaysAtStartPosition() = runTest {
        val s = setup(Section.MOVIES, startPosition = 42_000)
        s.controller.startCurrent()
        assertEquals(Play(s.items[0], s.items[0].url, 42_000, false), s.engine.plays.single())
        assertEquals(42_000, s.controller.startedFromMs)
    }

    private val duplicates = mapOf(
        "get_live_categories" to """[{"category_id":"1","category_name":"UK | General"}]""",
        "get_live_streams" to """[{"stream_id":1,"name":"UK: BBC ONE","category_id":"1"},
            {"stream_id":2,"name":"UK: BBC ONE ","category_id":"1"},
            {"stream_id":3,"name":"UK: BBC ONE","category_id":"1"},
            {"stream_id":5,"name":"UK: ITV","category_id":"1"},
            {"stream_id":6,"name":"UK: C4","category_id":"1"}]""",
    )

    @Test
    fun selectSourcePlaysAlternate() = runTest {
        val s = setup(app = testAppState(http = SampleData.http(duplicates)))
        s.controller.startCurrent()
        assertEquals(3, s.controller.sources.size)
        s.controller.selectSource(1)
        val play = s.engine.plays.last()
        assertEquals(s.controller.sources[1].url, play.url)
        assertEquals(0, play.startMs)
        assertTrue(play.keepFrame)
        assertEquals(1, s.controller.sourceIndex)
    }

    @Test
    fun sourceResetsOnTitleChange() = runTest {
        val s = setup(app = testAppState(http = SampleData.http(duplicates)))
        s.controller.startCurrent()
        s.controller.selectSource(1)
        s.controller.switchTo(1)
        assertEquals(0, s.controller.sourceIndex)
    }

    @Test
    fun startPositionOnlyOnFirstStart() = runTest {
        val s = setup(Section.MOVIES, startPosition = 42_000)
        s.controller.startCurrent()
        s.controller.switchTo(1)
        s.controller.startCurrent()
        s.controller.switchTo(0)
        s.controller.startCurrent()
        assertEquals(0, s.engine.plays[2].startMs)
    }

    @Test
    fun switchingSavesPreviousProgress() = runTest {
        val s = setup(Section.MOVIES)
        s.controller.startCurrent()
        s.engine.timeMs = 30_000
        s.engine.lengthMs = 100_000
        s.controller.switchTo(1)
        s.controller.startCurrent()
        assertEquals(30_000, s.app.history.first { it.item.url == s.items[0].url }.position)
    }

    @Test
    fun switchToWrapsAndRemembersLast() = runTest {
        val s = setup()
        s.controller.switchTo(-1)
        assertEquals(2, s.controller.index)
        assertEquals(0, s.controller.lastIndex)
        s.controller.lastChannel()
        assertEquals(0, s.controller.index)
    }

    @Test
    fun switchToSameIndexIsNoOp() = runTest {
        val s = setup(app = testAppState(http = SampleData.http(duplicates)))
        s.controller.startCurrent()
        s.controller.switchTo(1)
        s.controller.switchTo(0)
        s.controller.selectSource(1)
        s.controller.seek.press(30_000, 0, 100_000)
        val target = s.controller.seek.positionOf(0)
        s.controller.switchTo(0)
        assertEquals(1, s.controller.lastIndex)
        assertEquals(1, s.controller.sourceIndex)
        assertEquals(target, s.controller.seek.positionOf(0))
        s.controller.switchTo(s.items.size) // umgebrochen: ebenfalls derselbe Titel
        assertEquals(1, s.controller.sourceIndex)
    }

    @Test
    fun digitsOfCurrentChannelKeepLastIndex() = runTest {
        val s = setup(startIndex = 1)
        s.controller.switchTo(0)
        s.controller.switchTo(1)
        s.controller.numberInput.type(2)
        val job = launch { s.controller.commitDigits { error("nicht erwartet") } }
        advanceTimeBy(1_600)
        assertEquals(1, s.controller.index)
        assertEquals(0, s.controller.lastIndex)
        job.cancel()
    }

    @Test
    fun switchCancelsPendingSeek() = runTest {
        val s = setup(Section.MOVIES)
        s.controller.seek.press(30_000, 0, 100_000)
        s.controller.switchTo(1)
        assertEquals(5, s.controller.seek.positionOf(5))
    }

    @Test
    fun resolveUrlIsUsed() = runTest {
        val s = setup(resolveUrl = { "file://x" })
        s.controller.startCurrent()
        assertEquals("file://x", s.engine.plays.single().url)
    }

    @Test
    fun epgOnlyForLive() = runTest {
        val http = SampleData.http()
        val s = setup(Section.MOVIES, app = testAppState(http = http))
        val before = http.requests.size
        s.controller.startCurrent()
        assertTrue(s.controller.epg.isEmpty())
        assertTrue(http.requests.drop(before).none { FakeHttp.actionOf(it) == "get_short_epg" })
    }

    @Test
    fun autoNextCountsDownAndSwitches() = runTest {
        val s = setup(Section.SERIES)
        s.engine.finished = true
        val job = launch { s.controller.runAutoNext() }
        runCurrent()
        assertEquals(10, s.controller.nextIn)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(9, s.controller.nextIn)
        advanceTimeBy(8_000); runCurrent()
        assertEquals(1, s.controller.nextIn)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, s.controller.index)
        assertNull(s.controller.nextIn)
        job.cancel()
    }

    /** Aus „Weiterschauen“: die Liste ist die gemischte Reihe – weiter geht es mit der nächsten Folge derselben Serie. */
    @Test
    fun autoNextFromContinueRowContinuesTheSameSeries() = runTest {
        val app = testAppState()
        app.open(app.section(Section.SERIES).items, 0)
        advanceUntilIdle()
        val episodes = app.seasons.flatMap { it.episodes } // S1F1, S1F2, S2F1
        app.back()
        val movie = app.section(Section.MOVIES).items.first()
        val row = listOf(episodes[1], movie)
        val engine = FakePlaybackEngine { currentTime }
        val controller = PlaybackController(app, engine, row, 0, 0, { currentTime }, { currentTime })
        engine.finished = true
        val job = launch { controller.runAutoNext() }
        advanceTimeBy(11_000); runCurrent()
        assertEquals(episodes[2].url, controller.items[controller.index].url, "nächste Folge der Serie (über die Staffel hinweg), nicht der Film")
        job.cancel()
    }

    @Test
    fun autoNextStopsAfterTheLastEpisodeOfTheSeries() = runTest {
        val app = testAppState()
        app.open(app.section(Section.SERIES).items, 0)
        advanceUntilIdle()
        val last = app.seasons.last().episodes.last()
        app.back()
        val engine = FakePlaybackEngine { currentTime }
        val controller = PlaybackController(app, engine, listOf(last), 0, 0, { currentTime }, { currentTime })
        engine.finished = true
        val job = launch { controller.runAutoNext() }
        advanceTimeBy(20_000); runCurrent()
        assertNull(controller.nextIn)
        assertEquals(last.url, controller.items[controller.index].url)
        job.cancel()
    }

    @Test
    fun autoNextCancelled() = runTest {
        val s = setup(Section.SERIES)
        s.engine.finished = true
        val job = launch { s.controller.runAutoNext() }
        advanceTimeBy(3_000)
        s.controller.cancelNext()
        job.cancel() // die Oberfläche startet den Effekt mit neuem Schlüssel neu
        val restarted = launch { s.controller.runAutoNext() }
        advanceTimeBy(20_000)
        assertEquals(0, s.controller.index)
        assertNull(s.controller.nextIn)
        restarted.cancel()
    }

    @Test
    fun autoNextAgainAfterManualSwitch() = runTest {
        val s = setup(Section.SERIES)
        s.engine.finished = true
        s.controller.cancelNext()
        s.controller.switchTo(1)
        assertTrue(!s.controller.nextCancelled)
        val job = launch { s.controller.runAutoNext() }
        runCurrent()
        assertEquals(10, s.controller.nextIn)
        job.cancel()
    }

    @Test
    fun digitsSwitchWithinList() = runTest {
        val s = setup()
        s.controller.numberInput.type(2)
        val job = launch { s.controller.commitDigits { error("nicht erwartet") } }
        advanceTimeBy(1_400)
        assertEquals(0, s.controller.index)
        advanceTimeBy(200)
        assertEquals(1, s.controller.index)
        job.cancel()
    }

    @Test
    fun digitsSwitchToAllChannelsList() = runTest {
        val numbered = SampleData.http(mapOf("get_live_streams" to """[
            {"stream_id":1,"name":"Das Erste","category_id":"1","num":1},
            {"stream_id":2,"name":"ZDF","category_id":"1","num":2},
            {"stream_id":3,"name":"Sport1","category_id":"2","num":3}]"""))
        val s = setup(app = testAppState(http = numbered), pick = { listOf(it[0]) })
        val all = s.app.section(Section.LIVE).items
        s.controller.numberInput.type(3)
        s.controller.commitDigits { error("nicht erwartet") }
        assertEquals(all, s.controller.items)
        assertEquals(2, s.controller.index)
        assertNull(s.controller.lastIndex)
    }

    @Test
    fun openListSwitchesListAndClearsLastIndex() = runTest {
        val s = setup()
        s.controller.switchTo(1)
        val other = s.items.reversed()
        s.controller.openList(other, 1)
        assertEquals(other, s.controller.items)
        assertEquals(1, s.controller.index)
        assertNull(s.controller.lastIndex)
        assertEquals(0, s.controller.sourceIndex)
    }

    @Test
    fun digitsNotFound() = runTest {
        val s = setup()
        s.controller.numberInput.type(9)
        s.controller.numberInput.type(9)
        var missing: Int? = null
        s.controller.commitDigits { missing = it }
        assertEquals(99, missing)
        assertEquals(0, s.controller.index)
    }

    @Test
    fun clockSavesEvery15sForVodOnly() = runTest {
        val vod = setup(Section.MOVIES)
        vod.engine.timeMs = 5_000
        vod.engine.lengthMs = 100_000
        val vodJob = launch { vod.controller.runClock(onSleep = {}) }
        advanceTimeBy(14_400)
        assertEquals(0, vod.app.history.size)
        advanceTimeBy(1_000)
        assertEquals(1, vod.app.history.size)
        vodJob.cancel()

        val live = setup()
        val liveJob = launch { live.controller.runClock(onSleep = {}) }
        advanceTimeBy(15_000)
        assertEquals(0, live.app.history.size)
        liveJob.cancel()
    }

    @Test
    fun clockStopsOnSleepTimer() = runTest {
        var now = 1_000L
        val s = setup(app = testAppState(now = { now }))
        s.app.cycleSleepTimer()
        var slept = 0
        val job = launch { s.controller.runClock(onSleep = { slept++ }) }
        advanceTimeBy(2_000)
        assertEquals(0, slept)
        now += 31 * 60_000L
        advanceTimeBy(1_000)
        assertEquals(1, slept)
        assertTrue(job.isCompleted)
    }
}
