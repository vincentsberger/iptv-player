package de.vincentsberger.iptvplayer.shared

/**
 * Entscheidet, wann ein abgebrochener oder hängender Stream automatisch neu geladen wird (PC und TV).
 * Reine Logik ohne Player und ohne Uhr – die Oberfläche fragt einmal pro Sekunde nach und übergibt die Zeit.
 *
 * Ablauf: Abbruch erkannt → [nextRetryDelay] warten → Stream neu laden. Nach allen Versuchen gibt sie auf
 * (null), dann zeigt die Oberfläche die Fehlermeldung. Läuft der Stream danach wieder [stableMs] am Stück,
 * zählen spätere Abbrüche wieder von vorn.
 */
class StreamRecovery(
    private val delaysMs: List<Long> = listOf(2_000, 4_000, 8_000, 15_000, 30_000),
    /** So lange darf weder ein neues Bild noch Zeitfortschritt kommen, bevor der Stream als hängend gilt. */
    private val stallMs: Long = 15_000,
    private val stableMs: Long = 60_000,
) {
    /** Bisherige Versuche seit dem letzten stabilen Abschnitt. */
    var attempts = 0; private set
    private var progressSince: Long? = null

    /** Neuer Titel oder Handbedienung („Erneut versuchen“): wieder alle Versuche frei. */
    fun reset() {
        attempts = 0
        progressSince = null
    }

    /** Hängt der Stream? [lastProgressMs]: Zeitpunkt des letzten Bildes bzw. Zeitfortschritts. */
    fun isStalled(lastProgressMs: Long, nowMs: Long): Boolean = nowMs - lastProgressMs >= stallMs

    /** Die Wiedergabe kommt gerade voran. */
    fun onProgress(nowMs: Long) {
        val since = progressSince ?: nowMs.also { progressSince = it }
        if (nowMs - since >= stableMs) attempts = 0
    }

    /** Abbruch erkannt: Wartezeit bis zum nächsten Versuch, oder null = aufgeben und Fehler zeigen. */
    fun nextRetryDelay(): Long? {
        progressSince = null
        return delaysMs.getOrNull(attempts)?.also { attempts++ }
    }
}
