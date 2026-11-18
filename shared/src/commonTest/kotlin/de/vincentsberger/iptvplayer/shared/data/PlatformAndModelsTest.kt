package de.vincentsberger.iptvplayer.shared.data

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

    @Test
    fun onlyStreamSchemesAreAllowed() {
        listOf("http://a/1.ts", "HTTPS://a/1.m3u8", "rtmp://a/x", "rtsp://a/x").forEach { assertTrue(isAllowedStreamUrl(it), it) }
        listOf("file:///etc/passwd", "smb://nas/x", "javascript:alert(1)", "a/b.ts", "").forEach { assertFalse(isAllowedStreamUrl(it), it) }
    }

    @Test
    fun pathSegmentsAndExtensionsAreRestrictedToSafeCharacters() {
        assertEquals("12etc", safePathSegment("12/../etc"))
        assertEquals("ab_c-1", safePathSegment("ab_c-1"))
        assertEquals("", safePathSegment("ü/."))
        assertEquals("mkv", safeExtension("mkv", "mp4"))
        assertEquals("mp4", safeExtension("mp4?x=1", "mp4"))
        assertEquals("mp4", safeExtension("", "mp4"))
        assertEquals("mp4", safeExtension("toolong", "mp4"))
    }

    @Test
    fun insecureServerHintOnlyForHttp() {
        assertTrue(insecureServerHint(" HTTP://srv")!!.contains("unverschlüsselt"))
        assertEquals(null, insecureServerHint("https://srv"))
        assertEquals(null, insecureServerHint("srv:8080"), "ohne Schema wird https zuerst probiert")
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

    @Test
    fun providerTagIsSplitOffForDisplay() {
        assertEquals(TaggedName("DE", "Nachrichten & Info"), splitProviderTag("DE | NACHRICHTEN & INFO"))
        assertEquals(TaggedName("DE", "Sport Arena 1 FHD"), splitProviderTag("DE: Sport Arena 1 FHD"))
        assertEquals(TaggedName("AT", "Alpen TV"), splitProviderTag("|AT| Alpen TV"))
        // Pfeile und Gedankenstriche, wie manche Anbieter sie nutzen
        assertEquals(TaggedName("DE", "2026 Filme"), splitProviderTag("DE ➥ 2026 Filme"))
        assertEquals(TaggedName("DE", "Doku Filme ► 24/7"), splitProviderTag("DE ➥ Doku Filme ► 24/7"))
        assertEquals(TaggedName("UK", "Drama"), splitProviderTag("UK ► Drama"))
        assertEquals(TaggedName("FR", "Cinéma"), splitProviderTag("FR - Cinéma"))
        assertEquals(TaggedName("PL", "Seriale"), splitProviderTag("PL – Seriale"))
        assertEquals(TaggedName(null, "ZDF-info"), splitProviderTag("ZDF-info"))
        assertEquals(TaggedName("4K", "Natur & Tier UHD"), splitProviderTag("4K: Natur & Tier UHD"))
        assertEquals(TaggedName("24/7", "Hafenwache – alle Staffeln"), splitProviderTag("24/7 | Hafenwache – alle Staffeln"))
    }

    @Test
    fun namesWithoutProviderTagStayUnchanged() {
        assertEquals(TaggedName(null, "Das Erste"), splitProviderTag("Das Erste"))
        assertEquals(TaggedName(null, "Nachrichten"), splitProviderTag(" Nachrichten "))
        // Kein Etikett: Doppelpunkt mitten im Titel oder kleingeschriebene Wörter
        assertEquals(TaggedName(null, "Folge 1: Der Zug"), splitProviderTag("Folge 1: Der Zug"))
        assertEquals(TaggedName(null, "Mission: Impossible"), splitProviderTag("Mission: Impossible"))
    }

    @Test
    fun allCapsNamesAreSoftenedButAcronymsStay() {
        assertEquals("Ultra HD Sender (nur mit Schneller Leitung)", softenCaps("ULTRA HD SENDER (NUR MIT SCHNELLER LEITUNG)"))
        assertEquals("Sport ▸ Fussball & Live-Events", softenCaps("SPORT ▸ FUSSBALL & LIVE-EVENTS"))
        assertEquals("Kinder- & Familienfilme", softenCaps("KINDER- & FAMILIENFILME"))
        assertEquals("Nachrichten & Info ᴴᴰ", softenCaps("NACHRICHTEN & INFO ᴴᴰ"), "Hochgestelltes zählt nicht als Kleinschreibung")
        // Adjektive lassen sich ohne Wörterbuch nicht von Nomen unterscheiden („Schneller“) – für Labels vertretbar.
        // Gemischte Schreibweise und kurze Kürzel bleiben, wie sie sind
        assertEquals("Sport Arena 1 FHD", softenCaps("Sport Arena 1 FHD"))
        assertEquals("ZDF", softenCaps("ZDF"))
    }

    @Test
    fun stableKeysAreUniqueEvenForDuplicateStreams() {
        val a = Item("1", "A", null, "1", "http://x/1.ts", Section.LIVE)
        val b = Item("2", "B", null, "1", "http://x/2.ts", Section.LIVE)
        assertEquals(listOf("http://x/1.ts", "http://x/2.ts", "http://x/1.ts#2"), stableKeys(listOf(a, b, a)))
    }

    @Test
    fun itemSurvivesJsonRoundTripAndBrokenDataIsRejected() {
        val ep = Item("100", "Folge 1 – „Anfang“", null, "11", "http://srv/series/u/p/100.mp4", Section.SERIES)
        assertEquals(ep, Item.fromJson(ep.toJson()))
        assertEquals(null, Item.fromJson("kein json"))
        assertEquals(null, Item.fromJson("""{"id":"1","name":"x","url":"http://a","section":"RADIO"}"""))
        assertEquals(null, Item.fromJson("""{"id":"1","name":"ohne url","url":"","section":"MOVIES"}"""))
    }

}
