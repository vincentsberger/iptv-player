package de.vincentsberger.iptvplayer.ios.player

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.UIKitView
import de.vincentsberger.iptvplayer.shared.MediaTrack
import de.vincentsberger.iptvplayer.shared.VideoFit
import de.vincentsberger.iptvplayer.shared.trackLabel
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import platform.Foundation.NSDictionary
import platform.Foundation.NSNumber
import de.vincentsberger.iptvplayer.ios.ui.monotonicMs
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerEngine
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerState
import de.vincentsberger.iptvplayer.shared.data.USER_AGENT
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
import vlckit.VLCMediaTracksInformationAudioChannelsNumber
import vlckit.VLCMediaTracksInformationDescription
import vlckit.VLCMediaTracksInformationId
import vlckit.VLCMediaTracksInformationLanguage
import vlckit.VLCMediaTracksInformationType
import vlckit.VLCMediaTracksInformationTypeAudio
import vlckit.VLCMediaTracksInformationTypeText
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
    // Keine Berührungen: Tippen und Wischen gehören den Gesten des Players (Compose), nicht der Videofläche.
    private val view = UIView().apply { backgroundColor = UIColor.blackColor; userInteractionEnabled = false }
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
        // Wie am PC: eigener User-Agent (manche Anbieter binden den Stream daran), Puffer, VLC verbindet selbst neu.
        media.addOption(":http-user-agent=$USER_AGENT")
        media.addOption(":network-caching=1500")
        media.addOption(":http-reconnect")
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000}")
        if (!keepFrame) { hasPicture = false; lengthMs = 0L }
        timeMs = startMs
        startedAt = monotonicMs()
        lastProgressAt = 0L // erst Zeitfortschritt des neuen Streams zählt
        state = PlayerState.OPENING
        player.media = media
        player.play()
        applyFit()
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
        // Nach dem Fortsetzen braucht VLC einen Moment bis zum ersten Fortschritt – das ist kein Hänger.
        lastProgressAt = monotonicMs()
        player.play()
    }

    override fun seekTo(ms: Long) {
        player.time = VLCTime(int = ms.coerceAtLeast(0).toInt())
        timeMs = ms
        lastProgressAt = monotonicMs() // Nachladen nach dem Sprung ist kein Hänger
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

    // ---------- Ton, Untertitel, Bildformat ----------

    override val audioTracks: List<MediaTrack>
        get() = tracks(VLCMediaTracksInformationTypeAudio, player.audioTrackIndexes, player.currentAudioTrackIndex)

    override val subtitleTracks: List<MediaTrack>
        get() = tracks(VLCMediaTracksInformationTypeText, player.videoSubTitlesIndexes, player.currentVideoSubTitleIndex)

    override fun selectAudio(id: Int) {
        player.currentAudioTrackIndex = id
    }

    override fun selectSubtitles(id: Int?) {
        player.currentVideoSubTitleIndex = id ?: -1
    }

    /**
     * Spuren mit Sprache und Beschreibung: Die IDs (ohne -1 = „Aus“) liefert der Player, Sprache, Beschreibung und
     * Kanalzahl stehen in den Track-Infos des Mediums (NSDictionary je Spur, Schlüssel als VLCKit-Konstanten).
     */
    private fun tracks(type: String?, indexes: List<*>?, current: Int): List<MediaTrack> {
        val ids = indexes.orEmpty().mapNotNull { it.asInt() }.filter { it >= 0 }
        if (ids.isEmpty()) return emptyList()
        val infos = player.media?.tracksInformation.orEmpty()
            .filter { it.entry(VLCMediaTracksInformationType) == type }
            .associateBy { it.entry(VLCMediaTracksInformationId).asInt() }
        return ids.mapIndexed { i, id ->
            val info = infos[id]
            val language = info.entry(VLCMediaTracksInformationLanguage) as? String
            val description = info.entry(VLCMediaTracksInformationDescription) as? String
            val channels = info.entry(VLCMediaTracksInformationAudioChannelsNumber).asInt() ?: 0
            MediaTrack(id, trackLabel(language, description, channels, i), language, id == current, channels)
        }
    }

    private var fit = VideoFit.FIT
    private var viewSize = IntSize.Zero

    override fun setVideoFit(fit: VideoFit) {
        this.fit = fit
        applyFit()
    }

    /**
     * Füllen = auf das Seitenverhältnis der Bildfläche beschneiden, Strecken = Bild auf dieses Verhältnis zwingen.
     * libVLC kopiert die Zeichenkette, der Speicher darf danach frei werden. Beim Drehen neu (andere Fläche).
     */
    private fun applyFit() {
        val ratio = viewSize.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}:${it.height}" }
        memScoped {
            player.videoCropGeometry = if (fit == VideoFit.ZOOM && ratio != null) ratio.cstr.ptr else null
            player.videoAspectRatio = if (fit == VideoFit.FILL && ratio != null) ratio.cstr.ptr else null
        }
    }

    /**
     * Bildfläche als UIKit-Ansicht. `interactionMode = null`: nicht interaktiv – sonst landen Tipper bei der UIView,
     * die Bedienleiste (mit „Schließen“) erschiene nie.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Video(modifier: Modifier) {
        UIKitView(
            factory = { view },
            modifier = modifier.onSizeChanged { viewSize = it; applyFit() },
            properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
        )
    }
}

/** Zahl aus VLCKit: kommt je nach Weg als NSNumber oder schon als Kotlin-Zahl an. */
private fun Any?.asInt(): Int? = when (this) {
    is Number -> toInt()
    is NSNumber -> intValue
    else -> null
}

/** Wert einer Spur-Info (NSDictionary oder bereits als Kotlin-Map gebrückt). */
@Suppress("UNCHECKED_CAST")
private fun Any?.entry(key: String?): Any? = when {
    key == null -> null
    this is Map<*, *> -> (this as Map<Any?, Any?>)[key]
    this is NSDictionary -> objectForKey(key)
    else -> null
}
