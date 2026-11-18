package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {

    private val release = """{"tag_name":"v1.4.0","html_url":"https://example.org/releases/tag/v1.4.0","assets":[
        {"name":"freeiptvplayer_1.4.0-1_amd64.deb","browser_download_url":"https://example.org/a.deb"},
        {"name":"IPTV-Player-1.4.0.apk","browser_download_url":"https://example.org/a.apk"}]}"""

    @Test
    fun releaseIsParsedWithDownloadsPerPlatform() {
        assertEquals(
            LatestRelease("1.4.0", "https://example.org/releases/tag/v1.4.0", "https://example.org/a.apk", "https://example.org/a.deb"),
            Updates.parse(release),
        )
        assertNull(Updates.parse("""{"message":"Not Found"}"""))
        assertNull(Updates.parse("kein json"))
    }

    @Test
    fun versionsAreComparedNumerically() {
        assertTrue(Updates.isNewer("1.4.0", "1.3.1"))
        assertTrue(Updates.isNewer("1.10.0", "1.9.2"))
        assertTrue(Updates.isNewer("v2.0", "1.99.99"))
        assertFalse(Updates.isNewer("1.3.1", "1.3.1"))
        assertFalse(Updates.isNewer("1.3.0", "1.3.1"))
        assertFalse(Updates.isNewer("1.4.0-beta", "1.4.0"))
    }

    @Test
    fun checkRunsAtMostDailyUnlessForcedAndCanBeSwitchedOff() = runTest {
        var now = 1_000L
        val http = FakeHttp { release }
        val app = testAppState(kv = InMemoryKeyValueStore(), now = { now })

        assertEquals("1.4.0", app.checkForUpdate("1.3.1", http = http)?.version)
        assertEquals(1, http.requests.size)

        now += 60_000
        app.checkForUpdate("1.3.1", http = http)
        assertEquals(1, http.requests.size, "innerhalb eines Tages nicht erneut")

        app.checkForUpdate("1.3.1", force = true, http = http)
        assertEquals(2, http.requests.size, "„Jetzt prüfen“ fragt immer")

        app.updateCheck = false
        now += Updates.CHECK_INTERVAL_MS
        app.checkForUpdate("1.3.1", http = http)
        assertEquals(2, http.requests.size, "ausgeschaltet: keine Anfrage")

        assertNull(app.checkForUpdate("1.4.0", force = true, http = http), "schon aktuell")
    }

    @Test
    fun networkErrorsMeanNoUpdate() = runTest {
        assertNull(Updates.newerThan("1.0.0", FakeHttp { error("offline") }))
    }
}
