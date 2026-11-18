package de.vincentsberger.iptvplayer.desktop.player

import de.vincentsberger.iptvplayer.shared.data.Storage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.sun.jna.NativeLibrary
import de.vincentsberger.iptvplayer.shared.data.USER_AGENT
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer

object VlcSetup {
    /** true, wenn eine VLC-Engine gefunden wurde (mitgeliefert oder installiert). */
    val available: Boolean by lazy { discover() }

    private fun discover(): Boolean {
        // 1. Mitgelieferte VLC-Dateien (resources/windows/vlc) – für die Store-Version.
        System.getProperty("compose.application.resources.dir")?.let { res ->
            val dir = File(res, "vlc")
            if (File(dir, "libvlc.dll").exists()) {
                NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
                NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
                return true
            }
        }
        // 2. Auf dem PC installiertes VLC (praktisch beim Entwickeln).
        return try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            false
        }
    }
}

/**
 * VLC-Player, der jedes Videobild als [ImageBitmap] liefert. So kann Compose
 * Bedienelemente frei über dem Video anzeigen.
 */
class VlcPlayer {
    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    var finished by mutableStateOf(false); private set

    private val factory = MediaPlayerFactory("--no-video-title-show", "--quiet")
    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    private var info: ImageInfo? = null
    private var bytes = ByteArray(0)

    init {
        val surface = CallbackVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    info = ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.OPAQUE)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                    bytes = ByteArray(buffers[0].limit())
                }
            },
            object : RenderCallback {
                override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                    val imageInfo = info ?: return
                    val buffer = nativeBuffers[0]
                    if (bytes.size != buffer.limit()) bytes = ByteArray(buffer.limit())
                    buffer.rewind()
                    buffer.get(bytes)
                    buffer.rewind()
                    val bmp = Bitmap()
                    bmp.allocPixels(imageInfo)
                    bmp.installPixels(bytes)
                    frame = bmp.asComposeImageBitmap()
                }
            },
            true,
            VideoSurfaceAdapters.getVideoSurfaceAdapter(),
        )
        player.videoSurface().set(surface)
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) { playing = true; buffering = false; error = null }
            override fun paused(mediaPlayer: MediaPlayer) { playing = false }
            override fun stopped(mediaPlayer: MediaPlayer) { playing = false }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) { buffering = newCache < 100f }
            override fun finished(mediaPlayer: MediaPlayer) { playing = false; finished = true }
            override fun error(mediaPlayer: MediaPlayer) {
                playing = false; buffering = false
                error = "Der Stream konnte nicht abgespielt werden."
            }
        })
    }

    /** Bevorzugte Tonspur-Sprache (z. B. „deu“), gilt ab dem nächsten [play]. */
    var preferredAudio: String? = null
    /** Bevorzugte Untertitel-Sprache, [Storage.SUBTITLES_OFF] = aus, null = Vorgabe des Streams. */
    var preferredSubtitles: String? = null

    /** Vorlieben für den laufenden Titel schon angewendet? (einmal pro [play], danach zählt die Handauswahl) */
    private var preferencesPending = false

    fun play(url: String, startMs: Long = 0) {
        error = null; finished = false; buffering = true; frame = null
        preferencesPending = preferredAudio != null || preferredSubtitles != null
        val options = buildList {
            add(":http-user-agent=$USER_AGENT")
            add(":network-caching=1500")
            if (startMs > 0) add(":start-time=${startMs / 1000}")
        }
        player.media().play(url, *options.toTypedArray())
    }

    fun togglePause() = player.controls().pause()
    fun resume() = player.controls().play()
    fun pause() = player.controls().setPause(true)
    fun seekBy(deltaMs: Long) = player.controls().skipTime(deltaMs)
    fun seekTo(ms: Long) = player.controls().setTime(ms.coerceAtLeast(0))

    val time: Long get() = player.status().time().coerceAtLeast(0)
    val length: Long get() = player.status().length().coerceAtLeast(0)

    var volume: Int
        get() = player.audio().volume()
        set(v) { player.audio().setVolume(v.coerceIn(0, 150)) }

    /** Ton-Verzögerung in ms: positiv = Ton später (Bild eilt voraus), negativ = Ton früher. */
    var audioDelayMs: Int
        get() = (player.audio().delay() / 1000).toInt()
        set(v) { player.audio().setDelay(v * 1000L) }

    // ---------- Ton und Untertitel ----------

    /** Tonspuren des laufenden Streams (leer, solange VLC sie noch nicht kennt). */
    fun audioTracks(): List<VlcTrack> {
        val current = player.audio().track()
        return player.media().info()?.audioTracks().orEmpty().mapIndexed { i, t ->
            VlcTrack(t.id(), trackLabel(t.language(), t.description(), t.channels(), i), t.language(), t.id() == current)
        }
    }

    fun subtitleTracks(): List<VlcTrack> {
        val current = player.subpictures().track()
        return player.media().info()?.textTracks().orEmpty().mapIndexed { i, t ->
            VlcTrack(t.id(), trackLabel(t.language(), t.description(), 0, i), t.language(), t.id() == current)
        }
    }

    fun selectAudio(track: VlcTrack) { player.audio().setTrack(track.id); preferencesPending = false }

    /** null schaltet die Untertitel aus. */
    fun selectSubtitles(track: VlcTrack?) { player.subpictures().setTrack(track?.id ?: -1); preferencesPending = false }

    /**
     * Gespeicherte Sprachen anwenden, sobald VLC die Spuren kennt (die VLC-Optionen :audio-language/:sub-language
     * greifen bei vielen Dateien nicht). Regelmäßig aus der Oberfläche aufrufen; wirkt nur einmal pro Titel.
     */
    fun applyPreferencesIfPending() {
        if (!preferencesPending) return
        val audio = audioTracks()
        if (audio.isEmpty()) return // Spuren noch unbekannt – beim nächsten Aufruf erneut
        preferencesPending = false
        preferredAudio?.let { lang -> audio.firstOrNull { sameLanguage(it.language, lang) }?.let { player.audio().setTrack(it.id) } }
        when (val sub = preferredSubtitles) {
            null -> Unit
            Storage.SUBTITLES_OFF -> player.subpictures().setTrack(-1)
            else -> subtitleTracks().firstOrNull { sameLanguage(it.language, sub) }?.let { player.subpictures().setTrack(it.id) }
        }
    }

    fun release() {
        player.controls().stop()
        player.release()
        factory.release()
    }
}
