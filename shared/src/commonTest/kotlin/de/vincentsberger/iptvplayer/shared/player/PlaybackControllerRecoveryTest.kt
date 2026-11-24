package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.FakePlaybackEngine
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerRecoveryTest {
    private class Setup(val engine: FakePlaybackEngine, val controller: PlaybackController, val watcher: Job)

    /** Startet Titel und Wächter; [section] bestimmt Live oder Film. */
    private fun TestScope.start(section: Section): Setup {
        val app = testAppState()
        val engine = FakePlaybackEngine { currentTime }
        val items: List<Item> = app.section(section).items
        val c = PlaybackController(app, engine, items, 0, 0, { currentTime }, { currentTime })
        val job = launch { c.startCurrent(); c.watch() }
        runCurrent()
        return Setup(engine, c, job)
    }

    @Test
    fun failedStreamReconnectsWithKeepFrame() = runTest {
        val s = start(Section.LIVE)
        s.engine.failed = true
        advanceTimeBy(1_001); runCurrent()
        assertTrue(s.controller.reconnecting)
        assertEquals(1, s.engine.plays.size)
        advanceTimeBy(2_000); runCurrent()
        assertTrue(s.engine.plays.last().keepFrame)
        assertEquals(2, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun vodResumesAtLastGoodPosition() = runTest {
        val s = start(Section.MOVIES)
        s.engine.playing = true
        s.engine.timeMs = 50_000; advanceTimeBy(1_000); runCurrent()
        s.engine.timeMs = 60_000; advanceTimeBy(1_000); runCurrent()
        s.engine.failed = true
        advanceTimeBy(4_000); runCurrent()
        assertEquals(60_000, s.engine.plays.last().startMs)
        assertEquals(2, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun liveReconnectsAtLiveEdge() = runTest {
        val s = start(Section.LIVE)
        s.engine.failed = true
        advanceTimeBy(4_000); runCurrent()
        assertEquals(0, s.engine.plays.last().startMs)
        assertEquals(2, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun liveFinishedCountsAsBroken() = runTest {
        val s = start(Section.LIVE)
        s.engine.finished = true
        advanceTimeBy(4_000); runCurrent()
        assertEquals(2, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun stallDetectedWithoutEngineProgress() = runTest {
        val s = start(Section.LIVE)
        s.engine.playing = true
        advanceTimeBy(14_000); runCurrent()
        assertEquals(1, s.engine.plays.size)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun progressViaTimeSamplingResetsState() = runTest {
        val s = start(Section.LIVE)
        s.engine.failed = true
        advanceTimeBy(1_500); runCurrent()
        assertTrue(s.controller.reconnecting)
        advanceTimeBy(2_000); runCurrent()
        s.engine.playing = true
        s.engine.timeMs += 1_000; advanceTimeBy(1_000); runCurrent()
        assertFalse(s.controller.reconnecting)
        s.watcher.cancel()
    }

    @Test
    fun givesUpAfterAllRetries() = runTest {
        val s = start(Section.LIVE)
        repeat(70) { s.engine.failed = true; advanceTimeBy(1_000); runCurrent() }
        assertTrue(s.controller.connectionLost)
        assertFalse(s.controller.reconnecting)
        assertNull(s.controller.zap.channel)
        s.watcher.cancel()
    }

    @Test
    fun retryAfterLostReconnects() = runTest {
        val s = start(Section.LIVE)
        repeat(70) { s.engine.failed = true; advanceTimeBy(1_000); runCurrent() }
        assertTrue(s.controller.connectionLost)
        val before = s.engine.plays.size
        s.controller.retry()
        assertFalse(s.controller.connectionLost)
        assertTrue(s.controller.reconnecting)
        assertEquals(before + 1, s.engine.plays.size)
        assertTrue(s.engine.plays.last().keepFrame)
        s.watcher.cancel()
    }

    @Test
    fun noReconnectWhileSuspended() = runTest {
        val s = start(Section.LIVE)
        s.controller.suspended = true
        s.engine.failed = true
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun pausedIsNotStalled() = runTest {
        val s = start(Section.MOVIES)
        s.engine.paused = true
        s.engine.timeMs = 5_000
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun restartResetsRetryBudget() = runTest {
        val s = start(Section.LIVE)
        s.engine.failed = true
        advanceTimeBy(3_001); runCurrent() // 1. Versuch nach 2 s
        s.engine.failed = true
        advanceTimeBy(5_000); runCurrent() // 2. Versuch nach 4 s
        assertEquals(3, s.engine.plays.size)
        s.controller.restart()
        assertEquals(4, s.engine.plays.size)
        s.engine.failed = true
        advanceTimeBy(3_500); runCurrent() // volle Folge beginnt wieder bei 2 s (sonst 8 s)
        assertEquals(5, s.engine.plays.size)
        s.watcher.cancel()
    }

    @Test
    fun restartPlaysCurrentSourceAtZero() = runTest {
        val s = start(Section.LIVE)
        s.engine.timeMs = 42_000
        s.controller.restart()
        val play = s.engine.plays.last()
        assertEquals(2, s.engine.plays.size)
        assertEquals(s.controller.item, play.item)
        assertEquals(0L, play.startMs)
        assertFalse(play.keepFrame)
        assertFalse(s.controller.reconnecting)
        assertFalse(s.controller.connectionLost)
        assertEquals(s.controller.item, s.controller.zap.channel)
        s.watcher.cancel()
    }

    @Test
    fun suspendDuringRetryDelaySkipsReload() = runTest {
        val s = start(Section.LIVE)
        s.engine.failed = true
        advanceTimeBy(1_001); runCurrent()
        assertTrue(s.controller.reconnecting)
        s.controller.suspended = true
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, s.engine.plays.size)
        assertFalse(s.controller.reconnecting)
        s.watcher.cancel()
    }
}
