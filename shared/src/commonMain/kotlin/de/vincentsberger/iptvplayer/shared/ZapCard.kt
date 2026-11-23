package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.Item

/**
 * Zapp-Karte beim Senderwechsel: sichtbar ([channel] nicht null), solange der neue Sender lädt, und noch
 * [HIDE_AFTER_MS] nach dem ersten Bild. Die Oberflächen rufen [tick] regelmäßig auf, solange die Karte sichtbar ist.
 */
class ZapCard(private val clock: () -> Long) {
    var channel by mutableStateOf<Item?>(null); private set
    private var firstFrameAt: Long? = null

    fun onChannelChanged(item: Item) {
        channel = item
        firstFrameAt = null // Countdown beginnt erst mit dem ersten Bild dieses Senders
    }

    fun onFirstFrame() {
        if (channel != null && firstFrameAt == null) firstFrameAt = clock()
    }

    fun tick() {
        val since = firstFrameAt ?: return
        if (clock() - since >= HIDE_AFTER_MS) hide()
    }

    fun hide() {
        channel = null
        firstFrameAt = null
    }

    companion object {
        const val HIDE_AFTER_MS = 4_000L
    }
}
