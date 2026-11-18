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

    @Test
    fun cacheExpiresAfterTenMinutes() {
        var now = 0L
        val http = SampleData.http(mapOf("get_short_epg" to epgJson))
        val app = testAppState(http = http, now = { now })
        val ch = app.section(Section.LIVE).items.first()
        app.requestEpg(ch)
        now = AppState.EPG_TTL_MS + 1
        app.requestEpg(ch)
        assertEquals(2, http.requests.count { "get_short_epg" in it })
    }
}
