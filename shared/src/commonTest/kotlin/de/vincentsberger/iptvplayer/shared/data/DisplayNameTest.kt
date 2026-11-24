package de.vincentsberger.iptvplayer.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Namen, wie ein echter Anbieter sie liefert (Auswertung November 2026). */
class DisplayNameTest {
    @Test
    fun superscriptAndTrailingQualityBecomeBadges() {
        val raw = splitProviderTag("UK: SKY MIX DOCS 9 ᴿᴬᵂ")
        assertEquals("UK", raw.tag); assertEquals("SKY MIX Docs 9", raw.name, "kurze Wörter bleiben groß (softenCaps)"); assertEquals(listOf("RAW"), raw.badges)
        val twice = splitProviderTag("VIP: TNT SPORT 2 ᴿᴬᵂ ᴿᴬᵂ")
        assertEquals("TNT Sport 2", twice.name); assertEquals(listOf("RAW"), twice.badges)
        assertEquals("BBC Two", splitProviderTag("UK: BBC Two ◉").name)
        assertEquals(listOf("4K", "60 FPS", "RAW"), splitProviderTag("DE: Prime ᴿᴬᵂ ⁶⁰ᶠᵖˢ ᵁᴴᴰ").badges)
        assertEquals(listOf("4K", "HEVC"), splitProviderTag("DE: Kino HEVC 4K").badges)
    }

    @Test
    fun yearAndOriginLeaveMovieAndSeriesTitles() {
        val movie = splitProviderTag("EN - King Arthur: Legend of the Sword (2017)")
        assertEquals("King Arthur: Legend of the Sword", movie.name); assertEquals("2017", movie.year)
        val series = splitProviderTag("NF - Angel Eyes (2014) (KR)")
        assertEquals("NF", series.tag); assertEquals("Angel Eyes", series.name); assertEquals("2014", series.year)
        val bracket = splitProviderTag("Unter dem Eis (2024) [4K]")
        assertEquals("Unter dem Eis", bracket.name); assertEquals(listOf("4K"), bracket.badges)
        assertNull(splitProviderTag("Tatort").year)
    }

    @Test
    fun namesThatAreOnlyQualityOrContainItInTheMiddleStay() {
        assertEquals("Ultra HD Sender", splitProviderTag("ULTRA HD SENDER").name)
        assertEquals("HD", splitProviderTag("HD").name)
        assertEquals("[REC] 3 Génesis", splitProviderTag("[REC] 3 Génesis").name, "unbekannte Klammer bleibt")
    }

    @Test
    fun categoriesKeepTheirBadgesSoTheyStayDistinguishable() {
        assertEquals("Netflix Movies 4K Dolby Vision", splitCategoryName("NETFLIX MOVIES ⁴ᴷ ³⁸⁴⁰ᴾ ᴰᵒˡᵇʸ ⱽᶦˢᶦᵒⁿ").name)
        assertEquals("Netflix Movies", splitCategoryName("NETFLIX MOVIES").name)
    }
}
