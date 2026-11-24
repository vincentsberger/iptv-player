package de.vincentsberger.iptvplayer.ios.ui.player

import de.vincentsberger.iptvplayer.ios.ui.TextSoft
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import de.vincentsberger.iptvplayer.ios.ui.toggleFavoriteWithFeedback
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import de.vincentsberger.iptvplayer.shared.epgMinutesLeft
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.ios.ui.Signal
import androidx.compose.material3.TextButton
import de.vincentsberger.iptvplayer.shared.touch.isVerticalDrag
import de.vincentsberger.iptvplayer.shared.touch.dragLevel
import de.vincentsberger.iptvplayer.shared.touch.dragZone
import de.vincentsberger.iptvplayer.shared.touch.DragZone
import androidx.compose.ui.backhandler.BackHandler
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
import de.vincentsberger.iptvplayer.ios.ui.design.LogoLoader
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.ios.ui.monotonicMs
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.SeekTarget
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.epgProgress
import de.vincentsberger.iptvplayer.shared.formatTime
import de.vincentsberger.iptvplayer.shared.player.PlaybackController
import de.vincentsberger.iptvplayer.shared.touch.BackgroundAction
import de.vincentsberger.iptvplayer.shared.touch.ForegroundAction
import de.vincentsberger.iptvplayer.shared.touch.TouchAction
import de.vincentsberger.iptvplayer.shared.touch.playbackOnBackground
import de.vincentsberger.iptvplayer.shared.touch.playbackOnForeground
import de.vincentsberger.iptvplayer.shared.touch.swipeAction
import de.vincentsberger.iptvplayer.shared.touch.tapAction
import kotlinx.coroutines.delay

