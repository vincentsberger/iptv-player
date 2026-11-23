package de.vincentsberger.iptvplayer.app.ui

import de.vincentsberger.iptvplayer.shared.PauseStage
import de.vincentsberger.iptvplayer.shared.PauseInfo
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.ZapCard
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
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
import de.vincentsberger.iptvplayer.shared.ChannelNumberInput
import de.vincentsberger.iptvplayer.shared.StreamRecovery
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.app.download.Downloads
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

private fun mediaItem(url: String): MediaItem {
    val b = MediaItem.Builder().setUri(url)
    if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    return b.build()
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
    val player = remember { buildPlayer(context, vm) }
    // Laufende Liste: die Senderliste im Player kann in eine andere Kategorie wechseln, ▲▼ zappt dann dort.
    var list by remember { mutableStateOf(items) }
    var firstStart by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(startIndex) }
    val item = list[index]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.section == Section.LIVE
    val isEpisodes = item.section == Section.SERIES && list.size > 1

    var error by remember { mutableStateOf<String?>(null) }
    val recovery = remember { StreamRecovery() }
    /** Stream abgebrochen, neuer Versuch läuft bzw. wartet. */
    var reconnecting by remember { mutableStateOf(false) }
    /** Alle automatischen Versuche gescheitert → Fehlermeldung, OK versucht es erneut. */
    var connectionLost by remember { mutableStateOf(false) }
    /** Letzte sichere Stelle bei Filmen/Folgen – dort geht es nach einem Abbruch weiter. */
    var resumeAt by remember { mutableLongStateOf(0L) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(infoVisibleAtStart(items[startIndex].section == Section.LIVE)) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var resumedAt by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf<Item?>(null) }
    val focus = remember { FocusRequester() }
    val numberInput = remember { ChannelNumberInput() }
    var lastIndex by remember { mutableStateOf<Int?>(null) }
    var ended by remember { mutableStateOf(false) }
    var nextIn by remember { mutableStateOf<Int?>(null) }
    var nextCancelled by remember(index) { mutableStateOf(false) }
    var sleepLeft by remember { mutableStateOf(vm.sleepMinutesLeft()) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var optionsOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    val overlayOpen = optionsOpen || guideOpen
    var videoFit by remember { mutableStateOf(vm.videoFit) }
    /** OK wird gerade lange gehalten (die Taste wiederholt sich) → beim Loslassen öffnet das Menü. */
    var okHeld by remember { mutableStateOf(false) }

    val zap = remember { ZapCard(System::currentTimeMillis) }
    val pause = remember { PauseInfo(System::currentTimeMillis) }

    fun switchTo(newIndex: Int) {
        val target = wrapIndex(newIndex, list.size)
        if (target != index) { lastIndex = index; index = target }
    }

    BackHandler { onBack() }
    // Während des Countdowns bricht „Zurück“ nur den Countdown ab (später registriert → hat Vorrang).
    BackHandler(enabled = nextIn != null) { nextCancelled = true }
    BackHandler(enabled = overlayOpen) { optionsOpen = false; guideOpen = false }
    // Menü/Senderliste zu → Fokus zurück an den Player, damit die Fernbedienung wieder steuert.
    LaunchedEffect(overlayOpen) { if (!overlayOpen) runCatching { focus.requestFocus() } }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Wiedergabe nicht möglich (${e.errorCodeName})"
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onRenderedFirstFrame() { zap.onFirstFrame() }
            // Radiosender haben kein Bild: dann zählt der Start der Wiedergabe.
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) zap.onFirstFrame() }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose {
            vm.saveProgress(currentItem, player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    vm.saveProgress(currentItem, player.currentPosition, player.duration)
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

    LaunchedEffect(list, index) {
        previous?.let { vm.saveProgress(it, player.currentPosition, player.duration) }
        previous = item
        error = null
        epg = emptyList()
        val start = if (firstStart) startPosition else 0L
        firstStart = false
        resumedAt = start
        player.setMediaItem(mediaItem(item.url), start)
        player.prepare()
        player.play()
        // Live: Zapp-Karte statt der ganzen Leiste; Filme/Folgen: Leiste wie bisher.
        if (isLive) zap.onChannelChanged(item) else { zap.hide(); infoTick++; vm.requestMediaInfo(item) }
        epg = vm.nowPlaying(item)
    }

    /** Stream neu laden: Live an der aktuellen Stelle der Sendung, Filme/Folgen dort, wo es abgebrochen ist. */
    fun reload() {
        error = null
        if (isLive) player.setMediaItem(mediaItem(item.url)) else player.setMediaItem(mediaItem(item.url), resumeAt)
        player.prepare()
        player.play()
    }

    // Abbrüche und Hänger erkennen und den Stream selbst neu laden.
    LaunchedEffect(list, index) {
        recovery.reset(); reconnecting = false; connectionLost = false; resumeAt = 0L
        var startedAt = SystemClock.elapsedRealtime()
        var lastProgressAt = Long.MIN_VALUE / 2
        var lastPos = -1L
        while (true) {
            delay(1000)
            val now = SystemClock.elapsedRealtime()
            val pos = player.currentPosition
            if (player.isPlaying && pos != lastPos) {
                lastProgressAt = now
                recovery.onProgress(now)
                reconnecting = false
                connectionLost = false
                if (!isLive && pos > 0) resumeAt = pos
            }
            lastPos = pos
            if (connectionLost) continue
            val broken = error != null ||
                (isLive && ended) ||
                (player.playWhenReady && !ended && recovery.isStalled(maxOf(lastProgressAt, startedAt), now))
            if (!broken) continue
            val wait = recovery.nextRetryDelay()
            if (wait == null) { reconnecting = false; connectionLost = true; continue }
            reconnecting = true
            delay(wait)
            startedAt = SystemClock.elapsedRealtime()
            lastProgressAt = Long.MIN_VALUE / 2
            reload()
        }
    }

    // Nächste Folge automatisch – mit 10-Sekunden-Countdown, abbrechbar, in den Einstellungen abschaltbar
    LaunchedEffect(ended, index, nextCancelled) {
        nextIn = null
        val next = vm.nextEpisodeIndex(list, index)
        if (!ended || next == null || !vm.autoNextEpisode || nextCancelled) return@LaunchedEffect
        for (s in 10 downTo 1) { nextIn = s; delay(1000) }
        nextIn = null
        switchTo(next)
    }

    // Senderwahl per Zifferntasten: kurz nach der letzten Ziffer umschalten
    LaunchedEffect(numberInput.digits) {
        if (numberInput.digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        numberInput.take()?.let { n ->
            val pick = ChannelNumberInput.find(n, list, if (isLive) vm.section(Section.LIVE).items else list)
            when {
                pick == null -> Feedback.show("Sender $n nicht gefunden")
                pick.list == list -> switchTo(pick.index)
                else -> { lastIndex = null; list = pick.list; index = pick.index }
            }
        }
    }

    // Pause-Info: angehalten (nicht puffernd, ohne Fehler) → kompakt, nach 5 s ausführlich.
    val paused = !isLive && !playing && !buffering && error == null && !reconnecting && !connectionLost
    LaunchedEffect(paused) { if (paused) pause.onPaused() else pause.onResumed() }
    LaunchedEffect(pause.stage) { while (pause.stage == PauseStage.COMPACT) { delay(250); pause.tick() } }

    // Zapp-Karte: nach dem ersten Bild regelmäßig prüfen, ob sie ausblenden soll; Fehlerdialog verdrängt sie.
    LaunchedEffect(zap.channel) { while (zap.channel != null) { delay(250); zap.tick() } }
    LaunchedEffect(connectionLost) { if (connectionLost) zap.hide() }

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
        var ticks = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            clock = fmt.format(Date())
            sleepLeft = vm.sleepMinutesLeft()
            if (vm.consumeSleepTimer()) { onBack(); return@LaunchedEffect }
            // Alle 15 Sekunden Position sichern, falls die App unerwartet beendet wird.
            if (++ticks % 30 == 0 && !isLive) vm.saveProgress(currentItem, position, duration)
            delay(500)
        }
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
                    if (ev.type == KeyEventType.KeyUp) { optionsOpen = false; guideOpen = false }
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
                    if (playerActionFor(ev.key, isLive, isEpisodes) == PlayerAction.OPTIONS) { optionsOpen = false; guideOpen = false; return@onKeyEvent true }
                    return@onKeyEvent false
                }
                digitOf(ev.key)?.let { d ->
                    if (isLive) numberInput.type(d)
                    if (showsInfoOnKey(null, isDigit = true, isLive = isLive)) infoTick++
                    return@onKeyEvent true
                }
                val action = playerActionFor(ev.key, isLive, isEpisodes) ?: return@onKeyEvent false
                // Countdown sichtbar: OK startet die nächste Folge sofort.
                if (nextIn != null && action == PlayerAction.TOGGLE_PLAY) { switchTo(index + 1); return@onKeyEvent true }
                when (action) {
                    PlayerAction.LAST_CHANNEL -> lastIndex?.let(::switchTo)
                    PlayerAction.SLEEP_TIMER -> { vm.cycleSleepTimer(); sleepLeft = vm.sleepMinutesLeft() }
                    PlayerAction.OPTIONS -> { optionsOpen = true; return@onKeyEvent true }
                    PlayerAction.PREVIOUS -> switchTo(index - 1)
                    PlayerAction.NEXT -> switchTo(index + 1)
                    PlayerAction.SHOW_INFO -> Unit
                    PlayerAction.TOGGLE_PLAY -> when {
                        connectionLost -> { recovery.reset(); connectionLost = false; reload() }
                        isLive -> { guideOpen = true; return@onKeyEvent true } // Live: OK öffnet die Senderliste
                        player.isPlaying -> player.pause()
                        else -> player.play()
                    }
                    PlayerAction.PLAY -> player.play()
                    PlayerAction.PAUSE -> player.pause()
                    // Gehaltene Taste: Sprünge werden größer (10 s → 30 s → 1 min bzw. 30 s → 1,5 min → 3 min).
                    PlayerAction.REWIND -> { pause.dismissFull(); player.seekTo((player.currentPosition - 10_000 * seekMultiplier(ev.nativeKeyEvent.repeatCount)).coerceAtLeast(0)) }
                    PlayerAction.FORWARD -> { pause.dismissFull(); player.seekTo(player.currentPosition + 30_000 * seekMultiplier(ev.nativeKeyEvent.repeatCount)) }
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

        if ((buffering && error == null || reconnecting) && !connectionLost) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(64.dp)
                if (reconnecting) {
                    Text(
                        "Verbindung wird wiederhergestellt …", color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp).background(Color(0x99000000), Corner).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Pause-Info statt Pause-Symbol (nicht zusammen mit Info-Leiste, Optionen oder Countdown)
        if (pause.stage != PauseStage.NONE && !showInfo && !optionsOpen && nextIn == null) {
            PauseInfoView(
                title = if (item.section == Section.SERIES) vm.seriesNameOf(item) ?: item.name else item.name,
                subtitle = if (item.section == Section.SERIES) vm.episodeLabel(item) else null,
                info = vm.mediaInfoFor(item), stage = pause.stage,
                position = position, duration = duration, endsAt = endsAt(duration - position),
            )
        }

        if (connectionLost) {
            Column(
                Modifier.align(Alignment.Center).background(Surface1.copy(alpha = 0.95f), Corner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Headline("Wiedergabe nicht möglich", 24.sp)
                Text(error ?: "Die Verbindung zum Stream ist abgebrochen.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    if (isLive) "OK  erneut versuchen     ·     ▲ ▼  anderer Sender" else "OK  erneut versuchen     ·     Zurück  zur Übersicht",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        // Zapp-Karte oben links (nur Live, nicht zusammen mit Leiste, Senderliste oder Optionen)
        val zapItem = zap.channel
        if (isLive && zapItem != null && !showInfo && !guideOpen && !optionsOpen && !connectionLost) {
            val now = epg.getOrNull(0)
            ZapCardView(zapItem, channelNumberOf(zapItem, list), now, now?.let(::epgProgress) ?: 0f, Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 40.dp))
        }

        // Eingetippte Sendernummer
        if (numberInput.digits.isNotEmpty()) {
            Text(
                numberInput.digits + "_", color = Color.White, fontFamily = Mono, fontSize = 48.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 110.dp, end = 56.dp).background(Color(0xCC000000), Corner).padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        // Countdown zur nächsten Folge
        nextIn?.let { secs ->
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 150.dp).width(360.dp).panel().padding(22.dp)) {
                Kicker("Nächste Folge in $secs s", Signal)
                Headline(list.getOrNull(index + 1)?.name.orEmpty(), 20.sp, Modifier.padding(top = 6.dp, bottom = 10.dp), maxLines = 2)
                Text("OK  jetzt ansehen     ·     Zurück  abbrechen", color = Muted, fontSize = 16.sp)
            }
        }

        if (optionsOpen) {
            PlayerOptionsPanel(
                audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO),
                subtitles = trackOptions(tracks, C.TRACK_TYPE_TEXT),
                sleepLeft = sleepLeft,
                onAudio = { o ->
                    selectTrack(player, C.TRACK_TYPE_AUDIO, o)
                    vm.audioLanguage = o.language
                },
                onSubtitles = { o ->
                    selectTrack(player, C.TRACK_TYPE_TEXT, o)
                    vm.subtitleLanguage = o?.language ?: Storage.SUBTITLES_OFF
                },
                onSleep = { vm.cycleSleepTimer(); sleepLeft = vm.sleepMinutesLeft() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp),
            ) {
                if (!isLive) DownloadOption(item)
                VideoFitOption(videoFit) { videoFit = it; vm.videoFit = it }
            }
        }

        // Senderliste über dem laufenden Bild (nur Live TV)
        if (guideOpen && isLive) {
            ChannelGuide(
                vm, playing = item, startCategory = vm.liveCategory,
                onPick = { newList, i ->
                    guideOpen = false
                    if (newList == list) switchTo(i) else { lastIndex = null; list = newList; index = i }
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        // Obere Leiste: Titel und Uhrzeit
        AnimatedVisibility(showInfo && !guideOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
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
                    if (isLive) Text(channelNumberOf(item, list).toString(), color = Muted, fontSize = 16.sp, fontFamily = Mono)
                    else if (isEpisodes) Text("${index + 1} / ${list.size}", color = Muted, fontSize = 16.sp)
                    Headline(item.name, 28.sp, color = Color.White)
                }
                sleepLeft?.let {
                    Icon(Icons.Rounded.Bedtime, Modifier.padding(end = 6.dp), tint = Signal, size = 22.dp)
                    Text("$it min", color = Color.White, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier.padding(end = 24.dp))
                }
                Text(clock, color = Color.White, fontSize = 24.sp, fontFamily = Mono)
            }
        }

        // Untere Leiste: Programm bzw. Fortschritt
        AnimatedVisibility(showInfo && !guideOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                    .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLive) {
                    val now = epg.getOrNull(0)
                    if (now != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge()
                            Text(now.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(now.start, color = Muted, fontSize = 16.sp)
                            ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 12.dp), height = 5.dp)
                            Text(now.end, color = Muted, fontSize = 16.sp)
                        }
                        epg.getOrNull(1)?.let { Text("Danach  ${it.start}  ${it.title}", color = Muted, fontSize = 16.sp) }
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
                        Icon(if (playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, tint = Color.White, size = 26.dp)
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
private fun LiveBadge() {
    Text(
        "LIVE", color = OnSignal, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = Modifier.background(Signal, SmallCorner).padding(horizontal = 8.dp, vertical = 3.dp),
    )
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

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
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

internal fun seekMultiplier(repeatCount: Int): Int = when {
    repeatCount < 10 -> 1
    repeatCount < 30 -> 3
    else -> 6
}

/** Uhrzeit, zu der der Film bei normaler Wiedergabe endet („21:45“). */
internal fun endsAt(remainingMs: Long, now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(now + remainingMs.coerceAtLeast(0)))

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

/** Index im Kreis: vor dem ersten kommt der letzte Eintrag und umgekehrt. */
internal fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size
