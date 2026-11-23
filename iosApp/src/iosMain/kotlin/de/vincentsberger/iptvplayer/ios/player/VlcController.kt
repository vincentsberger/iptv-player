package de.vincentsberger.iptvplayer.ios.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import de.vincentsberger.iptvplayer.ios.ui.monotonicMs
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerEngine
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSNotification
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import vlckit.VLCMedia
import vlckit.VLCMediaPlayer
import vlckit.VLCMediaPlayerDelegateProtocol
import vlckit.VLCMediaPlayerStateBuffering
import vlckit.VLCMediaPlayerStateEnded
import vlckit.VLCMediaPlayerStateError
import vlckit.VLCMediaPlayerStateOpening
import vlckit.VLCMediaPlayerStatePaused
import vlckit.VLCMediaPlayerStatePlaying
import vlckit.VLCMediaPlayerStateStopped
import vlckit.VLCTime

/** Zustandsmeldungen von VLCKit (kommen auf dem Main-Thread). VLCKit hält den Delegate nur schwach. */
private class StateDelegate(private val onChange: () -> Unit) : NSObject(), VLCMediaPlayerDelegateProtocol {
    override fun mediaPlayerStateChanged(aNotification: NSNotification) = onChange()
}

/**
 * Wiedergabe über MobileVLCKit: eine UIView als Bildfläche, die über die ganze Lebenszeit dieselbe bleibt (Drehen
 * startet nichts neu). Zeit und Länge werden alle 250 ms abgefragt – daraus entsteht auch [lastProgressAt] für die
 * Hänger-Erkennung. Ton läuft auch bei Stummschalter (Audio-Session „Playback“), der Bildschirm bleibt beim Abspielen an.
 */
class VlcController : PlayerEngine {
    private val player = VLCMediaPlayer()
    private val view = UIView().apply { backgroundColor = UIColor.blackColor }
    private val delegate = StateDelegate(::onStateChanged)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var poll: Job? = null
    /** stop() von uns (App im Hintergrund) ist kein Ende des Streams. */
    private var stoppedOnPurpose = false

    override var state by mutableStateOf(PlayerState.IDLE); private set
    override var hasPicture by mutableStateOf(false); private set
    override var timeMs by mutableLongStateOf(0L); private set
    override var lengthMs by mutableLongStateOf(0L); private set
    override var lastProgressAt by mutableLongStateOf(0L); private set
    override var startedAt by mutableLongStateOf(0L); private set

    init {
        player.drawable = view
        player.delegate = delegate
    }

    private fun onStateChanged() {
        state = when (player.state) {
            VLCMediaPlayerStateOpening -> PlayerState.OPENING
            VLCMediaPlayerStateBuffering -> if (player.playing) PlayerState.PLAYING else PlayerState.BUFFERING
            VLCMediaPlayerStatePlaying -> PlayerState.PLAYING
            VLCMediaPlayerStatePaused -> PlayerState.PAUSED
            VLCMediaPlayerStateError -> PlayerState.ERROR
            VLCMediaPlayerStateEnded -> PlayerState.ENDED
            VLCMediaPlayerStateStopped -> if (stoppedOnPurpose) PlayerState.IDLE else PlayerState.ENDED
            else -> return
        }
        UIApplication.sharedApplication.idleTimerDisabled = state == PlayerState.PLAYING
    }

    override fun play(url: String, startMs: Long, keepFrame: Boolean) {
        // keepFrame: VLCKit lässt beim Neuladen ohnehin das letzte Bild in der UIView stehen.
        stoppedOnPurpose = false
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) { state = PlayerState.ERROR; return }
        val media = VLCMedia(uRL = nsUrl)
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000}")
        if (!keepFrame) hasPicture = false
        timeMs = startMs
        startedAt = monotonicMs()
        state = PlayerState.OPENING
        player.media = media
        player.play()
        startPolling()
    }

    private fun startPolling() {
        poll?.cancel()
        poll = scope.launch {
            var lastTime = -1L
            while (isActive) {
                val t = player.time?.intValue?.toLong() ?: 0L
                if (t > 0 && t != lastTime) {
                    lastTime = t
                    timeMs = t
                    lastProgressAt = monotonicMs()
                }
                val len = player.media?.length?.intValue?.toLong() ?: 0L
                if (len > 0) lengthMs = len
                if (!hasPicture && player.hasVideoOut) hasPicture = true
                delay(250)
            }
        }
    }

    override fun togglePause() = if (state == PlayerState.PAUSED) resume() else pause()

    override fun pause() {
        if (player.playing) player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seekTo(ms: Long) {
        player.time = VLCTime(int = ms.coerceAtLeast(0).toInt())
        timeMs = ms
    }

    override fun stop() {
        stoppedOnPurpose = true
        poll?.cancel()
        player.stop()
        state = PlayerState.IDLE
        UIApplication.sharedApplication.idleTimerDisabled = false
    }

    override fun release() {
        stop()
        player.delegate = null
        player.drawable = null
        scope.cancel()
    }

    @Composable
    override fun Video(modifier: Modifier) {
        UIKitView(factory = { view }, modifier = modifier)
    }
}
