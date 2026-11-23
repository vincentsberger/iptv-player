package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracksTest {
    @Test
    fun languageNamesForTwoAndThreeLetterCodes() {
        assertEquals("Deutsch", languageName("de"))
        assertEquals("Deutsch", languageName("deu"))
        assertEquals("Deutsch", languageName("GER"))
        assertEquals("Englisch", languageName("eng"))
        assertEquals("Türkisch", languageName("tur"))
        assertNull(languageName("und"))
        assertNull(languageName(" "))
        assertEquals("XYZ", languageName("xyz"), "Unbekanntes in Großbuchstaben")
    }

    @Test
    fun sameLanguageIgnoresCodeVariants() {
        assertTrue(sameLanguage("de", "ger") && sameLanguage("deu", "DE") && sameLanguage("fre", "fra"))
        assertFalse(sameLanguage("de", "en"))
        assertFalse(sameLanguage(null, "de"))
    }

    @Test
    fun labelsCombineDescriptionLanguageAndChannels() {
        assertEquals("Englisch · 5.1", trackLabel("eng", null, 6, 0))
        assertEquals("Kommentar · Englisch", trackLabel("en", "Kommentar", 2, 0))
        assertEquals("Deutsch", trackLabel("deu", "Deutsch", 0, 0), "Beschreibung = Sprache nicht doppelt")
        assertEquals("Spur 3", trackLabel(null, " ", 0, 2))
    }

    private val tracks = listOf(
        MediaTrack(3, "Deutsch", "deu", selected = true),
        MediaTrack(7, "Englisch", "eng", selected = false),
    )

    @Test
    fun preferredAudioMatchesAnyCodeVariant() {
        assertEquals(7, preferredAudioTrack(tracks, "en")?.id)
        assertNull(preferredAudioTrack(tracks, "fr"))
        assertNull(preferredAudioTrack(tracks, null))
    }

    @Test
    fun preferredSubtitlesKeepOffOrTrack() {
        assertEquals(SubtitleChoice.Keep, preferredSubtitles(tracks, null))
        assertEquals(SubtitleChoice.Off, preferredSubtitles(tracks, Storage.SUBTITLES_OFF))
        assertEquals(SubtitleChoice.Track(3), preferredSubtitles(tracks, "ger"))
        assertEquals(SubtitleChoice.Keep, preferredSubtitles(tracks, "fr"), "Sprache fehlt → Vorgabe des Streams")
    }

    @Test
    fun videoFitFallsBackToFit() {
        assertEquals(VideoFit.ZOOM, VideoFit.of("zoom"))
        assertEquals(VideoFit.FIT, VideoFit.of(null))
        assertEquals(VideoFit.FIT, VideoFit.of("quatsch"))
    }
}
