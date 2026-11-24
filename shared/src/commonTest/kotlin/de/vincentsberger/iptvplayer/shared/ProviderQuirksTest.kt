package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.isSeparatorEntry
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Eigenheiten echter Anbieter-Listen: Überschriften als „Sender“, Sender ohne EPG-Kennung, vierstellige Kürzel. */
class ProviderQuirksTest {
    private val live = mapOf(
        "get_live_categories" to """[{"category_id":"1","category_name":"UK | GENERAL"}]""",
        "get_live_streams" to """[{"stream_id":1,"name":"##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ #####","category_id":"1"},
            {"stream_id":2,"name":"UK: BBC ONE","category_id":"1","epg_channel_id":"bbc1.uk"},
            {"stream_id":3,"name":"UK: HGTV ◉","category_id":"1","epg_channel_id":""}]""",
    )

    @Test
    fun separatorRowsAreNotChannels() {
        assertTrue(isSeparatorEntry("##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ #####"))
        assertTrue(isSeparatorEntry("=== SPORT ==="))
        assertFalse(isSeparatorEntry("UK: BBC ONE"))
        assertFalse(isSeparatorEntry("#1 Hits"))
        val app = testAppState(http = SampleData.http(live))
        assertEquals(listOf("UK: BBC ONE", "UK: HGTV ◉"), app.section(Section.LIVE).items.map { it.name })
    }

    @Test
    fun noEpgRequestForChannelsWithoutEpgId() {
        val http = SampleData.http(live + mapOf("get_short_epg" to """{"epg_listings":[]}"""))
        val app = testAppState(http = http)
        val (bbc, hgtv) = app.section(Section.LIVE).items
        assertTrue(bbc.hasEpg); assertFalse(hgtv.hasEpg)
        app.requestEpg(hgtv)
        assertTrue(http.requests.none { "get_short_epg" in it && "stream_id=3" in it })
        app.requestEpg(bbc)
        assertTrue(http.requests.any { "get_short_epg" in it && "stream_id=2" in it })
    }

    @Test
    fun exyuIsATag() {
        assertEquals("EXYU", splitProviderTag("EXYU| BALKAN SKY 24/7").tag)
    }
}
