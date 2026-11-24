package de.vincentsberger.iptvplayer.ios.ui.player

import de.vincentsberger.iptvplayer.shared.epgExcerpt
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import de.vincentsberger.iptvplayer.shared.VideoFit
import de.vincentsberger.iptvplayer.ios.ui.LiveBadge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.AppLifecycleEffect
import de.vincentsberger.iptvplayer.ios.ui.Bg
import de.vincentsberger.iptvplayer.ios.ui.ChannelRow
import de.vincentsberger.iptvplayer.ios.ui.Kicker
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Mono
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.ProgressBar
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.ios.ui.monotonicMs
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.SeekTarget
import de.vincentsberger.iptvplayer.shared.StreamRecovery
import de.vincentsberger.iptvplayer.shared.ZapCard
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.epgProgress
import de.vincentsberger.iptvplayer.shared.formatTime
import de.vincentsberger.iptvplayer.shared.touch.BackgroundAction
import de.vincentsberger.iptvplayer.shared.touch.ForegroundAction
import de.vincentsberger.iptvplayer.shared.touch.TouchAction
import de.vincentsberger.iptvplayer.shared.touch.playbackOnBackground
import de.vincentsberger.iptvplayer.shared.touch.playbackOnForeground
import de.vincentsberger.iptvplayer.shared.touch.swipeAction
import de.vincentsberger.iptvplayer.shared.touch.tapAction
import de.vincentsberger.iptvplayer.shared.wrapIndex
import kotlinx.coroutines.delay

/**
 * Player für Sender, Filme und Folgen. Hochformat: Bild oben, darunter Programm und Senderliste (Live) bzw. Titel und
 * Position. Querformat: Bild füllt den Schirm, Senderliste per Wischen vom linken Rand.
 *
 * Die Bildfläche ist immer das erste Kind derselben Column – beim Drehen ändert sich nur ihr Modifier. So bleibt die
 * UIView des Players erhalten und der Stream läuft ohne Neustart weiter.
 */
