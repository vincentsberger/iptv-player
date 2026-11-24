package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.cleaned
import de.vincentsberger.iptvplayer.shared.data.epgSeconds
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

    private fun at(title: String, start: String, end: String, d: String = "") =
        EpgEntry(title, start.takeLast(8).take(5), end.takeLast(8).take(5), start, end, description = d)

    /** Wie ein echter Anbieter: zwei Quellen, jede Folge doppelt mit um Sekunden verschobener Startzeit. */
    @Test
    fun shiftedDuplicatesFromASecondSourceAreDropped() {
        val a1 = at("Law & Order", "2026-11-23 23:58:13", "2026-11-24 00:44:27", "Harper sagt aus")
        val b1 = at("Law & Order", "2026-11-23 23:58:48", "2026-11-24 00:45:24", "Harper sagt aus")
        val a2 = at("Law & Order", "2026-11-24 00:44:27", "2026-11-24 01:30:21", "Seth wird erschlagen")
        val b2 = at("Law & Order", "2026-11-24 00:45:24", "2026-11-24 01:31:13", "Seth wird erschlagen")
        assertEquals(listOf(a1, a2), listOf(a1, b1, a2, b2).cleaned())
    }

    /** Zwei Quellen mit verschiedenen, überlappenden Programmen: die erste Kette bleibt. */
    @Test
    fun overlappingProgrammesKeepOneChain() {
        val hunt = at("The Hunt", "2026-11-24 00:05:00", "2026-11-24 01:35:00")
        val mandy = at("Mandy", "2026-11-24 00:30:00", "2026-11-24 02:35:00")
        val kandahar = at("Kandahar", "2026-11-24 01:35:00", "2026-11-24 03:40:00")
        val pa3 = at("Paranormal Activity 3", "2026-11-24 02:35:00", "2026-11-24 04:00:00")
        assertEquals(listOf("The Hunt", "Kandahar"), listOf(hunt, mandy, kandahar, pa3).cleaned().map { it.title })
    }

    @Test
    fun smallOverlapsAndUnsortedInputAreFine() {
        val now = at("Tagesschau", "2026-11-23 20:00:00", "2026-11-23 20:16:00")
        val next = at("Wetter", "2026-11-23 20:15:00", "2026-11-23 20:30:00") // 1 min Überlappung
        assertEquals(listOf(now, next), listOf(next, now).cleaned())
        val noTimes = listOf(EpgEntry("A", "20:00", "20:15"), EpgEntry("A", "20:00", "20:15"), EpgEntry("B", "20:15", "20:30"))
        assertEquals(listOf("A", "B"), noTimes.cleaned().map { it.title })
        assertEquals(epgSeconds("2026-11-24 00:00:00")!! - 86400, epgSeconds("2026-11-23 00:00:00"))
        assertEquals(0L, epgSeconds("1970-01-01 00:00:00"))
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
