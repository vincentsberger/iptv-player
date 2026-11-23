package de.vincentsberger.iptvplayer.desktop.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import androidx.compose.ui.platform.testTag
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
    // Strg + 1 … 7: Bereiche der Seitenleiste per Tastatur (Downloads gibt es am PC nicht).
    val tabs = Tab.entries.filter { it != Tab.DOWNLOADS }
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown || !ev.isCtrlPressed) return@onPreviewKeyEvent false
        val n = digitOf(ev.key) ?: return@onPreviewKeyEvent false
        tabs.getOrNull(n - 1)?.let { app.selectTab(it); true } ?: false
    }) {
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

@Composable
private fun Sidebar(app: AppState) {
    Row(Modifier.fillMaxHeight()) {
        Column(Modifier.width(if (LocalCompact.current) 172.dp else 220.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 26.dp)) {
            Masthead(21.sp, Modifier.padding(start = 12.dp))
            Clock(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 28.dp))
            // Downloads gibt es nur in der TV-App (am PC streamt man ohnehin).
            Tab.entries.filter { it != Tab.DOWNLOADS }.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                NavRow(t.title, t == app.tab, { app.selectTab(t) }, icon = t.icon())
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
    }
}

/** Eintrag in Seitenleiste und Kategorielisten: Signal-Strich links, Fläche nur als Zusatz. */
@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, reserveIcon: Boolean = false, onHide: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected || hovered) Surface1 else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        val color = if (selected || hovered) TextColor else Muted
        if (icon != null) Icon(icon, null, tint = if (selected) Signal else color, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        else if (reserveIcon) Spacer(Modifier.width(32.dp))
        val tagged = splitProviderTag(label)
        tagged.tag?.let { TagChip(it, Modifier.padding(start = 12.dp)) }
        Text(
            // Anbieter-Kategorien sind oft lang („DE | SPORT ▸ FUSSBALL & LIVE-EVENTS“) → bis zu zwei Zeilen.
            tagged.name, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        )
        if (onHide != null && hovered) {
            IconButton(onHide, Modifier.size(32.dp)) { Icon(Icons.Rounded.VisibilityOff, "Kategorie ausblenden", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

// ---------- Live TV ----------

/** „1.223 Sender · Gefiltert: AT, DE“ – erklärt eine kurze Liste bei aktivem Länderfilter. */
private fun liveSubtitle(app: AppState, channels: Int): String =
    "$channels Sender" + app.activeLiveCountries.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", ", " · Gefiltert: ").orEmpty()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveScreen(app: AppState) {
    val state = app.section(Section.LIVE)
    when {
        state.loading -> Loading("Sender werden geladen …")
        state.error != null -> ErrorView(state.error.orEmpty()) { app.reloadAll() }
        else -> {
            val channels = app.liveChannels
            var selected by rememberSaveable { mutableStateOf<Item?>(null) }
            /** Tastatur: 0 = Kategorien, 1 = Sender. */
            var column by remember { mutableStateOf(1) }
            val keyFocus = remember { FocusRequester() }
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
            var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
            LaunchedEffect(selected) {
                epg = emptyList()
                val s = selected ?: return@LaunchedEffect
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
                scope.launch { listState.animateScrollToItem((i - 3).coerceAtLeast(0)) }
            }
            fun moveCategory(delta: Int) {
                val i = (cats.indexOfFirst { it.id == app.liveCategory } + delta).coerceIn(0, cats.lastIndex)
                app.liveCategory = cats[i].id
                selected = null
            }
            fun play(item: Item?) {
                val i = item?.let(channels::indexOf)?.takeIf { it >= 0 } ?: return
                app.open(channels, i)
            }
            // Tastatur: ↑↓ blättern (Bild ↑↓ zehn auf einmal), ← → Kategorien/Sender, Enter abspielen, F Favorit.
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 24.dp)
                .focusRequester(keyFocus).focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown || ev.isCtrlPressed) return@onPreviewKeyEvent false
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
                    Headline(splitProviderTag(cats.firstOrNull { it.id == app.liveCategory }?.name ?: "").name, 24.sp, Modifier.padding(top = 14.dp))
                    Text(
                        "${channels.size} Sender · Klick oder Enter zum Ansehen · ↑↓ blättern · ← → Kategorien",
                        color = if (column == 1) Muted else Muted.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
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
                                        channelNumberOf(ch, channels), ch, ch == selected, ch.favKey in app.favorites, app.nowFor(ch),
                                        onSelect = { selected = ch },
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
                LiveInfo(selected, epg, selected?.let { it.favKey in app.favorites } == true,
                    onPlay = { selected?.let { s -> app.open(channels, channels.indexOf(s).coerceAtLeast(0)) } },
                    onToggleFavorite = { selected?.let(app::toggleFavorite) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    number: Int, item: Item, selected: Boolean, favorite: Boolean, now: EpgEntry?,
    onSelect: () -> Unit, onPlay: () -> Unit, onToggleFavorite: () -> Unit,
    /** Senderspalte hat die Tastatur – sonst Auswahl gedämpft zeigen. */
    active: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Maus darüber: Infospalte zeigt schon das Programm dieses Senders.
    LaunchedEffect(hovered) { if (hovered) onSelect() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(when { selected && active -> Surface2; selected || hovered -> Surface1; else -> Color.Transparent })
            .hoverable(interaction)
            // Ein Klick spielt ab – kein Doppelklick und kein kleiner Play-Knopf nötig.
            .clickable(interactionSource = interaction, indication = null) { onSelect(); onPlay() }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Text(number.toString().padStart(2, '0'), color = if (selected) TextColor else Muted, fontSize = 14.sp, fontFamily = Mono, maxLines = 1, modifier = Modifier.padding(start = 14.dp).width(52.dp))
        Box(Modifier.width(60.dp).height(36.dp), Alignment.Center) { ChannelLogo(item, 34.dp) }
        val tagged = splitProviderTag(item.name)
        tagged.tag?.let { TagChip(it, Modifier.padding(start = 12.dp)) }
        Column(Modifier.weight(1f).padding(start = if (tagged.tag != null) 10.dp else 14.dp)) {
            Text(tagged.name, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // „Jetzt läuft“: Sendung plus kleiner Fortschrittsbalken
            if (now != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(now.title, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                ProgressBar(epgProgress(now), Modifier.padding(start = 10.dp).width(44.dp), height = 2.dp)
            }
        }
        if (hovered || selected) {
            IconButton(onPlay) { Icon(Icons.Rounded.PlayArrow, "Ansehen", tint = TextColor) }
        }
        if (favorite || hovered) {
            IconButton(onToggleFavorite) { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit", tint = if (favorite) Signal else Muted) }
        }
    }
}

@Composable
private fun LiveInfo(item: Item?, epg: List<EpgEntry>, favorite: Boolean, onPlay: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(Modifier.width(if (LocalCompact.current) 290.dp else 360.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxHeight().panel().padding(24.dp)) {
            // Inhalt blendet beim Senderwechsel (und wenn das Programm eintrifft) weich über.
            AnimatedContent(
                item to epg, Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "info",
            ) { (shown, shownEpg) -> LiveInfoContent(shown, shownEpg) }
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
private fun LiveInfoContent(item: Item?, epg: List<EpgEntry>) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Bg.copy(alpha = 0.6f), Corner), Alignment.Center) {
            if (item != null) ChannelLogo(item, 96.dp) else Masthead(18.sp)
        }
        Spacer(Modifier.height(22.dp))
        if (item == null) {
            Headline("Wähle einen Sender", 24.sp, maxLines = 2)
            Text("Ein Klick zeigt das Programm, ein Doppelklick startet den Sender.", color = Muted, modifier = Modifier.padding(top = 8.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
            LiveDot()
            Kicker("Jetzt live", Signal, Modifier.padding(start = 8.dp))
        }
        Headline(now.title, 21.sp, maxLines = 2)
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
    AppState.LIVE_FAV, CAT_FAV -> Icons.Rounded.StarBorder
    AppState.LIVE_ALL, CAT_ALL -> Icons.Rounded.GridView
    CAT_CONTINUE -> Icons.Rounded.PlayCircleOutline
    else -> null
}

// ---------- Filme und Serien ----------

private const val CAT_CONTINUE = "__continue"
private const val CAT_FAV = "__fav"
private const val CAT_ALL = "__all"

@Composable
private fun BrowseScreen(app: AppState, section: Section) {
    val state = app.section(section)
    when {
        state.loading -> Loading("${section.title} werden geladen …")
        state.error != null -> ErrorView(state.error.orEmpty()) { app.reloadAll() }
        state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.", sectionIcon(section))
        else -> {
            val continueList = app.continueWatching(section)
            val cats = remember(state.categories, continueList.isNotEmpty()) {
                buildList {
                    if (continueList.isNotEmpty()) add(Category(CAT_CONTINUE, "Weiterschauen"))
                    if (app.newItemsOf(section).isNotEmpty()) add(Category(AppState.BROWSE_NEW, "Neu (7 Tage)"))
                    add(Category(CAT_FAV, "Favoriten"))
                    add(Category(CAT_ALL, "Alle ${section.title}"))
                    addAll(state.categories)
                }
            }
            // Zuletzt gewählte Kategorie wiederherstellen, sofern es sie noch gibt.
            var selected by rememberSaveable(section) {
                mutableStateOf(
                    app.browseCategory(section)?.takeIf { k -> cats.any { it.id == k } }
                        ?: if (continueList.isNotEmpty()) CAT_CONTINUE else state.categories.firstOrNull()?.id ?: CAT_ALL,
                )
            }
            LaunchedEffect(selected) { app.rememberBrowseCategory(section, selected) }
            var filter by rememberSaveable(section) { mutableStateOf("") }
            val items = remember(selected, state.items, app.favorites, app.history, filter) {
                val base = when (selected) {
                    CAT_CONTINUE -> continueList.map { it.item }
                    CAT_FAV -> app.favoriteItems(section)
                    AppState.BROWSE_NEW -> app.newItemsOf(section)
                    CAT_ALL -> state.items
                    else -> state.byCategory[selected].orEmpty()
                }
                if (filter.isBlank()) base else base.filter { it.name.contains(filter.trim(), ignoreCase = true) }
            }
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, bottom = 24.dp)) {
                CategoryList(section.title, sectionIcon(section), "${state.items.size} Titel", cats, selected, onHide = { app.toggleHidden(section, it) }) { selected = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Headline(splitProviderTag(cats.firstOrNull { it.id == selected }?.name ?: "").name, 24.sp)
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
                        EmptyView("Nichts gefunden", icon = Icons.Rounded.SearchOff, text = if (selected == CAT_FAV) "Klicke auf den Stern bei einem Titel, um ihn hier zu speichern." else "Hier gibt es keine passenden Titel.")
                    } else {
                        val gridState = rememberLazyGridState()
                        Box {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(if (section == Section.SERIES && selected == CAT_CONTINUE) 230.dp else 170.dp),
                                state = gridState,
                                contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                val keys = stableKeys(items)
                                itemsIndexed(items, key = { i, _ -> keys[i] }) { i, item ->
                                    MediaCard(item, item.favKey in app.favorites, app.progressOf(item),
                                        { if (selected == CAT_CONTINUE) app.play(items, i) else app.open(items, i) }, { app.toggleFavorite(item) }, Modifier.animateItem())
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
                        ItemsRow(section.title, list, app.favorites, app::progressOf, app::open, app::toggleFavorite)
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
    Column(
        Modifier.padding(start = 40.dp, top = 20.dp).width(680.dp).panel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Kicker("App")
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
                        upToDate = app.checkForUpdate(appVersion, force = true) == null
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

/** „Abo: läuft bis 14.03.2027 · 1 von 2 Verbindungen“ – rot, wenn das Abo in höchstens 7 Tagen abläuft. */
@Composable
private fun AccountLine(app: AppState) {
    val status = app.accountStatus ?: return
    val info = status.info
    val until = info.expiresAt?.let { "läuft bis " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: "unbegrenzt"
    val cons = if (info.maxConnections != null) " · ${info.activeConnections ?: 0} von ${info.maxConnections} Verbindungen" else ""
    val stale = if (app.accountStatusStale) " · Stand vom ${standLabel(status.fetchedAt)}" else ""
    Text("Abo: $until$cons$stale", color = if (app.accountExpiresSoon()) Danger else Muted, modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
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
    val rows: List<Triple<String, List<Item>, (() -> Unit)?>> = buildList {
        add(Triple("Weiterschauen", app.continueWatchingAll.map { it.item }, null))
        if (app.supportsDashboard) {
            add(Triple("Neue Filme", app.newMovies.take(NEW_ROW_LIMIT), { app.showNewInBrowse(Section.MOVIES) }))
            add(Triple("Neue Folgen", app.newEpisodes.take(NEW_ROW_LIMIT), { app.showNewInBrowse(Section.SERIES) }))
        }
        add(Triple("Zuletzt gesehene Sender", app.recentChannels, null))
        Section.entries.forEach { s -> add(Triple("Favoriten · ${s.title}", app.favoriteItems(s), null)) }
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
        LaunchedEffect(Unit) { if (appVersion.isNotEmpty()) app.checkForUpdate(appVersion) }
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
                        ItemsRow(title, list, app.favorites, app::progressOf, onOpen, app::toggleFavorite, showAll)
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
    Column(Modifier.fillMaxSize()) {
        PageHeader("Suche", icon = Icons.Rounded.Search)
        OutlinedTextField(
            app.query, { app.query = it }, singleLine = true,
            placeholder = { Text("Sender, Filme oder Serien suchen", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = Corner,
            colors = fieldColors(),
            modifier = Modifier.padding(horizontal = 40.dp).width(640.dp).testTag("search-field"),
        )
        Spacer(Modifier.height(16.dp))
        when {
            app.query.trim().length < 2 -> EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.", Icons.Rounded.Search)
            results.isEmpty() && loading -> Loading("Listen werden geladen …")
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.", Icons.Rounded.SearchOff)
            else -> {
                val state = rememberLazyListState()
                Box {
                    LazyColumn(state = state) {
                        items(results.entries.toList(), key = { it.key.name }) { (section, list) ->
                            ItemsRow("${section.title} (${list.size})", list, app.favorites, app::progressOf, app::open, app::toggleFavorite)
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(app: AppState) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Einstellungen", icon = Icons.Rounded.Settings)
        Column(
            Modifier.padding(horizontal = 40.dp).width(680.dp).panel().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Anbieter")
            Headline(app.accountLabel, 22.sp)
            Text(
                "${app.section(Section.LIVE).items.size} Sender · ${app.section(Section.MOVIES).items.size} Filme · ${app.section(Section.SERIES).items.size} Serien",
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Listen neu laden", Icons.Rounded.Refresh) { app.reloadAll() }
                SecondaryButton("Verlauf löschen", Icons.Rounded.DeleteOutline) { app.clearHistory() }
                if (!confirm) SecondaryButton("Anbieter entfernen", Icons.Rounded.PowerSettingsNew, danger = true) { confirm = true }
                else PrimaryButton("Wirklich entfernen?", Icons.Rounded.PowerSettingsNew) { app.logout() }
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
        }
        ComfortSettings(app)
        UpdateSettings(app)
        Column(Modifier.padding(start = 40.dp, top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Kicker("Tastenkürzel im Player")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp).width(680.dp)) {
                listOf(
                    "Strg 1–7" to "Bereich wechseln", "Enter" to "Sender ansehen", "F" to "Favorit",
                    "Leertaste" to "Pause", "← →" to "spulen", "↑ ↓" to "Sender/Folge", "0–9" to "Sender wählen", "L" to "letzter Sender",
                    "S" to "Schlaf-Timer", "F" to "Vollbild", "M" to "Ton aus", "Esc" to "zurück",
                ).forEach { (key, action) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyCap(key)
                        Text(action, color = Muted, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("IPTV Player ${System.getProperty("iptvplayer.version") ?: "(Entwicklung)"} · Wiedergabe mit VLC (libVLC, LGPL) · Diese App enthält keine Inhalte.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** Kategorien für die Neu-Reihen der Startseite (nur Xtream; ohne Erwachsenen-Kategorien). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSettings(app: AppState) {
    if (!app.supportsDashboard) return
    Rule(Modifier.padding(vertical = 10.dp))
    Text("Startseite: Neues aus diesen Kategorien", fontWeight = FontWeight.SemiBold)
    Text("Neue Filme und Folgen der letzten 7 Tage erscheinen auf der Startseite. Neue Kategorien deines Anbieters sind nicht automatisch dabei.", color = Muted, fontSize = 14.sp)
    listOf(Section.MOVIES, Section.SERIES).forEach { s ->
        val options = app.dashboardCategoryOptions(s)
        if (options.isEmpty()) return@forEach
        Text(s.title, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { c ->
                val on = "${s.name}:${c.id}" in app.dashboardCategories
                Row(
                    Modifier.clip(Corner).background(if (on) Signal else Surface2).clickable { app.toggleDashboardCategory(s, c.id) }
                        .testTag("dashcat:${s.name}:${c.id}").padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(c.name, fontSize = 13.sp, color = if (on) OnSignal else TextColor)
                }
            }
        }
    }
}

/** Länderfilter für Live-TV: Kürzel des Anbieters zum An- und Abwählen. Ohne Kürzel (z. B. M3U) kein Abschnitt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountrySettings(app: AppState) {
    val options = app.availableLiveCountries()
    if (options.isEmpty()) return
    Rule(Modifier.padding(vertical = 10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Länder in Live-TV", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (app.liveCountries.isNotEmpty()) SecondaryButton("Alle zeigen") { app.clearLiveCountries() }
    }
    Text("Nur Kategorien dieser Länder werden angezeigt. Kategorien ohne Kürzel bleiben sichtbar.", color = Muted, fontSize = 14.sp)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { CountryChip(it, it.tag in app.liveCountries) { app.toggleLiveCountry(it.tag) } }
    }
}

@Composable
private fun CountryChip(option: CountryOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(Corner).background(if (selected) Signal else Surface2).clickable(onClick = onClick)
            .testTag("country:${option.tag}").padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.tag, fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = if (selected) OnSignal else TextColor)
        Text(" ${option.count}", fontSize = 12.sp, color = if (selected) OnSignal else Muted)
    }
}

/** Komfort-Einstellungen: Sortierung, Start mit letztem Sender, nächste Folge, ausgeblendete Kategorien. */
@Composable
private fun ComfortSettings(app: AppState) {
    Column(
        Modifier.padding(start = 40.dp, end = 40.dp, top = 20.dp).width(680.dp).panel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Kicker("Komfort")
        SettingSwitch("Listen alphabetisch sortieren", "Sonst gilt die Reihenfolge deines Anbieters.", app.sortAz) { app.sortAz = it }
        SettingSwitch("Beim Start den letzten Sender abspielen", "Die App öffnet direkt den zuletzt gesehenen Live-Sender.", app.startWithLastChannel) { app.startWithLastChannel = it }
        SettingSwitch("Nächste Folge automatisch abspielen", "Mit 10 Sekunden Countdown, jederzeit abbrechbar.", app.autoNextEpisode) { app.autoNextEpisode = it }
        val hidden = Section.entries.flatMap { s -> app.hiddenCategories(s).map { s to it } }
        Rule(Modifier.padding(vertical = 10.dp))
        Text("Ausgeblendete Kategorien", fontWeight = FontWeight.SemiBold)
        if (hidden.isEmpty()) {
            Text("Keine. Zum Ausblenden in einer Kategorienliste auf das Augen-Symbol klicken.", color = Muted, fontSize = 14.sp)
        } else {
            hidden.forEach { (section, cat) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sectionIcon(section), null, tint = Muted, modifier = Modifier.size(18.dp))
                    val tagged = splitProviderTag(cat.name)
                    tagged.tag?.let { TagChip(it, Modifier.padding(start = 10.dp)) }
                    Text(tagged.name, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SecondaryButton("Einblenden", Icons.Rounded.Visibility) { app.toggleHidden(section, cat.id) }
                }
            }
        }
        CountrySettings(app)
        DashboardSettings(app)
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, color = Muted, fontSize = 13.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = OnSignal, checkedTrackColor = Signal, uncheckedThumbColor = Muted, uncheckedTrackColor = Surface2, uncheckedBorderColor = Line),
        )
    }
}
