package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.PlatformHttp
import de.vincentsberger.iptvplayer.shared.data.objects
import de.vincentsberger.iptvplayer.shared.data.parseJson
import de.vincentsberger.iptvplayer.shared.data.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Neueste veröffentlichte Version (GitHub-Release) mit den Downloads je Plattform. */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?, val debUrl: String?)

/**
 * Nach Updates suchen: fragt die öffentlichen GitHub-Releases ab (kein Konto, keine Daten außer der Anfrage selbst).
 * Die App vergleicht die Version und bietet das Update an; installieren muss man selbst bestätigen.
 */
object Updates {
    const val RELEASES_PAGE = "https://github.com/vincentsberger/iptv-player/releases"
    private const val LATEST_API = "https://api.github.com/repos/vincentsberger/iptv-player/releases/latest"

    /** Höchstens einmal am Tag von selbst nachsehen. */
    const val CHECK_INTERVAL_MS = 24 * 60 * 60_000L

    /** Antwort der GitHub-API → Release; unbrauchbare Antworten → null. */
    fun parse(json: String): LatestRelease? {
        val o = parseJson(json) as? JsonObject ?: return null
        val tag = o.str("tag_name").removePrefix("v").ifBlank { return null }
        val assets = o["assets"].objects()
        fun asset(suffix: String) = assets.firstOrNull { it.str("name").endsWith(suffix) }?.str("browser_download_url")?.ifBlank { null }
        return LatestRelease(tag, o.str("html_url").ifBlank { RELEASES_PAGE }, asset(".apk"), asset(".deb"))
    }

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
    suspend fun newerThan(current: String, http: HttpSource = PlatformHttp): LatestRelease? = try {
        parse(http.getText(LATEST_API))?.takeIf { isNewer(it.version, current) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
