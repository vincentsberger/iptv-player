package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.Airing
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.EpgGrid
import de.vincentsberger.iptvplayer.shared.GridCell
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.hasMixedProviderTags
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

/** Breite des festen Sender-Kopfs links in jeder Zeile. */
private val HeadWidth = 230.dp
private val RowHeight = 56.dp

/** So viele Zeilen ober- und unterhalb des Fokus laden ihr Programm schon vorab. */
private const val PREFETCH_ROWS = 5

/**
 * Programmführer als Raster (Spec `docs/superpowers/specs/2026-11-28-epg-grid-design.md`): Sender untereinander,
 * Sendungen auf einer gemeinsamen Zeitleiste. Das ganze Raster ist **ein** fokussierbares Element mit eigener
 * Tastenbehandlung – der Fokus ist Zustand (Zeile + Zeitstelle), nicht Compose-Fokus von Zelle zu Zelle; bei
 * verschieden breiten Zellen bleibt ▲▼ so verlässlich an derselben Uhrzeit.
 */
@Composable
fun GuideGrid(
    vm: AppState,
    channels: List<Item>,
    playing: Item,
    onSwitch: (Int) -> Unit,
    onCatchUp: (Item, EpgEntry) -> Unit,
    onExitLeft: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    /** Beschriftung der OK-Taste für die Tastenhinweise („Umschalten“, „Abspielen“, „Infos“). */
    onOkLabel: (String) -> Unit = {},
) {
    val menu = LocalMenu.current
    val now = EpgGrid.seconds(vm.localNow()) ?: 0L
    val opening = remember(channels) { EpgGrid.openingStart(now) }
    val left = remember(channels) { EpgGrid.leftLimit(now, opening, channels.maxOfOrNull { it.archiveDays } ?: 0) }
    var right by remember(channels) { mutableLongStateOf(opening + EpgGrid.VISIBLE_S) }
    var windowStart by remember(channels) { mutableLongStateOf(opening) }
    val playingIndex = remember(channels, playing) { channels.indexOfFirst { it.url == playing.url } }
    var row by remember(channels) { mutableStateOf(playingIndex.coerceAtLeast(0)) }
    // Zeitstelle des Fokus – robust gegen nachladende Programme: die Zelle wird jedes Mal neu bestimmt.
    var anchor by remember(channels) { mutableLongStateOf(now) }
    var info by remember(channels) { mutableStateOf<Pair<Item, EpgEntry>?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (row - 2).coerceAtLeast(0))

    fun cellsOf(i: Int): List<GridCell> = EpgGrid.cells(channels.getOrNull(i)?.let(vm::gridProgramme).orEmpty(), left, right)
    val focusedCells = cellsOf(row)
    val focusedIndex = EpgGrid.cellAt(focusedCells, anchor)
    val focused = focusedCells.getOrNull(focusedIndex)
    val focusedChannel = channels.getOrNull(row)

    fun okLabelFor(cell: GridCell?, ch: Item?): String {
        val entry = cell?.entry ?: return "Umschalten"
        return when (EpgGrid.airing(entry, now)) {
            Airing.NOW -> "Umschalten"
            Airing.PAST -> if (ch != null && vm.canCatchUp(ch, entry)) "Abspielen" else "Infos"
            Airing.FUTURE -> "Infos"
        }
    }
    LaunchedEffect(focused, focusedChannel) { onOkLabel(okLabelFor(focused, focusedChannel)) }

    // Zeilen rund um den Fokus vorab laden; sichtbare Zeilen fordern selbst an (siehe unten).
    LaunchedEffect(row, channels) {
        for (i in row - PREFETCH_ROWS..row + PREFETCH_ROWS) channels.getOrNull(i)?.let(vm::requestProgramme)
    }
    // Fokuszeile sichtbar halten.
    LaunchedEffect(row) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == row } || visible.firstOrNull()?.index == row || visible.lastOrNull()?.index == row) {
            listState.animateScrollToItem((row - 2).coerceAtLeast(0))
        }
    }

    /** ▲▼: Zeitstelle der fokussierten Zelle für die nächste Zeile merken. */
    fun keepTime() {
        focused?.let { anchor = EpgGrid.verticalAnchor(it, windowStart, now, anchor) }
    }

    fun moveTo(cell: GridCell) {
        anchor = cell.start
        windowStart = EpgGrid.scrollTo(windowStart, cell, left, right)
    }

    fun ok() {
        val ch = focusedChannel ?: return
        val entry = focused?.entry
        when {
            entry == null || EpgGrid.airing(entry, now) == Airing.NOW -> onSwitch(row)
            EpgGrid.airing(entry, now) == Airing.PAST && vm.canCatchUp(ch, entry) -> onCatchUp(ch, entry)
            else -> info = ch to entry
        }
    }

    fun infoOk() {
        val (ch, entry) = info ?: return
        info = null
        when {
            EpgGrid.airing(entry, now) == Airing.NOW -> channels.indexOfFirst { it.url == ch.url }.takeIf { it >= 0 }?.let(onSwitch)
            EpgGrid.airing(entry, now) == Airing.PAST && vm.canCatchUp(ch, entry) -> onCatchUp(ch, entry)
        }
    }

    Box(modifier) {
        Surface(
            onClick = { if (info != null) infoOk() else ok() },
            onLongClick = { focusedChannel?.let { menu.current = itemMenu(vm, it) } },
            modifier = Modifier.fillMaxSize().testTag("guide:grid").focusRequester(focusRequester)
                .onPreviewKeyEvent { ev ->
                    if (info != null) {
                        // Infokarte offen: Zurück schließt nur die Karte, Pfeile ruhen.
                        if (ev.key == Key.Back) {
                            if (ev.type == KeyEventType.KeyUp) info = null
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent ev.key in ArrowKeys
                    }
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent ev.key in ArrowKeys
                    when (ev.key) {
                        Key.DirectionUp -> if (row > 0) { keepTime(); row -= 1 }
                        Key.DirectionDown -> if (row < channels.lastIndex) { keepTime(); row += 1 }
                        Key.DirectionRight -> focusedCells.getOrNull(focusedIndex + 1)?.let(::moveTo)
                        Key.DirectionLeft -> {
                            val prev = focusedCells.getOrNull(focusedIndex - 1)
                            if (prev != null) moveTo(prev) else onExitLeft()
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
            shape = ClickableSurfaceDefaults.shape(Corner),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                contentColor = TextColor, focusedContentColor = TextColor,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Column(Modifier.fillMaxSize()) {
                TimeHeader(windowStart)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val trackWidth = maxWidth - HeadWidth
                    val dpPerSec = trackWidth / EpgGrid.VISIBLE_S.toFloat()
                    val mixedCountries = remember(channels) { hasMixedProviderTags(channels) }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(channels, key = { i, ch -> "$i:${ch.url}" }) { i, ch ->
                            LaunchedEffect(ch.url) { vm.requestProgramme(ch) }
                            DisposableEffect(ch.url) { onDispose { vm.releaseProgramme(ch) } }
                            val programme = vm.gridProgramme(ch)
                            LaunchedEffect(programme) {
                                if (programme != null) right = maxOf(right, EpgGrid.rightLimit(listOf(programme), opening))
                                // Ganzes Programm leer → „Jetzt/Danach“ als Rückfall holen.
                                if (programme?.isEmpty() == true) vm.requestEpg(ch)
                            }
                            // Zellen nur neu schneiden, wenn sich Programm oder Grenzen ändern – nicht bei jedem Tastendruck.
                            val cells = remember(programme, left, right) { programme?.let { EpgGrid.cells(it, left, right) } }
                            val number = remember(ch, channels) { channelNumberOf(ch, channels) }
                            GridRow(
                                vm, ch, number = number, playing = ch.url == playing.url,
                                cells = cells,
                                focusedCell = if (i == row) focused else null,
                                windowStart = windowStart, now = now, dpPerSec = dpPerSec, showCountry = mixedCountries,
                                modifier = Modifier.testTag("guide:row:$i"),
                            )
                        }
                    }
                    NowLine(now, windowStart, dpPerSec)
                }
            }
        }
        // zIndex: das fokussierte tv-Surface hebt sich sonst über die Karte.
        info?.let { (ch, entry) -> ProgrammeInfoCard(vm, ch, entry, now, Modifier.align(Alignment.Center).zIndex(1f)) }
    }
}

private val ArrowKeys = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/** Kopfzeile: Uhrzeiten halbstündlich ab dem Fensterbeginn, um Mitternacht mit Wochentag. */
@Composable
private fun TimeHeader(windowStart: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
        val dpPerSec = (maxWidth - HeadWidth) / EpgGrid.VISIBLE_S.toFloat()
        var t = windowStart
        while (t < windowStart + EpgGrid.VISIBLE_S) {
            Text(
                EpgGrid.timeLabel(t), fontSize = 14.sp, fontFamily = Mono, color = Muted, maxLines = 1,
                modifier = Modifier.offset(x = HeadWidth + dpPerSec * (t - windowStart).toFloat()).padding(start = 6.dp),
            )
            t += EpgGrid.SLOT_S
        }
    }
}

