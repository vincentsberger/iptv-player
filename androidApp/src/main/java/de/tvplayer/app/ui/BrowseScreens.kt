package de.tvplayer.app.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.tvplayer.shared.AppState
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
import de.tvplayer.shared.data.splitProviderTag
import kotlinx.coroutines.delay

/** Gemeinsame Logik für Zeilen-Ansichten: Fokus merken und nach dem Zurückkehren wiederherstellen. */
private class RowFocus(val restoreKey: String?) {
    val requester = FocusRequester()
}

@Composable
private fun rememberRowFocus(vm: AppState): RowFocus {
    val rf = remember { RowFocus(vm.focusKey) }
    LaunchedEffect(Unit) {
        if (rf.restoreKey != null) {
            delay(150)
            runCatching { rf.requester.requestFocus() }
        }
    }
    return rf
}

// ---------- Filme und Serien: Kategorien als Liste, Titel als Kacheln im Grid ----------

private const val KEY_CONTINUE = "continue"
private const val KEY_FAV = "fav"
private const val KEY_ALL = "all"

@Composable
fun BrowseScreen(vm: AppState, section: Section) {
    val state = vm.section(section)
    val rf = rememberRowFocus(vm)
    val firstFocus = remember { FocusRequester() }
    val continueList = vm.continueWatching(section)
    val ambient = LocalAmbient.current

    // Nach dem Zurückkehren (z. B. aus dem Player) dieselbe Kategorie wieder öffnen: sie steckt im Fokus-Schlüssel.
    var selected by remember(section) {
        mutableStateOf(
            rf.restoreKey?.substringBeforeLast(':')
                ?: if (continueList.isNotEmpty()) KEY_CONTINUE else state.categories.firstOrNull()?.let { "cat:${it.id}" } ?: KEY_ALL,
        )
    }

    LaunchedEffect(state.loaded) {
        if (state.loaded && rf.restoreKey == null) {
            delay(150)
            runCatching { firstFocus.requestFocus() }
        }
    }

    when {
        state.loading -> GridSkeleton()
        state.error != null -> ErrorView(state.error.orEmpty()) { vm.reloadAll() }
        state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.", sectionIcon(section))
        else -> {
            val categories = buildList {
                if (continueList.isNotEmpty()) add(Triple(KEY_CONTINUE, "Weiterschauen", Icons.Rounded.PlayCircleOutline))
                add(Triple(KEY_FAV, "Favoriten", Icons.Rounded.StarBorder))
                add(Triple(KEY_ALL, "Alle ${section.title}", Icons.Rounded.GridView))
                state.categories.forEach { add(Triple("cat:${it.id}", it.name, null)) }
            }
            val items = when (selected) {
                KEY_CONTINUE -> continueList.map { it.item }
                KEY_FAV -> vm.favoriteItems(section)
                KEY_ALL -> state.items
                else -> state.byCategory[selected.removePrefix("cat:")].orEmpty()
            }
            Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 27.dp, end = 48.dp)) {
                Column(Modifier.weight(0.8f).widthIn(max = 270.dp).fillMaxHeight()) {
                    TitleWithIcon(section.title, sectionIcon(section), 30.sp)
                    Text("${state.items.size} Titel", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(categories, key = { it.first }) { (key, name, icon) ->
                            CategoryItem(name, key == selected, Modifier.testTag("browse-category:$key"), icon) { selected = key }
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(2.6f).fillMaxHeight()) {
                    Headline(splitProviderTag(categories.firstOrNull { it.first == selected }?.second.orEmpty()).name, 22.sp)
                    Text(
                        "${items.size} Titel  ·  OK ${if (section == Section.SERIES) "Folgen ansehen" else "abspielen"}  ·  OK halten  Favorit",
                        color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (items.isEmpty()) {
                        EmptyView(
                            if (selected == KEY_FAV) "Noch keine Favoriten" else "Nichts gefunden",
                            if (selected == KEY_FAV) "Halte OK auf einem Titel gedrückt, um ihn hier zu speichern." else "In dieser Kategorie gibt es keine Titel.",
                            if (selected == KEY_FAV) Icons.Rounded.StarBorder else sectionIcon(section),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            itemsIndexed(items, key = { i, it -> "${it.favKey}#$i" }) { i, item ->
                                val key = "$selected:$i"
                                val mod = when {
                                    key == rf.restoreKey -> Modifier.focusRequester(rf.requester)
                                    i == 0 && rf.restoreKey == null -> Modifier.focusRequester(firstFocus)
                                    else -> Modifier
                                }
                                PosterCard(
                                    item, item.favKey in vm.favorites, vm.progressOf(item),
                                    onFocus = { vm.focusKey = key; ambient.url = item.logo }, onClick = { vm.open(items, i) }, onLongClick = { vm.toggleFavorite(item) },
                                    modifier = mod.testTag("card:$key"), fillCell = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridSkeleton() {
    Row(Modifier.fillMaxSize().padding(start = 48.dp, top = 40.dp)) {
        Column(Modifier.width(220.dp)) { repeat(6) { Shimmer(Modifier.fillMaxWidth().height(36.dp).padding(bottom = 8.dp)) } }
        Spacer(Modifier.width(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { repeat(4) { Shimmer(Modifier.size(width = 150.dp, height = 222.dp)) } } }
        }
    }
}

// ---------- Favoriten ----------

@Composable
fun FavoritesScreen(vm: AppState) {
    val rf = rememberRowFocus(vm)
    val rows = Section.entries.map { it to vm.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Favoriten", "Halte OK auf einem Sender, Film oder einer Serie gedrückt, um sie hier zu sammeln.", Icons.Rounded.Favorite)
        if (rows.isEmpty()) {
            EmptyView("Noch keine Favoriten", "Deine Lieblingssender, Filme und Serien erscheinen hier.", Icons.Rounded.StarBorder)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                items(rows, key = { it.first.name }) { (section, list) ->
                    ContentRow(
                        section.title, list, "favs:${section.name}", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key }, vm::open, vm::toggleFavorite,
                    )
                }
            }
        }
    }
}

// ---------- Suche ----------

@Composable
fun SearchScreen(vm: AppState) {
    val rf = rememberRowFocus(vm)
    val results = vm.searchResults
    val loading = Section.entries.any { vm.section(it).loading }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Suche", null, Icons.Rounded.Search)
        TvTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = "Sender, Filme oder Serien suchen",
            leading = Icons.Rounded.Search,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
            modifier = Modifier.testTag("search:field").padding(horizontal = 48.dp).width(720.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            vm.query.trim().length < 2 -> EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.", Icons.Rounded.Search)
            results.isEmpty() && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner() }
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.", Icons.Rounded.SearchOff)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                items(results.entries.toList(), key = { it.key.name }) { (section, list) ->
                    ContentRow(
                        "${section.title} (${list.size})", list, "search:${section.name}", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key }, vm::open, vm::toggleFavorite,
                    )
                }
            }
        }
    }
}

// ---------- Einstellungen ----------

@Composable
fun SettingsScreen(vm: AppState) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen", null, Icons.Rounded.Settings)
        Column(
            Modifier.padding(horizontal = 48.dp).width(720.dp).panel().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Konto")
            Headline(vm.accountLabel, 20.sp)
            Text(
                "${vm.section(Section.LIVE).items.size} Sender · ${vm.section(Section.MOVIES).items.size} Filme · ${vm.section(Section.SERIES).items.size} Serien",
                color = Muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Listen neu laden", icon = Icons.Rounded.Refresh) { vm.reloadAll() }
                TvButton("Verlauf löschen", icon = Icons.Rounded.DeleteOutline) { vm.clearHistory() }
                if (!confirmLogout) {
                    TvButton("Abmelden", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Danger) { confirmLogout = true }
                } else {
                    TvButton("Wirklich abmelden?", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Primary) { vm.logout() }
                }
            }
        }
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
        Text(
            "TV Player $version · Diese App ist ein reiner Player und enthält keine Inhalte.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp),
        )
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String?, icon: ImageVector? = null) {
    Column(Modifier.padding(start = 48.dp, top = 40.dp, bottom = 20.dp)) {
        TitleWithIcon(title, icon, 34.sp)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
