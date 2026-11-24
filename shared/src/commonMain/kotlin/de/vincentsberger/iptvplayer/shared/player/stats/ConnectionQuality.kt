package de.vincentsberger.iptvplayer.shared.player.stats

private const val POOR_STALLS = 3
private const val POOR_WINDOW = 10
private const val MIN_SAMPLES = 3
private const val FAIR_WINDOW = 60
private const val DROP_RATIO = 0.02
private const val LOW_BUFFER_MS = 2_000L

/** Einschätzung der Verbindung für den Indikator im Player. */
enum class ConnectionQuality { GOOD, FAIR, POOR, UNKNOWN }

/**
 * Bewertet die letzten [FAIR_WINDOW] Messpunkte. POOR: Neuverbindung läuft, Verbindung verloren oder ≥ [POOR_STALLS] gestockte
 * Sekunden in den letzten [POOR_WINDOW]. FAIR: Neuverbindung/Stockung im Fenster, mehr als [DROP_RATIO] verworfene Bilder
 * (nur mit bekannter fps) oder kleiner Puffer (nur bei [isLive] = false). Weniger als [MIN_SAMPLES] Punkte: UNKNOWN.
 */
fun connectionQuality(history: List<StatsSample>, reconnecting: Boolean, connectionLost: Boolean, isLive: Boolean): ConnectionQuality {
    if (reconnecting || connectionLost) return ConnectionQuality.POOR
    val window = history.takeLast(FAIR_WINDOW)
    if (window.size < MIN_SAMPLES) return ConnectionQuality.UNKNOWN
    if (window.takeLast(POOR_WINDOW).count { it.stalled } >= POOR_STALLS) return ConnectionQuality.POOR
    val fair = window.any { it.stalled || it.reconnected } || droppedRatio(window) > DROP_RATIO || lowBuffer(window, isLive)
    return if (fair) ConnectionQuality.FAIR else ConnectionQuality.GOOD
}

private fun droppedRatio(window: List<StatsSample>): Double {
    val known = window.filter { it.fps != null && it.droppedDelta != null }
    val fps = known.sumOf { it.fps!!.toDouble() }
    return if (fps <= 0.0) 0.0 else known.sumOf { it.droppedDelta!! }.toDouble() / fps
}

private fun lowBuffer(window: List<StatsSample>, isLive: Boolean): Boolean {
    val buffer = window.last().bufferMs
    return !isLive && buffer != null && buffer < LOW_BUFFER_MS
}

/** Punkte des Indikators: `●●●` gut, `●●○` mittel, `●○○` schlecht, `○○○` unbekannt. */
fun qualityDots(q: ConnectionQuality): String = when (q) {
    ConnectionQuality.GOOD -> "●●●"
    ConnectionQuality.FAIR -> "●●○"
    ConnectionQuality.POOR -> "●○○"
    ConnectionQuality.UNKNOWN -> "○○○"
}

/** Wort zur Bewertung. */
fun qualityLabel(q: ConnectionQuality): String = when (q) {
    ConnectionQuality.GOOD -> "gut"
    ConnectionQuality.FAIR -> "mittel"
    ConnectionQuality.POOR -> "schlecht"
    ConnectionQuality.UNKNOWN -> "–"
}
