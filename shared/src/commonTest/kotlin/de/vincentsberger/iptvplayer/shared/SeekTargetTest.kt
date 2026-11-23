package de.vincentsberger.iptvplayer.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Spulen mit Ziel: Drücke sammeln, einmal springen. */
class SeekTargetTest {

    private var now = 0L
    private val seek = SeekTarget { now }
    private val duration = 9_960_000L

    @Test
    fun firstPressAnchorsAtPosition() {
        seek.press(30_000, positionMs = 3_792_000, durationMs = duration)
        assertEquals(SeekState(3_792_000, 3_822_000), seek.state)
    }

    @Test
    fun pressesAccumulate() {
        repeat(4) { seek.press(30_000, positionMs = 3_792_000 + it * 500L, durationMs = duration) } // Bild läuft weiter
        assertEquals(120_000, seek.state!!.deltaMs, "Ausgang bleibt die Position beim ersten Druck")
        seek.press(-10_000, 3_800_000, duration)
        assertEquals(110_000, seek.state!!.deltaMs)
    }

    @Test
    fun clampedToStartAndBeforeEnd() {
        seek.press(-60_000, positionMs = 20_000, durationMs = duration)
        assertEquals(0, seek.state!!.targetMs)
        seek.cancel()
        seek.press(600_000, positionMs = duration - 5_000, durationMs = duration)
        assertEquals(duration - 1_000, seek.state!!.targetMs, "knapp vor dem Ende, sonst gilt der Film als beendet")
    }

    @Test
    fun unknownDurationOnlyClampsAtZero() {
        seek.press(30_000, positionMs = 0, durationMs = 0)
        assertEquals(30_000, seek.state!!.targetMs)
        seek.cancel()
        seek.press(-10_000, positionMs = 0, durationMs = 0)
        assertEquals(0, seek.state!!.targetMs)
    }

    @Test
    fun commitsOnceAfter800ms() {
        seek.press(30_000, 1_000_000, duration)
        now += 799
        assertNull(seek.due())
        now += 1
        assertEquals(1_030_000, seek.due())
        assertNull(seek.state)
        assertNull(seek.due(), "nur einmal")
    }

    @Test
    fun newPressExtendsTheWait() {
        seek.press(30_000, 1_000_000, duration)
        now += 700
        seek.press(30_000, 1_000_700, duration) // gehaltene Taste / nächster Druck
        now += 700
        assertNull(seek.due())
        now += 100
        assertEquals(1_060_000, seek.due())
    }

    @Test
    fun commitNowAndCancel() {
        seek.press(-10_000, 1_000_000, duration)
        assertEquals(990_000, seek.commitNow())
        assertNull(seek.state)
        seek.press(30_000, 1_000_000, duration)
        seek.cancel()
        assertNull(seek.commitNow())
    }

    @Test
    fun deltaLabels() {
        assertEquals("+2 min", seekDeltaLabel(120_000))
        assertEquals("−40 s", seekDeltaLabel(-40_000))
        assertEquals("+1 min 30 s", seekDeltaLabel(90_000))
        assertEquals("+1 h 5 min", seekDeltaLabel(3_900_000))
        assertEquals("±0 s", seekDeltaLabel(0))
    }
}