/**
 * Player für Sender, Filme und Folgen. Hochformat: Bild oben, darunter Programm und Senderliste (Live) bzw. Titel und
 * Position. Querformat: Bild füllt den Schirm, Senderliste per Wischen vom linken Rand.
 *
 * Die Bildfläche ist immer das erste Kind derselben Column – beim Drehen ändert sich nur ihr Modifier. So bleibt die
 * UIView des Players erhalten und der Stream läuft ohne Neustart weiter.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val c = remember { PlaybackController(app, player, startItems, startIndex, startPosition, ::currentTimeMillis, ::monotonicMs, resolveUrl = ::playUrl) }
    val items = c.items
    val index = c.index
    val item = c.item
    val isLive = c.isLive
    val zap = c.zap
    val seek = c.seek
    var showControls by remember { mutableStateOf(!isLive) }
    var activity by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    /** Aktuelle Ausrichtung für die Gesten (Senderliste vom Rand gibt es nur im Querformat). */
    var landscapeNow by remember { mutableStateOf(false) }

    fun poke() { activity++ }
    fun handle(action: TouchAction) {
        when (action) {
            TouchAction.NONE -> Unit
            TouchAction.TOGGLE_CONTROLS -> { showControls = !showControls; poke() }
            TouchAction.NEXT_CHANNEL -> c.switchTo(index + 1)
            TouchAction.PREVIOUS_CHANNEL -> c.switchTo(index - 1)
            TouchAction.OPEN_CHANNEL_LIST -> if (landscapeNow) { drawerOpen = true; showControls = false }
            TouchAction.SEEK_BACK -> c.seek.press(-10_000L, player.timeMs, player.lengthMs)
            TouchAction.SEEK_FORWARD -> c.seek.press(30_000L, player.timeMs, player.lengthMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Noch nicht angewendetes Spulziel zählt: „Weiterschauen“ beginnt dort.
            c.saveNow()
            player.release()
        }
    }

    // Titel starten; der vorige wandert in den Verlauf (Live: „Zuletzt gesehen“); Zapp-Karte, Infos und EPG im Controller.
    LaunchedEffect(c.items, c.index) { c.startCurrent() }
    // Abbrüche und Hänger erkennen und selbst neu laden (Live: neu verbinden, Film: an derselben Stelle).
    LaunchedEffect(c.items, c.index) { c.watch() }

    // Zap-Karte: erstes Bild (Radio: Wiedergabe) startet den Countdown.
    LaunchedEffect(items, index, player.hasPicture || player.playing) { if (player.hasPicture || player.playing) zap.onFirstFrame() }
    LaunchedEffect(zap.channel) { while (zap.channel != null) { delay(250); zap.tick() } }

    // Doppeltipp-Spulen: einmal springen, sobald nicht mehr getippt wird.
    LaunchedEffect(Unit) { seek.runCommits(player::seekTo) }
    var optionsOpen by remember { mutableStateOf(false) }
    var programmeOpen by remember { mutableStateOf(false) }
    // Sperrbildschirm/Kontrollzentrum: Befehle binden (aktuelle Lambdas über rememberUpdatedState), Anzeige pflegen.
    val toggleNow by rememberUpdatedState({ player.togglePause() })
    val nextNow by rememberUpdatedState({ handle(if (isLive) TouchAction.NEXT_CHANNEL else TouchAction.SEEK_FORWARD) })
    val previousNow by rememberUpdatedState({ handle(if (isLive) TouchAction.PREVIOUS_CHANNEL else TouchAction.SEEK_BACK) })
    DisposableEffect(Unit) {
        NowPlaying.bind(onToggle = { toggleNow() }, onNext = { nextNow() }, onPrevious = { previousNow() })
        onDispose { NowPlaying.unbind() }
    }
    LaunchedEffect(item, player.paused, player.lengthMs) {
        while (true) {
            val (title, sub) = if (isLive) splitProviderTag(item.name).name to app.nowFor(item)?.title else app.pauseTitles(item)
            NowPlaying.update(title, sub, isLive, playing = !player.paused, positionMs = player.timeMs, durationMs = player.lengthMs)
            delay(10_000) // Uhrzeit läuft im Kontrollzentrum selbst weiter; gelegentlich nachziehen (Sprünge, neue Sendung)
        }
    }
    // Wischgesten links/rechts: Helligkeit/Lautstärke mit kurzer Anzeige; Helligkeit beim Schließen zurücksetzen.
    var level by remember { mutableStateOf<Pair<DragZone, Float>?>(null) }
    LaunchedEffect(level) { if (level != null) { delay(900); level = null } }
    DisposableEffect(Unit) {
        val brightnessBefore = screenBrightness
        onDispose { screenBrightness = brightnessBefore }
    }
    // Zurück-Geste (vom Rand wischen) bzw. Zurück: erst Senderliste schließen, dann den Player.
    BackHandler { if (drawerOpen) drawerOpen = false else onBack() }
    // Einstellung „Player immer im Querformat“: beim Öffnen drehen, beim Schließen zurück ins Hochformat.
    DisposableEffect(Unit) {
        val forced = app.playerLandscape
        if (forced) requestLandscape(true)
        onDispose { if (forced) requestLandscape(false) }
    }
    // Heruntergeladen? Dann aus der Datei auf dem iPhone, ohne Netz. Sonst ruhen die Downloads (Einstellung), weil
    // viele Anbieter nur eine Verbindung erlauben.
    val fromNetwork = IosDownloads.localUrl(item.url) == null
    DisposableEffect(fromNetwork, app.pauseDownloadsWhilePlaying) {
        IosDownloads.setPaused(fromNetwork && app.pauseDownloadsWhilePlaying)
        onDispose { IosDownloads.setPaused(false) }
    }
    LaunchedEffect(Unit) { player.setVideoFit(VideoFit.of(app.videoFit)) }
    ApplyTrackPreferences(app, player, key = items to index)

    // Nächste Folge automatisch – 10-Sekunden-Countdown, abbrechbar, in den Einstellungen abschaltbar.
    LaunchedEffect(player.finished, c.index, c.nextCancelled) { c.runAutoNext() }

    // Bedienelemente nach 3,5 s ohne Berührung ausblenden – außer bei angehaltenem Film.
    LaunchedEffect(activity, showControls, player.paused) {
        if (!showControls || player.paused) return@LaunchedEffect
        delay(3500)
        showControls = false
    }

    // Schlaf-Timer und regelmäßiges Speichern der Position.
    LaunchedEffect(Unit) { c.runClock(onSleep = onBack) }

    AppLifecycleEffect(
        onBackground = {
            c.suspended = true
            when (playbackOnBackground(isLive)) {
                BackgroundAction.STOP -> player.stop()
                BackgroundAction.PAUSE -> { player.pause(); c.saveNow() }
            }
        },
        onForeground = {
            c.suspended = false
            if (playbackOnForeground(isLive) == ForegroundAction.RESTART) {
                c.restart()
            }
        },
    )

    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        landscapeNow = landscape
        LaunchedEffect(landscape) { if (!landscape) drawerOpen = false }
        val now = c.epg.firstOrNull()

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
                            val zone = dragZone(start.x, size.width.toFloat())
                            var levelMode = false
                            var startLevel = 0f
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
                                // Links/rechts senkrecht ziehen: Helligkeit bzw. Lautstärke fortlaufend, kein Senderwechsel.
                                if (dragging && !levelMode && zone != DragZone.NONE && isVerticalDrag(last.x - start.x, last.y - start.y)) {
                                    levelMode = true
                                    startLevel = if (zone == DragZone.VOLUME) player.volume else screenBrightness
                                }
                                if (levelMode) {
                                    val v = dragLevel(startLevel, last.y - start.y, size.height.toFloat())
                                    if (zone == DragZone.VOLUME) player.volume = v else screenBrightness = v
                                    level = zone to v
                                }
                                if (!change.pressed) break
                            }
                            if (dragging && !foreign && !levelMode) {
                                handle(swipeAction(start.x, last.x - start.x, last.y - start.y, lastTime - down.uptimeMillis, density, isLive))
                            }
                        }
                    },
            ) {
                player.Video(Modifier.fillMaxSize())
                // Logo nur bis zum ersten Bild eines Titels; späteres Puffern bleibt ohne Logo.
                var firstFrameSeen by remember(c.items, c.index) { mutableStateOf(false) }
                LaunchedEffect(c.items, c.index, player.hasPicture || player.playing) { if (player.hasPicture || player.playing) firstFrameSeen = true }
                if (!firstFrameSeen && !c.connectionLost && !player.failed) LogoLoader(72.dp, modifier = Modifier.align(Alignment.Center))

                zap.channel?.let { z ->
                    // Nachbarn wie beim Wischen (am Listenende geht es vorn weiter); bei ≤ 2 Sendern keine Leiter.
                    val ladder = landscape && items.size > 2
                    val prevItem = items[(index - 1 + items.size) % items.size]
                    val nextItem = items[(index + 1) % items.size]
                    ZapCardView(
                        z, channelNumberOf(z, items), now,
                        Modifier.align(Alignment.BottomStart)
                            .windowInsetsPadding(if (landscape) WindowInsets.safeDrawing else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(if (landscape) 16.dp else 8.dp),
                        next = c.epg.getOrNull(1),
                        prev = if (ladder) channelNumberOf(prevItem, items) to prevItem else null,
                        following = if (ladder) channelNumberOf(nextItem, items) to nextItem else null,
                        compact = !landscape,
                    )
                }
                seek.state?.let { SeekBadge(it, Modifier.align(Alignment.Center)) }
                level?.let { (zone, v) -> LevelBadge(zone, v, Modifier.align(Alignment.TopCenter).padding(top = 24.dp)) }
                if (c.reconnecting || c.connectionLost) ConnectionOverlay(
                    lost = c.connectionLost,
                    onRetry = c::retry,
                    onClose = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                c.nextIn?.let { NextEpisodeBadge(it, c::cancelNext, Modifier.align(Alignment.BottomEnd).padding(16.dp)) }

                androidx.compose.animation.AnimatedVisibility(showControls, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize()) {
                        PlayerControls(
                            title = splitProviderTag(item.name).name,
                            subtitle = if (isLive) null else app.pauseTitles(item).second,
                            // Im Hochformat steht die Beschreibung schon unter dem Bild.
                            description = if (isLive && landscape) epgExcerpt(now, max = 160) else null,
                            isLive = isLive,
                            paused = player.paused,
                            positionMs = seek.positionOf(player.timeMs),
                            durationMs = player.lengthMs,
                            sleepLeft = c.sleepLeft,
                            landscape = landscape,
                            canGoToLastChannel = c.lastIndex != null,
                            onClose = onBack,
                            onTogglePause = { player.togglePause(); poke() },
                            onSeek = { player.seekTo(it); poke() },
                            onSleep = { app.cycleSleepTimer(); poke() },
                            onLastChannel = { c.lastChannel(); poke() },
                            onToggleFullscreen = { requestLandscape(!landscape); poke() },
                            onOptions = { optionsOpen = true; poke() },
                            onProgramme = if (isLive) ({ programmeOpen = true; poke() }) else null,
                            nowEntry = if (isLive) now else null,
                            nextEntry = if (isLive) c.epg.getOrNull(1) else null,
                            badges = splitProviderTag(item.name).badges,
                            kicker = if (isLive) null else playerKicker(app, item),
                            onChannelList = if (isLive && landscape) ({ drawerOpen = true; showControls = false }) else null,
                            onJumpBack = { handle(TouchAction.SEEK_BACK); poke() },
                            onJumpForward = { handle(TouchAction.SEEK_FORWARD); poke() },
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(drawerOpen && landscape, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                    ChannelDrawer(app, items, index, onPick = { c.switchTo(it); drawerOpen = false }, onClose = { drawerOpen = false })
                }
            }

            if (!landscape) BelowVideo(
                app, items, index, isLive, c.epg, onPick = c::switchTo, player, seek, Modifier.weight(1f),
                onProgramme = { programmeOpen = true }, onOptions = { optionsOpen = true },
                onJumpBack = { handle(TouchAction.SEEK_BACK) }, onJumpForward = { handle(TouchAction.SEEK_FORWARD) },
            )
        }
    }
    if (programmeOpen && isLive) ProgrammeSheet(app, item, onDismiss = { programmeOpen = false })
    if (optionsOpen) PlayerOptionsSheet(
        app, player, onFit = player::setVideoFit, onDismiss = { optionsOpen = false },
        sources = c.sources.size, source = c.sourceIndex,
        onSource = c::selectSource,
    )
}

/**
 * Hochformat unter dem Bild (Entwurf „14“). Live: Sender mit Kennzeichen, Favorit und Optionen, „Jetzt“ mit Restzeit
 * und Beschreibung, „Danach“, die Senderliste der Kategorie. Film/Folge: Titel, Position, Sprungknöpfe und Pause.
 */
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
    onProgramme: () -> Unit,
    onOptions: () -> Unit,
    onJumpBack: () -> Unit,
    onJumpForward: () -> Unit,
) {
    val item = items[index]
    val tagged = remember(item.name) { splitProviderTag(item.name) }
    val muted = TextSoft
    Column(modifier.fillMaxWidth().background(Bg)) {
        if (isLive) {
            Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveBadge()
                    de.vincentsberger.iptvplayer.ios.ui.design.ChannelLogo(item.logo, item.name, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            de.vincentsberger.iptvplayer.ios.ui.design.H3(tagged.name, Modifier.weight(1f, fill = false), maxLines = 1)
                            tagged.badges.forEach { de.vincentsberger.iptvplayer.ios.ui.design.Tag(it) }
                        }
                        val category = app.section(Section.LIVE).categories.firstOrNull { it.id == item.categoryId }?.let { splitCategoryName(it.name).name }
                        Text(listOfNotNull("Nr. ${item.number ?: (index + 1)}", category).joinToString(" · "), color = Muted, fontFamily = Mono, fontSize = 11.5.sp, maxLines = 1)
                    }
                    val fav = item.favKey in app.favorites
                    de.vincentsberger.iptvplayer.ios.ui.design.IconAction(
                        if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Favorit entfernen" else "Als Favorit merken",
                        { toggleFavoriteWithFeedback(app, item) }, tint = if (fav) Signal else TextColor,
                    )
                    de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Tune, "Ton, Untertitel und Bild", onOptions)
                }
            }
            epg.getOrNull(0)?.let { e ->
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        de.vincentsberger.iptvplayer.ios.ui.design.Kicker("Jetzt", color = TextColor)
                        Text("${e.start}–${e.end}", color = Muted, fontFamily = Mono, fontSize = 13.sp)
                    }
                    Text(e.title, color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar(epgProgress(e), Modifier.weight(1f))
                        epgMinutesLeft(e)?.let { Text("noch $it Min.", color = Muted, fontSize = 11.5.sp) }
                    }
                    epgExcerpt(e, max = 240)?.let { Text(it, color = muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
            epg.getOrNull(1)?.let { e ->
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                    HorizontalDivider(color = Line, thickness = 1.dp)
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        de.vincentsberger.iptvplayer.ios.ui.design.Kicker("Danach")
                        Text(e.start, color = Muted, fontFamily = Mono, fontSize = 13.sp)
                        Text(e.title, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Senderliste der Kategorie; rechts das ganze Programm (bei Archiv-Sendern mit „Sendung verpasst“).
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val listName = when (app.liveCategory) {
                    AppState.LIVE_ALL -> "Alle Sender"
                    AppState.LIVE_FAV -> "Favoriten"
                    AppState.LIVE_RECENT -> "Zuletzt gesehen"
                    else -> app.section(Section.LIVE).categories.firstOrNull { it.id == app.liveCategory }?.let { splitCategoryName(it.name).name } ?: "Sender"
                }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(listName)
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = Muted, fontWeight = FontWeight.Normal)); append(" · ${items.size}"); pop()
                    },
                    Modifier.weight(1f), color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.height(32.dp).pressable(onClick = onProgramme).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(if (item.archiveDays > 0) "Programm · verpasst" else "Programm", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            val state = rememberLazyListState()
            LaunchedEffect(index) { state.animateScrollToItem((index - 2).coerceAtLeast(0)) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).background(Bg), state = state) {
                val keys = stableKeys(items)
                itemsIndexed(items, key = { i, _ -> keys[i] }) { i, ch ->
                    ChannelRow(app, ch, number = ch.number ?: (i + 1), selected = i == index, onClick = { onPick(i) }, showFavoriteButton = false)
                }
            }
        } else {
            val (title, subtitle) = app.pauseTitles(item)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.vincentsberger.iptvplayer.ios.ui.design.H3(title, Modifier.weight(1f, fill = false), maxLines = 2)
                    tagged.badges.forEach { de.vincentsberger.iptvplayer.ios.ui.design.Tag(it) }
                }
                subtitle?.let { de.vincentsberger.iptvplayer.ios.ui.design.SmallText(it) }
                val pos = seek.positionOf(player.timeMs)
                val dur = player.lengthMs
                if (dur > 0) {
                    ProgressBar((pos.toFloat() / dur), Modifier.padding(top = 8.dp).fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(pos), color = TextColor, fontFamily = Mono, fontSize = 13.sp)
                        Text("−" + formatTime((dur - pos).coerceAtLeast(0)), color = Muted, fontFamily = Mono, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Replay10, "10 Sekunden zurück", onJumpBack)
                    de.vincentsberger.iptvplayer.ios.ui.design.AppButton(
                        if (player.paused) "Abspielen" else "Pause", { player.togglePause() },
                        icon = if (player.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        size = de.vincentsberger.iptvplayer.ios.ui.design.ButtonSize.Small,
                    )
                    de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Forward30, "30 Sekunden vor", onJumpForward)
                }
                de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Tune, "Ton, Untertitel und Bild", onOptions)
            }
        }
    }
}

/** Rubrik über dem Titel im Player: „Film · 2025 · Thriller“ bzw. „Serie · …“. */
private fun playerKicker(app: AppState, item: Item): String {
    val info = app.mediaInfoFor(item)
    return listOfNotNull(
        if (item.section == de.vincentsberger.iptvplayer.shared.data.Section.SERIES) "Folge" else "Film",
        splitProviderTag(item.name).year ?: info?.year, info?.genres?.firstOrNull(),
    ).joinToString(" · ")
}

/** Adresse für den Player: lokale Datei eines fertigen Downloads, sonst der Stream. */
private fun playUrl(item: Item): String = IosDownloads.localUrl(item.url) ?: item.url
