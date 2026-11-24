package de.vincentsberger.iptvplayer.app.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.player.PlaybackEngine
import de.vincentsberger.iptvplayer.shared.player.stats.EngineStats

/** ExoPlayer hinter der [PlaybackEngine]-Schnittstelle des PlaybackController; alle Zugriffe auf dem Main-Thread. */
@OptIn(UnstableApi::class)
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

    /** Je Medium kumulierte Zähler (Main-Thread); [play] setzt sie zurück. */
    private var bytesLoaded = 0L
    private var droppedTotal = 0L

    private val analytics = object : AnalyticsListener {
        override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
            bytesLoaded += loadEventInfo.bytesLoaded
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            droppedTotal += droppedFrames
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    /** Rohwerte direkt vom Player; unbekannte Format-Felder (`NO_VALUE`) werden `null`. Vor dem ersten [play] ist der Puffer unbekannt. */
    override fun stats(): EngineStats {
        val v = player.videoFormat
        val a = player.audioFormat
        val buffered = if (player.playbackState == Player.STATE_IDLE) null else (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
        return EngineStats(
            bytesReceived = bytesLoaded,
            bufferedMs = buffered,
            droppedFrames = droppedTotal,
            videoCodec = v?.let { it.codecs ?: it.sampleMimeType },
            width = v?.width?.takeIf { it != Format.NO_VALUE },
            height = v?.height?.takeIf { it != Format.NO_VALUE },
            fps = v?.frameRate?.takeIf { it != Format.NO_VALUE.toFloat() && it > 0f },
            audioCodec = a?.let { it.codecs ?: it.sampleMimeType },
            audioChannels = a?.channelCount?.takeIf { it != Format.NO_VALUE },
            audioLanguage = a?.language?.takeIf { it != C.LANGUAGE_UNDETERMINED },
        )
    }

    /** Live beim Neuverbinden: Standardposition = Live-Kante; sonst (Erststart, Filme) ab [startMs]. */
    override fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean) {
        error = null
        bytesLoaded = 0
        droppedTotal = 0
        startedAt = clock()
        val mi = mediaItem(url, item)
        if (item.section == Section.LIVE && keepFrame) player.setMediaItem(mi) else player.setMediaItem(mi, startMs)
        player.prepare()
        player.play()
    }

    override fun seekTo(ms: Long) = player.seekTo(ms)

    fun release() {
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
        player.release()
    }
}
