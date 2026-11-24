package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account

private val XTREAM_LINK = Regex("""^(https?://[^/?#\s]+)(?:/[^?#\s]*)?/(?:get|player_api)\.php\?(\S*)$""", RegexOption.IGNORE_CASE)

/**
 * Xtream-Zugang aus einem eingefügten Link („http://host:8080/get.php?username=u&password=p&type=m3u_plus“ oder
 * `player_api.php`) – so füllt Strg V im Desktop-Login Server, Benutzer und Passwort auf einmal aus. Kein solcher
 * Link oder Benutzer/Passwort fehlen → null.
 */
fun xtreamFromLink(text: String): Account.Xtream? {
    val m = XTREAM_LINK.matchEntire(text.trim()) ?: return null
    val params = PhoneLogin.parseForm(m.groupValues[2])
    val user = params["username"]?.takeIf { it.isNotBlank() } ?: return null
    val pass = params["password"]?.takeIf { it.isNotBlank() } ?: return null
    return Account.Xtream(m.groupValues[1], user, pass)
}
