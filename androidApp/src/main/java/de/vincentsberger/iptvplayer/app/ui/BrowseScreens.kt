package de.vincentsberger.iptvplayer.app.ui

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import de.vincentsberger.iptvplayer.shared.labelOf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
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
    // Gewählte Kategorie: nach dem Player aus dem Fokus-Schlüssel, sonst die zuletzt gemerkte.
    var chosen by remember(section) { mutableStateOf(rf.restoreKey?.substringBeforeLast(':') ?: vm.browseCategory(section)) }

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
            // Gemerkte Kategorie gibt es nicht mehr (ausgeblendet, Anbieter geändert) → sinnvoller Standard.
            val fallback = if (continueList.isNotEmpty()) KEY_CONTINUE else categories.firstOrNull { it.first.startsWith("cat:") }?.first ?: KEY_ALL
            val selected = chosen?.takeIf { k -> categories.any { it.first == k } } ?: fallback
            LaunchedEffect(selected) { vm.rememberBrowseCategory(section, selected) }
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
                            CategoryItem(
                                name, key == selected, Modifier.testTag("browse-category:$key"), icon,
                                onHide = if (key.startsWith("cat:")) ({ vm.toggleHidden(section, key.removePrefix("cat:")); chosen = KEY_ALL }) else null,
                            ) { chosen = key }
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
                            val itemKeys = stableKeys(items)
                            itemsIndexed(items, key = { i, _ -> itemKeys[i] }) { i, item ->
                                val key = "$selected:$i"
                                val mod = when {
                                    key == rf.restoreKey -> Modifier.focusRequester(rf.requester)
                                    i == 0 && rf.restoreKey == null -> Modifier.focusRequester(firstFocus)
                                    else -> Modifier
                                }
                                PosterCard(
                                    item, item.favKey in vm.favorites, vm.progressOf(item),
                                    onFocus = { vm.focusKey = key; ambient.url = item.logo }, onClick = { vm.open(items, i) }, onLongClick = { vm.toggleFavorite(item) },
                                    modifier = Modifier.animateItem().then(mod).testTag("card:$key"), fillCell = true,
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Einstellungen", null, Icons.Rounded.Settings)
        Column(
            Modifier.padding(horizontal = 48.dp).width(720.dp).panel().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Anbieter")
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
                    TvButton("Entfernen", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Danger) { confirmLogout = true }
                } else {
                    TvButton("Wirklich entfernen?", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Primary) { vm.logout() }
                }
            }
            // Weitere Anbieter: mit einem Klick wechseln; Favoriten und Verlauf bleiben je Anbieter getrennt.
            val others = vm.accounts.filter { it.id != vm.activeAccountId }
            Rule(Modifier.padding(vertical = 6.dp))
            others.forEach { saved ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(labelOf(saved.account), fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    TvButton("Wechseln", Modifier.testTag("account:switch:${saved.id}"), icon = Icons.Rounded.SwapHoriz) { vm.switchAccount(saved.id) }
                }
            }
            TvButton("Anbieter hinzufügen", Modifier.testTag("account:add"), icon = Icons.Rounded.Add) { vm.addAccount() }
        }
        ComfortSettings(vm)
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
        Text(
            "IPTV Player $version · Diese App ist ein reiner Player und enthält keine Inhalte.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp),
        )
    }
}

@Composable
internal fun PageHeader(title: String, subtitle: String?, icon: ImageVector? = null) {
    Column(Modifier.padding(start = 48.dp, top = 40.dp, bottom = 20.dp)) {
        TitleWithIcon(title, icon, 34.sp)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Komfort-Einstellungen für die Fernbedienung: Umschalter als Knöpfe „An“/„Aus“. */
@Composable
private fun ComfortSettings(vm: AppState) {
    Column(
        Modifier.padding(start = 48.dp, top = 20.dp).width(720.dp).panel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Kicker("Komfort")
        SettingToggle("Listen alphabetisch sortieren", "Sonst gilt die Reihenfolge deines Anbieters.", vm.sortAz, "setting:sort") { vm.sortAz = it }
        SettingToggle("Beim Start den letzten Sender abspielen", "Die App öffnet direkt den zuletzt gesehenen Live-Sender.", vm.startWithLastChannel, "setting:startLast") { vm.startWithLastChannel = it }
        SettingToggle("Nächste Folge automatisch abspielen", "Mit 10 Sekunden Countdown – Zurück bricht ab.", vm.autoNextEpisode, "setting:autoNext") { vm.autoNextEpisode = it }
        SettingToggle(
            "Downloads pausieren, während du schaust",
            "Nötig, wenn dein Anbieter nur eine Verbindung gleichzeitig erlaubt.",
            vm.pauseDownloadsWhilePlaying, "setting:pauseDownloads",
        ) { vm.pauseDownloadsWhilePlaying = it }
        Rule(Modifier.padding(vertical = 6.dp))
        Text("Ausgeblendete Kategorien", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val hidden = Section.entries.flatMap { s -> vm.hiddenCategories(s).map { s to it } }
        if (hidden.isEmpty()) {
            Text("Keine. Zum Ausblenden in einer Kategorienliste OK gedrückt halten.", color = Muted, fontSize = 14.sp)
        } else {
            hidden.forEach { (section, cat) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sectionIcon(section), tint = Muted, size = 20.dp)
                    val tagged = splitProviderTag(cat.name)
                    tagged.tag?.let { TagChip(it, Modifier.padding(start = 10.dp)) }
                    Text(tagged.name, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    TvButton("Einblenden", Modifier.testTag("unhide:${section.name}:${cat.id}")) { vm.toggleHidden(section, cat.id) }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, hint: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = Muted, fontSize = 13.sp)
        }
        TvButton(if (on) "An" else "Aus", Modifier.testTag(tag), selected = on) { onChange(!on) }
    }
}
