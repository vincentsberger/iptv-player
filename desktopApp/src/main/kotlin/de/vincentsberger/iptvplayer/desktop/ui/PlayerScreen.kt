package de.vincentsberger.iptvplayer.desktop.ui

import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.ZapCard
import androidx.compose.runtime.SideEffect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subtitles
import de.vincentsberger.iptvplayer.desktop.player.VlcTrack
import de.vincentsberger.iptvplayer.shared.data.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.ChannelNumberInput
import de.vincentsberger.iptvplayer.shared.StreamRecovery
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.desktop.player.VlcPlayer
import de.vincentsberger.iptvplayer.desktop.player.VlcSetup
import de.vincentsberger.iptvplayer.desktop.player.monotonicMs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    app: AppState,
    startItems: List<Item>,
    startIndex: Int,
    startPosition: Long,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
) {
    if (!VlcSetup.available) {
        Column(Modifier.fillMaxSize().background(Bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Headline("Wiedergabe-Engine fehlt", 28.sp)
            Text("Installiere VLC (64-Bit) von videolan.org oder liefere die VLC-Dateien mit der App aus (siehe README).", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            PrimaryButton("Zurück", onClick = onBack)
        }
        return
    }

    // Gewählte Ton-/Untertitelsprache gilt auch für den nächsten Film oder Sender.
    val player = remember { VlcPlayer().apply { preferredAudio = app.audioLanguage; preferredSubtitles = app.subtitleLanguage } }
    // Liste kann wechseln: Ziffernwahl eines Senders außerhalb der aktuellen Kategorie springt in „alle Sender“.
    var items by remember { mutableStateOf(startItems) }
    var index by remember { mutableIntStateOf(startIndex) }
    val item = items[index]
    val zap = remember { ZapCard(System::currentTimeMillis) }
    /** Eingetippte Nummer, die es nicht gibt – kurz als Hinweis zeigen. */
    var notFound by remember { mutableStateOf<Int?>(null) }
    val currentItem by rememberUpdatedState(item)
    val isLive = item.section == Section.LIVE
    val isEpisodes = item.section == Section.SERIES && items.size > 1

    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableFloatStateOf(app.volume.toFloat()) }
    var muted by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf("") }
    var resumedAt by remember { mutableLongStateOf(0L) }
    var previous by remember { mutableStateOf<Item?>(null) }
    val focus = remember { FocusRequester() }
    val numberInput = remember { ChannelNumberInput() }
    var lastIndex by remember { mutableStateOf<Int?>(null) }
    var nextIn by remember { mutableStateOf<Int?>(null) }
    var nextCancelled by remember(index) { mutableStateOf(false) }
    var sleepLeft by remember { mutableStateOf(app.sleepMinutesLeft()) }
    var tracksOpen by remember { mutableStateOf(false) }
    // Nach dem Wechsel Vollbild ↔ Fenster den Tastaturfokus zurückholen, sonst reagiert der Player nicht mehr auf Tasten.
    LaunchedEffect(fullscreen) { delay(150); runCatching { focus.requestFocus() } }
    var videoFit by remember { mutableStateOf(app.videoFit) }
    var audioDelay by remember { mutableStateOf(app.audioDelayMs) }
    /** Wie oft eine Spultaste schon wiederholt wurde (gehalten) – für größere Sprünge. */
    var seekRepeat by remember { mutableStateOf(0) }
    val recovery = remember { StreamRecovery() }
    /** Stream abgebrochen, neuer Versuch läuft bzw. wartet. */
    var reconnecting by remember { mutableStateOf(false) }
    /** Alle automatischen Versuche gescheitert → Fehlermeldung mit „Erneut versuchen“. */
    var connectionLost by remember { mutableStateOf(false) }
    /** Letzte sichere Stelle bei Filmen/Folgen – dort geht es nach einem Abbruch weiter. */
    var resumeAt by remember { mutableLongStateOf(0L) }

    fun switchTo(i: Int) {
        val target = wrapIndex(i, items.size)
        if (target != index) { lastIndex = index; index = target }
    }
    fun poke() { activity++ }

    DisposableEffect(Unit) {
        onDispose {
            app.saveProgress(currentItem, player.time, player.length)
            app.volume = volume.toInt()
            player.release()
        }
    }

    LaunchedEffect(items, index) {
        previous?.let { app.saveProgress(it, player.time, player.length) }
        previous = item
        val start = if (index == startIndex) startPosition else 0L
        resumedAt = start
        player.play(item.url, start)
        player.volume = if (muted) 0 else volume.toInt()
        // Live: Zapp-Karte statt der ganzen Leiste; Filme/Folgen: Leiste wie bisher.
        if (isLive) zap.onChannelChanged(item) else { zap.hide(); poke() }
        epg = emptyList()
        epg = app.nowPlaying(item)
    }

    // Zapp-Karte: erstes Bild startet den Countdown, danach regelmäßig prüfen, ob sie ausblenden soll.
    LaunchedEffect(items, index, player.frame != null) { if (player.frame != null) zap.onFirstFrame() }
    LaunchedEffect(zap.channel) { while (zap.channel != null) { delay(250); zap.tick() } }
    LaunchedEffect(connectionLost) { if (connectionLost) zap.hide() }
    LaunchedEffect(notFound) { if (notFound != null) { delay(2000); notFound = null } }

    // Abbrüche und Hänger erkennen und den Stream selbst neu laden (Live: neu verbinden, Filme: an derselben Stelle).
    LaunchedEffect(items, index) {
        recovery.reset(); reconnecting = false; connectionLost = false; resumeAt = 0L
        while (true) {
            delay(1000)
            val now = monotonicMs()
            if (now - player.lastProgressAt < 2_000) {
                recovery.onProgress(now)
                reconnecting = false
                connectionLost = false
                if (!isLive && player.time > 0) resumeAt = player.time
            }
            if (connectionLost) continue
            val broken = player.error != null ||
                (isLive && player.finished) ||
                (!player.paused && !player.finished && recovery.isStalled(maxOf(player.lastProgressAt, player.startedAt), now))
            if (!broken) continue
            val wait = recovery.nextRetryDelay()
            if (wait == null) { reconnecting = false; connectionLost = true; continue }
            reconnecting = true
            delay(wait)
            player.play(item.url, if (isLive) 0L else resumeAt, keepFrame = true)
        }
    }

    // Lautstärke anwenden, sobald VLC wirklich abspielt
    LaunchedEffect(player.playing) {
        if (player.playing) player.audioDelayMs = audioDelay
        if (player.playing) player.volume = if (muted) 0 else volume.toInt()
    }

    // Nächste Folge automatisch – mit 10-Sekunden-Countdown, abbrechbar, in den Einstellungen abschaltbar
    LaunchedEffect(player.finished, index, nextCancelled) {
        nextIn = null
        val next = app.nextEpisodeIndex(items, index)
        if (!player.finished || next == null || !app.autoNextEpisode || nextCancelled) return@LaunchedEffect
        for (s in 10 downTo 1) { nextIn = s; delay(1000) }
        nextIn = null
        switchTo(next)
    }

    // Senderwahl per Ziffern: kurz nach der letzten Ziffer umschalten
    LaunchedEffect(numberInput.digits) {
        if (numberInput.digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        numberInput.take()?.let { n ->
            val pick = ChannelNumberInput.find(n, items, if (isLive) app.section(Section.LIVE).items else items)
            when {
                pick == null -> notFound = n
                pick.list == items -> switchTo(pick.index)
                else -> { lastIndex = null; items = pick.list; index = pick.index }
            }
        }
    }

    LaunchedEffect(activity, tracksOpen) {
        showUi = true
        if (tracksOpen) return@LaunchedEffect // Menü offen: Steuerleiste nicht ausblenden
        delay(if (resumedAt > 0) 6000 else 3500)
        showUi = false
        resumedAt = 0
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        val fmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
        var ticks = 0
        while (true) {
            position = player.time
            duration = player.length
            clock = fmt.format(Date())
            sleepLeft = app.sleepMinutesLeft()
            player.applyPreferencesIfPending()
            if (app.consumeSleepTimer()) { onBack(); return@LaunchedEffect }
            if (++ticks % 30 == 0 && !isLive) app.saveProgress(currentItem, position, duration)
            delay(500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp) { seekRepeat = 0; return@onPreviewKeyEvent false } // losgelassen: wieder kleine Sprünge
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                digitOf(ev.key)?.let { d -> if (isLive) numberInput.type(d); return@onPreviewKeyEvent true }
                val action = playerActionFor(ev.key, isLive, canSwitch = isLive || isEpisodes, fullscreen)
                    ?: return@onPreviewKeyEvent false
                when (action) {
                    PlayerAction.TogglePause -> player.togglePause()
                    PlayerAction.SeekBack -> player.seekBy(-10_000L * seekMultiplier(seekRepeat++))
                    PlayerAction.SeekForward -> player.seekBy(30_000L * seekMultiplier(seekRepeat++))
                    PlayerAction.Previous -> switchTo(index - 1)
                    PlayerAction.Next -> switchTo(index + 1)
                    PlayerAction.ToggleFullscreen -> onToggleFullscreen()
                    PlayerAction.ToggleMute -> { muted = !muted; player.volume = if (muted) 0 else volume.toInt() }
                    PlayerAction.Back -> onBack()
                    PlayerAction.LastChannel -> lastIndex?.let(::switchTo)
                    PlayerAction.SleepTimer -> { app.cycleSleepTimer(); sleepLeft = app.sleepMinutesLeft() }
                    PlayerAction.Tracks -> tracksOpen = !tracksOpen
                    PlayerAction.Ignore -> Unit
                }
                true
            }
            .focusable()
            .onPointerEvent(PointerEventType.Move) { poke() }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                poke(); if (!isLive) player.togglePause()
            },
    ) {
        player.frame?.let { img ->
            // Melden, welches Bild jetzt gezeigt wird – erst dann darf der Player ältere Bilder freigeben.
            SideEffect { player.markShown(img) }
            Image(img, null, contentScale = contentScaleOf(videoFit), modifier = Modifier.fillMaxSize())
        }

        if ((player.buffering && player.error == null || reconnecting) && !connectionLost) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(56.dp), color = Signal, trackColor = Color(0x40ECEFF4), strokeWidth = 3.dp)
                if (reconnecting) {
                    Text(
                        "Verbindung wird wiederhergestellt …", color = Color.White,
                        modifier = Modifier.padding(top = 14.dp).background(Color(0x99000000), Corner).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (connectionLost) {
            Column(Modifier.align(Alignment.Center).background(Surface1.copy(alpha = 0.94f), Corner).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Headline("Wiedergabe nicht möglich", 26.sp)
                Text(player.error ?: "Die Verbindung zum Stream ist abgebrochen.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                PrimaryButton("Erneut versuchen") {
                    recovery.reset(); connectionLost = false
                    player.play(item.url, if (isLive) 0L else resumeAt)
                }
            }
        }
        if (!isLive && !player.playing && !player.buffering && player.error == null && player.frame != null) {
            Box(Modifier.align(Alignment.Center).size(88.dp).background(Color(0x99000000), CircleShape), Alignment.Center) {
                Icon(Icons.Rounded.Pause, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }

        // Zapp-Karte oben links (nur Live, nicht zusammen mit der Leiste)
        val zapItem = zap.channel
        if (isLive && zapItem != null && !showUi && !connectionLost) {
            val now = epg.getOrNull(0)
            ZapCardView(zapItem, channelNumberOf(zapItem, items), now, now?.let(::epgProgress) ?: 0f, Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 28.dp))
        }
        notFound?.let { n ->
            Text(
                "Sender $n nicht gefunden", color = Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 32.dp).background(Color(0xCC000000), Corner).padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        // Eingetippte Sendernummer
        if (numberInput.digits.isNotEmpty()) {
            Text(
                numberInput.digits + "_", color = Color.White, fontFamily = Mono, fontSize = 44.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 32.dp).background(Color(0xCC000000), Corner).padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }

        // Countdown zur nächsten Folge
        nextIn?.let { secs ->
            val next = items.getOrNull(index + 1)
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 140.dp).width(340.dp).panel().padding(20.dp)) {
                Kicker("Nächste Folge in $secs s", Signal)
                Headline(next?.name.orEmpty(), 20.sp, Modifier.padding(top = 6.dp, bottom = 14.dp), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Jetzt ansehen", Icons.Rounded.PlayArrow) { switchTo(index + 1) }
                    SecondaryButton("Abbrechen") { nextCancelled = true }
                }
            }
        }

        // Obere Leiste
        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = Color.White) }
                if (isLive) Box(Modifier.padding(start = 8.dp).background(Color(0x33FFFFFF), SmallCorner).padding(6.dp)) { ChannelLogo(item, 36.dp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    if (isLive) Text(channelNumberOf(item, items).toString(), color = Muted, fontSize = 13.sp, fontFamily = Mono)
                    else if (isEpisodes) Text("${index + 1} / ${items.size}", color = Muted, fontSize = 13.sp)
                    Headline(item.name, 26.sp, color = Color.White)
                }
                sleepLeft?.let {
                    Icon(Icons.Rounded.Bedtime, "Schlaf-Timer", tint = Signal, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                    Text("$it min", color = Color.White, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.padding(end = 20.dp))
                }
                Text(clock, fontSize = 22.sp, fontFamily = Mono, color = Color.White)
            }
        }

        // Untere Leiste
        AnimatedVisibility(showUi, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000)))).padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 18.dp),
            ) {
                if (isLive) {
                    val now = epg.getOrNull(0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = OnSignal, modifier = Modifier.background(Signal, SmallCorner).padding(horizontal = 8.dp, vertical = 2.dp))
                        Text(now?.title ?: "Keine Programminformationen", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (now != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(now.start, color = Muted, fontSize = 13.sp)
                            ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 10.dp), height = 5.dp)
                            Text(now.end, color = Muted, fontSize = 13.sp)
                        }
                        epg.getOrNull(1)?.let { Text("Danach  ${it.start}  ${it.title}", color = Muted, modifier = Modifier.padding(top = 4.dp)) }
                    }
                } else {
                    if (resumedAt > 0) Text("Fortgesetzt bei ${formatTime(resumedAt)}", color = Signal, fontSize = 14.sp)
                    Slider(
                        value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { seeking = it; poke() },
                        onValueChangeFinished = { seeking?.let { player.seekTo((it * duration).toLong()) }; seeking = null },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Signal, inactiveTrackColor = Color(0x40FFFFFF)),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive || isEpisodes) CtrlButton(Icons.Rounded.SkipPrevious, if (isLive) "Vorheriger Sender" else "Vorherige Folge") { switchTo(index - 1) }
                    if (!isLive) {
                        CtrlButton(Icons.Rounded.Replay10, "10 Sekunden zurück") { player.seekBy(-10_000) }
                        CtrlButton(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Pause", big = true) { player.togglePause() }
                        CtrlButton(Icons.Rounded.Forward30, "30 Sekunden vor") { player.seekBy(30_000) }
                    }
                    if (isLive || isEpisodes) CtrlButton(Icons.Rounded.SkipNext, if (isLive) "Nächster Sender" else "Nächste Folge") { switchTo(index + 1) }
                    if (!isLive) Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, modifier = Modifier.padding(start = 12.dp))
                    if (!isLive && duration > 0) Text("endet um ${endsAt(duration - position)}", color = Muted, modifier = Modifier.padding(start = 12.dp))
                    Spacer(Modifier.weight(1f))
                    CtrlButton(if (muted || volume < 1f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Ton") {
                        muted = !muted; player.volume = if (muted) 0 else volume.toInt()
                    }
                    Slider(
                        value = volume, valueRange = 0f..100f,
                        onValueChange = { volume = it; muted = false; player.volume = it.toInt(); poke() },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x40FFFFFF)),
                        modifier = Modifier.width(130.dp),
                    )
                    Box {
                        CtrlButton(Icons.Rounded.Subtitles, "Ton & Untertitel (T)") { tracksOpen = !tracksOpen }
                        TrackMenu(
                            player, expanded = tracksOpen,
                            onDismiss = { tracksOpen = false; runCatching { focus.requestFocus() } },
                            onAudio = { app.audioLanguage = it.language },
                            onSubtitles = { app.subtitleLanguage = it?.language ?: Storage.SUBTITLES_OFF },
                            videoFit = videoFit, onVideoFit = { videoFit = it; app.videoFit = it },
                            audioDelay = audioDelay, onAudioDelay = { audioDelay = it; app.audioDelayMs = it; player.audioDelayMs = it },
                        )
                    }
                    CtrlButton(Icons.Rounded.Bedtime, sleepLeft?.let { "Schlaf-Timer: noch $it min (S)" } ?: "Schlaf-Timer (S)") {
                        app.cycleSleepTimer(); sleepLeft = app.sleepMinutesLeft()
                    }
                    CtrlButton(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Vollbild", onClick = onToggleFullscreen)
                }
            }
        }
    }
}

