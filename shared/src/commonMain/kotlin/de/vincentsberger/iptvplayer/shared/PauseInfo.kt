package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stufe der Pause-Info: nichts, kompakte Leiste oder ausführliche Info. */
enum class PauseStage { NONE, COMPACT, FULL }

/**
 * Pause-Info bei Filmen/Folgen: kompakt sofort beim Pausieren, ausführlich nach [FULL_AFTER_MS] ununterbrochener
 * Pause. Die Oberflächen rufen [tick] regelmäßig auf, solange pausiert ist.
 */
class PauseInfo(private val clock: () -> Long) {
    var stage by mutableStateOf(PauseStage.NONE); private set
    private var since = 0L

    fun onPaused() {
        if (stage != PauseStage.NONE) return
        stage = PauseStage.COMPACT
        since = clock()
    }

    fun onResumed() {
        stage = PauseStage.NONE
    }

    fun tick() {
        if (stage == PauseStage.COMPACT && clock() - since >= FULL_AFTER_MS) stage = PauseStage.FULL
    }

    /** Spulen o. Ä. während der Pause: zurück auf kompakt, die 5 s beginnen neu. */
    fun dismissFull() {
        if (stage == PauseStage.NONE) return
        stage = PauseStage.COMPACT
        since = clock()
    }

    companion object {
        const val FULL_AFTER_MS = 5_000L
    }
}

/**
 * Gilt der Player als „angehalten“ für die Pause-Info? Nicht bei Live, nicht beim Puffern, nicht nach dem Ende
 * (dort läuft ggf. der Countdown zur nächsten Folge) und nicht bei einem Fehler.
 */
fun isPausedForInfo(isLive: Boolean, playing: Boolean, buffering: Boolean, ended: Boolean, failed: Boolean): Boolean =
    !isLive && !playing && !buffering && !ended && !failed
