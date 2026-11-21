package de.vincentsberger.iptvplayer.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamRecoveryTest {

    @Test
    fun retriesWithGrowingDelaysThenGivesUp() {
        val r = StreamRecovery(delaysMs = listOf(1_000, 2_000, 4_000))
        assertEquals(1_000, r.nextRetryDelay())
        assertEquals(2_000, r.nextRetryDelay())
        assertEquals(4_000, r.nextRetryDelay())
        assertNull(r.nextRetryDelay())
        assertEquals(3, r.attempts)
    }

    @Test
    fun stallIsDetectedOnlyAfterTimeout() {
        val r = StreamRecovery(stallMs = 15_000)
        assertFalse(r.isStalled(lastProgressMs = 100_000, nowMs = 114_999))
        assertTrue(r.isStalled(lastProgressMs = 100_000, nowMs = 115_000))
    }

    @Test
    fun stablePlaybackResetsAttempts() {
        val r = StreamRecovery(delaysMs = listOf(1_000, 2_000), stableMs = 60_000)
        r.nextRetryDelay()
        r.nextRetryDelay()
        r.onProgress(0)
        r.onProgress(30_000)
        assertEquals(2, r.attempts) // noch nicht lange genug stabil
        r.onProgress(60_000)
        assertEquals(0, r.attempts)
        assertEquals(1_000, r.nextRetryDelay())
    }

    @Test
    fun shortProgressBetweenDropoutsDoesNotReset() {
        val r = StreamRecovery(delaysMs = listOf(1_000, 2_000), stableMs = 60_000)
        r.onProgress(0)
        r.onProgress(50_000)
        r.nextRetryDelay() // Abbruch: Stabilitätszeit beginnt neu
        r.onProgress(55_000)
        r.onProgress(70_000)
        assertEquals(1, r.attempts)
        assertEquals(2_000, r.nextRetryDelay())
    }

    @Test
    fun resetFreesAllAttempts() {
        val r = StreamRecovery(delaysMs = listOf(1_000))
        r.nextRetryDelay()
        assertNull(r.nextRetryDelay())
        r.reset()
        assertEquals(1_000, r.nextRetryDelay())
    }
}
