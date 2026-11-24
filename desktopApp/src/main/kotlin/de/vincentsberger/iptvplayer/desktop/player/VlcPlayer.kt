package de.vincentsberger.iptvplayer.desktop.player

import de.vincentsberger.iptvplayer.shared.SubtitleChoice
import de.vincentsberger.iptvplayer.shared.preferredAudioTrack
import de.vincentsberger.iptvplayer.shared.preferredSubtitles
import de.vincentsberger.iptvplayer.shared.trackLabel
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.player.PlaybackEngine
import de.vincentsberger.iptvplayer.shared.player.stats.EngineStats

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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object VlcSetup {
    /** true, wenn eine VLC-Engine gefunden wurde (mitgeliefert oder installiert). */
    val available: Boolean by lazy { discover() }

    /** Die mitgelieferte Engine wird genutzt – dann darf vlcj nicht zusätzlich ein installiertes VLC suchen. */
    var bundled = false
        private set

    private fun discover(): Boolean {
        // 1. Mitgelieferte VLC-Dateien (resources/windows/vlc) – für die Store-Version.
        System.getProperty("compose.application.resources.dir")?.let { res ->
            val dir = File(res, "vlc")
            if (File(dir, "libvlc.dll").exists()) {
                NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
                NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
                // Wirklich laden statt nur Dateien zu prüfen: Ein defektes Paket fällt sonst erst im Player auf (Absturz).
                if (runCatching { NativeLibrary.getInstance("libvlc") }.isSuccess) {
                    bundled = true
                    return true
                }
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
class VlcPlayer : PlaybackEngine {
    var frame by mutableStateOf<ImageBitmap?>(null); private set
    /** Höhe des laufenden Videos (für das Auflösungs-Abzeichen); 0 = unbekannt oder kein Bild (Radio). */
    var videoHeight by mutableStateOf(0); private set
    var videoWidth by mutableStateOf(0); private set
    override var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    override var finished by mutableStateOf(false); private set
    /** Vom Nutzer angehalten – dann ist ein stehendes Bild kein Hänger. */
    override var paused by mutableStateOf(false); private set

    /** Zeitpunkt ([monotonicMs]) des letzten Bildes bzw. Zeitfortschritts – für die Hänger-Erkennung. */
    @Volatile override var lastProgressAt = NO_PROGRESS; private set
    /** Zeitpunkt des letzten [play] – ab hier läuft die Frist, bis das erste Bild kommen muss. */
    override var startedAt = monotonicMs(); private set

    // Mitgelieferte Engine: ohne vlcj-Suche (null) – sonst findet vlcj zusätzlich ein installiertes VLC und setzt
    // dessen Plugin-Pfad, und beide Plugin-Sätze landen gemischt im Prozess.
    private val factory = if (VlcSetup.bundled) MediaPlayerFactory(null as NativeDiscovery?, "--no-video-title-show", "--quiet")
        else MediaPlayerFactory("--no-video-title-show", "--quiet")
    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    private var info: ImageInfo? = null
    private var bytes = ByteArray(0)

    /**
     * Ausgegebene, noch nicht freigegebene Bilder. Jede Bitmap belegt nativen Speicher (Full HD ≈ 8 MB), den Javas
     * Garbage Collector nicht sieht – ohne close() wuchs die App bei HD-Streams in Sekunden auf mehrere GB.
     * Freigegeben wird nur, was die Oberfläche sicher nicht mehr zeichnet (siehe [markShown]); ein Freigeben nach
     * Anzahl war falsch: Stockt die Oberfläche kurz (z. B. beim Öffnen eines Menüs), zeichnete sie ein schon
     * freigegebenes Bild → Absturz in Skia.
     */
    private val pending = ArrayDeque<Pair<Bitmap, ImageBitmap>>()
    private var previousShown: Bitmap? = null
    /** Bilder, die VLC geliefert hat, seit die Oberfläche zuletzt eines gezeigt hat. */
    @Volatile private var undrawn = 0

    init {
        val surface = CallbackVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    info = ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.OPAQUE)
                    videoHeight = sourceHeight; videoWidth = sourceWidth
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                    bytes = ByteArray(buffers[0].limit())
                }
            },
            object : RenderCallback {
                override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                    val imageInfo = info ?: return
                    // Oberfläche hängt hinterher: Bild auslassen, statt nativen Speicher anzuhäufen.
                    if (undrawn > MAX_UNDRAWN) return
                    val buffer = nativeBuffers[0]
                    if (bytes.size != buffer.limit()) bytes = ByteArray(buffer.limit())
                    buffer.rewind()
                    buffer.get(bytes)
                    buffer.rewind()
                    val bmp = Bitmap()
                    bmp.allocPixels(imageInfo)
                    bmp.installPixels(bytes)
                    val image = bmp.asComposeImageBitmap()
                    synchronized(pending) { pending.addLast(bmp to image) }
                    undrawn++
                    lastProgressAt = monotonicMs()
                    frame = image
                }
            },
            true,
            VideoSurfaceAdapters.getVideoSurfaceAdapter(),
        )
        player.videoSurface().set(surface)
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) { playing = true; paused = false; buffering = false; error = null }
            override fun paused(mediaPlayer: MediaPlayer) { playing = false; paused = true }
            // Auch reine Radiosender (ohne Bild) gelten so als laufend.
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                lastProgressAt = monotonicMs()
            }
            override fun stopped(mediaPlayer: MediaPlayer) { playing = false }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) { buffering = newCache < 100f }
            override fun finished(mediaPlayer: MediaPlayer) { playing = false; finished = true }
            override fun error(mediaPlayer: MediaPlayer) {
                playing = false; buffering = false
                error = "Der Stream konnte nicht abgespielt werden."
            }
        })
    }

    /** Zuletzt gelesene Rohwerte; nur [refreshStats] (vlcj-Ereignis-Thread) schreibt, [stats] liest billig von jedem Thread. */
    @Volatile private var cachedStats = EngineStats()
    /** Nur solange Anzeige oder Fenster laufen, wird VLC überhaupt gefragt: ein Timer, der einmal pro Sekunde liest. */
    @Volatile private var statsTimer: ScheduledExecutorService? = null

    /**
     * `timeChanged` bleibt beim Puffern aus – gerade dann sollen die Werte weiterlaufen, also liest ein eigener Takt.
     * Die Abfrage geht über [MediaPlayer.submit] auf den vlcj-Ereignis-Thread (Aufrufe in libvlc nicht aus fremden Threads/Callbacks).
     */
    @Synchronized
    override fun setStatsActive(active: Boolean) {
        statsTimer?.shutdownNow()
        statsTimer = null
        if (!active) return
        cachedStats = EngineStats() // nichts Altes zeigen
        statsTimer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vlc-stats").apply { isDaemon = true } }.also {
            val read = Runnable { runCatching { player.submit { refreshStats(player) } } }
            it.scheduleWithFixedDelay(read, STATS_INTERVAL_MS, STATS_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Rohwerte für die Verbindungsanzeige: der Wert vom letzten Lesen, VLC wird hier nicht gefragt. */
    override fun stats(): EngineStats = cachedStats

    /** Liest Statistik und Spurinfo (läuft auf dem vlcj-Ereignis-Thread). Der Puffer ist in VLC nicht abfragbar. */
    private fun refreshStats(mediaPlayer: MediaPlayer) {
        if (statsTimer == null) return
        val info = mediaPlayer.media().info() ?: return
        val counters = info.statistics()
        val video = info.videoTracks().firstOrNull()
        val selectedAudio = mediaPlayer.audio().track()
        val audio = info.audioTracks().firstOrNull { it.id() == selectedAudio }
        val fps = video?.takeIf { it.frameRate() > 0 && it.frameRateBase() > 0 }?.let { it.frameRate().toFloat() / it.frameRateBase() }
        cachedStats = EngineStats(
            // libvlc zählt in einem 32-Bit-Zähler; als vorzeichenlos lesen (ein Überlauf wirkt wie ein Neustart des Zählers).
            bytesReceived = counters?.let { it.inputBytesRead().toLong() and UINT_MASK },
            droppedFrames = counters?.picturesLost()?.toLong(),
            videoCodec = video?.codecName() ?: video?.codecDescription(),
            width = video?.width()?.takeIf { it > 0 },
            height = video?.height()?.takeIf { it > 0 },
            fps = fps,
            audioCodec = audio?.codecName() ?: audio?.codecDescription(),
            audioChannels = audio?.channels()?.takeIf { it > 0 },
            audioLanguage = audio?.language()?.takeIf { it.isNotBlank() },
        )
    }

    /** Bevorzugte Tonspur-Sprache (z. B. „deu“), gilt ab dem nächsten [play]. */
    var preferredAudio: String? = null
    /** Bevorzugte Untertitel-Sprache, [Storage.SUBTITLES_OFF] = aus, null = Vorgabe des Streams. */
    var preferredSubtitles: String? = null

    /** Vorlieben für den laufenden Titel schon angewendet? (einmal pro [play], danach zählt die Handauswahl) */
    private var preferencesPending = false

    /** Schnittstelle der [PlaybackEngine]: spielt [url] (die Quelle des Titels). */
    override fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean) = play(url, startMs, keepFrame)

    /**
     * [keepFrame]: beim automatischen Neuverbinden das letzte Bild stehen lassen, statt kurz schwarz zu zeigen.
     */
    fun play(url: String, startMs: Long = 0, keepFrame: Boolean = false) {
        error = null; finished = false; paused = false; buffering = true
        cachedStats = EngineStats() // Zähler und Spuren des vorigen Streams gelten nicht mehr
        if (!keepFrame) { frame = null; videoHeight = 0; videoWidth = 0 }
        startedAt = monotonicMs()
        lastProgressAt = NO_PROGRESS // erst echte Bilder bzw. Zeitfortschritt des neuen Streams zählen
        preferencesPending = preferredAudio != null || preferredSubtitles != null
        val options = buildList {
            add(":http-user-agent=$USER_AGENT")
            add(":network-caching=1500")
            // VLC selbst bei abgerissener HTTP-Verbindung neu verbinden lassen (häufig bei IPTV-Anbietern).
            add(":http-reconnect")
            if (startMs > 0) add(":start-time=${startMs / 1000}")
        }
        player.media().play(url, *options.toTypedArray())
    }

    fun togglePause() = player.controls().pause()
    fun resume() = player.controls().play()
    fun pause() = player.controls().setPause(true)
    fun seekBy(deltaMs: Long) = player.controls().skipTime(deltaMs)
    override fun seekTo(ms: Long) = player.controls().setTime(ms.coerceAtLeast(0))

    val time: Long get() = player.status().time().coerceAtLeast(0)
    val length: Long get() = player.status().length().coerceAtLeast(0)

    override val timeMs: Long get() = time
    override val lengthMs: Long get() = length
    override val failed: Boolean get() = error != null

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
            VlcTrack(t.id(), trackLabel(t.language(), t.description(), t.channels(), i), t.language(), t.id() == current, t.channels())
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
        preferredAudioTrack(audio, preferredAudio)?.let { player.audio().setTrack(it.id) }
        when (val sub = preferredSubtitles(subtitleTracks(), preferredSubtitles)) {
            SubtitleChoice.Keep -> Unit
            SubtitleChoice.Off -> player.subpictures().setTrack(-1)
            is SubtitleChoice.Track -> player.subpictures().setTrack(sub.id)
        }
    }

    fun release() {
        setStatsActive(false)
        player.controls().stop()
        player.release()
        factory.release()
        frame = null
        synchronized(pending) { pending.forEach { it.first.close() }; pending.clear() }
        previousShown = null
    }

    /**
     * Die Oberfläche zeigt jetzt [shown] (Aufruf aus einem SideEffect, also auf dem UI-Thread vor dem Zeichnen).
     * Gezeichnet wurde bis eben höchstens das vorher gezeigte Bild – alles Ältere ist sicher frei.
     */
    fun markShown(shown: ImageBitmap) {
        undrawn = 0
        synchronized(pending) {
            val shownBitmap = pending.firstOrNull { it.second === shown }?.first ?: return
            val keep = setOfNotNull(shownBitmap, previousShown)
            val it = pending.iterator()
            while (it.hasNext()) {
                val (bmp, _) = it.next()
                if (bmp === shownBitmap) break // Neuere als das gezeigte bleiben (kommen gleich dran)
                if (bmp !in keep) { bmp.close(); it.remove() }
            }
            previousShown = shownBitmap
        }
    }

    private companion object {
        /** Höchstens so viele Bilder liegen bereit, solange die Oberfläche nicht zeichnet. */
        const val MAX_UNDRAWN = 3
        /** Noch nichts gelaufen (weit in der Vergangenheit, ohne Überlauf beim Rechnen). */
        const val NO_PROGRESS = Long.MIN_VALUE / 2
        const val STATS_INTERVAL_MS = 1_000L
        const val UINT_MASK = 0xFFFFFFFFL
    }
}

/** Uhr, die nicht springt (Zeitumstellung, Systemzeit geändert). */
fun monotonicMs(): Long = System.nanoTime() / 1_000_000
