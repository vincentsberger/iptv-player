package de.tvplayer.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
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
    Column(Modifier.width(232.dp).fillMaxHeight().background(Color(0xFF080A10)).padding(horizontal = 14.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 28.dp)) {
            LogoTile(40.dp)
            Text("TV Player", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Tab.entries.forEach { t ->
            if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
            NavigationDrawerItem(
                label = { Text(t.title, fontWeight = FontWeight.Medium) },
                icon = { Icon(t.icon(), null) },
                selected = t == app.tab,
                onClick = { app.selectTab(t) },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = Surface2,
                    unselectedContainerColor = Color.Transparent,
                    selectedIconColor = Accent2,
                    selectedTextColor = Color.White,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted,
                ),
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
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
            Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 28.dp, end = 28.dp, bottom = 20.dp)) {
                CategoryList("Live TV", "${state.items.size} Sender", cats, app.liveCategory) { app.liveCategory = it }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(cats.firstOrNull { it.id == app.liveCategory }?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("${channels.size} Sender · Doppelklick zum Ansehen", color = Muted, modifier = Modifier.padding(bottom = 12.dp))
                    if (channels.isEmpty()) {
                        EmptyView("Keine Sender", if (app.liveCategory == AppState.LIVE_FAV) "Klicke auf den Stern bei einem Sender, um ihn hier zu speichern." else "In dieser Kategorie gibt es keine Sender.")
                    } else {
                        val listState = rememberLazyListState()
                        Box {
                            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 14.dp)) {
                                itemsIndexed(channels) { i, ch ->
                                    ChannelRow(
                                        i + 1, ch, ch == selected, ch.favKey in app.favorites,
                                        onSelect = { selected = ch },
                                        onPlay = { app.open(channels, i) },
                                        onToggleFavorite = { app.toggleFavorite(ch) },
                                    )
                                }
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                }
                Spacer(Modifier.width(24.dp))
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
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(when { selected -> Surface2; hovered -> Color(0xFF1B2030); else -> Surface1 })
            .hoverable(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onSelect, onDoubleClick = onPlay)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", color = Muted, modifier = Modifier.width(44.dp))
        Box(Modifier.width(60.dp).height(40.dp).background(Color(0x14FFFFFF), RoundedCornerShape(8.dp)), Alignment.Center) { ChannelLogo(item, 32.dp) }
        Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
        if (hovered || selected) {
            IconButton(onPlay) { Icon(Icons.Rounded.PlayArrow, "Ansehen", tint = Color.White) }
        }
        if (favorite || hovered) {
            IconButton(onToggleFavorite) { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit", tint = if (favorite) Gold else Muted) }
        }
    }
}

@Composable
private fun LiveInfo(item: Item?, epg: List<EpgEntry>, favorite: Boolean, onPlay: () -> Unit, onToggleFavorite: () -> Unit) {
    Column(Modifier.width(340.dp).fillMaxHeight().background(Surface1, RoundedCornerShape(20.dp)).padding(22.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Bg, RoundedCornerShape(14.dp)), Alignment.Center) {
            if (item != null) ChannelLogo(item, 96.dp) else LogoTile(56.dp)
        }
        Spacer(Modifier.height(18.dp))
        if (item == null) {
            Text("Wähle einen Sender", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Ein Klick zeigt das Programm, ein Doppelklick startet den Sender.", color = Muted, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text(item.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            val now = epg.getOrNull(0)
            if (now != null) {
                Text("JETZT", color = Accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(now.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(now.start, color = Muted, fontSize = 13.sp)
                    ProgressBar(epgProgress(now), Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text(now.end, color = Muted, fontSize = 13.sp)
                }
                epg.getOrNull(1)?.let {
                    Text("DANACH", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text("${it.start}  ${it.title}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("Keine Programminformationen", color = Muted)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Ansehen", Icons.Rounded.PlayArrow, onClick = onPlay)
                SecondaryButton(if (favorite) "Favorit" else "Merken", if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = onToggleFavorite)
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
private fun CategoryList(title: String, subtitle: String, categories: List<Category>, selectedId: String, onSelect: (String) -> Unit) {
    Column(Modifier.width(250.dp).fillMaxHeight()) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, modifier = Modifier.padding(bottom = 14.dp))
        val state = rememberLazyListState()
        Box {
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(end = 12.dp)) {
                items(categories) { cat ->
                    val sel = cat.id == selectedId
                    NavigationDrawerItem(
                        label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) },
                        selected = sel,
                        onClick = { onSelect(cat.id) },
                        shape = RoundedCornerShape(10.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Surface2, unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White, unselectedTextColor = Muted,
                        ),
                        modifier = Modifier.height(44.dp),
                    )
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
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
        state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.")
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
            Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 28.dp, bottom = 20.dp)) {
                CategoryList(section.title, "${state.items.size} Titel", cats, selected) { selected = it }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cats.firstOrNull { it.id == selected }?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${items.size} Titel", color = Muted)
                        }
                        OutlinedTextField(
                            filter, { filter = it }, singleLine = true,
                            placeholder = { Text("In dieser Liste suchen", color = Muted) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0x33FFFFFF)),
                            modifier = Modifier.width(300.dp).testTag("browse-filter"),
                        )
                    }
                    if (items.isEmpty()) {
                        EmptyView("Nichts gefunden", if (selected == CAT_FAV) "Klicke auf den Stern bei einem Titel, um ihn hier zu speichern." else "Hier gibt es keine passenden Titel.")
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
        PageHeader("Favoriten", "Klicke auf den Stern bei einem Sender, Film oder einer Serie, um sie hier zu sammeln.")
        if (rows.isEmpty()) {
            EmptyView("Noch keine Favoriten", "Deine Lieblingssender, Filme und Serien erscheinen hier.")
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
        PageHeader("Suche")
        OutlinedTextField(
            app.query, { app.query = it }, singleLine = true,
            placeholder = { Text("Sender, Filme oder Serien suchen", color = Muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0x33FFFFFF)),
            modifier = Modifier.padding(horizontal = 40.dp).width(640.dp).testTag("search-field"),
        )
        Spacer(Modifier.height(16.dp))
        when {
            app.query.trim().length < 2 -> EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.")
            results.isEmpty() && loading -> Loading("Listen werden geladen …")
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.")
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

@Composable
private fun SettingsScreen(app: AppState) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen")
        Column(
            Modifier.padding(horizontal = 40.dp).width(680.dp).background(Surface1, RoundedCornerShape(20.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Konto", color = Muted)
            Text(app.accountLabel, fontSize = 19.sp, fontWeight = FontWeight.Medium)
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
            Text("Tastenkürzel im Player", fontWeight = FontWeight.SemiBold)
            Text("Leertaste Pause · ← → spulen · ↑ ↓ Sender/Folge · F Vollbild · M Ton aus · Esc zurück", color = Muted)
            Text("TV Player 1.1 · Wiedergabe mit VLC (libVLC, LGPL) · Diese App enthält keine Inhalte.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
