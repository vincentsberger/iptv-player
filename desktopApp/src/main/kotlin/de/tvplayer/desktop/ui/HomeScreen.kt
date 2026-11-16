package de.tvplayer.desktop.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Tab
import de.tvplayer.shared.data.Category
import de.tvplayer.shared.data.EpgEntry
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
import kotlinx.coroutines.delay
import java.util.Calendar

private fun Tab.icon(): ImageVector = when (this) {
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.LIVE -> Icons.Rounded.LiveTv
    Tab.MOVIES -> Icons.Rounded.Movie
    Tab.SERIES -> Icons.Rounded.VideoLibrary
    Tab.FAVORITES -> Icons.Rounded.Favorite
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun HomeScreen(app: AppState) {
    Row(Modifier.fillMaxSize().background(Bg)) {
        Sidebar(app)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            key(app.tab) {
                when (app.tab) {
                    Tab.LIVE -> LiveScreen(app)
                    Tab.MOVIES -> BrowseScreen(app, Section.MOVIES)
                    Tab.SERIES -> BrowseScreen(app, Section.SERIES)
                    Tab.FAVORITES -> FavoritesScreen(app)
                    Tab.SEARCH -> SearchScreen(app)
                    Tab.SETTINGS -> SettingsScreen(app)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    Row(Modifier.fillMaxHeight()) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 26.dp)) {
            Masthead(21.sp, Modifier.padding(start = 12.dp))
            Clock(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 28.dp))
            Tab.entries.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                NavRow(t.title, t == app.tab, { app.selectTab(t) }, icon = t.icon())
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
    }
}

/** Eintrag in Seitenleiste und Kategorielisten: Signal-Strich links, Fläche nur als Zusatz. */
@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, reserveIcon: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (selected || hovered) Surface1 else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        val color = if (selected || hovered) TextColor else Muted
        if (icon != null) Icon(icon, null, tint = if (selected) Signal else color, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        else if (reserveIcon) Spacer(Modifier.width(32.dp))
        Text(
            label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp),
        )
    }
}

