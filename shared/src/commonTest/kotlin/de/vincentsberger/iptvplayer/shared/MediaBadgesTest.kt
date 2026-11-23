package de.vincentsberger.iptvplayer.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/** Info-Abzeichen im Player: Auflösung, Tonkanäle, Sprache, Untertitel. */
class MediaBadgesTest {

    private fun b(h: Int? = null, ch: Int? = null, lang: String? = null, ut: Boolean = false) = mediaBadges(h, ch, lang, ut)

    @Test
    fun resolutionThresholds() {
        assertEquals(listOf("SD"), b(h = 576))
        assertEquals(listOf("HD"), b(h = 720))
        assertEquals(listOf("FHD"), b(h = 1080))
        assertEquals(listOf("FHD"), b(h = 1088), "Encoder-Padding")
        assertEquals(listOf("4K"), b(h = 2160))
    }

    @Test
    fun channelLayouts() {
        assertEquals(listOf("Mono"), b(ch = 1))
        assertEquals(listOf("2.0"), b(ch = 2))
        assertEquals(listOf("5.1"), b(ch = 6))
        assertEquals(listOf("7.1"), b(ch = 8))
        assertEquals(emptyList(), b(ch = 4))
    }

    @Test
    fun languageCodes() {
        assertEquals(listOf("DE"), b(lang = "de"))
        assertEquals(listOf("DE"), b(lang = "deu"))
        assertEquals(listOf("DE"), b(lang = "ger"))
        assertEquals(listOf("EN"), b(lang = "eng"))
        assertEquals(emptyList(), b(lang = "und"))
        assertEquals(emptyList(), b(lang = ""))
    }

    @Test
    fun subtitles() {
        assertEquals(listOf("UT"), b(ut = true))
    }

    @Test
    fun orderAndMissingValues() {
        assertEquals(listOf("FHD", "5.1", "DE", "UT"), b(1080, 6, "deu", true))
        assertEquals(emptyList(), b(h = 0, ch = 0, lang = null), "Radio / noch nichts bekannt")
    }
}