/** Senkrechte Linie bei „jetzt“, nur wenn „jetzt“ im Fenster liegt. */
@Composable
private fun NowLine(now: Long, windowStart: Long, dpPerSec: Dp) {
    if (now < windowStart || now >= windowStart + EpgGrid.VISIBLE_S) return
    Box(Modifier.offset(x = HeadWidth + dpPerSec * (now - windowStart).toFloat()).width(2.dp).fillMaxHeight().background(Signal))
}

@Composable
private fun GridRow(
    vm: AppState, channel: Item, number: Int, playing: Boolean,
    /** null = Programm wird noch geladen. */
    cells: List<GridCell>?,
    focusedCell: GridCell?,
    windowStart: Long, now: Long, dpPerSec: Dp, showCountry: Boolean,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        ChannelHead(channel, number, playing, favorite = channel.favKey in vm.favorites, showCountry = showCountry)
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            if (cells == null) {
                Text("Programm wird geladen …", color = Muted, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
                return@Box
            }
            val windowEnd = windowStart + EpgGrid.VISIBLE_S
            cells.filter { it.end > windowStart && it.start < windowEnd }.forEach { cell ->
                val from = maxOf(cell.start, windowStart)
                val to = minOf(cell.end, windowEnd)
                val isFocused = cell == focusedCell
                val dim = cell.entry?.let { EpgGrid.airing(it, now) == Airing.PAST && !vm.canCatchUp(channel, it) } ?: false
                GridCellView(
                    cell, isFocused, dim,
                    Modifier.offset(x = dpPerSec * (from - windowStart).toFloat()).width(dpPerSec * (to - from).toFloat()).fillMaxHeight()
                        .padding(end = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelHead(channel: Item, number: Int, playing: Boolean, favorite: Boolean, showCountry: Boolean) {
    Row(
        Modifier.width(HeadWidth).fillMaxHeight().padding(end = 8.dp).background(Surface1, Corner).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Laufender Sender: Markierung statt nur Farbe.
        Text(if (playing) "▶" else number.toString().padStart(2, '0'), fontSize = 14.sp, fontFamily = Mono, color = if (playing) Signal else Muted,
            maxLines = 1, modifier = Modifier.widthIn(min = 30.dp))
        ChannelLogo(channel, 32.dp)
        val tagged = splitProviderTag(channel.name)
        tagged.tag?.takeIf { showCountry }?.let { TagChip(it, Modifier.padding(start = 8.dp)) }
        Text(
            tagged.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (favorite) Icon(Icons.Rounded.Star, tint = Signal, size = 16.dp)
    }
}

@Composable
private fun GridCellView(cell: GridCell, focused: Boolean, dim: Boolean, modifier: Modifier) {
    val bg = if (focused) FocusBg else Surface1
    val fg = if (focused) FocusFg else TextColor
    Box(
        modifier.alpha(if (dim && !focused) 0.45f else 1f).background(bg, Corner)
            .then(if (focused) Modifier.border(2.dp, FocusFg, Corner) else Modifier)
            .then(if (focused) Modifier.semantics(mergeDescendants = true) {}.testTag("guide:focused") else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val entry = cell.entry
        Column {
            Text(entry?.title ?: "Keine Programmdaten", color = if (entry == null) fg.copy(alpha = 0.6f) else fg, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry != null) Text(entry.start, color = fg.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = Mono, maxLines = 1)
        }
    }
}

/** Infos zu einer Sendung als Karte über dem Raster; OK spielt ab bzw. schaltet um, Zurück schließt. */
@Composable
private fun ProgrammeInfoCard(vm: AppState, channel: Item, entry: EpgEntry, now: Long, modifier: Modifier) {
    val airing = EpgGrid.airing(entry, now)
    val action = when {
        airing == Airing.NOW -> "OK: Umschalten"
        airing == Airing.PAST && vm.canCatchUp(channel, entry) -> "OK: Abspielen"
        else -> null
    }
    Column(
        modifier.widthIn(max = 640.dp).background(Surface0, Corner).border(1.dp, Line, Corner).padding(28.dp)
            .semantics(mergeDescendants = true) {}.testTag("guide:info"),
    ) {
        Kicker(splitProviderTag(channel.name).name)
        Headline(entry.title, 26.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        val day = entry.startAt.take(10).split('-').let { if (it.size == 3) "${it[2]}.${it[1]}. · " else "" }
        Text("$day${entry.start}–${entry.end}", color = Muted, fontSize = 16.sp, fontFamily = Mono, modifier = Modifier.padding(top = 4.dp))
        if (entry.description.isNotBlank()) {
            Text(entry.description, fontSize = 16.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
        }
        Text(listOfNotNull(action, "Zurück: Schließen").joinToString(" · "), color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
    }
}