// ---------- Live TV ----------

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
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 24.dp)) {
                CategoryList("Live TV", Icons.Rounded.LiveTv, "${state.items.size} Sender", cats, app.liveCategory) { app.liveCategory = it }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Headline(cats.firstOrNull { it.id == app.liveCategory }?.name ?: "", 24.sp, Modifier.padding(top = 14.dp))
                    Text("${channels.size} Sender · Doppelklick zum Ansehen", color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                    Rule(Modifier.padding(end = 14.dp))
                    if (channels.isEmpty()) {
                        EmptyView("Keine Sender", icon = Icons.Rounded.LiveTv, text = if (app.liveCategory == AppState.LIVE_FAV) "Klicke auf den Stern bei einem Sender, um ihn hier zu speichern." else "In dieser Kategorie gibt es keine Sender.")
                    } else {
                        val listState = rememberLazyListState()
                        Box {
                            LazyColumn(state = listState, contentPadding = PaddingValues(end = 14.dp)) {
                                itemsIndexed(channels) { i, ch ->
                                    ChannelRow(
                                        i + 1, ch, ch == selected, ch.favKey in app.favorites,
                                        onSelect = { selected = ch },
                                        onPlay = { app.open(channels, i) },
                                        onToggleFavorite = { app.toggleFavorite(ch) },
                                    )
                                    Rule()
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
    number: Int, item: Item, selected: Boolean, favorite: Boolean,
    onSelect: () -> Unit, onPlay: () -> Unit, onToggleFavorite: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(when { selected -> Surface2; hovered -> Surface1; else -> Color.Transparent })
            .hoverable(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onSelect, onDoubleClick = onPlay)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Text(number.toString().padStart(2, '0'), color = if (selected) TextColor else Muted, fontSize = 14.sp, fontFamily = Mono, modifier = Modifier.padding(start = 14.dp).width(40.dp))
        Box(Modifier.width(52.dp).height(36.dp), Alignment.Center) { ChannelLogo(item, 32.dp) }
        Text(item.name, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
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
    Row(Modifier.width(360.dp).fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
        Column(Modifier.fillMaxHeight().padding(start = 28.dp, top = 14.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Surface1, Corner), Alignment.Center) {
                if (item != null) ChannelLogo(item, 96.dp) else Masthead(18.sp)
            }
            Spacer(Modifier.height(22.dp))
            if (item == null) {
                Headline("Wähle einen Sender", 24.sp, maxLines = 2)
                Text("Ein Klick zeigt das Programm, ein Doppelklick startet den Sender.", color = Muted, modifier = Modifier.padding(top = 8.dp))
            } else {
                Headline(item.name, 30.sp, maxLines = 2)
                Spacer(Modifier.height(18.dp))
                Rule()
                val now = epg.getOrNull(0)
                if (now != null) {
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
                } else {
                    Text("Keine Programminformationen", color = Muted, modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("Ansehen", Icons.Rounded.PlayArrow, onClick = onPlay)
                    SecondaryButton(if (favorite) "Favorit" else "Merken", if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = onToggleFavorite)
                }
            }
        }
    }
}

/** Minute des Tages (0–1439) in der lokalen Zeitzone. */
internal fun currentMinuteOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/** Anteil einer Sendung, der schon gelaufen ist; [nowMinute] ist für Tests austauschbar. */
internal fun epgProgress(e: EpgEntry, nowMinute: Int = currentMinuteOfDay()): Float {
    fun minutes(s: String): Int? {
        val p = s.split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
    val start = minutes(e.start) ?: return 0f
    var end = minutes(e.end) ?: return 0f
    var now = nowMinute
    // Nur Sendungen über Mitternacht (z. B. 23:30–00:30) verschieben Ende und „jetzt“ in den nächsten Tag.
    // Sonst bedeutet „jetzt vor Beginn“ schlicht: noch nicht angefangen (0 %).
    if (end <= start) {
        end += 1440
        if (now < start) now += 1440
    }
    return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

@Composable
private fun CategoryList(title: String, icon: ImageVector, subtitle: String, categories: List<Category>, selectedId: String, onSelect: (String) -> Unit) {
    Column(Modifier.width(250.dp).fillMaxHeight()) {
        TitleWithIcon(title, icon, 38.sp)
        Text(subtitle, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 18.dp))
        val state = rememberLazyListState()
        Box {
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(end = 12.dp)) {
                items(categories) { cat -> NavRow(cat.name, cat.id == selectedId, { onSelect(cat.id) }, icon = categoryIcon(cat.id), reserveIcon = true) }
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
                    add(Category(CAT_FAV, "Favoriten"))
                    add(Category(CAT_ALL, "Alle ${section.title}"))
                    addAll(state.categories)
                }
            }
            var selected by rememberSaveable(section) { mutableStateOf(if (continueList.isNotEmpty()) CAT_CONTINUE else state.categories.firstOrNull()?.id ?: CAT_ALL) }
            var filter by rememberSaveable(section) { mutableStateOf("") }
            val items = remember(selected, state.items, app.favorites, app.history, filter) {
                val base = when (selected) {
                    CAT_CONTINUE -> continueList.map { it.item }
                    CAT_FAV -> app.favoriteItems(section)
                    CAT_ALL -> state.items
                    else -> state.byCategory[selected].orEmpty()
                }
                if (filter.isBlank()) base else base.filter { it.name.contains(filter.trim(), ignoreCase = true) }
            }
            Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, bottom = 24.dp)) {
                CategoryList(section.title, sectionIcon(section), "${state.items.size} Titel", cats, selected) { selected = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Headline(cats.firstOrNull { it.id == selected }?.name ?: "", 24.sp)
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
                                itemsIndexed(items) { i, item ->
                                    MediaCard(item, item.favKey in app.favorites, app.progressOf(item), { app.open(items, i) }, { app.toggleFavorite(item) })
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
    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen", icon = Icons.Rounded.Settings)
        Column(
            Modifier.padding(horizontal = 40.dp).width(680.dp).background(Surface1, Corner).border(1.dp, Line, Corner).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Konto")
            Headline(app.accountLabel, 22.sp)
            Text(
                "${app.section(Section.LIVE).items.size} Sender · ${app.section(Section.MOVIES).items.size} Filme · ${app.section(Section.SERIES).items.size} Serien",
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Listen neu laden", Icons.Rounded.Refresh) { app.reloadAll() }
                SecondaryButton("Verlauf löschen", Icons.Rounded.DeleteOutline) { app.clearHistory() }
                if (!confirm) SecondaryButton("Abmelden", Icons.Rounded.PowerSettingsNew, danger = true) { confirm = true }
                else PrimaryButton("Wirklich abmelden?", Icons.Rounded.PowerSettingsNew) { app.logout() }
            }
        }
        Column(Modifier.padding(start = 40.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Kicker("Tastenkürzel im Player")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp).width(680.dp)) {
                listOf("Leertaste" to "Pause", "← →" to "spulen", "↑ ↓" to "Sender/Folge", "F" to "Vollbild", "M" to "Ton aus", "Esc" to "zurück").forEach { (key, action) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyCap(key)
                        Text(action, color = Muted, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text("TV Player ${System.getProperty("tvplayer.version") ?: "(Entwicklung)"} · Wiedergabe mit VLC (libVLC, LGPL) · Diese App enthält keine Inhalte.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
