package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgrammeDaysTest {
    private fun e(start: String, end: String) = EpgEntry("x", start.substring(11, 16), end.substring(11, 16), start, end)

    @Test
    fun daysAreLabelledRelativeToToday() {
        val list = listOf(
            e("2026-11-22 20:00:00", "2026-11-22 21:00:00"), e("2026-11-23 07:00:00", "2026-11-23 11:00:00"),
            e("2026-11-24 20:00:00", "2026-11-24 21:00:00"), e("2026-11-25 20:00:00", "2026-11-25 21:00:00"),
            e("2026-11-26 20:00:00", "2026-11-26 21:00:00"),
        )
        val days = programmeDays(list, "2026-11-24 03:00:00")
        assertEquals(listOf("So, 22.11.", "Gestern", "Heute", "Morgen", "Do, 26.11."), days.map { it.label })
    }

    @Test
    fun onAir() {
        val show = e("2026-11-24 20:00:00", "2026-11-24 21:00:00")
        assertTrue(isOnAir(show, "2026-11-24 20:30:00"))
        assertFalse(isOnAir(show, "2026-11-24 21:00:00"))
    }
}