/** Auswahl von Tonspur und Untertiteln (liest die Spuren beim Öffnen frisch aus VLC). */
@Composable
private fun TrackMenu(
    player: VlcPlayer,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAudio: (VlcTrack) -> Unit,
    onSubtitles: (VlcTrack?) -> Unit,
    videoFit: String,
    onVideoFit: (String) -> Unit,
    audioDelay: Int,
    onAudioDelay: (Int) -> Unit,
) {
    val audio = remember(expanded) { if (expanded) player.audioTracks() else emptyList() }
    val subtitles = remember(expanded) { if (expanded) player.subtitleTracks() else emptyList() }
    DropdownMenu(expanded, onDismiss) {
        @Composable
        fun Entry(label: String, selected: Boolean, onClick: () -> Unit) = DropdownMenuItem(
            text = { Text(label) },
            onClick = { onClick(); onDismiss() },
            leadingIcon = { Icon(Icons.Rounded.Check, null, tint = if (selected) Signal else Color.Transparent) },
        )
        Text("TONSPUR", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (audio.isEmpty()) Text("Keine Auswahl verfügbar", color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        audio.forEach { t -> Entry(t.label, t.selected) { player.selectAudio(t); onAudio(t) } }
        Text("UNTERTITEL", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp))
        Entry("Aus", subtitles.none { it.selected }) { player.selectSubtitles(null); onSubtitles(null) }
        subtitles.forEach { t -> Entry(t.label, t.selected) { player.selectSubtitles(t); onSubtitles(t) } }
        Text("BILDFORMAT", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp))
        listOf("fit" to "Einpassen", "zoom" to "Füllen (beschneidet)", "fill" to "Strecken").forEach { (k, l) -> Entry(l, videoFit == k) { onVideoFit(k) } }
        Text("TON-VERZÖGERUNG", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp))
        listOf(-200, -100, 0, 100, 200, 300).forEach { ms ->
            Entry(if (ms == 0) "Aus" else "${if (ms > 0) "+" else "−"}${kotlin.math.abs(ms)} ms (Ton ${if (ms > 0) "später" else "früher"})", audioDelay == ms) { onAudioDelay(ms) }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(if (big) 56.dp else 44.dp)) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (big) 40.dp else 28.dp))
    }
}

