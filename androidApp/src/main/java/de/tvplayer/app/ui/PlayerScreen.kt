package de.tvplayer.app.ui

import android.content.Context
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import de.tvplayer.shared.AppState
import de.tvplayer.shared.data.EpgEntry
import de.tvplayer.shared.data.USER_AGENT
import de.tvplayer.shared.data.httpClient
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val http = OkHttpDataSource.Factory(httpClient).setUserAgent(USER_AGENT)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
        .build()
        .apply { playWhenReady = true }
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
    val player = remember { buildPlayer(context) }
    var index by remember { mutableIntStateOf(startIndex) }
    val item = items[index]
    val currentItem by rememberUpdatedState(item)
    val isLive = item.section == Section.LIVE
    val isEpisodes = item.section == Section.SERIES && items.size > 1

    var error by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var resumedAt by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf<Item?>(null) }
    val focus = remember { FocusRequester() }

    fun switchTo(newIndex: Int) {
        index = wrapIndex(newIndex, items.size)
    }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Wiedergabe nicht möglich (${e.errorCodeName})"
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && isEpisodes && index < items.lastIndex) switchTo(index + 1)
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

    LaunchedEffect(index) {
        previous?.let { vm.saveProgress(it, player.currentPosition, player.duration) }
        previous = item
        error = null
        epg = emptyList()
        val start = if (index == startIndex) startPosition else 0L
        resumedAt = start
        player.setMediaItem(mediaItem(item.url), start)
        player.prepare()
        player.play()
        infoTick++
        epg = vm.nowPlaying(item)
    }

    LaunchedEffect(infoTick) {
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
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action = playerActionFor(ev.key, isLive, isEpisodes) ?: return@onKeyEvent false
                when (action) {
                    PlayerAction.PREVIOUS -> switchTo(index - 1)
                    PlayerAction.NEXT -> switchTo(index + 1)
                    PlayerAction.SHOW_INFO -> Unit
                    PlayerAction.TOGGLE_PLAY -> when {
                        error != null -> { error = null; player.prepare(); player.play() }
                        isLive -> Unit
                        player.isPlaying -> player.pause()
                        else -> player.play()
                    }
                    PlayerAction.PLAY -> player.play()
                    PlayerAction.PAUSE -> player.pause()
                    PlayerAction.REWIND -> player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                    PlayerAction.FORWARD -> player.seekTo(player.currentPosition + 30_000)
                }
                // Beim Senderwechsel erscheint die Info ohnehin, sonst Info-Leiste kurz einblenden.
                if (action != PlayerAction.PREVIOUS && action != PlayerAction.NEXT) infoTick++
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    this.player = player
                }
            },
        )

        if (buffering && error == null) Box(Modifier.align(Alignment.Center)) { Spinner(64.dp) }

        if (!isLive && !playing && !buffering && error == null) {
            Box(Modifier.align(Alignment.Center).size(96.dp).background(Color(0x99000000), CircleShape), Alignment.Center) {
                Icon(Icons.Rounded.Pause, tint = Color.White, size = 56.dp)
            }
        }

        error?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color(0xE6161A26), RoundedCornerShape(20.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Wiedergabe nicht möglich", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(it, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    if (isLive) "OK  erneut versuchen     ·     ▲ ▼  anderer Sender" else "OK  erneut versuchen     ·     Zurück  zur Übersicht",
                    color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        // Obere Leiste: Titel und Uhrzeit
        AnimatedVisibility(showInfo, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLive) {
                    Box(Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(10.dp)).padding(8.dp)) { ChannelLogo(item, 44.dp) }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (isLive || isEpisodes) Text("${index + 1} / ${items.size}", color = Muted, fontSize = 14.sp)
                    Text(item.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(clock, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Untere Leiste: Programm bzw. Fortschritt
        AnimatedVisibility(showInfo, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
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
                            Text(now.start, color = Muted, fontSize = 14.sp)
                            ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 12.dp), height = 5.dp)
                            Text(now.end, color = Muted, fontSize = 14.sp)
                        }
                        epg.getOrNull(1)?.let { Text("Danach  ${it.start}  ${it.title}", color = Muted, fontSize = 16.sp) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveBadge()
                            Text("Keine Programminformationen", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Hints("▲ ▼  Sender wechseln", "Zurück  Senderliste")
                } else {
                    if (resumedAt > 0) Text("Fortgesetzt bei ${formatTime(resumedAt)}  ·  ◀ zum Zurückspulen", color = Accent2, fontSize = 15.sp)
                    val fraction = if (duration > 0) position.toFloat() / duration else 0f
                    ProgressBar(fraction, Modifier.fillMaxWidth(), height = 6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, tint = Color.White, size = 26.dp)
                        Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f))
                        if (duration > 0) Text("Noch ${formatTime(duration - position)}", color = Muted, fontSize = 15.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        HintIcon(Icons.Rounded.FastRewind, "10 s")
                        HintIcon(Icons.Rounded.PlayArrow, "OK Pause")
                        HintIcon(Icons.Rounded.FastForward, "30 s")
                        if (isEpisodes) Text("▲ ▼  Folge wechseln", color = Muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Text(
        "LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xFFE5484D), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Hints(vararg hints: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 4.dp)) {
        hints.forEach { Text(it, color = Muted, fontSize = 14.sp) }
    }
}

@Composable
private fun HintIcon(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint = Muted, size = 20.dp)
        Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
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
internal enum class PlayerAction { PREVIOUS, NEXT, SHOW_INFO, TOGGLE_PLAY, PLAY, PAUSE, REWIND, FORWARD }

/**
 * Tastenzuordnung im Player: ▲ ▼ wechseln bei Live TV und Serienfolgen den Eintrag,
 * ◀ ▶ spulen (nicht bei Live), OK pausiert. Unbekannte Tasten → null (nicht behandelt).
 */
internal fun playerActionFor(key: Key, isLive: Boolean, isEpisodes: Boolean): PlayerAction? {
    val zapping = isLive || isEpisodes
    return when (key) {
        Key.DirectionUp, Key.ChannelUp -> if (zapping) PlayerAction.PREVIOUS else PlayerAction.SHOW_INFO
        Key.DirectionDown, Key.ChannelDown -> if (zapping) PlayerAction.NEXT else PlayerAction.SHOW_INFO
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> PlayerAction.TOGGLE_PLAY
        Key.MediaPlay -> PlayerAction.PLAY
        Key.MediaPause -> PlayerAction.PAUSE
        Key.DirectionLeft, Key.MediaRewind -> if (isLive) PlayerAction.SHOW_INFO else PlayerAction.REWIND
        Key.DirectionRight, Key.MediaFastForward -> if (isLive) PlayerAction.SHOW_INFO else PlayerAction.FORWARD
        else -> null
    }
}

/** Index im Kreis: vor dem ersten kommt der letzte Eintrag und umgekehrt. */
internal fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size
