package de.vincentsberger.iptvplayer.desktop.ui

import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.AlertDialog
import de.vincentsberger.iptvplayer.shared.dashboardNeedsToggle
import de.vincentsberger.iptvplayer.shared.dashboardOptionsShown
import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import de.vincentsberger.iptvplayer.shared.initialsOf
import de.vincentsberger.iptvplayer.shared.subscriptionLabel
import de.vincentsberger.iptvplayer.shared.connectionsLabel
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.hostOf
import de.vincentsberger.iptvplayer.shared.isOnAir
import de.vincentsberger.iptvplayer.shared.programmeDays
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CheckboxDefaults
import de.vincentsberger.iptvplayer.shared.HomeRow
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.epgDescription
import de.vincentsberger.iptvplayer.shared.UpdatePlatform
import de.vincentsberger.iptvplayer.shared.flagOf
import de.vincentsberger.iptvplayer.shared.channelNumberOf
import androidx.compose.ui.draw.clip
import de.vincentsberger.iptvplayer.shared.data.CountryOption
import androidx.compose.material3.Checkbox

import androidx.compose.material.icons.rounded.SystemUpdate

import kotlinx.coroutines.launch

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.foundation.focusable

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.input.key.Key

import androidx.compose.ui.input.key.key

import androidx.compose.ui.input.key.type

import androidx.compose.ui.input.key.KeyEventType

import androidx.compose.ui.input.key.isCtrlPressed

import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.compose.material.icons.rounded.Home

import de.vincentsberger.iptvplayer.shared.labelOf

import androidx.compose.material.icons.rounded.Add

import androidx.compose.material.icons.rounded.SwapHoriz

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.delay
import java.util.Calendar
import de.vincentsberger.iptvplayer.shared.epgProgress
import de.vincentsberger.iptvplayer.shared.standLabel

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Rounded.Home
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.LIVE -> Icons.Rounded.LiveTv
    Tab.MOVIES -> Icons.Rounded.Movie
    Tab.SERIES -> Icons.Rounded.VideoLibrary
    Tab.FAVORITES -> Icons.Rounded.Favorite
    Tab.SETTINGS -> Icons.Rounded.Settings
    Tab.DOWNLOADS -> Icons.Rounded.Settings // gibt es am PC nicht (siehe Sidebar)
}

