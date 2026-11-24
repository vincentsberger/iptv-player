package de.vincentsberger.iptvplayer.app.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.player.PlaybackEngine

/** ExoPlayer hinter der [PlaybackEngine]-Schnittstelle des PlaybackController; alle Zugriffe auf dem Main-Thread. */
class ExoPlaybackEngine(val player: ExoPlayer, private val clock: () -> Long = SystemClock::elapsedRealtime) : PlaybackEngine {
    /** „Wiedergabe nicht möglich (…)“ nach einem Player-Fehler, bis zum nächsten [play]. */
    var error by mutableStateOf<String?>(null); private set

    /** Der Player puffert (Compose-State für Spinner und Pause-Info). */
    var buffering by mutableStateOf(true); private set

    private var ended by mutableStateOf(false)
    private var isPlayingNow by mutableStateOf(player.isPlaying)
    private var playWhenReadyNow by mutableStateOf(player.playWhenReady)

    override val timeMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val lengthMs: Long get() = player.duration.coerceAtLeast(0)
    override val playing: Boolean get() = isPlayingNow
    override val paused: Boolean get() = !playWhenReadyNow
    override val finished: Boolean get() = ended
    override val failed: Boolean get() = error != null

    /** ExoPlayer meldet keinen Fortschritt-Zeitpunkt: der Controller misst selbst. */
    override val lastProgressAt: Long = Long.MIN_VALUE / 2
    override var startedAt: Long = clock(); private set

    private val listener = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) { error = "Wiedergabe nicht möglich (${e.errorCodeName})" }
        override fun onIsPlayingChanged(isPlaying: Boolean) { isPlayingNow = isPlaying }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playWhenReadyNow = playWhenReady }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            ended = state == Player.STATE_ENDED
        }
    }

    init { player.addListener(listener) }

    /** Live beim Neuverbinden: Standardposition = Live-Kante; sonst (Erststart, Filme) ab [startMs]. */
    override fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean) {
        error = null
        startedAt = clock()
        val mi = mediaItem(url, item)
        if (item.section == Section.LIVE && keepFrame) player.setMediaItem(mi) else player.setMediaItem(mi, startMs)
        player.prepare()
        player.play()
    }

    override fun seekTo(ms: Long) = player.seekTo(ms)

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}
