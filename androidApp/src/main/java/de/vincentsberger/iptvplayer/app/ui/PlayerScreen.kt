package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.CalendarMonth
import de.vincentsberger.iptvplayer.shared.epgExcerpt
import androidx.media3.common.VideoSize
import de.vincentsberger.iptvplayer.shared.mediaBadges
import de.vincentsberger.iptvplayer.shared.isPausedForInfo
import de.vincentsberger.iptvplayer.shared.PauseStage
import de.vincentsberger.iptvplayer.shared.PauseInfo
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.player.PlaybackController
import de.vincentsberger.iptvplayer.shared.player.stats.ConnectionQuality
import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.app.download.Downloads
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import de.vincentsberger.iptvplayer.shared.formatTime
import de.vincentsberger.iptvplayer.shared.player.endsAt
import de.vincentsberger.iptvplayer.shared.player.seekMultiplier
import de.vincentsberger.iptvplayer.shared.epgProgress

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, vm: AppState): ExoPlayer {
    return ExoPlayer.Builder(context)
        // Liest Heruntergeladenes aus dem Gerätespeicher (auch offline), alles andere aus dem Netz.
        .setMediaSourceFactory(DefaultMediaSourceFactory(Downloads.playerDataSource(context)))
        .build()
        .apply {
            playWhenReady = true
            // Gewählte Ton-/Untertitelsprache gilt auch für den nächsten Film oder Sender.
            trackSelectionParameters = preferredTrackParameters(trackSelectionParameters, vm.audioLanguage, vm.subtitleLanguage)
        }
}

/** Stream [url] (eine der Quellen von [item]) mit Titel und Bild – für „Läuft gerade“ und Sprachsteuerung. */
internal fun mediaItem(url: String, item: Item): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(splitProviderTag(item.name).name)
        .apply { item.logo?.takeIf { it.isNotBlank() }?.let { setArtworkUri(android.net.Uri.parse(it)) } }
        .build()
    val b = MediaItem.Builder().setUri(url).setMediaMetadata(meta)
    if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    return b.build()
}

/**
 * Was die MediaSession nach außen anbietet (Alexa, Bluetooth-Fernbedienungen): alles außer Weiter/Zurück – der Player
 * kennt nur das laufende Element; „Zurück“ spränge an den Filmanfang und überschriebe die Weiterschauen-Stelle.
 */
internal fun sessionPlayerCommands(available: Player.Commands): Player.Commands = available.buildUpon()
    .removeAll(
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )
    .build()

/**
 * Der Player, wie die MediaSession ihn sieht: ohne [sessionPlayerCommands]-Ausnahmen. Ein Callback mit eingeschränkten
 * Befehlen reicht nicht – er gilt nur für Media3-Controller, nicht für die System-Sitzung (Alexa, Bluetooth).
 */
@OptIn(UnstableApi::class)
private class SessionPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands = sessionPlayerCommands(super.getAvailableCommands())
    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
}

