package de.tvplayer.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformAndModelsTest {

    // ---------- urlEncode / hostOf ----------

    @Test
    fun urlEncodeKeepsUnreservedCharacters() {
        assertEquals("Abc-123_.~", urlEncode("Abc-123_.~"))
    }

    @Test
    fun urlEncodeEscapesReservedCharactersAndUtf8() {
        assertEquals("a%20b%26c%3D%2F", urlEncode("a b&c=/"))
        assertEquals("M%C3%BCller", urlEncode("Müller"))
    }

    @Test
    fun hostOfStripsSchemePortPathAndQuery() {
        assertEquals("tv.example.com", hostOf("http://tv.example.com:8080/get.php?user=x"))
        assertEquals("tv.example.com", hostOf("tv.example.com/playlist.m3u"))
        assertEquals("tv.example.com", hostOf("https://tv.example.com?x=1"))
    }

    // ---------- Item ----------

    private fun item(section: Section, url: String = "http://srv/1", id: String = "1") =
        Item(id, "Name", null, "cat", url, section)

    @Test
    fun seriesWithoutUrlIsAFolderAndUsesIdAsFavoriteKey() {
        val folder = item(Section.SERIES, url = "", id = "42")
        assertTrue(folder.isSeriesFolder)
        assertEquals("SERIES:42", folder.favKey)
    }

    @Test
    fun playableItemUsesUrlAsFavoriteKey() {
        val episode = item(Section.SERIES, url = "http://srv/series/9.mp4")
        assertFalse(episode.isSeriesFolder)
        assertEquals("SERIES:http://srv/series/9.mp4", episode.favKey)
    }

    // ---------- HistoryEntry ----------

    private fun history(section: Section, pos: Long, dur: Long) = HistoryEntry(item(section), pos, dur, 0)

    @Test
    fun progressIsZeroWithoutDurationAndClampedToOne() {
        assertEquals(0f, history(Section.MOVIES, 5_000, 0).progress)
        assertEquals(0.5f, history(Section.MOVIES, 50_000, 100_000).progress)
        assertEquals(1f, history(Section.MOVIES, 200_000, 100_000).progress)
    }

    @Test
    fun canResumeOnlyBetweenThirtySecondsAndNinetyFivePercent() {
        val dur = 1_000_000L
        assertFalse(history(Section.MOVIES, 30_000, dur).canResume, "zu früh")
        assertTrue(history(Section.MOVIES, 30_001, dur).canResume)
        assertTrue(history(Section.MOVIES, 940_000, dur).canResume)
        assertFalse(history(Section.MOVIES, 950_000, dur).canResume, "praktisch zu Ende")
        assertFalse(history(Section.LIVE, 500_000, dur).canResume, "Live kann man nicht fortsetzen")
    }
}
