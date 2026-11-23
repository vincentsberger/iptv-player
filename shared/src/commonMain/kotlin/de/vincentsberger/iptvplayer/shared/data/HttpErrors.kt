package de.vincentsberger.iptvplayer.shared.data

/** Fehlertexte für HTTP auf allen Plattformen – bewusst ohne URL, die enthält Benutzername und Passwort. */
object HttpErrors {
    const val INVALID_ADDRESS = "Die Server-Adresse ist ungültig. Bitte prüfen, z. B. anbieter.example:8080 (Port höchstens 65535)."
    const val UNREACHABLE = "Server nicht erreichbar. Prüfe Adresse und Internetverbindung."
    const val EMPTY = "Leere Antwort vom Server"
    fun status(code: Int): String = "Server antwortet mit Fehler $code"
}