/**
 * Vollbild-Player.
 * Live TV:      ▲ ▼ oder CH+/CH− Sender wechseln · OK Info
 * Filme/Serien: OK Pause · ◀ 10 s zurück · ▶ 30 s vor · bei Serien ▲ ▼ Folge wechseln
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppState, items: List<Item>, startIndex: Int, startPosition: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { ExoPlaybackEngine(buildPlayer(context, vm)) }
    val player = engine.player
    // Playlist, Quellen, Zappen, Verlauf, Ziffernwahl, nächste Folge, Neuverbinden: gemeinsamer Ablauf (shared), hier nur Anzeige.
    val c = remember { PlaybackController(vm, engine, items, startIndex, startPosition, System::currentTimeMillis, SystemClock::elapsedRealtime) }
    val item = c.item
    val isLive = c.isLive
    val isEpisodes = c.isEpisodes && c.items.size > 1

    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(infoVisibleAtStart(items[startIndex].section == Section.LIVE)) }
    val position = c.positionMs
    val duration = c.durationMs
    /** Startposition des laufenden Titels – „Fortgesetzt bei …“ nur, bis die Leiste das erste Mal wieder ausgeblendet wurde. */
    var resumedAt by remember(c.startedFromMs) { mutableLongStateOf(c.startedFromMs) }
    var clock by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    /** Tatsächlich gerenderte Bildgröße (für das Auflösungs-Abzeichen); UNKNOWN bei Radio oder vor dem ersten Bild. */
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var optionsOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    /** Programm des Senders mit „Sendung verpasst“ (aus dem Optionen-Menü). */
    var programmeOpen by remember { mutableStateOf(false) }
    val overlayOpen = optionsOpen || guideOpen || programmeOpen
    var videoFit by remember { mutableStateOf(vm.videoFit) }
    /** OK wird gerade lange gehalten (die Taste wiederholt sich) → beim Loslassen öffnet das Menü. */
    var okHeld by remember { mutableStateOf(false) }

    val pause = remember { PauseInfo(System::currentTimeMillis) }

    // Zurück springt erst zum gesammelten Spul-Ziel – sonst speichert „Weiterschauen“ die alte Stelle.
    BackHandler { c.seek.commitNow()?.let(engine::seekTo); onBack() }
    // Während des Countdowns bricht „Zurück“ nur den Countdown ab (später registriert → hat Vorrang).
    BackHandler(enabled = c.nextIn != null) { c.cancelNext() }
    BackHandler(enabled = overlayOpen) { optionsOpen = false; guideOpen = false; programmeOpen = false }
    // Das Statistik-Fenster schließt zuerst (später registriert → hat Vorrang), das Menü bleibt.
    BackHandler(enabled = statsOpen && !overlayOpen) { statsOpen = false }
    // Abfrage nur, solange Anzeige oder Fenster sie brauchen.
    LaunchedEffect(vm.showConnectionIndicator || statsOpen, c.items, c.index) { if (vm.showConnectionIndicator || statsOpen) c.runStats() }
    // Menü/Senderliste zu → Fokus zurück an den Player, damit die Fernbedienung wieder steuert.
    LaunchedEffect(overlayOpen) { if (!overlayOpen) runCatching { focus.requestFocus() } }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
            override fun onRenderedFirstFrame() { c.zap.onFirstFrame() }
            // Radiosender haben kein Bild: dann zählt der Start der Wiedergabe.
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) c.zap.onFirstFrame() }
        }
        player.addListener(listener)
        onDispose {
            c.saveNow()
            player.removeListener(listener)
            engine.release()
        }
    }

    // MediaSession: Alexa („Pause“, „spul vor“), Medientasten von Bluetooth-Fernbedienungen, „Läuft gerade“ im System.
    // Steht hinter dem Player-Effekt → wird vor ihm aufgeräumt (Compose räumt in umgekehrter Reihenfolge auf).
    DisposableEffect(Unit) {
        val session = MediaSession.Builder(context, SessionPlayer(player)).build()
        onDispose { session.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    c.saveNow()
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Viele Anbieter erlauben nur eine Verbindung: Solange etwas aus dem Netz läuft, ruhen die Downloads.
    LaunchedEffect(item.url) { Downloads.setPaused(vm.pauseDownloadsWhilePlaying && !Downloads.isDone(item.url)) }
    DisposableEffect(Unit) { onDispose { Downloads.setPaused(false) } }

    // Live: Zapp-Karte statt der ganzen Leiste; Filme/Folgen: Leiste wie bisher (infoTick), Infos für die Pause-Info vorladen (im Controller).
    LaunchedEffect(c.items, c.index) {
        videoSize = VideoSize.UNKNOWN // neuer Stream: Größe erst mit seinem ersten Bild (Radio bleibt ohne)
        c.startCurrent()
        if (!c.isLive) infoTick++
    }
    // Eigener Effekt: Die Überwachung läuft schon, während startCurrent noch das EPG lädt.
    LaunchedEffect(c.items, c.index) { c.watch() }

    // Nächste Folge automatisch – mit 10-Sekunden-Countdown, abbrechbar, in den Einstellungen abschaltbar
    LaunchedEffect(engine.finished, c.index, c.nextCancelled) { c.runAutoNext() }

    // Senderwahl per Zifferntasten: kurz nach der letzten Ziffer umschalten
    LaunchedEffect(c.numberInput.digits) { c.commitDigits { Feedback.show("Sender $it nicht gefunden") } }

    // Pause-Info: angehalten (nicht puffernd, ohne Fehler) → kompakt, nach 5 s ausführlich.
    val failed = engine.failed || c.reconnecting || c.connectionLost
    val paused = isPausedForInfo(isLive, engine.playing, engine.buffering, ended = engine.finished, failed = failed)
    LaunchedEffect(paused) { if (paused) pause.onPaused() else pause.onResumed() }
    LaunchedEffect(pause.stage) { while (pause.stage == PauseStage.COMPACT) { delay(250); pause.tick() } }

    // Spulen mit Ziel: einmal springen, sobald keine Taste mehr kommt; beim Folgenwechsel verwerfen.
    LaunchedEffect(Unit) { c.seek.runCommits(engine::seekTo) }

    // Zapp-Karte: nach dem ersten Bild regelmäßig prüfen, ob sie ausblenden soll; Fehlerdialog verdrängt sie (im Controller).
    LaunchedEffect(c.zap.channel) { while (c.zap.channel != null) { delay(250); c.zap.tick() } }

    LaunchedEffect(infoTick) {
        // Erster Lauf beim Öffnen: Live startet ohne Info-Leiste (Zapp-Karte), erst ein Tastendruck zeigt sie.
        if (infoTick == 0 && !infoVisibleAtStart(isLive)) return@LaunchedEffect
        showInfo = true
        delay(if (resumedAt > 0) 7000 else 5000)
        showInfo = false
        resumedAt = 0
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        val fmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
        c.runClock(onSleep = onBack) { clock = fmt.format(Date()) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { ev ->
                // Zurück bei offenem Menü selbst abfangen: Compose würde die Taste sonst als
                // „Fokus verlassen“ verbrauchen, und der BackHandler käme nie an die Reihe.
                if (overlayOpen && ev.key == Key.Back) {
                    if (ev.type == KeyEventType.KeyUp) { optionsOpen = false; guideOpen = false; programmeOpen = false }
                    return@onKeyEvent true
                }
                // OK lange drücken öffnet das Menü – auf jeder Fernbedienung, auch ohne ☰-Taste.
                // Kurzer Druck wirkt wie bisher, aber erst beim Loslassen; das Menü öffnet ebenfalls erst beim
                // Loslassen, sonst landen die Tastenwiederholungen im ersten Menüeintrag und wählen ihn aus.
                if (!overlayOpen && isOkKey(ev.key)) {
                    if (ev.type == KeyEventType.KeyDown) {
                        if (ev.nativeKeyEvent.repeatCount > 0) okHeld = true
                        return@onKeyEvent true
                    }
                    if (ev.type == KeyEventType.KeyUp && okHeld) {
                        okHeld = false
                        optionsOpen = true
                        return@onKeyEvent true
                    }
                } else if (ev.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                // Offenes Menü / offene Senderliste: Pfeile und OK gehören ihnen, ☰ schließt.
                if (overlayOpen) {
                    if (playerActionFor(ev.key, isLive, isEpisodes) == PlayerAction.OPTIONS) { optionsOpen = false; guideOpen = false; programmeOpen = false; return@onKeyEvent true }
                    return@onKeyEvent false
                }
                digitOf(ev.key)?.let { d ->
                    if (isLive) c.numberInput.type(d)
                    if (showsInfoOnKey(null, isDigit = true, isLive = isLive)) infoTick++
                    return@onKeyEvent true
                }
                val action = playerActionFor(ev.key, isLive, isEpisodes) ?: return@onKeyEvent false
                // Spulen sammeln; jede andere Taste springt erst zum gesammelten Ziel.
                if (action != PlayerAction.REWIND && action != PlayerAction.FORWARD) c.seek.commitNow()?.let(engine::seekTo)
                // Countdown sichtbar: OK startet die nächste Folge sofort.
                if (c.nextIn != null && action == PlayerAction.TOGGLE_PLAY) { c.switchTo(c.index + 1); return@onKeyEvent true }
                when (action) {
                    PlayerAction.LAST_CHANNEL -> c.lastChannel()
                    PlayerAction.SLEEP_TIMER -> vm.cycleSleepTimer()
                    PlayerAction.OPTIONS -> { optionsOpen = true; return@onKeyEvent true }
                    PlayerAction.PREVIOUS -> c.switchTo(c.index - 1)
                    PlayerAction.NEXT -> c.switchTo(c.index + 1)
                    PlayerAction.SHOW_INFO -> Unit
                    PlayerAction.TOGGLE_PLAY -> when {
                        c.connectionLost -> c.retry()
                        isLive -> { guideOpen = true; return@onKeyEvent true } // Live: OK öffnet die Senderliste
                        engine.playing -> player.pause()
                        else -> player.play()
                    }
                    PlayerAction.PLAY -> player.play()
                    PlayerAction.PAUSE -> player.pause()
                    // Gehaltene Taste: Sprünge werden größer (10 s → 30 s → 1 min bzw. 30 s → 1,5 min → 3 min).
                    PlayerAction.REWIND -> { pause.dismissFull(); c.seek.press(-10_000L * seekMultiplier(ev.nativeKeyEvent.repeatCount), engine.timeMs, duration) }
                    PlayerAction.FORWARD -> { pause.dismissFull(); c.seek.press(30_000L * seekMultiplier(ev.nativeKeyEvent.repeatCount), engine.timeMs, duration) }
                }
                // Beim Senderwechsel erscheint die Info ohnehin, sonst Info-Leiste kurz einblenden.
                if (showsInfoOnKey(action, isDigit = false, isLive = isLive)) infoTick++
                true
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    isFocusable = false
                    resizeMode = resizeModeOf(videoFit)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeModeOf(videoFit) },
        )

        if ((engine.buffering && engine.error == null || c.reconnecting) && !c.connectionLost) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(64.dp)
                if (c.reconnecting) {
                    Text(
                        "Verbindung wird wiederhergestellt …", color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp).background(Color(0x99000000), Corner).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Spulen mit Ziel (eigener Balken mit Markierung; die Info-Leiste bleibt solange weg)
        c.seek.state?.let { SeekOverlay(it, durationMs = duration, positionMs = position) }

        // Pause-Info statt Pause-Symbol (nicht zusammen mit Info-Leiste, Optionen oder Countdown)
        if (pause.stage != PauseStage.NONE && !showInfo && !optionsOpen && c.nextIn == null) {
            val (title, subtitle) = vm.pauseTitles(item)
            PauseInfoView(
                title = title, subtitle = subtitle,
                info = vm.mediaInfoFor(item), stage = pause.stage,
                position = position, duration = duration, endsAt = endsAt(duration - position),
            )
        }

        if (c.connectionLost) {
            Column(
                Modifier.align(Alignment.Center).background(Surface1.copy(alpha = 0.95f), Corner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Headline("Wiedergabe nicht möglich", 24.sp)
                Text(engine.error ?: "Die Verbindung zum Stream ist abgebrochen.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    if (isLive) "OK  erneut versuchen     ·     ▲ ▼  anderer Sender" else "OK  erneut versuchen     ·     Zurück  zur Übersicht",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        // Zapp-Karte oben links (nur Live, nicht zusammen mit Leiste, Senderliste oder Optionen)
        val zapItem = c.zap.channel
        if (isLive && zapItem != null && !showInfo && !guideOpen && !optionsOpen && !c.connectionLost) {
            val now = c.epg.getOrNull(0)
            ZapCardView(zapItem, channelNumberOf(zapItem, c.items), now, now?.let(::epgProgress) ?: 0f, Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 40.dp))
        }

        // Eingetippte Sendernummer
        if (c.numberInput.digits.isNotEmpty()) {
            Text(
                c.numberInput.digits + "_", color = Color.White, fontFamily = Mono, fontSize = 48.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 56.dp).background(Color(0xCC000000), Corner).padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        // Countdown zur nächsten Folge
        c.nextIn?.let { secs ->
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 150.dp).width(360.dp).panel().padding(22.dp)) {
                Kicker("Nächste Folge in $secs s", Signal)
                Headline(c.items.getOrNull(c.index + 1)?.name.orEmpty(), 20.sp, Modifier.padding(top = 6.dp, bottom = 10.dp), maxLines = 2)
                Text("OK  jetzt ansehen     ·     Zurück  abbrechen", color = Muted, fontSize = 16.sp)
            }
        }

        if (programmeOpen && isLive) {
            ProgrammePanel(vm, item, onPlayed = { programmeOpen = false }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp))
        }
        if (optionsOpen) {
            PlayerOptionsPanel(
                audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO),
                subtitles = trackOptions(tracks, C.TRACK_TYPE_TEXT),
                sleepLeft = c.sleepLeft,
                onAudio = { o ->
                    selectTrack(player, C.TRACK_TYPE_AUDIO, o)
                    vm.audioLanguage = o.language
                },
                onSubtitles = { o ->
                    selectTrack(player, C.TRACK_TYPE_TEXT, o)
                    vm.subtitleLanguage = o?.language ?: Storage.SUBTITLES_OFF
                },
                onSleep = { vm.cycleSleepTimer() },
                statsOpen = statsOpen,
                onStats = { statsOpen = !statsOpen },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp),
            ) {
                if (isLive && item.hasEpg) TvButton(
                    if (item.archiveDays > 0) "Programm · Sendung verpasst" else "Programm",
                    Modifier.fillMaxWidth().padding(top = 12.dp).testTag("option:programme"), icon = Icons.Rounded.CalendarMonth,
                ) { optionsOpen = false; programmeOpen = true }
                SourceOption(c.sources.size, c.sourceIndex) { c.selectSource(it) }
                if (!isLive) DownloadOption(item)
                VideoFitOption(videoFit) { videoFit = it; vm.videoFit = it }
            }
        }

        if (statsOpen) StatsPanel(c.stats, Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 40.dp))
        if (vm.showConnectionIndicator) {
            ConnectionIndicator(c.stats?.quality ?: ConnectionQuality.UNKNOWN, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 56.dp))
        }

        // Senderliste über dem laufenden Bild (nur Live TV)
        if (guideOpen && isLive) {
            ChannelGuide(
                vm, playing = item, startCategory = vm.liveCategory,
                onPick = { newList, i ->
                    guideOpen = false
                    if (newList == c.items) c.switchTo(i) else c.openList(newList, i)
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        // Obere Leiste: Titel und Uhrzeit
        // Sind Leiste und Titel sichtbar, das ganze Bild leicht abdunkeln – Schrift bleibt auch auf hellen Szenen lesbar.
        AnimatedVisibility(showInfo && !guideOpen && c.seek.state == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x40000000)))
        }
        AnimatedVisibility(showInfo && !guideOpen && c.seek.state == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLive) {
                    Box(Modifier.background(Color(0x33FFFFFF), SmallCorner).padding(8.dp)) { ChannelLogo(item, 44.dp) }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (isLive) Text(channelNumberOf(item, c.items).toString(), color = Muted, fontSize = 16.sp, fontFamily = Mono)
                    else if (isEpisodes) Text("${c.index + 1} / ${c.items.size}", color = Muted, fontSize = 16.sp)
                    Headline(item.name, 28.sp, color = Color.White)
                }
                c.sleepLeft?.let {
                    Icon(Icons.Rounded.Bedtime, Modifier.padding(end = 6.dp), tint = Signal, size = 22.dp)
                    Text("$it min", color = Color.White, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier.padding(end = 24.dp))
                }
                MediaBadges(badgesOf(tracks, videoSize.width, videoSize.height), Modifier.padding(end = 20.dp))
                Text(clock, color = Color.White, fontSize = 24.sp, fontFamily = Mono)
            }
        }

        // Untere Leiste: Programm bzw. Fortschritt
        AnimatedVisibility(showInfo && !guideOpen && c.seek.state == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                    .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLive) {
                    val now = c.epg.getOrNull(0)
                    if (now != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge()
                            Text(now.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                        }
                        // Kurze Beschreibung der laufenden Sendung, wenn der Anbieter eine liefert.
                        epgExcerpt(now, max = 200)?.let {
                            Text(it, color = Muted, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(now.start, color = Muted, fontSize = 16.sp)
                            ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 12.dp), height = 5.dp)
                            Text(now.end, color = Muted, fontSize = 16.sp)
                        }
                        c.epg.getOrNull(1)?.let { Text("Danach  ${it.start}  ${it.title}", color = Muted, fontSize = 16.sp) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge()
                            Text("Keine Programminformationen", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Hints("▲ ▼  Sender wechseln", "OK  Senderliste", "OK halten  Optionen", "Zurück  Übersicht")
                } else {
                    if (resumedAt > 0) Text("Fortgesetzt bei ${formatTime(resumedAt)}  ·  ◀ zum Zurückspulen", color = Signal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val fraction = if (duration > 0) position.toFloat() / duration else 0f
                    ProgressBar(fraction, Modifier.fillMaxWidth(), height = 6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (engine.playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, tint = Color.White, size = 26.dp)
                        Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f))
                        if (duration > 0) Text("Noch ${formatTime(duration - position)}  ·  endet um ${endsAt(duration - position)}", color = Muted, fontSize = 17.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        HintIcon(Icons.Rounded.FastRewind, "10 s")
                        HintIcon(Icons.Rounded.PlayArrow, "OK Pause")
                        HintIcon(Icons.Rounded.FastForward, "30 s")
                        if (isEpisodes) Text("▲ ▼  Folge wechseln", color = Muted, fontSize = 16.sp)
                        Text(if (isEpisodes) "OK halten  Optionen" else "▼  Optionen", color = Muted, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun Hints(vararg hints: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 4.dp)) {
        hints.forEach { Text(it, color = Muted, fontSize = 16.sp) }
    }
}

@Composable
private fun HintIcon(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint = Muted, size = 20.dp)
        Text(text, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Was eine Taste der Fernbedienung im Player auslöst. */
internal enum class PlayerAction { PREVIOUS, NEXT, SHOW_INFO, TOGGLE_PLAY, PLAY, PAUSE, REWIND, FORWARD, LAST_CHANNEL, SLEEP_TIMER, OPTIONS }

/**
 * Tastenzuordnung im Player: ▲ ▼ wechseln bei Live TV und Serienfolgen den Eintrag,
 * ◀ ▶ spulen (nicht bei Live), OK pausiert. Unbekannte Tasten → null (nicht behandelt).
 */
internal fun playerActionFor(key: Key, isLive: Boolean, isEpisodes: Boolean): PlayerAction? {
    val zapping = isLive || isEpisodes
    return when (key) {
        Key.DirectionUp, Key.ChannelUp -> if (zapping) PlayerAction.PREVIOUS else PlayerAction.SHOW_INFO
        // Bei Filmen hat ▼ sonst nichts zu tun → Optionen (Ton, Untertitel, Herunterladen).
        Key.DirectionDown -> if (zapping) PlayerAction.NEXT else PlayerAction.OPTIONS
        Key.ChannelDown -> if (zapping) PlayerAction.NEXT else PlayerAction.SHOW_INFO
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> PlayerAction.TOGGLE_PLAY
        Key.MediaPlay -> PlayerAction.PLAY
        Key.MediaPause -> PlayerAction.PAUSE
        Key.DirectionLeft, Key.MediaRewind -> if (isLive) PlayerAction.SHOW_INFO else PlayerAction.REWIND
        Key.DirectionRight, Key.MediaFastForward -> if (isLive) PlayerAction.SHOW_INFO else PlayerAction.FORWARD
        Key.LastChannel, Key.L -> if (isLive) PlayerAction.LAST_CHANNEL else PlayerAction.SHOW_INFO
        Key.S -> PlayerAction.SLEEP_TIMER
        Key.Menu, Key.MediaAudioTrack, Key.Captions -> PlayerAction.OPTIONS
        else -> null
    }
}

/** Faktor für gehaltene Spultasten: erst normale Sprünge, nach ~1 s dreifach, nach ~3 s sechsfach. */
/**
 * Blendet dieser Tastendruck die Info-Leiste ein? Live-Zappen (▲▼, letzter Sender, Ziffern) nicht – dort erscheint
 * die Zapp-Karte. Filme/Folgen: wie bisher, nur ein Folgenwechsel nicht (die neue Folge zeigt die Leiste ohnehin).
 */
internal fun showsInfoOnKey(action: PlayerAction?, isDigit: Boolean, isLive: Boolean): Boolean = when {
    isDigit -> !isLive
    !isLive && action == PlayerAction.TOGGLE_PLAY -> false // Pausieren zeigt die Pause-Info, nicht die Leiste
    else -> action != PlayerAction.PREVIOUS && action != PlayerAction.NEXT && action != PlayerAction.LAST_CHANNEL
}

/** Beim Öffnen: Live zeigt die Zapp-Karte statt der Info-Leiste, Filme/Folgen die Leiste. */
internal fun infoVisibleAtStart(isLive: Boolean): Boolean = !isLive

/**
 * Info-Abzeichen: Auflösung aus der tatsächlich gerenderten Bildgröße (bei adaptivem HLS gelten alle Varianten als
 * „gewählt“), Tonkanäle, Tonsprache und Untertitel aus den gewählten Spuren von ExoPlayer.
 */
internal fun badgesOf(tracks: Tracks, videoWidth: Int, videoHeight: Int): List<String> {
    fun selected(type: Int) = tracks.groups.filter { it.type == type }.flatMap { g -> (0 until g.length).filter(g::isTrackSelected).map(g::getTrackFormat) }.firstOrNull()
    val audio = selected(C.TRACK_TYPE_AUDIO)
    return mediaBadges(
        videoWidth = videoWidth.takeIf { it > 0 },
        videoHeight = videoHeight.takeIf { it > 0 },
        audioChannels = audio?.channelCount?.takeIf { it > 0 },
        audioLanguage = audio?.language,
        subtitlesOn = selected(C.TRACK_TYPE_TEXT) != null,
    )
}

/** Bildformat → PlayerView-Modus. */
internal fun resizeModeOf(fit: String): Int = when (fit) {
    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

/** OK-Taste der Fernbedienung bzw. Enter der Tastatur. */
internal fun isOkKey(key: Key): Boolean = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/** Ziffer einer Taste (Fernbedienung, Tastatur oder Ziffernblock), sonst null. */
internal fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
    else -> null
}
