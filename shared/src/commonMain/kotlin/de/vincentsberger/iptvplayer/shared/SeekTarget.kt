package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Laufendes Sammeln beim Spulen: Ausgangsposition (beim ersten Druck) und Ziel. */
data class SeekState(val anchorMs: Long, val targetMs: Long) {
    val deltaMs: Long get() = targetMs - anchorMs
}

/**
 * Spulen mit Ziel: Jeder Druck verschiebt nur das Ziel; erst [COMMIT_AFTER_MS] nach dem letzten Druck soll der
 * Player einmal springen ([due]). So lädt ein IPTV-Stream nur einmal neu statt nach jedem Druck.
 */
class SeekTarget(private val clock: () -> Long) {
    var state by mutableStateOf<SeekState?>(null); private set
    private var lastPressAt = 0L
    /** Letzter Sprung (Ziel, Zeitpunkt) – manche Player melden die neue Zeit erst nach dem Nachladen. */
    private var lastCommit: Pair<Long, Long>? = null

    /**
     * Position, die gerade „gilt“: das gesammelte Ziel; kurz nach einem Sprung dessen Ziel, solange der Player noch
     * die alte Zeit meldet; sonst die Player-Zeit. Für den Ausgang der nächsten Runde und fürs Speichern beim Verlassen.
     */
    fun positionOf(playerPositionMs: Long): Long {
        state?.let { return it.targetMs }
        val (target, at) = lastCommit ?: return playerPositionMs
        val settling = clock() - at < SETTLE_MS && abs(playerPositionMs - target) > CAUGHT_UP_MS
        return if (settling) target else playerPositionMs
    }

    fun press(deltaMs: Long, positionMs: Long, durationMs: Long) {
        val current = state ?: positionOf(positionMs).let { SeekState(it, it) }
        val max = if (durationMs > 0) (durationMs - END_MARGIN_MS).coerceAtLeast(0) else Long.MAX_VALUE
        state = current.copy(targetMs = (current.targetMs + deltaMs).coerceIn(0, max))
        lastPressAt = clock()
    }

    /** Ziel, sobald seit dem letzten Druck genug Zeit vergangen ist – danach ist das Sammeln beendet. */
    fun due(): Long? {
        val s = state ?: return null
        if (clock() - lastPressAt < COMMIT_AFTER_MS) return null
        state = null
        lastCommit = s.targetMs to clock()
        return s.targetMs
    }

    /** Sofort springen (z. B. weil eine andere Taste gedrückt wurde). */
    fun commitNow(): Long? = state?.targetMs?.also { state = null; lastCommit = it to clock() }

    /**
     * Läuft, solange der Player offen ist, und springt, sobald ein gesammeltes Ziel fällig ist. Bewusst eine
     * Dauerschleife statt eines Effekts je Sammelrunde – der würde bei schnellem Weiterdrücken nicht neu starten.
     */
    suspend fun runCommits(onSeek: (Long) -> Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            due()?.let(onSeek)
        }
    }

    fun cancel() {
        state = null
        lastCommit = null
    }

    companion object {
        const val COMMIT_AFTER_MS = 800L
        /** Ziel nie ganz ans Ende – sonst gilt der Film sofort als beendet. */
        private const val END_MARGIN_MS = 1_000L
        /** So lange nach einem Sprung darf der Player noch die alte Zeit melden. */
        private const val SETTLE_MS = 5_000L
        /** Ab dieser Nähe zum Ziel gilt es als erreicht. */
        private const val CAUGHT_UP_MS = 2_000L
    }
}

/** „+2 min“, „−40 s“, „+1 min 30 s“, „+1 h 5 min“, „±0 s“. */
fun seekDeltaLabel(deltaMs: Long): String {
    val secs = abs(deltaMs) / 1000
    if (secs == 0L) return "±0 s"
    val sign = if (deltaMs > 0) "+" else "−"
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    val parts = buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m min")
        if (s > 0 && h == 0L) add("$s s")
    }
    return sign + parts.joinToString(" ")
}
