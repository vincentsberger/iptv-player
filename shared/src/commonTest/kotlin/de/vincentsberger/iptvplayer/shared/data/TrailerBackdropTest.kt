package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.trailerUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hintergrundbild und Trailer: Serien sparsam aus der Liste, Filme aus dem Detailabruf. */
class TrailerBackdropTest {

    private val client = XtreamClient(Account.Xtream("tv.example.com:8080", "user", "pass"), FakeHttp.xtream(emptyMap()))

    @Test
    fun seriesListKeepsFirstBackdropAndTrailer() {
        val items = client.parseItems(
            Section.SERIES,
            """[{"series_id":1,"name":"Eisland","category_id":"20","cover":"https://a/c.jpg",
                "backdrop_path":["https://a/1.jpg","https://a/2.jpg"],"youtube_trailer":"abc123XYZ"},
               {"series_id":2,"name":"Ohne","category_id":"20","backdrop_path":[],"youtube_trailer":""}]""",
        )
        assertEquals("https://a/1.jpg", items[0].backdrop)
        assertEquals("abc123XYZ", items[0].trailer)
        assertNull(items[1].backdrop)
        assertNull(items[1].trailer)
    }

    @Test
    fun vodInfoReadsTrailer() {
        val info = client.parseVodInfo("""{"info":{"name":"Kaltfront","plot":"x","youtube_trailer":"dQw4w9WgXcQ"}}""")!!
        assertEquals("dQw4w9WgXcQ", info.trailer)
        assertEquals("dQw4w9WgXcQ", client.parseVodInfo("""{"info":{"youtube_trailer":"dQw4w9WgXcQ"}}""")?.trailer, "nur Trailer reicht")
    }

    @Test
    fun trailerUrlFromIdOrUrl() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", trailerUrl("dQw4w9WgXcQ"))
        assertEquals("https://youtu.be/abc", trailerUrl(" https://youtu.be/abc "))
        assertNull(trailerUrl(""))
        assertNull(trailerUrl("   "))
        assertNull(trailerUrl(null))
        assertNull(trailerUrl("kein trailer"))
        assertNull(trailerUrl("undefined"), "Platzhalter des Anbieters sind keine YouTube-ID (11 Zeichen)")
        assertEquals("https://www.youtube.com/watch?v=abc", trailerUrl("www.youtube.com/watch?v=abc"))
    }
}
