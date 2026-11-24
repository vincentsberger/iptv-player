package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.player.stats.ConnectionQuality
import de.vincentsberger.iptvplayer.shared.testing.FakePlaybackEngine
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerStatsTest {
    private class Setup(val engine: FakePlaybackEngine, val c: PlaybackController, val jobs: MutableList<Job>)

    private fun TestScope.start(
        resolve: (Item) -> String = { it.url },
        runWatch: Boolean = false,
        app: AppState = testAppState(),
    ): Setup {
        val engine = FakePlaybackEngine { currentTime }
        val c = PlaybackController(app, engine, app.section(Section.LIVE).items, 0, 0, { currentTime }, { currentTime }, resolve)
        val jobs = mutableListOf<Job>()
        jobs.add(launch { c.startCurrent(); if (runWatch) c.watch() })
        runCurrent()
        return Setup(engine, c, jobs)
    }

    private fun TestScope.runStats(s: Setup) {
        s.jobs.add(launch { s.c.runStats() })
        runCurrent()
    }

    @Test
    fun statsNullUntilFirstSample() = runTest {
        val s = start()
        assertNull(s.c.stats)
    }

    @Test
    fun sampleEverySecond() = runTest {
        val s = start()
        runStats(s)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(5, s.c.stats?.history?.size)
        s.jobs.forEach { it.cancel() }
    }

    @Test
    fun statsResetOnTitleAndSourceChange() = runTest {
        val s = start()
        runStats(s)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(10, s.c.stats?.history?.size)
        s.c.switchTo(1)
        assertNull(s.c.stats)
        assertEquals(0, s.c.reconnects)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, s.c.stats?.history?.size)
        s.jobs.forEach { it.cancel() }
    }

    @Test
    fun statsResetOnSourceChange() = runTest {
        val dup = mapOf(
            "get_live_categories" to """[{"category_id":"1","category_name":"UK | General"}]""",
            "get_live_streams" to """[{"stream_id":1,"name":"UK: BBC ONE","category_id":"1"},
                {"stream_id":3,"name":"UK: BBC ONE","category_id":"1"},{"stream_id":5,"name":"UK: ITV","category_id":"1"}]""",
        )
        val s = start(app = testAppState(http = SampleData.http(dup)))
        runStats(s)
        advanceTimeBy(10_000); runCurrent()
        s.c.selectSource(1)
        assertNull(s.c.stats)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, s.c.stats?.history?.size)
        s.jobs.forEach { it.cancel() }
    }

    @Test
    fun reconnectsCountReloadAndRetry() = runTest {
        val s = start(runWatch = true)
        s.engine.failed = true
        advanceTimeBy(1_001); runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, s.c.reconnects)
        s.c.retry()
        assertEquals(2, s.c.reconnects)
        s.jobs.forEach { it.cancel() }
    }

    @Test
    fun serverAndKindFromResolvedSource() = runTest {
        val s = start({ "http://h:8080/live/u/p/1.m3u8" })
        runStats(s)
        advanceTimeBy(1_000); runCurrent()
        val st = assertNotNull(s.c.stats)
        assertEquals("h:8080", st.server)
        assertEquals("HLS (m3u8)", st.streamKind)
        assertEquals("1/1", st.source)
        s.jobs.forEach { it.cancel() }
    }

    @Test
    fun qualityFollowsController() = runTest {
        val s = start(runWatch = true)
        runStats(s)
        advanceTimeBy(5_000); runCurrent()
        s.engine.failed = true
        advanceTimeBy(2_000); runCurrent()
        assertEquals(ConnectionQuality.POOR, s.c.stats?.quality)
        s.jobs.forEach { it.cancel() }
    }
}
