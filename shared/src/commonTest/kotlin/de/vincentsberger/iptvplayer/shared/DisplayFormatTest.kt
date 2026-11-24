package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Anzeige-Helfer, die alle Apps teilen (früher je eine Kopie in PC- und TV-App). */
class DisplayFormatTest {

    @Test
    fun wrapIndexGoesAroundBothEnds() {
        assertEquals(2, wrapIndex(-1, 3))
        assertEquals(0, wrapIndex(3, 3))
        assertEquals(1, wrapIndex(1, 3))
    }

    @Test
    fun formatTimeUsesHoursOnlyWhenNeeded() {
        assertEquals("00:00", formatTime(-5_000))
        assertEquals("01:05", formatTime(65_000))
        assertEquals("1:01:01", formatTime(3_661_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun epgProgressWithinAndAcrossMidnight() {
        val evening = EpgEntry("Tagesschau", "20:00", "20:15")
        assertEquals(0f, epgProgress(evening, nowMinute = 19 * 60 + 59), "vor Beginn")
        assertEquals(0f, epgProgress(evening, nowMinute = 20 * 60), 0.001f)
        assertEquals(0.4f, epgProgress(evening, nowMinute = 20 * 60 + 6), 0.001f)
        assertEquals(0.6f, epgProgress(evening, nowMinute = 20 * 60 + 9), 0.001f)
        assertEquals(1f, epgProgress(evening, nowMinute = 21 * 60), "danach")

        val night = EpgEntry("Film", "23:30", "00:30")
        assertEquals(0.5f, epgProgress(night, nowMinute = 0), 0.001f)
        assertEquals(0.25f, epgProgress(night, nowMinute = 23 * 60 + 45), 0.001f)
    }

    @Test
    fun epgProgressIsZeroForUnreadableTimes() {
        assertEquals(0f, epgProgress(EpgEntry("?", "", ""), nowMinute = 0))
        assertEquals(0f, epgProgress(EpgEntry("?", "", "20:00"), nowMinute = 600))
    }

    @Test
    fun minuteOfDayReadsLocalDateTime() {
        assertEquals(23 * 60 + 5, minuteOfDay("2026-11-23 23:05:59"))
        assertEquals(0, minuteOfDay("2026-11-23 00:00:00"))
        assertEquals(0, minuteOfDay("kaputt"))
    }

    @Test
    fun flagsForCountryTagsOnly() {
        assertEquals("🇩🇪", flagOf("DE"))
        assertEquals("🇩🇪", flagOf("de"))
        assertEquals("🇬🇧", flagOf("UK"))
        assertEquals("🇦🇹", flagOf("at"))
        assertNull(flagOf("AR"))
        assertNull(flagOf("4K"))
        assertNull(flagOf("24/7"))
        assertNull(flagOf("VIP"))
        assertNull(flagOf("XX"))
    }

    @Test
    fun initialsOfChannelNames() {
        assertEquals("DE", initialsOf("Das Erste"))
        assertEquals("T", initialsOf("tagesschau24"))
        assertEquals("ZI", initialsOf("ZDF info"))
        assertEquals("3", initialsOf("3sat"))
        assertEquals("ABC", initialsOf("a b c d"))
        assertEquals("TV", initialsOf(""))
        assertEquals("TV", initialsOf(" – ! "))
    }

    @Test
    fun epgMinutesLeftHandlesMidnightAndEndedShows() {
        fun e(start: String, end: String) = EpgEntry("x", start, end)
        assertEquals(64, epgMinutesLeft(e("20:15", "21:45"), nowMinute = 20 * 60 + 41))
        assertEquals(60, epgMinutesLeft(e("23:30", "00:30"), nowMinute = 23 * 60 + 30), "über Mitternacht, vor 0 Uhr")
        assertEquals(20, epgMinutesLeft(e("23:30", "00:30"), nowMinute = 10), "über Mitternacht, nach 0 Uhr")
        assertNull(epgMinutesLeft(e("20:15", "21:45"), nowMinute = 21 * 60 + 50), "schon vorbei")
        assertNull(epgMinutesLeft(e("20:15", ""), nowMinute = 20 * 60 + 30), "ohne Ende")
    }
}
