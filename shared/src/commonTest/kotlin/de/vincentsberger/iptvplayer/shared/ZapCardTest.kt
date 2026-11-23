package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Zapp-Karte: sichtbar ab dem Senderwechsel, bis 4 s nach dem ersten Bild. */
class ZapCardTest {

    private var now = 0L
    private val zap = ZapCard { now }
    private fun ch(name: String) = Item(name, name, null, "1", "http://srv/live/$name.ts", Section.LIVE)

    @Test
    fun showsUntilFourSecondsAfterFirstFrame() {
        zap.onChannelChanged(ch("A"))
        now += 10_000; zap.tick()
        assertEquals("A", zap.channel?.name, "lädt noch – Karte bleibt")
        zap.onFirstFrame()
        now += 3_900; zap.tick()
        assertEquals("A", zap.channel?.name)
        now += 100; zap.tick()
        assertNull(zap.channel)
    }

    @Test
    fun fastZappingRestartsWithNewChannel() {
        zap.onChannelChanged(ch("A"))
        zap.onFirstFrame()
        now += 3_000
        zap.onChannelChanged(ch("B"))
        now += 2_000; zap.tick()
        assertEquals("B", zap.channel?.name, "Countdown von A gilt nicht für B")
        zap.onFirstFrame()
        now += 4_000; zap.tick()
        assertNull(zap.channel)
    }

    @Test
    fun hideClearsImmediately() {
        zap.onChannelChanged(ch("A"))
        zap.hide()
        assertNull(zap.channel)
    }
}
