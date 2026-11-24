package de.vincentsberger.iptvplayer.shared.player.stats

private const val SCHEME_SEPARATOR = "://"
private val AUTHORITY_END = charArrayOf('/', '?', '#')

/**
 * `host:port` der Stream-Adresse – nie Zugangsdaten, Pfad oder Query. Unkodierte `/`, `?`, `#` in Benutzer/Passwort machen die Grenze
 * der Adresse mehrdeutig: dann zählt nur ein gültiges `host[:port]` hinter dem letzten `@`, im Zweifel „–“ statt eines Fragments der Zugangsdaten.
 */
fun hostAndPort(url: String): String {
    val schemeEnd = url.indexOf(SCHEME_SEPARATOR)
    if (schemeEnd <= 0 || url.any { it.isWhitespace() }) return STATS_DASH
    if (url.substring(0, schemeEnd).equals("file", ignoreCase = true)) return "Datei auf dem Gerät"
    return hostCandidate(url.substring(schemeEnd + SCHEME_SEPARATOR.length)).takeIf(::isHostPort) ?: STATS_DASH
}

private fun authorityEnd(s: String) = s.indexOfAny(AUTHORITY_END).let { if (it < 0) s.length else it }

private fun hostCandidate(rest: String): String {
    val authority = rest.substring(0, authorityEnd(rest))
    return when {
        '@' in authority -> authority.substringAfterLast('@')
        '@' !in rest -> authority
        // `@` erst hinter der Adresse: Benutzername ohne Passwort, Pfad-Zugangsdaten oder Passwort mit `/`, `?`, `#` – bei `host:port` nicht unterscheidbar
        isHostPort(authority) -> if (':' in authority) STATS_DASH else authority
        else -> rest.substringAfterLast('@').let { it.substring(0, authorityEnd(it)) }
    }
}

/** Gültiges `host`, `host:port` oder `[v6]:port` – sonst steckt etwas anderes (z. B. Zugangsdaten) darin. */
private fun isHostPort(a: String): Boolean {
    val (host, port) = splitHostPort(a) ?: return false
    val hostOk = host.isNotEmpty() && host.all { it.isLetterOrDigit() || it in ".-_:" }
    return hostOk && (port == null || (port.isNotEmpty() && port.all { it.isDigit() }))
}

/** Teilt in Host und Port (`null` = kein Port); `null` als Ergebnis: unbrauchbare Klammer-Schreibweise. */
private fun splitHostPort(a: String): Pair<String, String?>? {
    if (!a.startsWith('[')) return a.substringBefore(':') to (if (':' in a) a.substringAfter(':') else null)
    val close = a.indexOf(']')
    val tail = if (close < 0) null else a.substring(close + 1)
    return if (tail == null || (tail.isNotEmpty() && !tail.startsWith(':'))) null else a.substring(1, close) to tail.drop(1).takeIf { tail.isNotEmpty() }
}
