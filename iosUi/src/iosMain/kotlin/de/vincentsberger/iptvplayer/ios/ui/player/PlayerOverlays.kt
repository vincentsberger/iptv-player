package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import de.vincentsberger.iptvplayer.ios.ui.design.ChannelLogo
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import de.vincentsberger.iptvplayer.shared.touch.DragZone
import androidx.compose.ui.graphics.Brush
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
import de.vincentsberger.iptvplayer.ios.ui.Surface2
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

/** Leichte Abdunklung des ganzen Bildes, solange die Bedienelemente sichtbar sind … */
private val Scrim = Color.Black.copy(alpha = 0.25f)
/** … und kräftiger an den Rändern, wo Titel und Leisten stehen. */
private val EdgeTop = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.35f to Color.Transparent)
private val EdgeBottom = Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))

/**
 * Zap-Karte (Entwurf „17“): beim Wischen unten links Nummer, Logo, Sender mit Kennzeichen und LIVE, „Jetzt“ mit
 * Fortschritt und „Danach“; rechts davon eine kleine Leiter mit Vorgänger und Nachfolger ([prev]/[next] = null am
 * Listenrand), damit klar ist, wohin der nächste Wisch führt.
 */
@Composable
internal fun ZapCardView(
    item: Item, number: Int, now: EpgEntry?, modifier: Modifier = Modifier,
    next: EpgEntry? = null, prev: Pair<Int, Item>? = null, following: Pair<Int, Item>? = null,
    /** Hochformat: schmale Karte ohne Mindestbreite (die Bildfläche ist nur so breit wie das Telefon). */
    compact: Boolean = false,
) {
    val tagged = splitProviderTag(item.name)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.widthIn(max = 440.dp).clip(Corner).background(Bg.copy(alpha = 0.92f)).border(1.dp, Line, Corner).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$number", color = TextColor, fontFamily = Mono, fontSize = 26.sp, lineHeight = 26.sp)
            de.vincentsberger.iptvplayer.ios.ui.design.ChannelLogo(item.logo, item.name, size = if (compact) 44.dp else 60.dp)
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.vincentsberger.iptvplayer.ios.ui.design.H3(tagged.name, Modifier.weight(1f, fill = false), maxLines = 1)
                    tagged.badges.forEach { de.vincentsberger.iptvplayer.ios.ui.design.Tag(it) }
                    LiveBadge()
                }
                if (now != null) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = Muted, fontFamily = Mono)); append(now.start); pop()
                            append(" ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append(now.title); pop()
                        },
                        color = TextColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ProgressBar(epgProgress(now), (if (compact) Modifier else Modifier.widthIn(min = 200.dp)).fillMaxWidth())
                }
                next?.let { Text("Danach ${it.start} ${it.title}", color = Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        // Leiter: nur übergebene Nachbarn; für VoiceOver ohne Bedeutung (die Karte sagt alles).
        if (prev != null || following != null) Column(Modifier.width(190.dp).semantics { invisibleToUser() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            prev?.let { (n, ch) -> LadderRow(Icons.Rounded.KeyboardArrowUp, n, ch, current = false) }
            LadderRow(null, number, item, current = true)
            following?.let { (n, ch) -> LadderRow(Icons.Rounded.KeyboardArrowDown, n, ch, current = false) }
        }
    }
}

@Composable
private fun LadderRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, number: Int, item: Item, current: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(Corner)
            .background(if (current) Surface1.copy(alpha = 0.95f) else Bg.copy(alpha = 0.6f))
            .border(1.dp, if (current) Line else TextColor.copy(alpha = 0.12f), Corner)
            .drawBehind { if (current) drawRect(Signal, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp)) { if (icon != null) Icon(icon, null, tint = Color(0xFFC9CDD2), modifier = Modifier.size(16.dp)) }
        Text("$number", color = if (current) Color.White else Color(0xFFC9CDD2), fontFamily = Mono, fontSize = 13.sp)
        Text(
            splitProviderTag(item.name).name, color = if (current) Color.White else Color(0xFFC9CDD2), fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Bedienelemente über dem Bild (Entwurf „15a/15b“); [onToggleFullscreen] dreht das Telefon per Knopf (auch bei
 * Dreh-Sperre). Oben Titelblock und Symbole, Film: Sprungknöpfe und Pause in der Mitte, unten Zeitleiste; Live quer:
 * unten „Senderliste“, Sendungsfortschritt und „Danach“.
 */
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
    /** Nur Live: Programm des Senders (mit „Sendung verpasst“). */
    onProgramme: (() -> Unit)? = null,
    /** Live: laufende und nächste Sendung (für Zeitleiste und „Danach“). */
    nowEntry: EpgEntry? = null,
    nextEntry: EpgEntry? = null,
    /** Kennzeichen des Titels („HD“, „4K“). */
    badges: List<String> = emptyList(),
    /** Film/Folge: Rubrik über dem Titel („Film · 2025 · Thriller“). */
    kicker: String? = null,
    /** Live quer: Senderliste öffnen. */
    onChannelList: (() -> Unit)? = null,
    /** Film: 10 s zurück / 30 s vor – über das gesammelte Spulziel (ein Neuladen statt vieler). */
    onJumpBack: () -> Unit = {},
    onJumpForward: () -> Unit = {},
) {
    Box(Modifier.matchParentSize().background(Scrim))
    Box(Modifier.matchParentSize().background(EdgeTop))
    Box(Modifier.matchParentSize().background(EdgeBottom))
    // Querformat: Notch und Home-Indikator aussparen; Hochformat: die kleine Bildfläche liegt schon unter der Statusleiste.
    Column(Modifier.matchParentSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier).padding(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Close, "Schließen", onClose, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (isLive) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveBadge(onImage = true)
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    badges.forEach { de.vincentsberger.iptvplayer.ios.ui.design.Tag(it, highlight = true) }
                    nowEntry?.let { Text("${it.start}–${it.end}", color = Color(0xFFC9CDD2), fontFamily = Mono, fontSize = 13.sp, maxLines = 1) }
                } else {
                    kicker?.let { de.vincentsberger.iptvplayer.ios.ui.design.Kicker(it, color = Color(0xFFC9CDD2)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        badges.forEach { de.vincentsberger.iptvplayer.ios.ui.design.Tag(it, highlight = true) }
                    }
                    subtitle?.let { Text(it, color = Color(0xFFC9CDD2), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (isLive) nowEntry?.let { Text(it.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (landscape) 20.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                description?.let { Text(it, color = Color(0xFFD5D8DC), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 470.dp)) }
            }
            if (isLive && canGoToLastChannel) de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.History, "Letzter Sender", onLastChannel, tint = Color.White)
            if (onProgramme != null) de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.CalendarMonth, "Programm", onProgramme, tint = Color.White)
            de.vincentsberger.iptvplayer.ios.ui.design.IconAction(Icons.Rounded.Tune, "Ton, Untertitel und Bild", onOptions, tint = Color.White)
            Row(
                Modifier.height(44.dp).pressable(onClick = onSleep).padding(horizontal = 8.dp)
                    .semantics { contentDescription = if (sleepLeft != null) "Schlaf-Timer, noch $sleepLeft Minuten" else "Schlaf-Timer" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = if (sleepLeft != null) Signal else Color.White, modifier = Modifier.size(22.dp))
                if (sleepLeft != null) Text("$sleepLeft", color = Color.White, fontFamily = Mono, fontSize = 12.sp)
            }
            de.vincentsberger.iptvplayer.ios.ui.design.IconAction(
                if (landscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                if (landscape) "Vollbild verlassen" else "Vollbild", onToggleFullscreen, tint = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        if (!isLive) {
            // Mitte: 10 s zurück, Pause/Abspielen, 30 s vor.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                JumpButton(Icons.Rounded.Replay10, "10 Sekunden zurück", onJumpBack)
                Box(
                    Modifier.size(64.dp).semantics { contentDescription = if (paused) "Abspielen" else "Pause" }.pressable(onClick = onTogglePause)
                        .clip(Corner).background(Bg.copy(alpha = 0.55f)).border(1.dp, TextColor.copy(alpha = 0.25f), Corner),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                JumpButton(Icons.Rounded.Forward30, "30 Sekunden vor", onJumpForward)
            }
            Spacer(Modifier.weight(1f))
            // Ziehen verschiebt nur die Anzeige; gesprungen wird beim Loslassen (IPTV lädt sonst bei jedem Schritt neu).
            if (durationMs > 0) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val shown = dragging?.toLong() ?: positionMs
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(formatTime(shown), color = Color.White, fontFamily = Mono, fontSize = 13.sp)
                    Slider(
                        value = shown.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = TextColor, activeTrackColor = Signal, inactiveTrackColor = TextColor.copy(alpha = 0.25f)),
                    )
                    Text("−" + formatTime((durationMs - shown).coerceAtLeast(0)), color = Color(0xFFC9CDD2), fontFamily = Mono, fontSize = 13.sp)
                }
            }
        } else if (landscape) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onChannelList != null) de.vincentsberger.iptvplayer.ios.ui.design.AppButton(
                    "Senderliste", onChannelList, style = de.vincentsberger.iptvplayer.ios.ui.design.ButtonStyle.Glass,
                    size = de.vincentsberger.iptvplayer.ios.ui.design.ButtonSize.Small, icon = Icons.AutoMirrored.Rounded.List,
                )
                nowEntry?.let { e ->
                    Text(e.start, color = Color(0xFFC9CDD2), fontFamily = Mono, fontSize = 11.5.sp)
                    ProgressBar(epgProgress(e), Modifier.weight(1f), height = 4.dp)
                    Text(e.end, color = Color(0xFFC9CDD2), fontFamily = Mono, fontSize = 11.5.sp)
                } ?: Spacer(Modifier.weight(1f))
                nextEntry?.let { e ->
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("Danach "); append(e.start); append(" ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)); append(e.title); pop()
                        },
                        color = Color(0xFFC9CDD2), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
            }
        }
    }
}

/** Sprungknopf 56 dp (10 s zurück / 30 s vor). */
@Composable
private fun JumpButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).semantics { contentDescription = description }.pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
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
                // Liste liegt auf surface1 – Auswahl daher surface2; der Stern bleibt der Senderliste von Live TV vorbehalten.
                ChannelRow(
                    app, item, number = item.number ?: (i + 1), selected = i == current, onClick = { onPick(i) },
                    background = Surface1, selectedBackground = Surface2, showFavoriteButton = false,
                )
            }
        }
    }
}

/** Anzeige beim Ziehen: Symbol, Balken und Prozent für Helligkeit bzw. Lautstärke. */
@Composable
internal fun LevelBadge(zone: DragZone, value: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = 0.7f), Corner).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (zone == DragZone.VOLUME) (if (value <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp) else Icons.Rounded.LightMode,
            if (zone == DragZone.VOLUME) "Lautstärke" else "Helligkeit", tint = Color.White, modifier = Modifier.size(20.dp),
        )
        ProgressBar(value, Modifier.padding(horizontal = 10.dp).width(120.dp), height = 4.dp)
        Text("${(value * 100).toInt()} %", color = Color.White, fontFamily = Mono, fontSize = 13.sp)
    }
}
