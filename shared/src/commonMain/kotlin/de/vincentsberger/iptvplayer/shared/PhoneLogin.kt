package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account

/**
 * Anmeldung vom Handy: Der Fernseher zeigt einen QR-Code mit einer Adresse im Heimnetz,
 * das Handy öffnet dort ein Formular und schickt die Zugangsdaten zurück.
 * Hier steckt nur die plattformunabhängige Logik (Formular, Auswertung); den Server stellt die App.
 */
object PhoneLogin {

    /** Formulardaten (application/x-www-form-urlencoded) → Schlüssel/Wert. */
    fun parseForm(body: String): Map<String, String> =
        body.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val k = pair.substringBefore('=')
            val v = if ('=' in pair) pair.substringAfter('=') else ""
            urlDecode(k) to urlDecode(v)
        }

    /** Zugang aus dem Formular – oder eine Fehlermeldung für das Handy. */
    fun accountFrom(form: Map<String, String>): Result {
        fun f(name: String) = form[name]?.trim().orEmpty()
        return when (form["type"]) {
            "m3u" -> if (f("url").isEmpty()) Result.Invalid("Bitte den Link zur Playlist eingeben.")
            else Result.Ok(Account.M3u(f("url")))
            else -> if (f("server").isEmpty() || f("user").isEmpty() || f("pass").isEmpty())
                Result.Invalid("Bitte Server, Benutzername und Passwort eingeben.")
            else Result.Ok(Account.Xtream(f("server"), f("user"), f("pass")))
        }
    }

    sealed interface Result {
        data class Ok(val account: Account) : Result
        data class Invalid(val message: String) : Result
    }

    /** Formularseite fürs Handy (eine Datei, ohne externe Abhängigkeiten). */
    fun formPage(error: String? = null): String = page(
        """
        <h1>IPTV Player</h1>
        <p class="muted">Gib die Zugangsdaten deines Anbieters ein. Sie werden nur an deinen Fernseher geschickt.</p>
        ${error?.let { "<p class=\"err\">${escape(it)}</p>" }.orEmpty()}
        <div class="tabs">
          <label><input type="radio" name="t" checked onclick="show('x')"> Xtream Codes</label>
          <label><input type="radio" name="t" onclick="show('m')"> M3U-Playlist</label>
        </div>
        <form id="x" method="post">
          <input type="hidden" name="type" value="xtream">
          <input name="server" placeholder="Server, z. B. anbieter.example:8080" autocapitalize="off" autocorrect="off" inputmode="url" required>
          <input name="user" placeholder="Benutzername" autocapitalize="off" autocorrect="off" required>
          <input name="pass" type="password" placeholder="Passwort" required>
          <button>An Fernseher senden</button>
        </form>
        <form id="m" method="post" hidden>
          <input type="hidden" name="type" value="m3u">
          <input name="url" placeholder="Link zur M3U-Playlist" autocapitalize="off" autocorrect="off" inputmode="url" required>
          <button>An Fernseher senden</button>
        </form>
        <script>function show(id){document.getElementById('x').hidden=id!=='x';document.getElementById('m').hidden=id!=='m';}</script>
        """.trimIndent()
    )

    /** Bestätigung nach dem Absenden. */
    fun sentPage(): String = page(
        """
        <h1>Gesendet ✓</h1>
        <p>Schau jetzt auf deinen Fernseher – dort wird die Anmeldung geprüft.</p>
        <p class="muted">Falls etwas nicht stimmt, kannst du die Seite neu laden und es erneut versuchen.</p>
        """.trimIndent()
    )

    private fun page(body: String) = """
        <!doctype html><html lang="de"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>IPTV Player – Anmelden</title>
        <style>
        body{font-family:system-ui,sans-serif;background:#16181d;color:#eceef2;margin:0;padding:24px;max-width:480px}
        h1{font-size:24px;margin:8px 0 4px}.muted{color:#9aa0ab}.err{color:#ff6b5b;font-weight:600}
        .tabs{display:flex;gap:16px;margin:16px 0}
        input:not([type=radio]){display:block;box-sizing:border-box;width:100%;font-size:17px;padding:14px;margin:0 0 12px;
          border-radius:10px;border:2px solid #343a45;background:#21252d;color:#eceef2}
        input:focus{outline:none;border-color:#ff5545}
        button{width:100%;font-size:17px;font-weight:700;padding:15px;border:0;border-radius:10px;background:#ff5545;color:#16181d}
        </style></head><body>
        $body
        </body></html>
    """.trimIndent()

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Prozent-Dekodierung (UTF-8), „+“ = Leerzeichen. Kaputte Sequenzen bleiben stehen. */
    internal fun urlDecode(s: String): String {
        val out = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { out += ' '.code.toByte(); i++ }
                c == '%' -> {
                    val hex = if (i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
                    if (hex != null) { out += hex.toByte(); i += 3 } else { out += '%'.code.toByte(); i++ }
                }
                else -> { c.toString().encodeToByteArray().forEach { out += it }; i++ }
            }
        }
        return out.toByteArray().decodeToString()
    }
}
