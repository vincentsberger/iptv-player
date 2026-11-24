package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.cleaned
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpgExcerptTest {
    private fun e(description: String, title: String = "Tagesschau") = EpgEntry(title, "20:00", "20:15", description = description)

    @Test
    fun shortDescriptionsStayWhole() {
        assertEquals("Nachrichten des Tages.", epgExcerpt(e("Nachrichten\n des   Tages.")))
        assertNull(epgExcerpt(e("")))
        assertNull(epgExcerpt(e("tagesschau")), "nur der Titel wiederholt")
        assertNull(epgExcerpt(null))
    }

    @Test
    fun fullDescriptionOnlyWhenItSaysMoreThanTheTitle() {
        assertEquals("Nachrichten des Tages.", epgDescription(e("  Nachrichten des Tages.  ")))
        assertNull(epgDescription(e("Tagesschau")))
        assertNull(epgDescription(e(" ")))
    }

    @Test
    fun longDescriptionsAreCutAtAWord() {
        val text = "Die Sendung zeigt " + "sehr ".repeat(40) + "viel."
        val cut = epgExcerpt(e(text), max = 40)!!
        assertEquals("Die Sendung zeigt sehr sehr sehr sehr …", cut)
    }

    @Test
    fun duplicateListingsAreDroppedSoNextIsReallyNext() {
        val now = EpgEntry("Tagesschau", "20:00", "20:15", "2026-11-23 20:00:00", "2026-11-23 20:15:00")
        val next = EpgEntry("Wetter", "20:15", "20:30", "2026-11-23 20:15:00", "2026-11-23 20:30:00")
        assertEquals(listOf(now, next), listOf(now, now.copy(description = "zweite Quelle"), next).cleaned())
        assertEquals(listOf(now, next), listOf(next, now).cleaned(), "nach Startzeit sortiert")
        val noTimes = listOf(EpgEntry("A", "20:00", "20:15"), EpgEntry("A", "20:00", "20:15"), EpgEntry("B", "20:15", "20:30"))
        assertEquals(listOf("A", "B"), noTimes.cleaned().map { it.title })
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun xtreamDescriptionIsDecoded() = runTest {
        fun b64(s: String) = Base64.encode(s.encodeToByteArray())
        val epg = """{"epg_listings":[{"title":"${b64("Tagesschau")}","description":"${b64("Nachrichten des Tages")}",""" +
            """"start":"2026-11-23 20:00:00","end":"2026-11-23 20:15:00"}]}"""
        val app = testAppState(http = SampleData.http(mapOf("get_short_epg" to epg)))
        val channel = app.section(Section.LIVE).items.first()
        app.requestEpg(channel)
        assertEquals("Nachrichten des Tages", app.nowFor(channel)?.description)
    }
}