@Composable
fun HomeScreen(app: AppState) {
    // Strg 1–7 und Strg+F fängt das Fenster ab (Main.kt, globalActionFor) – sie wirken auch ohne Tastaturfokus.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        DepthBackground()
        CompositionLocalProvider(LocalCompact provides (maxWidth < 1280.dp)) {
            // Nach Anmeldung/Start: Ladebildschirm, bis der aktuelle Bereich da ist – dann weich überblenden.
            // Erst nach 300 ms zeigen: Bei schnellen Servern soll nichts kurz aufblitzen.
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(app.startupDone) { slow = false; if (!app.startupDone) { delay(300); slow = true } }
            // Danach die Übersicht (bis „Weiter“), dann die App.
            val overview = app.overview
            val phase = when { !app.startupDone -> if (slow) 1 else 0; overview != null -> 2; else -> 3 }
            Crossfade(phase, animationSpec = tween(400), label = "startup") { p ->
                if (p == 0) Box(Modifier.fillMaxSize())
                else if (p == 1) PlaylistLoadingScreen(app)
                else if (p == 2) PlaylistOverviewScreen(app, overview ?: emptyList())
                else Row(Modifier.fillMaxSize()) {
                    Sidebar(app)
                    AnimatedContent(app.tab, Modifier.weight(1f).fillMaxHeight(), transitionSpec = { tabTransition() }, label = "tab") { tab ->
                        Box(Modifier.fillMaxSize()) {
                            when (tab) {
                                Tab.LIVE -> LiveScreen(app)
                                Tab.MOVIES -> BrowseScreen(app, Section.MOVIES)
                                Tab.SERIES -> BrowseScreen(app, Section.SERIES)
                                Tab.FAVORITES -> FavoritesScreen(app)
                                Tab.HOME -> StartScreen(app)
                                Tab.SEARCH -> SearchScreen(app)
                                Tab.SETTINGS -> SettingsScreen(app)
                                Tab.DOWNLOADS -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seitenleiste (Entwurf „Seitenleiste“): Bereiche mit Tastenkürzel „Strg 1–7“, unten der Anbieter mit Abo. Schmales
 * Fenster: nur Symbole (64 dp) statt umbrechender Beschriftungen.
 */
@Composable
private fun Sidebar(app: AppState) {
    val compact = LocalCompact.current
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(if (compact) 64.dp else 220.dp).fillMaxHeight().background(SidebarBg)
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Downloads gibt es nur in der TV-App (am PC streamt man ohnehin).
            desktopTabs.forEachIndexed { i, t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                SidebarItem(t.title, t.icon(), "Strg ${i + 1}", t == app.tab, compact) { app.selectTab(t) }
            }
            AccountBlock(app, compact)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(SidebarLine))
    }
}

/** Fläche und Linie der Seitenleiste: eine Spur dunkler als der Inhalt (Entwurf `#131518`/`#26292D`). */
private val SidebarBg = Color(Tokens.BgDeep)
private val SidebarLine = Color(Tokens.Surface2)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarItem(label: String, icon: ImageVector, shortcut: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val signal = Signal
    // Kürzel-Kappen nur, solange Strg gedrückt ist (sonst sieben Kappen Dauerrauschen); der Tooltip verrät sie immer.
    val showCap = LocalKeyboard.current.ctrlHeld
    val item: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(Corner)
                .background(if (selected || hovered) Surface1 else Color.Transparent)
                .drawBehind { if (selected) drawRect(signal, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                .hoverable(interaction).handCursor().focusRing().clickable(interaction, indication = null, onClick = onClick)
                // Schmal ohne sichtbare Beschriftung: Name trotzdem als Text für Bedienungshilfen (und Tests).
                .semantics { this.selected = selected; if (compact) text = androidx.compose.ui.text.AnnotatedString(label) }
                .padding(start = if (compact) 0.dp else 10.dp, end = if (compact) 0.dp else 6.dp),
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (selected || hovered) TextColor else Muted
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            if (!compact) {
                Text(label, Modifier.weight(1f), color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showCap) ShortcutCap(shortcut)
            }
        }
    }
    // Schmal steht nur das Symbol da → Name und Kürzel; breit steht der Name schon da → nur das Kürzel.
    val tip = if (compact) "$label · $shortcut" else shortcut
    TooltipArea(
        tooltip = {
            Text(tip, color = TextColor, fontSize = 12.sp, modifier = Modifier.background(Surface2, Corner).padding(horizontal = 8.dp, vertical = 4.dp))
        },
    ) { item() }
}

/** Tastenkappe klein (Entwurf `.k`): Mono 10,5, Rand. [onPrimary] = auf dem roten Hauptknopf (dunkel, durchscheinend). */
@Composable
fun ShortcutCap(text: String, modifier: Modifier = Modifier, onPrimary: Boolean = false) {
    val border = if (onPrimary) OnSignal.copy(alpha = 0.35f) else Line
    val bg = if (onPrimary) OnSignal.copy(alpha = 0.12f) else Bg.copy(alpha = 0.55f)
    Text(
        text, modifier.height(18.dp).border(1.dp, border, SmallCorner).background(bg, SmallCorner).padding(horizontal = 3.dp, vertical = 2.dp),
        color = if (onPrimary) OnSignal else Muted, fontFamily = Mono, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1,
    )
}

/** Unten in der Seitenleiste: Kürzel des Anbieters, Name und Abo („Abo bis 14.03.2027 · 1/2“). */
@Composable
private fun AccountBlock(app: AppState, compact: Boolean) {
    val label = app.accountLabel
    if (label.isEmpty()) return
    // Kurz: eigener Name oder der Server („tv.example“) – „Xtream Codes · user …“ wurde abgeschnitten und hieß bei allen gleich.
    val title = app.accounts.firstOrNull { it.id == app.activeAccountId }?.let { it.name ?: accountHostOf(it.account) } ?: label
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(
            Modifier.fillMaxWidth().padding(start = if (compact) 0.dp else 4.dp, end = 4.dp, top = 12.dp),
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).background(Surface2, Corner), contentAlignment = Alignment.Center) {
                Text(initialsOf(title).take(2), color = TextColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!compact) Column(Modifier.weight(1f)) {
                Text(title, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                app.accountStatus?.info?.let { info ->
                    val cons = info.maxConnections?.let { " · ${info.activeConnections ?: 0}/$it" }.orEmpty()
                    Text(subscriptionLabel(info) + cons, color = Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Server eines Anbieters („tv.example“) – kurzer Name für die Seitenleiste. */
internal fun accountHostOf(account: Account): String = when (account) {
    is Account.Xtream -> hostOf(account.server)
    is Account.M3u -> hostOf(account.url)
}

/** Eintrag in Seitenleiste und Kategorielisten: Signal-Strich links, Fläche nur als Zusatz. */
@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, reserveIcon: Boolean = false, onHide: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    WithContextMenu(listOfNotNull(onHide?.let { ContextMenuItem("Kategorie ausblenden", it) })) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected || hovered) Surface1 else Color.Transparent)
            .hoverable(interaction)
            .handCursor().focusRing().clickable(interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        val color = if (selected || hovered) TextColor else Muted
        if (icon != null) Icon(icon, null, tint = if (selected) Signal else color, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        else if (reserveIcon) Spacer(Modifier.width(32.dp))
        val tagged = splitCategoryName(label)
        tagged.tag?.let { FlagOrTag(it, Modifier.padding(start = 12.dp)) }
        Text(
            // Anbieter-Kategorien sind oft lang („DE | SPORT ▸ FUSSBALL & LIVE-EVENTS“) → bis zu zwei Zeilen.
            tagged.name, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        )
        if (onHide != null && hovered) {
            IconButton(onHide, Modifier.size(32.dp).handCursor()) {
                Icon(Icons.Rounded.VisibilityOff, "Kategorie ausblenden", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    }
}

// ---------- Live TV ----------

/** „1.223 Sender · Gefiltert: AT, DE“ – erklärt eine kurze Liste bei aktivem Länderfilter. */
private fun liveSubtitle(app: AppState, channels: Int): String =
    "$channels Sender" + app.activeLiveCountries.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", ", " · Gefiltert: ").orEmpty()

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun LiveScreen(app: AppState) {
    val state = app.section(Section.LIVE)
    when {
        state.loading -> Loading("Sender werden geladen …")
        state.error != null -> ErrorView(state.error.orEmpty()) { app.reloadAll() }
        else -> {
            val all = app.liveChannels
            var filter by rememberSaveable { mutableStateOf("") }
            val channels = remember(all, filter) { if (filter.isBlank()) all else all.filter { it.name.contains(filter.trim(), ignoreCase = true) } }
            // Auswahl (Klick, Tastatur) und Vorschau (Maus verweilt) getrennt: Überfahren verschiebt nie die Auswahl.
            var selected by rememberSaveable { mutableStateOf<Item?>(null) }
            var preview by remember { mutableStateOf<Item?>(null) }
            val shown = preview ?: selected
            /** Zuletzt mit der Tastatur geblättert: Zeilen, die unter die ruhende Maus rollen, zeigen keine Vorschau. */
            var keyboardLast by remember { mutableStateOf(false) }
            var pointerAt by remember { mutableStateOf(Offset.Unspecified) }
            var filterFocused by remember { mutableStateOf(false) }
            /** Tastatur: 0 = Kategorien, 1 = Sender. */
            var column by remember { mutableStateOf(1) }
            val keyFocus = remember { FocusRequester() }
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
            var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
            LaunchedEffect(shown) {
                epg = emptyList()
                val s = shown ?: return@LaunchedEffect
                delay(200)
                epg = app.nowPlaying(s)
            }
            val cats = remember(state.categories, app.history.isNotEmpty()) {
                buildList {
                    if (app.history.any { it.item.section == Section.LIVE }) add(Category(AppState.LIVE_RECENT, "Zuletzt gesehen"))
                    add(Category(AppState.LIVE_FAV, "Favoriten"))
                    add(Category(AppState.LIVE_ALL, "Alle Sender"))
                    addAll(state.categories)
                }
            }
            fun moveChannel(delta: Int) {
                if (channels.isEmpty()) return
                val i = (channels.indexOf(selected).takeIf { it >= 0 }?.plus(delta) ?: 0).coerceIn(0, channels.lastIndex)
                selected = channels[i]
                preview = null
                keyboardLast = true
                scope.launch { listState.animateScrollToItem((i - 3).coerceAtLeast(0)) }
            }
            fun moveCategory(delta: Int) {
                val i = (cats.indexOfFirst { it.id == app.liveCategory } + delta).coerceIn(0, cats.lastIndex)
                app.liveCategory = cats[i].id
                selected = null
                preview = null
            }
            fun play(item: Item?) {
                val i = item?.let(channels::indexOf)?.takeIf { it >= 0 } ?: return
                app.open(channels, i)
            }
            // Tastatur: ↑↓ blättern (Bild ↑↓ zehn auf einmal), ← → Kategorien/Sender, Enter abspielen, F Favorit.
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 24.dp)
                .focusRequester(keyFocus).focusable()
                .onPointerEvent(PointerEventType.Move) {
                    // Nur echte Mausbewegung zählt – beim Blättern rollen Zeilen unter den ruhenden Zeiger.
                    val at = it.changes.first().position
                    if (at != pointerAt) { pointerAt = at; keyboardLast = false }
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown || ev.isCtrlPressed) return@onPreviewKeyEvent false
                    // Im Filterfeld sind Buchstaben Text; ↓ und Enter springen in die Liste.
                    if (filterFocused) {
                        if (ev.key !in setOf(Key.DirectionDown, Key.Enter, Key.NumPadEnter)) return@onPreviewKeyEvent false
                        runCatching { keyFocus.requestFocus() }
                        column = 1
                        selected = null
                        moveChannel(0)
                        return@onPreviewKeyEvent true
                    }
                    when (ev.key) {
                        Key.DirectionDown -> if (column == 1) moveChannel(1) else moveCategory(1)
                        Key.DirectionUp -> if (column == 1) moveChannel(-1) else moveCategory(-1)
                        Key.PageDown -> if (column == 1) moveChannel(10) else moveCategory(5)
                        Key.PageUp -> if (column == 1) moveChannel(-10) else moveCategory(-5)
                        Key.DirectionLeft -> column = 0
                        Key.DirectionRight -> column = 1
                        Key.Enter, Key.NumPadEnter -> if (column == 1) play(selected) else column = 1
                        Key.F -> selected?.let(app::toggleFavorite)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }) {
                CategoryList("Live TV", Icons.Rounded.LiveTv, liveSubtitle(app, state.items.size), cats, app.liveCategory, onHide = { app.toggleHidden(Section.LIVE, it) }) { app.liveCategory = it }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(top = 14.dp)) {
                            Headline(splitCategoryName(cats.firstOrNull { it.id == app.liveCategory }?.name ?: "").name, 24.sp)
                            // Gedämpft, solange die Tastatur in den Kategorien steht. Bedienhinweise stehen in der leeren Info-Spalte.
                            Text(
                                "${channels.size} Sender",
                                color = if (column == 1) Muted else Muted.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        // Wie in Filme und Serien: sucht nur in der gewählten Kategorie.
                        OutlinedTextField(
                            filter, { filter = it }, singleLine = true,
                            placeholder = { Text("Sender suchen", color = Muted, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            shape = Corner,
                            colors = fieldColors(),
                            modifier = Modifier.padding(start = 16.dp).width(if (LocalCompact.current) 200.dp else 260.dp)
                                .onFocusChanged { filterFocused = it.isFocused }.testTag("live-filter"),
                        )
                    }
                    Rule(Modifier.padding(end = 14.dp))
                    if (channels.isEmpty()) {
                        EmptyView("Keine Sender", icon = Icons.Rounded.LiveTv, text = if (app.liveCategory == AppState.LIVE_FAV) "Klicke auf den Stern bei einem Sender, um ihn hier zu speichern." else "In dieser Kategorie gibt es keine Sender.")
                    } else {
                        Box(Modifier.panel()) {
                            val keys = remember(channels) { stableKeys(channels) }
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 14.dp)) {
                                itemsIndexed(channels, key = { i, _ -> keys[i] }) { i, ch ->
                                    // Lazy-Liste: nur sichtbare Zeilen existieren → Programm nur für sie laden.
                                    LaunchedEffect(ch.url) { app.requestEpg(ch) }
                                    // animateItem: beim Sortieren/Ausblenden gleiten Zeilen an ihren neuen Platz.
                                    Column(Modifier.animateItem()) {
                                    ChannelRow(
                                        channelNumberOf(ch, all), ch, ch == selected, ch.favKey in app.favorites, app.nowFor(ch),
                                        onSelect = { selected = ch; preview = null },
                                        onPreview = { if (!keyboardLast) preview = ch },
                                        onPlay = { app.open(channels, i) },
                                        active = column == 1,
                                        onToggleFavorite = { app.toggleFavorite(ch) },
                                    )
                                    Rule()
                                    }
                                }
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                }
                Spacer(Modifier.width(28.dp))
                LiveInfo(app, shown, epg, shown?.let { it.favKey in app.favorites } == true,
                    onPlay = { shown?.let { s -> app.open(channels, channels.indexOf(s).coerceAtLeast(0)) } },
                    onToggleFavorite = { shown?.let(app::toggleFavorite) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    number: Int, item: Item, selected: Boolean, favorite: Boolean, now: EpgEntry?,
    onSelect: () -> Unit, onPlay: () -> Unit, onToggleFavorite: () -> Unit,
    /** Maus verweilt kurz: Info-Spalte zeigt dieses Programm, ohne die Auswahl zu ändern. */
    onPreview: () -> Unit = {},
    /** Senderspalte hat die Tastatur – sonst Auswahl gedämpft zeigen. */
    active: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Erst nach kurzem Verweilen: Wer schräg zum „Ansehen“-Knopf fährt, streift andere Sender nur.
    LaunchedEffect(hovered) { if (hovered) { delay(LIVE_PREVIEW_DELAY_MS); onPreview() } }
    WithContextMenu(listOf(ContextMenuItem("Ansehen", onPlay), ContextMenuItem(favoriteMenuLabel(favorite), onToggleFavorite))) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(when { selected && active -> Surface2; selected || hovered -> Surface1; else -> Color.Transparent })
            .hoverable(interaction)
            // Ein Klick spielt ab – kein Doppelklick und kein kleiner Play-Knopf nötig.
            .handCursor().focusRing().clickable(interactionSource = interaction, indication = null) { onSelect(); onPlay() }
            .semantics { this.selected = selected }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Text(number.toString().padStart(2, '0'), color = if (selected) TextColor else Muted, fontSize = 14.sp, fontFamily = Mono, maxLines = 1, modifier = Modifier.padding(start = 14.dp).width(52.dp))
        Box(Modifier.width(60.dp).height(36.dp), Alignment.Center) { ChannelLogo(item, 34.dp) }
        val tagged = splitProviderTag(item.name)
        tagged.tag?.let { FlagOrTag(it, Modifier.padding(start = 12.dp)) }
        Column(Modifier.weight(1f).padding(start = if (tagged.tag != null) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tagged.name, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Qualität aus dem Anbieternamen („ᴿᴬᵂ“, „ᴴᴰ“) als kleine Kennzeichen statt im Namen.
                tagged.badges.forEach { TagChip(it, Modifier.padding(start = 6.dp)) }
            }
            // „Jetzt läuft“: Sendung plus kleiner Fortschrittsbalken
            if (now != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(now.title, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                ProgressBar(epgProgress(now), Modifier.padding(start = 10.dp).width(44.dp), height = 2.dp)
            }
        }
        if (hovered || selected) {
            IconButton(onPlay, Modifier.handCursor()) { Icon(Icons.Rounded.PlayArrow, "Ansehen", tint = TextColor) }
        }
        if (favorite || hovered) {
            IconButton(onToggleFavorite, Modifier.handCursor()) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit", tint = if (favorite) Signal else Muted)
            }
        }
    }
    }
}

/** Verweildauer, bevor die Info-Spalte einem überfahrenen Sender folgt. */
private const val LIVE_PREVIEW_DELAY_MS = 150L

@Composable
private fun LiveInfo(app: AppState, item: Item?, epg: List<EpgEntry>, favorite: Boolean, onPlay: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(Modifier.width(if (LocalCompact.current) 290.dp else 360.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxHeight().panel().padding(24.dp)) {
            // Inhalt blendet beim Senderwechsel (und wenn das Programm eintrifft) weich über.
            AnimatedContent(
                item to epg, Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "info",
            ) { (shown, shownEpg) -> LiveInfoContent(app, shown, shownEpg) }
            if (item != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("Ansehen", Icons.Rounded.PlayArrow, onClick = onPlay)
                    SecondaryButton(if (favorite) "Favorit" else "Merken", if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = onToggleFavorite)
                }
            }
        }
    }
}

@Composable
private fun LiveInfoContent(app: AppState, item: Item?, epg: List<EpgEntry>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Bg.copy(alpha = 0.6f), Corner), Alignment.Center) {
            if (item != null) ChannelLogo(item, 96.dp) else Masthead(18.sp)
        }
        Spacer(Modifier.height(22.dp))
        if (item == null) {
            Headline("Wähle einen Sender", 24.sp, maxLines = 2)
            Text(
                "Zeig mit der Maus auf einen Sender, um sein Programm zu sehen. Ein Klick oder Enter startet ihn.",
                color = Muted, modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        Headline(splitProviderTag(item.name).name, 30.sp, maxLines = 2)
        Spacer(Modifier.height(18.dp))
        Rule()
        val now = epg.getOrNull(0)
        if (now == null) {
            Text("Keine Programminformationen", color = Muted, modifier = Modifier.padding(top = 14.dp))
            return@Column
        }
        LiveBadge(Modifier.padding(top = 14.dp, bottom = 6.dp))
        Headline(now.title, 21.sp, maxLines = 2)
        // Vorschau: die ganze Beschreibung (im Player nur ein Auszug); der Bereich scrollt bei langen Texten.
        epgDescription(now)?.let {
            Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Text(now.start, color = Muted, fontSize = 13.sp, fontFamily = Mono)
            ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(now.end, color = Muted, fontSize = 13.sp, fontFamily = Mono)
        }
        epg.getOrNull(1)?.let {
            Kicker("Danach", modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            Row {
                Text(it.start, color = Muted, fontFamily = Mono, modifier = Modifier.width(56.dp))
                Text(it.title, color = TextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            epgDescription(it)?.let { d ->
                Text(d, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 56.dp, top = 4.dp))
            }
        }
        if (item != null) ProgrammeList(app, item)
    }
}

/**
 * Weiteres Programm unter „Jetzt“/„Danach“, nach Tagen gruppiert. Vergangene Sendungen im Archiv des Anbieters
 * („Sendung verpasst“) haben ↺ und spielen per Klick.
 */
@Composable
private fun ProgrammeList(app: AppState, channel: Item) {
    LaunchedEffect(channel.url) { app.requestProgramme(channel) }
    val programme = app.programmes[channel.url].orEmpty()
    if (programme.isEmpty()) return
    val now = app.localNow()
    // Vergangenes nur, wenn es nachträglich abspielbar ist; Laufendes und Nächstes stehen schon oben.
    val shown = programme.filter { e -> if (e.endAt <= now) app.canCatchUp(channel, e) else !isOnAir(e, now) }
    if (shown.isEmpty()) return
    Kicker(if (channel.archiveDays > 0) "Programm · Sendung verpasst" else "Programm", modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    programmeDays(shown, now).forEach { day ->
        Text(day.label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        day.entries.forEach { e ->
            val catchup = e.endAt <= now
            Row(
                Modifier.fillMaxWidth().clip(Corner).then(if (catchup) Modifier.handCursor() else Modifier)
                    .clickable(enabled = catchup) { app.playCatchup(channel, e) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(e.start, color = Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                Text(e.title, color = TextColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (catchup) Icon(Icons.Rounded.History, "Nachträglich ansehen", tint = Signal, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
        }
    }
}

@Composable
private fun CategoryList(
    title: String, icon: ImageVector, subtitle: String, categories: List<Category>, selectedId: String,
    onHide: (String) -> Unit, onSelect: (String) -> Unit,
) {
    Column(Modifier.width(if (LocalCompact.current) 200.dp else 250.dp).fillMaxHeight()) {
        TitleWithIcon(title, icon, 38.sp)
        Text(subtitle, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 18.dp))
        val state = rememberLazyListState()
        Box {
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(end = 12.dp)) {
                items(categories) { cat ->
                    // Nur Anbieter-Kategorien lassen sich ausblenden, nicht „Favoriten“, „Alle“ usw. (IDs mit „__“).
                    NavRow(
                        cat.name, cat.id == selectedId, { onSelect(cat.id) }, icon = categoryIcon(cat.id), reserveIcon = true,
                        onHide = if (cat.id.startsWith("__")) null else ({ onHide(cat.id) }),
                    )
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Symbole nur für die festen Sammel-Kategorien; Anbieter-Kategorien bleiben reiner Text. */
private fun categoryIcon(id: String): ImageVector? = when (id) {
    AppState.LIVE_RECENT -> Icons.Rounded.History
    AppState.LIVE_FAV, AppState.BROWSE_FAV -> Icons.Rounded.StarBorder
    AppState.LIVE_ALL, AppState.BROWSE_ALL -> Icons.Rounded.GridView
    AppState.BROWSE_CONTINUE -> Icons.Rounded.PlayCircleOutline
    else -> null
}

// ---------- Filme und Serien ----------

@Composable
private fun BrowseScreen(app: AppState, section: Section) {
    val state = app.section(section)
    when {
        state.loading -> Loading("${section.title} werden geladen …")
        state.error != null -> ErrorView(state.error.orEmpty()) { app.reloadAll() }
        state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.", sectionIcon(section))
        else -> {
            val hasContinue = app.continueWatching(section).isNotEmpty()
            val cats = remember(state.categories, hasContinue) { app.browseCategories(section) }
            // Zuletzt gewählte Kategorie wiederherstellen, sofern es sie noch gibt.
            var selected by rememberSaveable(section) { mutableStateOf(app.initialBrowseCategory(section)) }
            LaunchedEffect(selected) { app.rememberBrowseCategory(section, selected) }
            var filter by rememberSaveable(section) { mutableStateOf("") }
            val items = remember(selected, state.items, app.favorites, app.history, filter) {
                val base = app.browseItems(section, selected)
                if (filter.isBlank()) base else base.filter { it.name.contains(filter.trim(), ignoreCase = true) }
            }
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, bottom = 24.dp)) {
                CategoryList(section.title, sectionIcon(section), "${state.items.size} Titel", cats, selected, onHide = { app.toggleHidden(section, it) }) { selected = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Oben ausgerichtet wie in Live TV: Die Überschrift steht auf derselben Höhe, egal wie hoch das Suchfeld ist.
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(top = 14.dp)) {
                            Headline(splitCategoryName(cats.firstOrNull { it.id == selected }?.name ?: "").name, 24.sp)
                            Text("${items.size} Titel", color = Muted)
                        }
                        OutlinedTextField(
                            filter, { filter = it }, singleLine = true,
                            placeholder = { Text("In dieser Liste suchen", color = Muted) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            shape = Corner,
                            colors = fieldColors(),
                            modifier = Modifier.width(300.dp).testTag("browse-filter"),
                        )
                    }
                    if (items.isEmpty()) {
                        EmptyView("Nichts gefunden", icon = Icons.Rounded.SearchOff, text = if (selected == AppState.BROWSE_FAV) "Klicke auf den Stern bei einem Titel, um ihn hier zu speichern." else "Hier gibt es keine passenden Titel.")
                    } else {
                        val gridState = rememberLazyGridState()
                        Box {
                            LazyVerticalGrid(
                                columns = cardColumns(wide = section == Section.SERIES && selected == AppState.BROWSE_CONTINUE),
                                state = gridState,
                                contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                val keys = stableKeys(items)
                                itemsIndexed(items, key = { i, _ -> keys[i] }) { i, item ->
                                    MediaCard(item, item.favKey in app.favorites, app.progressOf(item),
                                        { if (selected == AppState.BROWSE_CONTINUE) app.play(items, i) else app.open(items, i) }, { app.toggleFavorite(item) }, Modifier.animateItem(),
                                        info = app.mediaInfoFor(item), onDwell = { app.requestMediaInfo(item) },
                                        infoLoaded = app.mediaInfoLoaded(item))
                                }
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Favoriten, Suche, Einstellungen ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSection(title: String, list: List<Item>, app: AppState) {
    Column(Modifier.padding(bottom = 28.dp)) {
        Rule(Modifier.padding(horizontal = 40.dp))
        Headline(title, 21.sp, Modifier.padding(start = 40.dp, end = 40.dp, top = 14.dp, bottom = 10.dp))
        FlowRow(
            Modifier.padding(horizontal = 40.dp).testTag("search-grid:$title"),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            list.forEachIndexed { i, item ->
                MediaCard(item, item.favKey in app.favorites, app.progressOf(item), { app.rememberSearch(); app.open(list, i) }, { app.toggleFavorite(item) },
                    info = app.mediaInfoFor(item), onDwell = { app.requestMediaInfo(item) }, infoLoaded = app.mediaInfoLoaded(item))
            }
        }
    }
}

@Composable
private fun FavoritesScreen(app: AppState) {
    val rows = Section.entries.map { it to app.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Favoriten", "Klicke auf den Stern bei einem Sender, Film oder einer Serie, um sie hier zu sammeln.", Icons.Rounded.Favorite)
        if (rows.isEmpty()) {
            EmptyView("Noch keine Favoriten", "Deine Lieblingssender, Filme und Serien erscheinen hier.", Icons.Rounded.StarBorder)
        } else {
            val state = rememberLazyListState()
            Box {
                LazyColumn(state = state) {
                    items(rows, key = { it.first.name }) { (section, list) ->
                        ItemsRow(section.title, list, app.favorites, app::progressOf, app::open, app::toggleFavorite,
                            infoOf = app::mediaInfoFor, onDwell = app::requestMediaInfo, infoLoadedOf = app::mediaInfoLoaded)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** Laufende Version (aus dem Build) – leer beim Entwickeln ohne Paket. */
internal val appVersion: String get() = System.getProperty("iptvplayer.version").orEmpty()

/** Download-Seite im Browser öffnen (am PC installiert man das neue Paket selbst). */
internal fun openInBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** Updates am PC: nur Hinweis und Link – installieren übernimmt das Paket (Windows-Installer bzw. .deb). */
@Composable
private fun UpdateSettings(app: AppState) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var upToDate by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Version ${appVersion.ifEmpty { "(Entwicklung)" }}", color = TextColor)
        val update = app.availableUpdate
        if (update != null) {
            Text("Version ${update.version} ist verfügbar.", color = TextColor)
            PrimaryButton("Download-Seite öffnen", Icons.Rounded.SystemUpdate) { openInBrowser(update.pageUrl) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryButton(if (checking) "Wird geprüft …" else "Nach Updates suchen", Icons.Rounded.Refresh) {
                    if (checking || appVersion.isEmpty()) return@SecondaryButton
                    scope.launch {
                        checking = true
                        upToDate = app.checkForUpdate(appVersion, DesktopUpdates, force = true) == null
                        checking = false
                    }
                }
                if (upToDate) Text("Du hast die neueste Version.", color = Muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(app.updateCheck, { app.updateCheck = it })
            Text("Einmal am Tag automatisch nachsehen (bei GitHub)", color = Muted)
        }
    }
}

/** Startseite: Weiterschauen, zuletzt gesehene Sender, Favoriten – dort weitermachen, wo man war. */
@Composable
private fun StartScreen(app: AppState) {
    StartScreenContent(app)
}

/** Neu-Reihen zeigen höchstens so viele Einträge; „Alle anzeigen“ öffnet den Rest. */
private const val NEW_ROW_LIMIT = 30

/**
 * „Abo bis 14.03.2027 · 1 von 2 Verbindungen in Gebrauch“ – nur, wenn das Abo in höchstens 7 Tagen abläuft (dann rot).
 * Sonst steht das Abo ohnehin unten in der Seitenleiste, im selben Wortlaut.
 */
@Composable
private fun AccountLine(app: AppState) {
    val status = app.accountStatus ?: return
    if (!app.accountExpiresSoon()) return
    val info = status.info
    val cons = connectionsLabel(info)?.let { " · $it" }.orEmpty()
    val stale = if (app.accountStatusStale) " · Stand vom ${standLabel(status.fetchedAt)}" else ""
    Text(subscriptionLabel(info) + cons + stale, color = Danger, modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
}

/** Solange keine Kategorie gewählt ist: Hinweis statt der Neu-Reihen. */
@Composable
private fun ChooseCategoriesCard(app: AppState) {
    Row(
        Modifier.padding(start = 40.dp, end = 40.dp, bottom = 14.dp).panel().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wähle, was dich interessiert", fontWeight = FontWeight.SemiBold)
            Text("Dann zeigt die Startseite neue Filme und Folgen aus diesen Kategorien.", color = Muted, fontSize = 14.sp)
        }
        PrimaryButton("Kategorien wählen") { app.selectTab(Tab.SETTINGS) }
    }
}

@Composable
private fun StartScreenContent(app: AppState) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    // Reihen mit optionalem „Alle anzeigen“ (Neu-Reihen öffnen Filme/Serien mit „Neu (7 Tage)“).
    // Reihenfolge und Auswahl aus den Einstellungen („Startseite“); Downloads gibt es am PC nicht.
    val rows: List<Triple<String, List<Item>, (() -> Unit)?>> = app.homeRows.mapNotNull { row ->
        when (row) {
            HomeRow.CONTINUE -> Triple(row.title, app.continueWatchingAll.map { it.item }, null)
            HomeRow.NEW_MOVIES -> if (app.supportsDashboard) Triple(row.title, app.newMovies.take(NEW_ROW_LIMIT), { app.showNewInBrowse(Section.MOVIES) }) else null
            HomeRow.NEW_EPISODES -> if (app.supportsDashboard) Triple(row.title, app.newEpisodes.take(NEW_ROW_LIMIT), { app.showNewInBrowse(Section.SERIES) }) else null
            HomeRow.RECENT_CHANNELS -> Triple(row.title, app.recentChannels, null)
            HomeRow.OFFLINE -> null
            HomeRow.FAVORITE_LIVE -> Triple(row.title, app.favoriteItems(Section.LIVE), null)
            HomeRow.FAVORITE_MOVIES -> Triple(row.title, app.favoriteItems(Section.MOVIES), null)
            HomeRow.FAVORITE_SERIES -> Triple(row.title, app.favoriteItems(Section.SERIES), null)
        }
    }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            when (hour) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Guten Tag"; else -> "Guten Abend" },
            "Hier geht es dort weiter, wo du aufgehört hast.", Icons.Rounded.Home,
        )
        app.availableUpdate?.let { update ->
            Row(Modifier.padding(start = 40.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Version ${update.version} ist verfügbar.", color = TextColor)
                SecondaryButton("Download-Seite öffnen", Icons.Rounded.SystemUpdate) { openInBrowser(update.pageUrl) }
            }
        }
        // Einmal am Tag im Hintergrund nach Updates suchen.
        LaunchedEffect(Unit) { if (appVersion.isNotEmpty()) app.checkForUpdate(appVersion, DesktopUpdates) }
        if (app.supportsDashboard) {
            AccountLine(app)
            if (!app.dashboardConfigured) ChooseCategoriesCard(app)
        }
        if (rows.isEmpty()) {
            EmptyView("Willkommen", "Sobald du etwas ansiehst oder Favoriten speicherst, findest du es hier wieder.", Icons.Rounded.LiveTv)
        } else {
            val state = rememberLazyListState()
            Box {
                LazyColumn(state = state) {
                    items(rows, key = { it.first }) { (title, list, showAll) ->
                        // „Weiterschauen“ spielt direkt – die Entscheidung ist schon gefallen; sonst Film → Detailseite.
                        val onOpen: (List<Item>, Int) -> Unit = if (title == "Weiterschauen") { l, i -> app.play(l, i) } else app::open
                        ItemsRow(title, list, app.favorites, app::progressOf, onOpen, app::toggleFavorite, showAll,
                            infoOf = app::mediaInfoFor, onDwell = app::requestMediaInfo, infoLoadedOf = app::mediaInfoLoaded)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SearchScreen(app: AppState) {
    val results = app.searchResults
    val loading = Section.entries.any { app.section(it).loading }
    // Beim Öffnen gleich tippen können; Strg+F holt den Fokus zurück, auch wenn die Suche schon offen ist.
    val field = remember { FocusRequester() }
    val focusRequests = LocalKeyboard.current.searchFocusRequests
    LaunchedEffect(focusRequests) { runCatching { field.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Suche", icon = Icons.Rounded.Search)
        OutlinedTextField(
            app.query, { app.query = it }, singleLine = true,
            placeholder = { Text("Sender, Filme oder Serien suchen", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = Corner,
            colors = fieldColors(),
            modifier = Modifier.padding(horizontal = 40.dp).width(640.dp).focusRequester(field).testTag("search-field"),
        )
        Spacer(Modifier.height(16.dp))
        when {
            app.query.trim().length < 2 -> Column {
                RecentSearches(app)
                EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.", Icons.Rounded.Search)
            }
            results.isEmpty() && loading -> Loading("Listen werden geladen …")
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.", Icons.Rounded.SearchOff)
            else -> {
                // Treffer als Raster je Bereich: alles auf einen Blick, das Mausrad blättert senkrecht.
                val state = rememberLazyListState()
                Box {
                    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(results.entries.toList(), key = { it.key.name }) { (section, list) ->
                            SearchSection("${section.title} (${list.size})", list, app)
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

/** Abschnitte der Einstellungen in Lesereihenfolge – links als Inhaltsverzeichnis. */
internal enum class SettingsPart(val title: String) {
    ACCOUNT("Anbieter"), PLAYBACK("Wiedergabe"), HOME("Startseite"), FILTER("Listen & Filter"), APP("App"),
}

/** Abschnitt, in dem man gerade liest: der letzte, dessen Anfang schon (fast) oben angekommen ist. */
internal fun currentSettingsPart(tops: Map<SettingsPart, Int>, scrolled: Int, slack: Int = 48): SettingsPart =
    SettingsPart.entries.lastOrNull { p -> tops[p]?.let { it <= scrolled + slack } == true } ?: SettingsPart.ACCOUNT

/** Platz für Inhaltsverzeichnis plus Spalte; darunter (schmales Fenster) nur die Spalte. */
private val SettingsTocMinWidth = 980.dp

@Composable
private fun SettingsScreen(app: AppState) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val tops = remember { mutableStateMapOf<SettingsPart, Int>() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen", icon = Icons.Rounded.Settings)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val withToc = maxWidth >= SettingsTocMinWidth
            Row(Modifier.fillMaxSize()) {
                if (withToc) SettingsToc(currentSettingsPart(tops, scroll.value)) { part -> scope.launch { scroll.animateScrollTo(tops[part] ?: 0) } }
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = if (withToc) 8.dp else 40.dp, bottom = 32.dp)) {
                        SettingsPart.entries.forEach { part ->
                            SettingsPanel(part, Modifier.onGloballyPositioned { tops[part] = it.positionInParent().y.toInt() }) {
                                when (part) {
                                    SettingsPart.ACCOUNT -> AccountSettings(app)
                                    SettingsPart.PLAYBACK -> PlaybackSettings(app)
                                    SettingsPart.HOME -> { HomeRowSettings(app); DashboardSettings(app) }
                                    SettingsPart.FILTER -> FilterSettings(app)
                                    SettingsPart.APP -> AppSettings(app)
                                }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsToc(current: SettingsPart, onPick: (SettingsPart) -> Unit) {
    Column(Modifier.padding(start = 40.dp, end = 24.dp, top = 4.dp).width(160.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingsPart.entries.forEach { part ->
            val on = part == current
            Row(
                Modifier.fillMaxWidth().clip(Corner).handCursor().clickable { onPick(part) }.testTag("settings-toc:${part.name}")
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionMark(on)
                Text(
                    part.title, color = if (on) TextColor else Muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(part: SettingsPart, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.padding(top = 20.dp).width(680.dp).panel().padding(28.dp).testTag("settings:${part.name}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Kicker(part.title)
        content()
    }
}

@Composable
private fun AccountSettings(app: AppState) {
    var confirm by remember { mutableStateOf(false) }
    Headline(app.accountLabel, 22.sp)
    Text(
        "${app.section(Section.LIVE).items.size} Sender · ${app.section(Section.MOVIES).items.size} Filme · ${app.section(Section.SERIES).items.size} Serien",
        color = Muted,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Listen neu laden", Icons.Rounded.Refresh) { app.reloadAll() }
        SecondaryButton("Anbieter entfernen", Icons.Rounded.PowerSettingsNew, danger = true) { confirm = true }
    }
    // Weitere Anbieter: Favoriten und Verlauf bleiben je Anbieter getrennt.
    Rule(Modifier.padding(vertical = 6.dp))
    app.accounts.filter { it.id != app.activeAccountId }.forEach { saved ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(labelOf(saved.account), maxLines = 1, modifier = Modifier.weight(1f))
            SecondaryButton("Wechseln", Icons.Rounded.SwapHoriz) { app.switchAccount(saved.id) }
        }
    }
    SecondaryButton("Anbieter hinzufügen", Icons.Rounded.Add) { app.addAccount() }
    if (confirm) ConfirmDialog(
        title = "Anbieter entfernen?",
        text = "„${app.accountLabel}“ wird von diesem Gerät entfernt – mit Favoriten, Verlauf und gespeicherten Listen. " +
            "Das lässt sich nicht rückgängig machen.",
        confirmLabel = "Entfernen",
        onConfirm = { confirm = false; app.logout() },
        onDismiss = { confirm = false },
    )
}

/** Sicherheitsabfrage für Endgültiges: Abbrechen ist der sichere Ausweg (Esc, Klick daneben), Bestätigen ist rot. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, color = Muted) },
        confirmButton = { DangerButton(confirmLabel, onConfirm) },
        dismissButton = { SecondaryButton("Abbrechen", onClick = onDismiss) },
        shape = Corner,
        containerColor = Surface1,
        titleContentColor = TextColor,
        textContentColor = Muted,
    )
}

/** Wie lange „Rückgängig“ nach dem Löschen des Verlaufs angeboten wird. */
private const val UNDO_MS = 10_000L

@Composable
private fun PlaybackSettings(app: AppState) {
    SettingSwitch("Beim Start den letzten Sender abspielen", "Die App öffnet direkt den zuletzt gesehenen Live-Sender.", app.startWithLastChannel) { app.startWithLastChannel = it }
    SettingSwitch("Nächste Folge automatisch abspielen", "Mit 10 Sekunden Countdown, jederzeit abbrechbar.", app.autoNextEpisode) { app.autoNextEpisode = it }
    SettingSwitch("Verbindungsanzeige im Player", "Klein oben rechts; Klick oder Taste I öffnet die Statistik.", app.showConnectionIndicator) {
        app.showConnectionIndicator = it
    }
    Rule(Modifier.padding(vertical = 10.dp))
    // Löschen ohne Rückfrage, dafür eine Weile „Rückgängig“ – der alte Stand bleibt so lange im Speicher.
    var undo by remember { mutableStateOf<List<HistoryEntry>?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(UNDO_MS); undo = null } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Verlauf", fontWeight = FontWeight.Medium)
            Text(if (undo != null) "Verlauf gelöscht." else "Weiterschauen und zuletzt gesehene Sender.", color = Muted, fontSize = 13.sp)
        }
        val saved = undo
        if (saved != null) SecondaryButton("Rückgängig", Icons.AutoMirrored.Rounded.Undo) { app.restoreHistory(saved); undo = null }
        else SecondaryButton("Verlauf löschen", Icons.Rounded.DeleteOutline) { undo = app.history; app.clearHistory() }
    }
}

@Composable
private fun FilterSettings(app: AppState) {
    SettingSwitch("Listen alphabetisch sortieren", "Sonst gilt die Reihenfolge deines Anbieters.", app.sortAz) { app.sortAz = it }
    SettingSwitch("Filme und Serien: neueste zuerst", "Nach dem Jahr im Titel; ohne Jahr ans Ende.", app.sortByYear) { app.sortByYear = it }
    val hidden = Section.entries.flatMap { s -> app.hiddenCategories(s).map { s to it } }
    Rule(Modifier.padding(vertical = 10.dp))
    Text("Ausgeblendete Kategorien", fontWeight = FontWeight.SemiBold)
    if (hidden.isEmpty()) {
        Text("Keine. Zum Ausblenden in einer Kategorienliste auf das Augen-Symbol klicken oder rechts klicken.", color = Muted, fontSize = 14.sp)
    } else {
        hidden.forEach { (section, cat) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sectionIcon(section), null, tint = Muted, modifier = Modifier.size(18.dp))
                val tagged = splitCategoryName(cat.name)
                tagged.tag?.let { FlagOrTag(it, Modifier.padding(start = 10.dp)) }
                Text(tagged.name, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                SecondaryButton("Einblenden", Icons.Rounded.Visibility) { app.toggleHidden(section, cat.id) }
            }
        }
    }
    Section.entries.forEach { CountrySettings(app, it) }
}

@Composable
private fun AppSettings(app: AppState) {
    UpdateSettings(app)
    Rule(Modifier.padding(vertical = 10.dp))
    Text("Tastenkürzel", fontWeight = FontWeight.SemiBold)
    ShortcutList()
    Text(
        "Wiedergabe mit VLC (libVLC, LGPL) · Diese App enthält keine Inhalte.",
        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
    )
}

/** Tastenkürzel nach Ort gruppiert (überall, Listen, Player) – Daten in `shortcutHelp`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutList() {
    shortcutHelp.forEach { group ->
        Text(group.title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            group.keys.forEach { (key, action) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyCap(key)
                    Text(action, color = Muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Kategorien für die Neu-Reihen der Startseite (nur Xtream; ohne Erwachsenen-Kategorien). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSettings(app: AppState) {
    if (!app.supportsDashboard) return
    Rule(Modifier.padding(vertical = 10.dp))
    Text("Neues aus diesen Kategorien", fontWeight = FontWeight.SemiBold)
    Text("Neue Filme und Folgen der letzten 7 Tage erscheinen auf der Startseite. Neue Kategorien deines Anbieters sind nicht automatisch dabei.", color = Muted, fontSize = 14.sp)
    listOf(Section.MOVIES, Section.SERIES).forEach { s ->
        val options = app.dashboardCategoryOptions(s)
        if (options.isEmpty()) return@forEach
        Text(s.title, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        // Anbieter haben oft Hunderte Kategorien: zugeklappt 8 (gewählte zuerst), „Alle anzeigen“ klappt auf.
        var expanded by remember(s) { mutableStateOf(false) }
        val chosenIds = app.dashboardCategories.filter { it.startsWith("${s.name}:") }.map { it.substringAfter(':') }.toSet()
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dashboardOptionsShown(options, chosenIds, expanded).forEach { c ->
                val on = "${s.name}:${c.id}" in app.dashboardCategories
                Row(
                    Modifier.clip(Corner).background(if (on) Signal else Surface2).handCursor().clickable { app.toggleDashboardCategory(s, c.id) }
                        .testTag("dashcat:${s.name}:${c.id}").padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(c.name, fontSize = 13.sp, color = if (on) OnSignal else TextColor)
                }
            }
            if (dashboardNeedsToggle(options)) Row(
                Modifier.clip(Corner).border(1.dp, Line, Corner).handCursor().clickable { expanded = !expanded }
                    .testTag("dashmore:${s.name}").padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(if (expanded) "Weniger anzeigen" else "Alle anzeigen (${options.size})", fontSize = 13.sp, color = Signal)
            }
        }
    }
}

/** Länderfilter je Bereich: Kürzel des Anbieters zum An- und Abwählen. Ohne Kürzel (z. B. M3U) kein Abschnitt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountrySettings(app: AppState, s: Section) {
    val options = app.availableCountries(s)
    val chosen = app.countries(s)
    if (options.isEmpty()) return
    Rule(Modifier.padding(vertical = 10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Länder in ${s.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (chosen.isNotEmpty()) SecondaryButton("Alle zeigen") { app.clearCountries(s) }
    }
    Text("Nur Kategorien dieser Länder werden angezeigt. Kategorien ohne Kürzel bleiben sichtbar.", color = Muted, fontSize = 14.sp)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { CountryChip(it, it.tag in chosen, s) { app.toggleCountry(s, it.tag) } }
    }
}

@Composable
private fun CountryChip(option: CountryOption, selected: Boolean, s: Section, onClick: () -> Unit) {
    Row(
        Modifier.clip(Corner).background(if (selected) Signal else Surface2).handCursor().clickable(onClick = onClick)
            .testTag(if (s == Section.LIVE) "country:${option.tag}" else "country:${s.name}:${option.tag}").padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flagOf(option.tag) != null) FlagOrTag(option.tag, Modifier.padding(end = 6.dp))
        Text(option.tag, fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = if (selected) OnSignal else TextColor)
        Text(" ${option.count}", fontSize = 12.sp, color = if (selected) OnSignal else Muted)
    }
}

/** Startseite: Reihen ein-/ausblenden und mit den Pfeilen verschieben. */
@Composable
private fun HomeRowSettings(app: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Reihen", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SecondaryButton("Zurücksetzen") { app.resetHomeRows() }
    }
    Text("Leere Reihen erscheinen nicht.", color = Muted, fontSize = 14.sp)
    val rows = app.homeRowOrder.filterNot { it == HomeRow.OFFLINE }
    rows.forEachIndexed { i, row ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(row !in app.hiddenHomeRows, { app.toggleHomeRow(row) }, colors = CheckboxDefaults.colors(checkedColor = Signal, checkmarkColor = OnSignal))
            Text(row.title, Modifier.weight(1f))
            IconButton({ app.moveHomeRow(row, up = true) }, Modifier.handCursor(), enabled = i > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Nach oben", tint = Muted)
            }
            IconButton({ app.moveHomeRow(row, up = false) }, Modifier.handCursor(), enabled = i < rows.lastIndex) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Nach unten", tint = Muted)
            }
        }
    }
}

/** Ein-/Aus-Einstellung: ganze Zeile klickbar, Kästchen statt Kapsel-Schalter (Style-Lock: keine Kapseln). */
@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(Corner).toggleable(checked, role = Role.Checkbox, onValueChange = onChange).handCursor()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, color = Muted, fontSize = 13.sp)
        }
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = Signal, checkmarkColor = OnSignal, uncheckedColor = Muted))
    }
}

/** Welche Release-Datei für diese PC-App zählt (.msi unter Windows, sonst .deb). */
private val DesktopUpdates = UpdatePlatform.desktop(System.getProperty("os.name").orEmpty())

/** Zuletzt gesuchte Begriffe als Knöpfe – ein Klick sucht wieder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(app: AppState) {
    if (app.recentSearches.isEmpty()) return
    Column(Modifier.padding(horizontal = 40.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Zuletzt gesucht")
            Spacer(Modifier.width(12.dp))
            Text(
                "Löschen", color = Muted, fontSize = 13.sp,
                modifier = Modifier.clip(Corner).handCursor().clickable { app.clearRecentSearches() }.padding(4.dp),
            )
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            app.recentSearches.forEach { term ->
                Text(
                    term, color = TextColor, fontSize = 14.sp,
                    modifier = Modifier.clip(Corner).background(Surface2).handCursor().clickable { app.query = term }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
