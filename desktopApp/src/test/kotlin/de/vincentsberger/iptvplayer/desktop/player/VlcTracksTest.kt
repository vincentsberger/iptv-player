package de.vincentsberger.iptvplayer.desktop.player

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spurauswahl gegen echtes VLC mit dem Demo-Film (zwei Tonspuren, zwei Untertitel).
 * Läuft nur, wenn VLC installiert und tools/demo-server/vod.mp4 erzeugt ist (make_videos.sh) – sonst übersprungen.
 */
class VlcTracksTest {
    private val video = File("../tools/demo-server/vod.mp4")

    @Test
    fun tracksOfTheDemoFilmCanBeListedAndSwitched() {
        assumeTrue("VLC fehlt", VlcSetup.available)
        assumeTrue("Demo-Film fehlt", video.exists())
        val player = VlcPlayer()
        try {
            player.preferredAudio = "eng"
            player.preferredSubtitles = "deu"
            player.play(video.absolutePath)
            val deadline = System.currentTimeMillis() + 15_000
            while (player.audioTracks().size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(200)
            player.applyPreferencesIfPending()
            Thread.sleep(500)

            // Nur die Sprache vergleichen: Kanalzahl („· Mono“) meldet VLC je nach Zeitpunkt erst später.
            fun List<VlcTrack>.languages() = map { it.label.substringBefore(" · ") }
            assertEquals(listOf("Deutsch", "Englisch"), player.audioTracks().languages())
            assertEquals(listOf("Deutsch", "Englisch"), player.subtitleTracks().languages())
            assertEquals(listOf("Englisch"), player.audioTracks().filter { it.selected }.languages(), "Vorliebe beim Start")
            assertEquals("Deutsch", player.subtitleTracks().single { it.selected }.label.substringBefore(" · "))
            assertTrue(sameLanguage("de", "ger") && sameLanguage("deu", "DE") && !sameLanguage("de", "en"))

            player.selectAudio(player.audioTracks().first())
            player.selectSubtitles(null)
            Thread.sleep(500)
            assertEquals(listOf("Deutsch"), player.audioTracks().filter { it.selected }.languages())
            assertTrue(player.subtitleTracks().none { it.selected })
        } finally {
            player.release()
        }
    }
}
