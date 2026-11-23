package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.PlatformHttp
import de.vincentsberger.iptvplayer.shared.data.objects
import de.vincentsberger.iptvplayer.shared.data.parseJson
import de.vincentsberger.iptvplayer.shared.data.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Neueste veröffentlichte Version (GitHub-Release) mit den Downloads je Plattform. [version] ist die Version der Datei
 * für die eigene Plattform – siehe [Updates.parse].
 */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?, val debUrl: String?)

/** Plattform mit Selbst-Update-Hinweis und ihrer Release-Datei. Das iPhone fehlt: dort wird per Sideloading aktualisiert. */
enum class UpdatePlatform(val suffix: String) {
    ANDROID(".apk"), LINUX(".deb"), WINDOWS(".msi");

    companion object {
        /** PC-App: Windows-Installer oder Linux-Paket – nach `System.getProperty("os.name")`. */
        fun desktop(osName: String): UpdatePlatform = if (osName.startsWith("Windows", ignoreCase = true)) WINDOWS else LINUX
    }
}

/**
 * Nach Updates suchen: fragt die öffentlichen GitHub-Releases ab (kein Konto, keine Daten außer der Anfrage selbst).
 * Die App vergleicht die Version und bietet das Update an; installieren muss man selbst bestätigen.
 */
object Updates {
    const val RELEASES_PAGE = "https://github.com/vincentsberger/iptv-player/releases"
    private const val LATEST_API = "https://api.github.com/repos/vincentsberger/iptv-player/releases/latest"

    /** Höchstens einmal am Tag von selbst nachsehen. */
    const val CHECK_INTERVAL_MS = 24 * 60 * 60_000L

    /**
     * Antwort der GitHub-API → Release; unbrauchbare Antworten → null.
     *
     * Releases bauen nur die Plattformen neu, die sich geändert haben – unveränderte Dateien werden aus dem vorigen
     * Release übernommen und behalten ihre Version im Namen (`IPTV-Player-1.7.0.apk` in Release 1.8.0). Mit [platform]
     * zählt deshalb die Version im Namen der eigenen Datei, nicht der Tag: Ein reines iPhone-Release meldet der TV-App
     * kein Update. Ohne eigene Datei (ältere Releases) gilt der Tag.
     */
    fun parse(json: String, platform: UpdatePlatform? = null): LatestRelease? {
        val o = parseJson(json) as? JsonObject ?: return null
        val tag = o.str("tag_name").removePrefix("v").ifBlank { return null }
        // Nur Dateien mit Version im Namen – „IPTV-Player.apk“ (fester Name für den Downloader) ist eine Kopie.
        val assets = o["assets"].objects().filter { VERSION_IN_NAME.containsMatchIn(it.str("name")) }
        fun asset(suffix: String) = assets.firstOrNull { it.str("name").endsWith(suffix) }
        fun url(suffix: String) = asset(suffix)?.str("browser_download_url")?.ifBlank { null }
        val own = platform?.let { asset(it.suffix) }?.let { VERSION_IN_NAME.find(it.str("name"))?.value }
        return LatestRelease(own ?: tag, o.str("html_url").ifBlank { RELEASES_PAGE }, url(".apk"), url(".deb"))
    }

    /** „1.7.0“ in „IPTV-Player-1.7.0.apk“, „freeiptvplayer_1.7.0-1_amd64.deb“, „FreeIPTVPlayer-1.7.0.msi“. */
    private val VERSION_IN_NAME = Regex("""\d+\.\d+\.\d+""")

    /** Ist [remote] neuer als [current]? Vergleicht Zahlenteile („1.10.0“ > „1.9.2“), Zusätze wie „-beta“ zählen nicht. */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Neueste Version, wenn sie neuer ist als [current] – sonst null (auch bei Netzfehlern). */
    suspend fun newerThan(current: String, platform: UpdatePlatform? = null, http: HttpSource = PlatformHttp): LatestRelease? = try {
        parse(http.getText(LATEST_API), platform)?.takeIf { isNewer(it.version, current) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
