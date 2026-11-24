package de.vincentsberger.iptvplayer.shared.player.stats

import kotlin.test.Test
import kotlin.test.assertEquals

/** Bewertung der Verbindung aus dem Verlauf. */
class ConnectionQualityTest {

    private fun good(fps: Float? = 50f, dropped: Long? = 0, buffer: Long? = null, stalled: Boolean = false, reconnected: Boolean = false) =
        StatsSample(bufferMs = buffer, bitrateBps = null, stalled = stalled, droppedDelta = dropped, fps = fps, reconnected = reconnected)

    private fun goods(n: Int) = List(n) { good() }

    private fun q(h: List<StatsSample>, reconnecting: Boolean = false, lost: Boolean = false, live: Boolean = true) =
        connectionQuality(h, reconnecting, lost, live)

    @Test
    fun unknownForFirstThreeSamples() {
        assertEquals(ConnectionQuality.UNKNOWN, q(goods(2)))
        assertEquals(ConnectionQuality.GOOD, q(goods(3)))
    }

    @Test
    fun reconnectingOrLostIsPoor() {
        assertEquals(ConnectionQuality.POOR, q(goods(10), reconnecting = true))
        assertEquals(ConnectionQuality.POOR, q(goods(10), lost = true))
    }

    @Test
    fun threeStalledSecondsInLastTenIsPoor() {
        val three = goods(20) + List(3) { good(stalled = true) }
        assertEquals(ConnectionQuality.POOR, q(three))
        val two = goods(20) + List(2) { good(stalled = true) }
        assertEquals(ConnectionQuality.FAIR, q(two))
    }

    @Test
    fun oldStallOnlyIsFair() {
        assertEquals(ConnectionQuality.FAIR, q(listOf(good(stalled = true)) + goods(30)))
        assertEquals(ConnectionQuality.GOOD, q(listOf(good(stalled = true)) + goods(61)))
    }

    @Test
    fun reconnectInLast60IsFair() {
        assertEquals(ConnectionQuality.FAIR, q(goods(5) + good(reconnected = true) + goods(5)))
    }

    @Test
    fun droppedOverTwoPercentIsFair() {
        assertEquals(ConnectionQuality.FAIR, q(List(10) { good(dropped = 2) }))
        assertEquals(ConnectionQuality.GOOD, q(List(10) { good(dropped = 1) }))
        assertEquals(ConnectionQuality.GOOD, q(List(10) { good(dropped = 2, fps = null) }))
    }

    @Test
    fun lowBufferFairOnlyForVod() {
        val low = List(5) { good(buffer = 1_500) }
        assertEquals(ConnectionQuality.FAIR, q(low, live = false))
        assertEquals(ConnectionQuality.GOOD, q(low, live = true))
        assertEquals(ConnectionQuality.GOOD, q(goods(5), live = false))
    }

    @Test
    fun lowBufferIgnoredNearEndOfMovie() {
        val low = goods(5) + good(buffer = 500)
        assertEquals(ConnectionQuality.FAIR, connectionQuality(low, false, false, isLive = false))
        assertEquals(ConnectionQuality.GOOD, connectionQuality(low, false, false, isLive = false, nearEnd = true))
    }
}
