package de.vincentsberger.iptvplayer.shared.player.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Anzeigetexte des Statistik-Fensters. */
class StatsTextTest {

    @Test
    fun serverShowsNeverCredentials() {
        assertEquals("prov.example:8080", hostAndPort("http://prov.example:8080/live/user/secret/123.ts"))
        // IP statt Domain: der Pre-Commit-Hook hält Zugangsdaten vor einer Domain für eine Mail-Adresse
        assertEquals("10.0.0.5", hostAndPort("https://user:pw@10.0.0.5/movie/u/p/9.mkv?token=x"))
        assertEquals("prov.example:80", hostAndPort("http://prov.example:80/get.php?username=u&password=p"))
        assertEquals("[::1]:8080", hostAndPort("http://[::1]:8080/x.ts"))
        assertEquals("Datei auf dem Gerät", hostAndPort("file:///data/x.mp4"))
        assertEquals("–", hostAndPort("kein url"))
    }

    @Test
    fun streamKinds() {
        assertEquals("HLS (m3u8)", streamKind("http://h.example/live/u/p/1.m3u8"))
        assertEquals("MPEG-TS", streamKind("http://h.example/live/u/p/1.ts"))
        assertEquals("MKV", streamKind("http://h.example/movie/u/p/9.mkv?x=1"))
        assertEquals("–", streamKind("http://h.example/live/u/p/1"))
    }

    @Test
    fun codecNames() {
        listOf("avc1.64001f", "video/avc", "h264").forEach { assertEquals("H.264", codecName(it)) }
        listOf("hvc1", "hevc").forEach { assertEquals("H.265", codecName(it)) }
        listOf("mp4a.40.2", "audio/mp4a-latm").forEach { assertEquals("AAC", codecName(it)) }
        listOf("ac-3", "a52").forEach { assertEquals("AC-3", codecName(it)) }
        assertEquals("E-AC-3", codecName("ec-3"))
        assertEquals("XYZ", codecName("xyz"))
        assertNull(codecName(null))
    }

    @Test
    fun numbersGerman() {
        assertEquals("6,1 Mbit/s", formatBitrate(6_100_000))
        assertEquals("850 kbit/s", formatBitrate(850_000))
        assertEquals("–", formatBitrate(null))
        assertEquals("12,4 s", formatBuffer(12_400))
        assertEquals("–", formatBuffer(null))
    }

    @Test
    fun lines() {
        assertEquals("H.264 · 1920×1080 · 50 fps", videoLine(EngineStats(videoCodec = "h264", width = 1920, height = 1080, fps = 50f)))
        assertEquals("–", videoLine(EngineStats()))
        assertEquals("AAC · Stereo · deu", audioLine(EngineStats(audioCodec = "mp4a.40.2", audioChannels = 2, audioLanguage = "deu")))
        assertEquals("Mono", audioLine(EngineStats(audioChannels = 1)))
        assertEquals("6 Kanäle", audioLine(EngineStats(audioChannels = 6)))
        assertEquals("–", audioLine(EngineStats()))
    }

    @Test
    fun qualityTexts() {
        assertEquals("●●●", qualityDots(ConnectionQuality.GOOD))
        assertEquals("●●○", qualityDots(ConnectionQuality.FAIR))
        assertEquals("●○○", qualityDots(ConnectionQuality.POOR))
        assertEquals("○○○", qualityDots(ConnectionQuality.UNKNOWN))
        assertEquals("gut", qualityLabel(ConnectionQuality.GOOD))
        assertEquals("mittel", qualityLabel(ConnectionQuality.FAIR))
        assertEquals("schlecht", qualityLabel(ConnectionQuality.POOR))
        assertEquals("–", qualityLabel(ConnectionQuality.UNKNOWN))
    }

    private fun stats(reconnects: Int, stalled: Int, dropped: Long?) = PlaybackStats(
        ConnectionQuality.GOOD, emptyList(), reconnects, stalled, dropped, EngineStats(), "HLS (m3u8)", "1/1", "prov.example:8080",
    )

    @Test
    fun failuresLineShowsAllCounters() {
        assertEquals("2 Neuverbindungen · 3 s gestockt · 14 verworfene Bilder", failuresLine(stats(2, 3, 14)))
    }

    @Test
    fun failuresLineUsesDashForUnknownDroppedAndSingularForms() {
        assertEquals("0 Neuverbindungen · 0 s gestockt · – verworfene Bilder", failuresLine(stats(0, 0, null)))
        assertEquals("1 Neuverbindung · 1 s gestockt · 1 verworfenes Bild", failuresLine(stats(1, 1, 1)))
    }
}
