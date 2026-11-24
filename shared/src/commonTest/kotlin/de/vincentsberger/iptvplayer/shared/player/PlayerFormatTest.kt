package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.data.localDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Anzeige-Helfer des Players, die alle Oberflächen teilen. */
class PlayerFormatTest {

    @Test
    fun seekMultiplierGrowsWhileHeld() {
        assertEquals(1, seekMultiplier(0)); assertEquals(1, seekMultiplier(9))
        assertEquals(3, seekMultiplier(10)); assertEquals(3, seekMultiplier(29)); assertEquals(6, seekMultiplier(30))
    }

    @Test
    fun endsAtIsLocalClockTime() {
        val now = 1_700_000_000_000L // Ortszeit über localDateTime gegenprüfen – Test hängt so nicht an der Zeitzone
        assertEquals(localDateTime(now + 45 * 60_000L).substring(11, 16), endsAt(45 * 60_000L, now))
        assertTrue(Regex("\\d\\d:\\d\\d").matches(endsAt(45 * 60_000L, now)))
    }

    @Test
    fun endsAtClampsNegativeRemaining() {
        assertEquals(localDateTime(5_000L).substring(11, 16), endsAt(-1_000L, 5_000L))
        assertTrue(Regex("\\d\\d:\\d\\d").matches(endsAt(-1_000L, 5_000L)))
    }
}
