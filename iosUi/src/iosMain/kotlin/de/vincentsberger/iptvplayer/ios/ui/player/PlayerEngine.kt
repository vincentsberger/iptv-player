package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.vincentsberger.iptvplayer.shared.MediaTrack
import de.vincentsberger.iptvplayer.shared.VideoFit

enum class PlayerState { IDLE, OPENING, BUFFERING, PLAYING, PAUSED, ERROR, ENDED }

/**
 * Wiedergabe für den Player-Bildschirm. Umgesetzt in :iosApp mit MobileVLCKit (VlcController); die Oberfläche kennt
 * nur diese Schnittstelle und bleibt dadurch unter Linux kompilierbar. Alle Werte sind Compose-State.
 */
interface PlayerEngine {
    val state: PlayerState
    val playing: Boolean get() = state == PlayerState.PLAYING
    val paused: Boolean get() = state == PlayerState.PAUSED
    val finished: Boolean get() = state == PlayerState.ENDED
    val failed: Boolean get() = state == PlayerState.ERROR
    val buffering: Boolean get() = state == PlayerState.OPENING || state == PlayerState.BUFFERING
    /** Erstes Bild ist da (bzw. Ton läuft bei Radiosendern) – startet den Countdown der Zap-Karte. */
    val hasPicture: Boolean
    val timeMs: Long
    val lengthMs: Long
    /** Letzter Fortschritt der Wiedergabezeit bzw. Start des aktuellen Streams (monotonicMs) – für StreamRecovery. */
    val lastProgressAt: Long
    val startedAt: Long

    /** Startet [url] ab [startMs]; [keepFrame] lässt beim Neuverbinden das letzte Bild stehen. */
    fun play(url: String, startMs: Long = 0, keepFrame: Boolean = false)
    fun togglePause()
    fun pause()
    fun resume()
    fun seekTo(ms: Long)
    fun stop()
    fun release()

    /** Ton- und Untertitelspuren des laufenden Streams – leer, solange VLC sie noch nicht kennt. Kein Compose-State: abfragen. */
    val audioTracks: List<MediaTrack> get() = emptyList()
    val subtitleTracks: List<MediaTrack> get() = emptyList()
    fun selectAudio(id: Int) {}
    /** null schaltet die Untertitel aus. */
    fun selectSubtitles(id: Int?) {}
    /** Bildformat: einpassen, füllen (beschneidet) oder strecken – gilt bis zur nächsten Änderung, auch beim Drehen. */
    fun setVideoFit(fit: VideoFit) {}

    /** Bildfläche; dieselbe UIView bleibt beim Drehen erhalten. */
    @Composable
    fun Video(modifier: Modifier)
}