/** Was eine Taste im Player auslöst. */
/** Faktor für gehaltene Spultasten: erst normale Sprünge, dann dreifach, dann sechsfach. */
internal fun seekMultiplier(repeatCount: Int): Int = when {
    repeatCount < 10 -> 1
    repeatCount < 30 -> 3
    else -> 6
}

/** Uhrzeit, zu der der Film bei normaler Wiedergabe endet („21:45“). */
internal fun endsAt(remainingMs: Long, now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(now + remainingMs.coerceAtLeast(0)))

private fun contentScaleOf(fit: String): ContentScale = when (fit) {
    "zoom" -> ContentScale.Crop
    "fill" -> ContentScale.FillBounds
    else -> ContentScale.Fit
}

internal enum class PlayerAction { TogglePause, SeekBack, SeekForward, Previous, Next, ToggleFullscreen, ToggleMute, Back, LastChannel, SleepTimer, Tracks, Ignore }

/**
 * Tastenbelegung des Players als reine Funktion (testbar ohne VLC).
 * `null` = Taste nicht zuständig; [PlayerAction.Ignore] = Taste wird geschluckt, tut aber nichts
 * (z. B. Spulen bei Live TV).
 */
internal fun playerActionFor(key: Key, isLive: Boolean, canSwitch: Boolean, fullscreen: Boolean): PlayerAction? = when (key) {
    Key.Spacebar, Key.Enter, Key.MediaPlayPause -> if (isLive) PlayerAction.Ignore else PlayerAction.TogglePause
    Key.DirectionLeft -> if (isLive) PlayerAction.Ignore else PlayerAction.SeekBack
    Key.DirectionRight -> if (isLive) PlayerAction.Ignore else PlayerAction.SeekForward
    Key.DirectionUp, Key.PageUp -> if (canSwitch) PlayerAction.Previous else PlayerAction.Ignore
    Key.DirectionDown, Key.PageDown -> if (canSwitch) PlayerAction.Next else PlayerAction.Ignore
    Key.F -> PlayerAction.ToggleFullscreen
    Key.M -> PlayerAction.ToggleMute
    Key.L -> if (isLive) PlayerAction.LastChannel else PlayerAction.Ignore
    Key.S -> PlayerAction.SleepTimer
    Key.T -> PlayerAction.Tracks
    // Im Vollbild verlässt Esc zuerst nur den Vollbildmodus.
    Key.Escape, Key.Backspace -> if (fullscreen) PlayerAction.ToggleFullscreen else PlayerAction.Back
    else -> null
}

/** Ziffer einer Taste (Hauptfeld oder Ziffernblock), sonst null. */
internal fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/** Index mit Umlauf: nach dem letzten Sender kommt wieder der erste. */
internal fun wrapIndex(i: Int, size: Int): Int = ((i % size) + size) % size

internal fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
