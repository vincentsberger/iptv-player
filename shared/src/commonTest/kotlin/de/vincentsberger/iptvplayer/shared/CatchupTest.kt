package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.durationMinutes
import de.vincentsberger.iptvplayer.shared.data.timeshiftStart
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class CatchupTest {
    private fun b64(s: String) = Base64.encode(s.encodeToByteArray())

    /** Wie ein echter Anbieter: Archiv-Sender (3 Tage), Programm mit has_archive für Vergangenes. */
    private val data = mapOf(
        "get_live_categories" to """[{"category_id":"1","category_name":"UK | General"}]""",
        "get_live_streams" to """[{"stream_id":7,"name":"UK: BBC ONE","category_id":"1","epg_channel_id":"bbc1","tv_archive":1,"tv_archive_duration":"3"},
            {"stream_id":8,"name":"UK: ITV","category_id":"1","epg_channel_id":"itv","tv_archive":0}]""",
        "get_simple_data_table" to """{"epg_listings":[
            {"title":"${b64("Breakfast")}","start":"2026-11-23 07:00:00","end":"2026-11-23 11:00:00","has_archive":1,"description":"${b64("Nachrichten")}"},
            {"title":"${b64("Match of the Day")}","start":"2026-11-28 23:30:00","end":"2026-11-29 00:30:00","has_archive":0}]}""",
    )

    @Test
    fun timeshiftFormat() {
        assertEquals("2026-11-24:00-10", timeshiftStart("2026-11-24 00:10:00"))
        assertNull(timeshiftStart("kaputt"))
        assertEquals(50, EpgEntry("x", "", "", "2026-11-24 00:10:00", "2026-11-24 01:00:00").durationMinutes())
    }

    @Test
    fun programmeLoadsAndPastShowsCanBePlayed() {
        val app = testAppState(http = SampleData.http(data))
        val (bbc, itv) = app.section(Section.LIVE).items
        assertEquals(3, bbc.archiveDays); assertEquals(0, itv.archiveDays)
        app.requestProgramme(bbc)
        val programme = app.programmes[bbc.url].orEmpty()
        assertEquals(listOf("Breakfast", "Match of the Day"), programme.map { it.title })
        assertTrue(app.canCatchUp(bbc, programme[0]))
        assertFalse(app.canCatchUp(bbc, programme[1]), "Zukünftiges ist nicht im Archiv")
        assertFalse(app.canCatchUp(itv, programme[0]), "Sender ohne Archiv")

        app.playCatchup(bbc, programme[0])
        val played = (app.screen as Screen.Player).items.single()
        assertTrue(played.catchup)
        assertEquals("BBC ONE · Breakfast", played.name)
        assertTrue(played.url.endsWith("/timeshift/user/pass/240/2026-11-23:07-00/7.ts"), played.url)
        app.saveProgress(played, 60_000, 240 * 60_000)
        assertTrue(app.history.none { it.item.catchup }, "kein Verlauf für Archiv-Adressen")
        assertFalse(DownloadRules.canDownload(played))
    }
}
