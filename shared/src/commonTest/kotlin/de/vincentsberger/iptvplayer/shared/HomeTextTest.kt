package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.AccountInfo
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Texte der Startseite und der Sender-Filter von Live TV. */
class HomeTextTest {

    private val min = 60_000L
    private val movie = Item("1", "Film", null, "10", "http://x/1.mp4", Section.MOVIES)
    private fun entry(position: Long, duration: Long) = HistoryEntry(movie, position, duration, 0L)

    @Test
    fun remainingMinutesAndHours() {
        assertEquals("Noch 48 Min.", remainingLabel(entry(52 * min, 100 * min)))
        assertEquals("Noch 1 Std. 12 Min.", remainingLabel(entry(0, 72 * min)))
        assertEquals("Noch 2 Std.", remainingLabel(entry(10 * min, 130 * min)))
        assertEquals("Noch 1 Min.", remainingLabel(entry(100 * min - 20_000, 100 * min)))
        assertEquals("Noch 49 Min.", remainingLabel(entry(52 * min - 30_000, 100 * min)), "angefangene Minute zählt")
        assertNull(remainingLabel(entry(10 * min, 0)))
    }

    @Test
    fun accountSummaryWithAndWithoutConnections() {
        val noon = 1805025600000L // 14.03.2027 12:00 UTC
        assertEquals("Abo bis 14.03.2027 · 1 von 2 Verbindungen", accountSummary(AccountInfo(noon, 2, 1)))
        assertEquals("Abo ohne Ablaufdatum", accountSummary(AccountInfo(null, null, null)))
    }

    @Test
    fun filterMatchesCleanNameIgnoringCase() {
        fun ch(name: String) = Item(name, name, null, "1", "http://x/$name", Section.LIVE)
        val list = listOf(ch("DE: Das Erste"), ch("ZDF"), ch("DE: Sport1"))
        assertEquals(listOf("DE: Das Erste"), filterChannels(list, " erste ").map { it.name })
        assertEquals(emptyList(), filterChannels(list, "DE:").map { it.name }, "Länderkürzel ist kein Teil des Namens")
        assertEquals(list, filterChannels(list, "  "))
    }
}
