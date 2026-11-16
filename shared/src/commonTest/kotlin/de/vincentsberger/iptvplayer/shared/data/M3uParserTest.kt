package de.vincentsberger.iptvplayer.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M3uParserTest {

    private fun parse(text: String) = M3uParser.parse(text.trimIndent().lineSequence())

    @Test
    fun readsNameLogoAndGroupFromExtinf() {
        val r = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="ard.de" tvg-logo="http://logo/ard.png" group-title="Nachrichten",Das Erste HD
            http://srv/live/1.ts
            """
        )
        val item = r.items.single()
        assertEquals("Das Erste HD", item.name)
        assertEquals("http://logo/ard.png", item.logo)
        assertEquals("LIVE:Nachrichten", item.categoryId)
        assertEquals("http://srv/live/1.ts", item.url)
        assertEquals(listOf(Category("LIVE:Nachrichten", "Nachrichten")), r.categories)
    }

    @Test
    fun commaInsideQuotedAttributeDoesNotEndTheTitle() {
        val r = parse(
            """
            #EXTINF:-1 group-title="Filme, Drama",Der Pate
            http://srv/movie/7.mkv
            """
        )
        assertEquals("Der Pate", r.items.single().name)
        assertEquals("Filme, Drama", r.categories.single().name)
    }

    @Test
    fun nameFallsBackToTvgNameThenToFileName() {
        val r = parse(
            """
            #EXTINF:-1 tvg-name="ZDF",
            http://srv/live/2.ts
            http://srv/live/3.ts
            """
        )
        assertEquals(listOf("ZDF", "3.ts"), r.items.map { it.name })
    }

    @Test
    fun attributeNamesAreCaseInsensitive() {
        val r = parse(
            """
            #EXTINF:-1 TVG-LOGO="http://logo/x.png" GROUP-TITLE="Sport",Sport 1
            http://srv/live/4.ts
            """
        )
        assertEquals("http://logo/x.png", r.items.single().logo)
        assertEquals("Sport", r.categories.single().name)
    }

    @Test
    fun extgrpSetsGroupAndMissingGroupBecomesAllgemein() {
        val r = parse(
            """
            #EXTINF:-1,Kanal A
            #EXTGRP:Musik
            http://srv/live/a.ts
            #EXTINF:-1,Kanal B
            http://srv/live/b.ts
            """
        )
        assertEquals(listOf("Musik", "Allgemein"), r.categories.map { it.name })
    }

    @Test
    fun attributesDoNotLeakIntoTheNextEntry() {
        val r = parse(
            """
            #EXTINF:-1 tvg-logo="http://logo/a.png" group-title="G",A
            http://srv/live/a.ts
            http://srv/live/b.ts
            """
        )
        val second = r.items[1]
        assertNull(second.logo)
        assertEquals("LIVE:Allgemein", second.categoryId)
    }

    @Test
    fun detectsSectionFromUrl() {
        val r = parse(
            """
            http://srv/series/u/p/1.mp4
            http://srv/movie/u/p/2.ts
            http://srv/files/film.MKV
            http://srv/files/film.avi
            http://srv/live/u/p/3.m3u8
            """
        )
        assertEquals(
            listOf(Section.SERIES, Section.MOVIES, Section.MOVIES, Section.MOVIES, Section.LIVE),
            r.items.map { it.section },
        )
    }

    @Test
    fun sameGroupInDifferentSectionsGivesSeparateCategories() {
        val r = parse(
            """
            #EXTINF:-1 group-title="Neu",Kanal
            http://srv/live/1.ts
            #EXTINF:-1 group-title="Neu",Film
            http://srv/movie/2.mp4
            #EXTINF:-1 group-title="Neu",Noch ein Kanal
            http://srv/live/3.ts
            """
        )
        assertEquals(listOf("LIVE:Neu", "MOVIES:Neu"), r.categories.map { it.id })
    }

    @Test
    fun ignoresCommentsAndBlankLinesAndNumbersItems() {
        val r = parse(
            """
            #EXTM3U x-tvg-url="http://epg"

            # irgendein Kommentar
            #EXTVLCOPT:http-user-agent=foo
            http://srv/live/1.ts

            http://srv/live/2.ts
            """
        )
        assertEquals(listOf("m3u-0", "m3u-1"), r.items.map { it.id })
    }

    @Test
    fun entriesWithLocalOrUnknownSchemesAreDropped() {
        val r = parse(
            """
            #EXTINF:-1 tvg-logo="http://logo/x.png" group-title="Böse",Lokale Datei
            file:///etc/passwd
            #EXTINF:-1,Freigabe
            smb://nas/filme/x.mkv
            relativ/pfad.ts
            #EXTINF:-1,Echt
            HTTPS://srv/live/1.m3u8
            rtmp://srv/live/2
            """
        )
        assertEquals(listOf("Echt", "2"), r.items.map { it.name })
        assertEquals(listOf("Allgemein"), r.categories.map { it.name }, "Gruppe des verworfenen Eintrags bleibt nicht hängen")
    }

    @Test
    fun emptyInputGivesEmptyResult() {
        val r = parse("#EXTM3U")
        assertEquals(0, r.items.size)
        assertEquals(0, r.categories.size)
    }
}