@Composable
fun PlayerScreen(
    app: AppState,
    startItems: List<Item>,
    startIndex: Int,
    startPosition: Long,
    newPlayer: () -> PlayerEngine,
    onBack: () -> Unit,
) {
    val player = remember { newPlayer() }
    var items by remember { mutableStateOf(startItems) }
    var index by remember { mutableIntStateOf(startIndex) }
    val item = items[index]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.section == Section.LIVE
    val isEpisodes = item.section == Section.SERIES && items.size > 1

    val zap = remember { ZapCard(::currentTimeMillis) }
    val seek = remember { SeekTarget(::currentTimeMillis) }
    val recovery = remember { StreamRecovery() }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var showControls by remember { mutableStateOf(!isLive) }
    var activity by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var lastIndex by remember { mutableStateOf<Int?>(null) }
    var previous by remember { mutableStateOf<Item?>(null) }
    var startPending by remember { mutableStateOf(true) }
    var reconnecting by remember { mutableStateOf(false) }
    var connectionLost by remember { mutableStateOf(false) }
    var resumeAt by remember { mutableLongStateOf(0L) }
    var backgrounded by remember { mutableStateOf(false) }
    var nextIn by remember { mutableStateOf<Int?>(null) }
    var nextCancelled by remember(index) { mutableStateOf(false) }
    var sleepLeft by remember { mutableStateOf(app.sleepMinutesLeft()) }
    /** Aktuelle Ausrichtung für die Gesten (Senderliste vom Rand gibt es nur im Querformat). */
    var landscapeNow by remember { mutableStateOf(false) }

    fun switchTo(i: Int) {
        val target = wrapIndex(i, items.size)
        seek.cancel() // gesammeltes Ziel gehört zum alten Titel
        if (target != index) { lastIndex = index; index = target }
    }
    fun poke() { activity++ }
    fun handle(action: TouchAction) {
        when (action) {
            TouchAction.NONE -> Unit
            TouchAction.TOGGLE_CONTROLS -> { showControls = !showControls; poke() }
            TouchAction.NEXT_CHANNEL -> switchTo(index + 1)
            TouchAction.PREVIOUS_CHANNEL -> switchTo(index - 1)
            TouchAction.OPEN_CHANNEL_LIST -> if (landscapeNow) { drawerOpen = true; showControls = false }
            TouchAction.SEEK_BACK -> seek.press(-10_000L, player.timeMs, player.lengthMs)
            TouchAction.SEEK_FORWARD -> seek.press(30_000L, player.timeMs, player.lengthMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Noch nicht angewendetes Spulziel zählt: „Weiterschauen“ beginnt dort.
            app.saveProgress(currentItem, seek.positionOf(player.timeMs), player.lengthMs)
            player.release()
        }
    }

    // Titel starten; der vorige wandert in den Verlauf (Live: „Zuletzt gesehen“).
    LaunchedEffect(items, index) {
        previous?.let { app.saveProgress(it, player.timeMs, player.lengthMs) }
        previous = item
        val start = if (startPending) startPosition else 0L
        startPending = false
        player.play(playUrl(item), start)
        if (isLive) zap.onChannelChanged(item) else { zap.hide(); app.requestMediaInfo(item) }
        epg = emptyList()
        if (isLive) epg = app.nowPlaying(item)
    }

    // Zap-Karte: erstes Bild (Radio: Wiedergabe) startet den Countdown.
    LaunchedEffect(items, index, player.hasPicture || player.playing) { if (player.hasPicture || player.playing) zap.onFirstFrame() }
    LaunchedEffect(zap.channel) { while (zap.channel != null) { delay(250); zap.tick() } }
    LaunchedEffect(connectionLost) { if (connectionLost) zap.hide() }

    // Doppeltipp-Spulen: einmal springen, sobald nicht mehr getippt wird.
    LaunchedEffect(Unit) { seek.runCommits(player::seekTo) }
    var optionsOpen by remember { mutableStateOf(false) }
    // Heruntergeladen? Dann aus der Datei auf dem iPhone, ohne Netz. Sonst ruhen die Downloads (Einstellung), weil
    // viele Anbieter nur eine Verbindung erlauben.
    val fromNetwork = IosDownloads.localUrl(item.url) == null
    DisposableEffect(fromNetwork, app.pauseDownloadsWhilePlaying) {
        IosDownloads.setPaused(fromNetwork && app.pauseDownloadsWhilePlaying)
        onDispose { IosDownloads.setPaused(false) }
    }
    LaunchedEffect(Unit) { player.setVideoFit(VideoFit.of(app.videoFit)) }
    ApplyTrackPreferences(app, player, key = items to index)

    // Abbrüche und Hänger erkennen und selbst neu laden (Live: neu verbinden, Film: an derselben Stelle).
    LaunchedEffect(items, index) {
        recovery.reset(); reconnecting = false; connectionLost = false; resumeAt = 0L
        while (true) {
            delay(1000)
            if (backgrounded) continue // im Hintergrund absichtlich gestoppt
            val now = monotonicMs()
            if (now - player.lastProgressAt < 2_000) {
                recovery.onProgress(now)
                reconnecting = false
                connectionLost = false
                if (!isLive && player.timeMs > 0) resumeAt = player.timeMs
            }
            if (connectionLost) continue
            val broken = player.failed ||
                (isLive && player.finished) ||
                (!player.paused && !player.finished && recovery.isStalled(maxOf(player.lastProgressAt, player.startedAt), now))
            if (!broken) continue
            val wait = recovery.nextRetryDelay()
            if (wait == null) { reconnecting = false; connectionLost = true; continue }
            reconnecting = true
            delay(wait)
            if (backgrounded) { reconnecting = false; continue } // inzwischen im Hintergrund: dort nichts starten
            player.play(playUrl(item), if (isLive) 0L else resumeAt, keepFrame = true)
        }
    }

    // Nächste Folge automatisch – 10-Sekunden-Countdown, abbrechbar, in den Einstellungen abschaltbar.
    LaunchedEffect(player.finished, index, nextCancelled) {
        nextIn = null
        val next = app.nextEpisodeIndex(items, index)
        if (!player.finished || next == null || !app.autoNextEpisode || nextCancelled) return@LaunchedEffect
        for (s in 10 downTo 1) { nextIn = s; delay(1000) }
        nextIn = null
        switchTo(next)
    }

    // Bedienelemente nach 3,5 s ohne Berührung ausblenden – außer bei angehaltenem Film.
    LaunchedEffect(activity, showControls, player.paused) {
        if (!showControls || player.paused) return@LaunchedEffect
        delay(3500)
        showControls = false
    }

    // Schlaf-Timer und regelmäßiges Speichern der Position.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            sleepLeft = app.sleepMinutesLeft()
            if (app.consumeSleepTimer()) { onBack(); return@LaunchedEffect }
            if (++ticks % 30 == 0 && currentItem.section != Section.LIVE) app.saveProgress(currentItem, player.timeMs, player.lengthMs)
            delay(500)
        }
    }

    AppLifecycleEffect(
        onBackground = {
            backgrounded = true
            when (playbackOnBackground(isLive)) {
                BackgroundAction.STOP -> player.stop()
                BackgroundAction.PAUSE -> { player.pause(); app.saveProgress(item, player.timeMs, player.lengthMs) }
            }
        },
        onForeground = {
            backgrounded = false
            if (playbackOnForeground(isLive) == ForegroundAction.RESTART) {
                recovery.reset()
                player.play(playUrl(item))
                zap.onChannelChanged(item)
            }
        },
    )

    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        landscapeNow = landscape
        LaunchedEffect(landscape) { if (!landscape) drawerOpen = false }
        val now = epg.firstOrNull()

        Column(
            Modifier.fillMaxSize().then(
                if (landscape) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            ),
        ) {
            // 1. Kind: Bildfläche (nie umhängen, siehe oben)
            Box(
                (if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black)
                    .pointerInput(isLive) {
                        detectTapGestures(
                            onTap = { handle(tapAction(it.x, size.width.toFloat(), doubleTap = false, isLive = isLive)) },
                            onDoubleTap = { handle(tapAction(it.x, size.width.toFloat(), doubleTap = true, isLive = isLive)) },
                        )
                    }
                    .pointerInput(isLive, density, drawerOpen) {
                        // Steht hinter dem Tipp-Erkenner in der Kette und bekommt die Ereignisse deshalb zuerst:
                        // Ab dem Touch-Slop verbraucht es sie, dann wertet detectTapGestures den Wischer nicht als Tipp.
                        // Verbraucht ein Kind (Senderliste, Schieberegler) die Bewegung, ist es kein Wischer über dem Bild.
                        if (drawerOpen) return@pointerInput
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            var last = start
                            var lastTime = down.uptimeMillis
                            var dragging = false
                            var foreign = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed && !dragging) { foreign = true; break }
                                last = change.position
                                lastTime = change.uptimeMillis
                                if (!dragging && (last - start).getDistance() > slop) dragging = true
                                if (dragging) change.consume()
                                if (!change.pressed) break
                            }
                            if (dragging && !foreign) {
                                handle(swipeAction(start.x, last.x - start.x, last.y - start.y, lastTime - down.uptimeMillis, density, isLive))
                            }
                        }
                    },
            ) {
                player.Video(Modifier.fillMaxSize())

                zap.channel?.let { z ->
                    ZapCardView(
                        z, channelNumberOf(z, items), now,
                        Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    )
                }
                seek.state?.let { SeekBadge(it, Modifier.align(Alignment.Center)) }
                if (reconnecting || connectionLost) ConnectionOverlay(
                    lost = connectionLost,
                    onRetry = { recovery.reset(); connectionLost = false; reconnecting = true; player.play(playUrl(item), if (isLive) 0L else resumeAt, keepFrame = true) },
                    onClose = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                nextIn?.let { NextEpisodeBadge(it, { nextCancelled = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) }

                androidx.compose.animation.AnimatedVisibility(showControls, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize()) {
                        PlayerControls(
                            title = splitProviderTag(item.name).name,
                            subtitle = if (isLive) now?.let { "${it.start}–${it.end}  ${it.title}" } else app.pauseTitles(item).second,
                            // Im Hochformat steht die Beschreibung schon unter dem Bild.
                            description = if (isLive && landscape) epgExcerpt(now, max = 160) else null,
                            isLive = isLive,
                            paused = player.paused,
                            positionMs = seek.positionOf(player.timeMs),
                            durationMs = player.lengthMs,
                            sleepLeft = sleepLeft,
                            landscape = landscape,
                            canGoToLastChannel = lastIndex != null,
                            onClose = onBack,
                            onTogglePause = { player.togglePause(); poke() },
                            onSeek = { player.seekTo(it); poke() },
                            onSleep = { app.cycleSleepTimer(); sleepLeft = app.sleepMinutesLeft(); poke() },
                            onLastChannel = { lastIndex?.let(::switchTo); poke() },
                            onToggleFullscreen = { requestLandscape(!landscape); poke() },
                            onOptions = { optionsOpen = true; poke() },
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(drawerOpen && landscape, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                    ChannelDrawer(app, items, index, onPick = { switchTo(it); drawerOpen = false }, onClose = { drawerOpen = false })
                }
            }

            if (!landscape) BelowVideo(app, items, index, isLive, epg, onPick = ::switchTo, player, seek, Modifier.weight(1f))
        }
    }
    if (optionsOpen) PlayerOptionsSheet(app, player, onFit = player::setVideoFit, onDismiss = { optionsOpen = false })
}

/** Hochformat unter dem Bild: Live – Programm und Senderliste; Film/Folge – Titel, Position, Play/Pause. */
@Composable
private fun BelowVideo(
    app: AppState,
    items: List<Item>,
    index: Int,
    isLive: Boolean,
    epg: List<EpgEntry>,
    onPick: (Int) -> Unit,
    player: PlayerEngine,
    seek: SeekTarget,
    modifier: Modifier,
) {
    val item = items[index]
    Column(modifier.fillMaxWidth().background(Bg)) {
        if (isLive) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge(Modifier.padding(end = 10.dp))
                    Text(
                        splitProviderTag(item.name).name, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                epg.getOrNull(0)?.let { e ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Jetzt", Modifier.padding(end = 8.dp))
                        Text("${e.start}  ${e.title}", color = TextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ProgressBar(epgProgress(e), Modifier.padding(top = 4.dp).fillMaxWidth(), height = 2.dp)
                    epgExcerpt(e, max = 200)?.let {
                        Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                epg.getOrNull(1)?.let { e ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Danach", Modifier.padding(end = 8.dp))
                        Text("${e.start}  ${e.title}", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val state = rememberLazyListState()
            LaunchedEffect(index) { state.animateScrollToItem((index - 2).coerceAtLeast(0)) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).background(Bg), state = state) {
                val keys = stableKeys(items)
                itemsIndexed(items, key = { i, _ -> keys[i] }) { i, ch ->
                    ChannelRow(app, ch, number = ch.number ?: (i + 1), selected = i == index, onClick = { onPick(i) })
                }
            }
        } else {
            val (title, subtitle) = app.pauseTitles(item)
            Column(Modifier.padding(16.dp)) {
                Text(title, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                subtitle?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 2.dp)) }
                val pos = seek.positionOf(player.timeMs)
                val dur = player.lengthMs
                if (dur > 0) ProgressBar((pos.toFloat() / dur), Modifier.padding(top = 12.dp).fillMaxWidth())
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatTime(pos)} / ${formatTime(dur)}", color = Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconButton({ player.togglePause() }) {
                        Icon(if (player.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (player.paused) "Abspielen" else "Pause", tint = TextColor)
                    }
                }
                Text("Doppeltipp links/rechts im Bild: 10 s zurück / 30 s vor", color = Line, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Adresse für den Player: lokale Datei eines fertigen Downloads, sonst der Stream. */
private fun playUrl(item: Item): String = IosDownloads.localUrl(item.url) ?: item.url
