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

    /** Release 1.8.0 hat nur das iPhone neu gebaut – APK, .deb und .msi stammen unverändert aus 1.7.0. */
    private val iosOnlyRelease = """{"tag_name":"v1.8.0","html_url":"https://example.org/v1.8.0","assets":[
        {"name":"IPTV-Player.apk","browser_download_url":"https://example.org/fixed.apk"},
        {"name":"IPTV-Player-1.7.0.apk","browser_download_url":"https://example.org/1.7.0.apk"},
        {"name":"freeiptvplayer_1.7.0-1_amd64.deb","browser_download_url":"https://example.org/1.7.0.deb"},
        {"name":"FreeIPTVPlayer-1.7.1.msi","browser_download_url":"https://example.org/1.7.1.msi"},
        {"name":"IPTV-Player-1.8.0.ipa","browser_download_url":"https://example.org/1.8.0.ipa"}]}"""

    @Test
    fun ownPlatformFileDecidesTheVersion() {
        val android = Updates.parse(iosOnlyRelease, UpdatePlatform.ANDROID)
        assertEquals("1.7.0", android?.version)
        assertEquals("https://example.org/1.7.0.apk", android?.apkUrl, "versionierte Datei statt der Kopie mit festem Namen")
        assertEquals("1.7.0", Updates.parse(iosOnlyRelease, UpdatePlatform.LINUX)?.version)
        assertEquals("1.7.1", Updates.parse(iosOnlyRelease, UpdatePlatform.WINDOWS)?.version)
        assertEquals("1.8.0", Updates.parse(iosOnlyRelease)?.version, "ohne Plattform zählt der Tag")
        assertEquals("1.4.0", Updates.parse("""{"tag_name":"v1.4.0","assets":[]}""", UpdatePlatform.ANDROID)?.version, "ohne eigene Datei: Tag")
    }

    /** Seit der Umbenennung heißt der Installer „IPTVPlayer-…msi“; daneben liegt die portable Version als .zip. */
    @Test
    fun renamedWindowsInstallerIsFoundNextToPortableZip() {
        val release = """{"tag_name":"v2.2.0","assets":[
            {"name":"IPTVPlayer-2.2.0-windows-portable.zip","browser_download_url":"https://example.org/2.2.0.zip"},
            {"name":"IPTVPlayer-2.2.0.msi","browser_download_url":"https://example.org/2.2.0.msi"},
            {"name":"freeiptvplayer_2.2.0-1_amd64.deb","browser_download_url":"https://example.org/2.2.0.deb"}]}"""
        assertEquals("2.2.0", Updates.parse(release, UpdatePlatform.WINDOWS)?.version)
        assertEquals("https://example.org/2.2.0.deb", Updates.parse(release, UpdatePlatform.LINUX)?.debUrl)
    }

    @Test
    fun iosOnlyReleaseOffersNoUpdateToTvAndPc() = runTest {
        val http = FakeHttp { iosOnlyRelease }
        assertNull(Updates.newerThan("1.7.0", UpdatePlatform.ANDROID, http))
        assertNull(Updates.newerThan("1.7.0", UpdatePlatform.LINUX, http))
        assertEquals("1.7.1", Updates.newerThan("1.7.0", UpdatePlatform.WINDOWS, http)?.version)
        assertEquals("1.7.0", Updates.newerThan("1.6.2", UpdatePlatform.ANDROID, http)?.version)
    }

    @Test
    fun desktopPlatformFromOsName() {
        assertEquals(UpdatePlatform.WINDOWS, UpdatePlatform.desktop("Windows 11"))
        assertEquals(UpdatePlatform.LINUX, UpdatePlatform.desktop("Linux"))
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
        assertNull(Updates.newerThan("1.0.0", http = FakeHttp { error("offline") }))
    }
}
