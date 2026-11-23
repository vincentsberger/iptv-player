package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class ResumeAndEpgTest {

    // ---------- Dort weitermachen, wo man war ----------

    @Test
    fun tabAndLiveCategoryAreRestoredAfterRestart() {
        val kv = InMemoryKeyValueStore()
        testAppState(kv = kv).apply { selectTab(Tab.MOVIES); liveCategory = "2" }
        val again = testAppState(kv = kv)
        assertEquals(Tab.MOVIES, again.tab)
        assertEquals("2", again.liveCategory)
    }

    @Test
    fun searchAndSettingsAreNotRestored() {
        val kv = InMemoryKeyValueStore()
        testAppState(kv = kv).selectTab(Tab.SETTINGS)
        assertEquals(Tab.HOME, testAppState(kv = kv).tab)
    }

    @Test
    fun firstStartOpensTheHomePageWithContinueWatchingAndRecentChannels() {
        val app = testAppState()
        assertEquals(Tab.HOME, app.tab)
        val live = app.section(Section.LIVE).items.first()
        val movie = app.section(Section.MOVIES).items.first()
        app.saveProgress(live, 0, 0)
        app.saveProgress(movie, 60_000, 600_000)
        assertEquals(listOf(movie.name), app.continueWatchingAll.map { it.item.name })
        assertEquals(listOf(live.name), app.recentChannels.map { it.name })
    }

    @Test
    fun vanishedOrHiddenCategoryFallsBackToAllChannels() {
        val kv = InMemoryKeyValueStore()
        testAppState(kv = kv).liveCategory = "999"
        assertEquals(AppState.LIVE_ALL, testAppState(kv = kv).liveCategory)

        testAppState(kv = kv).apply { liveCategory = "2"; toggleHidden(Section.LIVE, "2") }
        assertEquals(AppState.LIVE_ALL, testAppState(kv = kv).liveCategory)
    }

    @Test
    fun browseCategoryIsRememberedPerSection() {
        val kv = InMemoryKeyValueStore()
        testAppState(kv = kv).rememberBrowseCategory(Section.MOVIES, "cat:11")
        val again = testAppState(kv = kv)
        assertEquals("cat:11", again.browseCategory(Section.MOVIES))
        assertNull(again.browseCategory(Section.SERIES))
    }

    // ---------- „Jetzt läuft“ ----------

    private val epgJson = """{"epg_listings":[{"title":"${Base64.encode("Tagesschau".encodeToByteArray())}",""" +
        """"start":"2026-11-16 20:00:00","end":"2026-11-16 20:15:00"}]}"""

    @Test
    fun visibleChannelLoadsItsProgrammeOnceAndCachesIt() {
        val http = SampleData.http(mapOf("get_short_epg" to epgJson))
        val app = testAppState(http = http)
        val ch = app.section(Section.LIVE).items.first()
        assertNull(app.nowFor(ch))

        app.requestEpg(ch)
        app.requestEpg(ch)
        assertEquals("Tagesschau", app.nowFor(ch)?.title)
        assertEquals(1, http.requests.count { "get_short_epg" in it }, "zweite Anfrage kommt aus dem Zwischenspeicher")
    }

    @Test
    fun infoPanelUsesTheCacheToo() = kotlinx.coroutines.test.runTest {
        val http = SampleData.http(mapOf("get_short_epg" to epgJson))
        val app = testAppState(http = http)
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        assertEquals("Tagesschau", app.nowPlaying(ch).first().title)
        assertEquals(1, http.requests.count { "get_short_epg" in it })
    }

    // Echter Fall (BR, 23.11.2026 19:03): Texte in deutscher Zeit, Timestamps um +1 h falsch – der Server liefert
    // deshalb noch die schon beendete Sendung zuerst.
    private fun b64(s: String) = Base64.encode(s.encodeToByteArray())
    private val brJson = """{"epg_listings":[""" +
        """{"title":"${b64("BR24")}","start":"2026-11-23 18:30:00","end":"2026-11-23 19:00:00","start_timestamp":"1763922600","stop_timestamp":"1763924400"},""" +
        """{"title":"${b64("Unkraut")}","start":"2026-11-23 19:00:00","end":"2026-11-23 19:30:00","start_timestamp":"1763924400","stop_timestamp":"1763926200"},""" +
        """{"title":"${b64("Dahoam is Dahoam")}","start":"2026-11-23 19:30:00","end":"2026-11-23 20:00:00","start_timestamp":"1763926200","stop_timestamp":"1763928000"}]}"""

    @Test
    fun programmeThatAlreadyEndedIsNotShownAsNow() = kotlinx.coroutines.test.runTest {
        val app = testAppState(http = SampleData.http(mapOf("get_short_epg" to brJson)), localTime = { "2026-11-23 19:03:00" })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        assertEquals("Unkraut", app.nowFor(ch)?.title)
        assertEquals(listOf("Unkraut", "Dahoam is Dahoam"), app.nowPlaying(ch).map { it.title }, "Jetzt + Danach ohne BR24")
    }

    @Test
    fun cachedProgrammeMovesOnWithTheClock() {
        var local = "2026-11-23 19:25:00"
        val http = SampleData.http(mapOf("get_short_epg" to brJson))
        val app = testAppState(http = http, localTime = { local })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        assertEquals("Unkraut", app.nowFor(ch)?.title)
        local = "2026-11-23 19:31:00" // noch im Zwischenspeicher, aber Unkraut ist vorbei
        assertEquals("Dahoam is Dahoam", app.nowFor(ch)?.title)
        assertEquals(1, http.requests.count { "get_short_epg" in it })
    }

    // ---------- Wann neu geladen wird ----------

    private fun requests(http: de.vincentsberger.iptvplayer.shared.testing.FakeHttp) = http.requests.count { "get_short_epg" in it }

    @Test
    fun enoughProgrammeLeftMeansNoReload() {
        var now = 0L
        val http = SampleData.http(mapOf("get_short_epg" to brJson))
        val app = testAppState(http = http, now = { now }, localTime = { "2026-11-23 19:03:00" })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_MAX_AGE_MS - 1 // Jetzt + Danach noch da: kein neuer Abruf, auch nach fast 3 Stunden
        app.requestEpg(ch)
        assertEquals(1, requests(http))
    }

    @Test
    fun reloadsWhenOnlyNowIsLeft() {
        var now = 0L
        var local = "2026-11-23 19:03:00"
        val http = SampleData.http(mapOf("get_short_epg" to brJson))
        val app = testAppState(http = http, now = { now }, localTime = { local })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_MIN_REFRESH_MS + 1
        local = "2026-11-23 19:31:00" // nur noch „Dahoam is Dahoam“ übrig – kein „Danach“ mehr
        app.requestEpg(ch)
        assertEquals(2, requests(http))
    }

    @Test
    fun neverReloadsMoreOftenThanTheMinimumInterval() {
        var now = 0L
        var local = "2026-11-23 19:03:00"
        val http = SampleData.http(mapOf("get_short_epg" to brJson))
        val app = testAppState(http = http, now = { now }, localTime = { local })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_MIN_REFRESH_MS - 1
        local = "2026-11-23 21:00:00" // alles vorbei – trotzdem nicht sofort wieder fragen (Anbieter liefert evtl. dasselbe)
        app.requestEpg(ch)
        assertEquals(1, requests(http))
    }

    @Test
    fun reloadsAfterThreeHoursAtTheLatest() {
        var now = 0L
        val http = SampleData.http(mapOf("get_short_epg" to brJson))
        val app = testAppState(http = http, now = { now }, localTime = { "2026-11-23 19:03:00" })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_MAX_AGE_MS // Programmänderungen (z. B. Verlängerung) sollen ankommen
        app.requestEpg(ch)
        assertEquals(2, requests(http))
    }

    @Test
    fun channelWithoutProgrammeIsAskedOnlyEveryThreeHours() {
        var now = 0L
        val http = SampleData.http(mapOf("get_short_epg" to """{"epg_listings":[]}"""))
        val app = testAppState(http = http, now = { now })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_MAX_AGE_MS - 1
        app.requestEpg(ch)
        assertEquals(1, requests(http))
        now = AppState.EPG_MAX_AGE_MS
        app.requestEpg(ch)
        assertEquals(2, requests(http))
    }
}
