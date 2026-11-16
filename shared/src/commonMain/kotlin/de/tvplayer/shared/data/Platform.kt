package de.tvplayer.shared.data

/** Gemeinsamer User-Agent für alle Anfragen und Streams. */
const val USER_AGENT = "TVPlayer/1.2"

/** Lädt eine Adresse als Text. */
expect suspend fun httpGetText(url: String): String

/** Lädt eine Adresse zeilenweise (für sehr große M3U-Listen). */
expect suspend fun <T> httpReadLines(url: String, block: (Sequence<String>) -> T): T

/**
 * HTTP-Zugriff als austauschbare Abhängigkeit: In der App [PlatformHttp], in Tests eine Attrappe
 * mit festen Antworten – so laufen Tests ohne Netzwerk.
 */
interface HttpSource {
    suspend fun getText(url: String): String
    suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T
}

/** Echtes HTTP über die Plattform-Implementierung (OkHttp). */
object PlatformHttp : HttpSource {
    override suspend fun getText(url: String): String = httpGetText(url)
    override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T = httpReadLines(url, block)
}

/** Aktuelle Zeit in Millisekunden. */
expect fun currentTimeMillis(): Long

/** Einfacher Schlüssel-Wert-Speicher, auf jeder Plattform anders umgesetzt. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun clear()
}

/** Prozent-Kodierung für URL-Bestandteile (RFC 3986, UTF-8). */
fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') append(ch)
        else { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 0xF]) }
    }
}

/** Host-Teil einer Adresse, z. B. für die Kontoanzeige. */
fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').ifBlank { url }
