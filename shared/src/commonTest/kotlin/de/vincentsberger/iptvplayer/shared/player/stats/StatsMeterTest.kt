package de.vincentsberger.iptvplayer.shared.player.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Messpunkte aus Rohwerten: Datenrate, Stockungen, Verlauf, Neuverbindungen. */
class StatsMeterTest {

    private fun StatsMeter.tick(
        bytes: Long? = null, dropped: Long? = null, progressed: Boolean = true, shouldPlay: Boolean = true, reconnects: Int = 0,
    ) = sample(EngineStats(bytesReceived = bytes, droppedFrames = dropped), progressed, shouldPlay, reconnects)

    @Test
    fun bitrateFromByteDelta() {
        val m = StatsMeter()
        m.tick(bytes = 0)
        assertEquals(8_000_000L, m.tick(bytes = 1_000_000).bitrateBps)
    }

    @Test
    fun firstSampleHasNoBitrate() {
        assertNull(StatsMeter().tick(bytes = 500).bitrateBps)
    }

    @Test
    fun counterDropDiscardsThatSecond() {
        val m = StatsMeter()
        m.tick(bytes = 5_000_000)
        assertNull(m.tick(bytes = 1_000).bitrateBps)
        assertEquals(1_000_000L, m.tick(bytes = 126_000).bitrateBps)
    }

    @Test
    fun missingBytesGiveNoBitrate() {
        val m = StatsMeter()
        m.tick(bytes = 0)
        assertNull(m.tick(bytes = null).bitrateBps)
    }

    @Test
    fun stallsNotCountedBeforeFirstProgress() {
        val m = StatsMeter()
        repeat(3) { assertFalse(m.tick(progressed = false).stalled) }
        m.tick(progressed = true)
        assertTrue(m.tick(progressed = false).stalled)
    }

    @Test
    fun pausedSecondsAreNotStalls() {
        val m = StatsMeter()
        m.tick(progressed = true)
        assertFalse(m.tick(progressed = false, shouldPlay = false).stalled)
    }

    @Test
    fun historyKeepsLast60() {
        val m = StatsMeter()
        var bytes = 0L
        // Sample i (0-basiert) hat Rate i * 1000 bit/s; Nr. 16 ist i = 15.
        repeat(75) { i ->
            bytes += i * 125L
            m.tick(bytes = bytes)
        }
        assertEquals(60, m.history.size)
        assertEquals(15_000L, m.history.first().bitrateBps)
        assertEquals(74_000L, m.history.last().bitrateBps)
    }

    @Test
    fun reconnectedFlagOnCounterIncrease() {
        val m = StatsMeter()
        val flags = listOf(0, 0, 1, 1).map { m.tick(reconnects = it).reconnected }
        assertEquals(listOf(false, false, true, false), flags)
    }

    @Test
    fun droppedDeltaAndTotal() {
        val m = StatsMeter()
        val deltas = listOf(10L, 14L, 14L).map { m.tick(dropped = it).droppedDelta }
        assertEquals(listOf(null, 4L, 0L), deltas)
        assertEquals(4L, m.droppedTotal)
    }

    @Test
    fun resetClearsEverything() {
        val m = StatsMeter()
        m.tick(bytes = 0, dropped = 1)
        m.tick(bytes = 1000, dropped = 3)
        m.reset()
        assertTrue(m.history.isEmpty())
        assertNull(m.droppedTotal)
        assertNull(m.tick(bytes = 2000).bitrateBps)
    }
}
