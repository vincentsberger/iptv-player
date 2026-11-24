package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.material.icons.rounded.Tune
import de.vincentsberger.iptvplayer.ios.ui.LiveBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Bg
import de.vincentsberger.iptvplayer.ios.ui.ChannelLogo
import de.vincentsberger.iptvplayer.ios.ui.ChannelRow
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.FlagOrTag
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Mono
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.OnSignal
import de.vincentsberger.iptvplayer.ios.ui.PrimaryButton
import de.vincentsberger.iptvplayer.ios.ui.ProgressBar
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.SeekState
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.epgProgress
import de.vincentsberger.iptvplayer.shared.formatTime
import de.vincentsberger.iptvplayer.shared.seekDeltaLabel

private val Scrim = Color.Black.copy(alpha = 0.55f)

/** Zap-Karte beim Senderwechsel: Logo, Nummer, Name, laufende Sendung. */
@Composable
internal fun ZapCardView(item: Item, number: Int, now: EpgEntry?, modifier: Modifier = Modifier) {
    val tagged = splitProviderTag(item.name)
    Row(
        modifier.widthIn(max = 420.dp).background(Bg.copy(alpha = 0.92f), Corner).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(item.logo, 48.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", color = Signal, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                tagged.tag?.let { FlagOrTag(it, Modifier.padding(end = 6.dp)) }
                Text(tagged.name, color = TextColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (now != null) {
                Text("${now.start}–${now.end}  ${now.title}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressBar(epgProgress(now), Modifier.padding(top = 4.dp).fillMaxWidth(), height = 2.dp)
            }
        }
    }
}

/** Bedienelemente über dem Bild; [onToggleFullscreen] dreht das Telefon per Knopf (auch bei Dreh-Sperre). */
@Composable
internal fun BoxScope.PlayerControls(
    title: String,
    subtitle: String?,
    /** Kurze Beschreibung der laufenden Sendung (Live), sonst null. */
    description: String?,
    isLive: Boolean,
    paused: Boolean,
    positionMs: Long,
    durationMs: Long,
    sleepLeft: Int?,
    landscape: Boolean,
    canGoToLastChannel: Boolean,
    onClose: () -> Unit,
    onTogglePause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSleep: () -> Unit,
    onLastChannel: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOptions: () -> Unit,
) {
    Box(Modifier.matchParentSize().background(Scrim))
    // Querformat: Notch und Home-Indikator aussparen; Hochformat: die kleine Bildfläche liegt schon unter der Statusleiste.
    Column(Modifier.matchParentSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Rounded.Close, "Schließen", tint = Color.White) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                if (isLive) LiveBadge(Modifier.padding(bottom = 2.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                description?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (isLive && canGoToLastChannel) IconButton(onLastChannel) { Icon(Icons.Rounded.History, "Letzter Sender", tint = Color.White) }
            TextButton(onSleep) {
                Icon(Icons.Rounded.Bedtime, "Schlaf-Timer", tint = if (sleepLeft != null) Signal else Color.White, modifier = Modifier.size(20.dp))
                if (sleepLeft != null) Text(" $sleepLeft min", color = Color.White, fontSize = 13.sp)
            }
            IconButton(onOptions) { Icon(Icons.Rounded.Tune, "Ton, Untertitel und Bild", tint = Color.White) }
            IconButton(onToggleFullscreen) {
                Icon(if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Vollbild", tint = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!isLive) {
            // Ziehen verschiebt nur die Anzeige; gesprungen wird beim Loslassen (IPTV lädt sonst bei jedem Schritt neu).
            var dragging by remember { mutableStateOf<Float?>(null) }
            val shown = dragging?.toLong() ?: positionMs
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onTogglePause) {
                    Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Abspielen" else "Pause", tint = Color.White)
                }
                Text(formatTime(shown), color = Color.White, fontFamily = Mono, fontSize = 13.sp)
                Slider(
                    value = shown.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                    enabled = durationMs > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = Signal, activeTrackColor = Signal, inactiveTrackColor = Line),
                )
                Text(formatTime(durationMs), color = Color.White, fontFamily = Mono, fontSize = 13.sp)
            }
        }
    }
}

/** Gesammeltes Spulziel: „+1 min 30 s“ und die Zielzeit. */
@Composable
internal fun SeekBadge(state: SeekState, modifier: Modifier = Modifier) {
    Column(modifier.background(Bg.copy(alpha = 0.9f), Corner).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(seekDeltaLabel(state.deltaMs), color = Signal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(formatTime(state.targetMs), color = Color.White, fontFamily = Mono)
    }
}

/** Stream abgebrochen: neuer Versuch läuft bzw. alle Versuche gescheitert. */
@Composable
internal fun ConnectionOverlay(lost: Boolean, onRetry: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(Bg.copy(alpha = 0.9f), Corner).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!lost) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp)
            Text("Verbindung wird wiederhergestellt …", color = TextColor, modifier = Modifier.padding(top = 10.dp))
        } else {
            Text("Verbindung verloren", color = TextColor, fontWeight = FontWeight.SemiBold)
            Text("Der Stream antwortet nicht mehr.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClose) { Text("Schließen", color = Muted) }
                PrimaryButton("Erneut versuchen", icon = Icons.Rounded.Refresh, onClick = onRetry)
            }
        }
    }
}

/** „Nächste Folge in 7 s“ – abbrechbar. */
@Composable
internal fun NextEpisodeBadge(seconds: Int, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing).background(Bg.copy(alpha = 0.92f), Corner).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Nächste Folge in $seconds s", color = TextColor)
        TextButton(onCancel) { Text("Abbrechen", color = Signal) }
    }
}

/** Senderliste vom linken Rand (Querformat) über dem laufenden Bild. */
@Composable
internal fun ChannelDrawer(app: AppState, items: List<Item>, current: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Scrim).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose))
        val state = rememberLazyListState()
        LaunchedEffect(Unit) { state.scrollToItem((current - 2).coerceAtLeast(0)) }
        LazyColumn(
            Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(Surface1).windowInsetsPadding(WindowInsets.safeDrawing),
            state = state,
        ) {
            val keys = stableKeys(items)
            itemsIndexed(items, key = { i, _ -> keys[i] }) { i, item ->
                ChannelRow(app, item, number = item.number ?: (i + 1), selected = i == current, onClick = { onPick(i) })
            }
        }
    }
}
