package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.material.icons.rounded.Theaters
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.UpdatePlatform
import de.vincentsberger.iptvplayer.shared.formatBytes
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.launch

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.material.icons.rounded.SystemUpdate

import de.vincentsberger.iptvplayer.app.update.Updater

import de.vincentsberger.iptvplayer.app.download.Downloads

import androidx.compose.material.icons.rounded.DownloadForOffline

import androidx.compose.foundation.layout.ColumnScope

import de.vincentsberger.iptvplayer.shared.Tab


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
import de.vincentsberger.iptvplayer.shared.flagOf

/** Gemeinsame Logik für Zeilen-Ansichten: Fokus merken und nach dem Zurückkehren wiederherstellen. */
internal class RowFocus(val restoreKey: String?) {
    val requester = FocusRequester()
}

@Composable
internal fun rememberRowFocus(vm: AppState): RowFocus {
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
    val menu = LocalMenu.current
    val state = vm.section(section)
    val rf = rememberRowFocus(vm)
    val firstFocus = remember { FocusRequester() }
    val continueList = vm.continueWatching(section)

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
        state.error != null -> ErrorView(state.error.orEmpty(), onDownloads = { vm.selectTab(Tab.DOWNLOADS) }) { vm.reloadAll() }
        state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.", sectionIcon(section))
        else -> {
            val categories = buildList {
                if (continueList.isNotEmpty()) add(Triple(KEY_CONTINUE, "Weiterschauen", Icons.Rounded.PlayCircleOutline))
                if (vm.newItemsOf(section).isNotEmpty()) add(Triple(AppState.BROWSE_NEW, "Neu (7 Tage)", Icons.Rounded.NewReleases))
                add(Triple(KEY_FAV, "Favoriten", Icons.Rounded.StarBorder))
                add(Triple(KEY_ALL, "Alle ${section.title}", Icons.Rounded.GridView))
                state.categories.forEach { add(Triple("cat:${it.id}", it.name, null)) }
                vm.genreCategories(section).forEach { add(Triple(it.id, it.name, Icons.Rounded.Theaters)) }
            }
            // Gemerkte Kategorie gibt es nicht mehr (ausgeblendet, Anbieter geändert) → sinnvoller Standard.
            val fallback = if (continueList.isNotEmpty()) KEY_CONTINUE else categories.firstOrNull { it.first.startsWith("cat:") }?.first ?: KEY_ALL
            val selected = chosen?.takeIf { k -> categories.any { it.first == k } } ?: fallback
            LaunchedEffect(selected) { vm.rememberBrowseCategory(section, selected) }
            val items = when (selected) {
                KEY_CONTINUE -> continueList.map { it.item }
                KEY_FAV -> vm.favoriteItems(section)
                AppState.BROWSE_NEW -> vm.newItemsOf(section)
                KEY_ALL -> state.items
                else -> if (selected.startsWith(AppState.BROWSE_GENRE)) vm.browseItems(section, selected)
                else state.byCategory[selected.removePrefix("cat:")].orEmpty()
            }
            Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 27.dp, end = 48.dp)) {
                Column(Modifier.weight(0.8f).widthIn(max = 270.dp).fillMaxHeight()) {
                    TitleWithIcon(section.title, sectionIcon(section), 30.sp)
                    Text("${state.items.size} Titel", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(categories, key = { it.first }) { (key, name, icon) ->
                            CategoryItem(
                                name, key == selected, Modifier.testTag("browse-category:$key"), icon,
                                onOptions = state.categories.firstOrNull { "cat:${it.id}" == key }?.let { cat -> { menu.current = categoryMenu(vm, section, cat) { chosen = KEY_ALL } } },
                            ) { chosen = key }
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(2.6f).fillMaxHeight()) {
                    Headline(splitCategoryName(categories.firstOrNull { it.first == selected }?.second.orEmpty()).name, 22.sp)
                    Text(
                        "${items.size} Titel",
                        color = Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (items.isEmpty()) {
                        EmptyView(
                            if (selected == KEY_FAV) "Noch keine Favoriten" else "Nichts gefunden",
                            if (selected == KEY_FAV) "Halte OK auf einem Titel und wähle „Zu Favoriten hinzufügen“." else "In dieser Kategorie gibt es keine Titel.",
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
                                    onFocus = { vm.focusKey = key }, onClick = { if (selected == KEY_CONTINUE) vm.play(items, i) else vm.open(items, i) }, onLongClick = { menu.current = itemMenu(vm, item) },
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
    val menu = LocalMenu.current
    val rf = rememberRowFocus(vm)
    val rows = Section.entries.map { it to vm.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Favoriten", "Halte OK auf einem Sender, Film oder einer Serie und wähle „Zu Favoriten hinzufügen“.", Icons.Rounded.Favorite)
        if (rows.isEmpty()) {
            EmptyView("Noch keine Favoriten", "Deine Lieblingssender, Filme und Serien erscheinen hier.", Icons.Rounded.StarBorder)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                items(rows, key = { it.first.name }) { (section, list) ->
                    ContentRow(
                        section.title, list, "favs:${section.name}", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key }, { l, i -> vm.rememberSearch(); vm.open(l, i) },
                        { menu.current = itemMenu(vm, it) },
                    )
                }
            }
        }
    }
}

/** Zuletzt gesuchte Begriffe als Knöpfe – OK sucht wieder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(vm: AppState) {
    if (vm.recentSearches.isEmpty()) return
    Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
        Kicker("Zuletzt gesucht")
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.recentSearches.forEach { term -> TvButton(term, Modifier.testTag("recent:$term")) { vm.query = term } }
            TvButton("Löschen", icon = Icons.Rounded.DeleteOutline) { vm.clearRecentSearches() }
        }
    }
}

// ---------- Suche ----------

@Composable
fun SearchScreen(vm: AppState) {
    val menu = LocalMenu.current
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
            vm.query.trim().length < 2 -> Column {
                RecentSearches(vm)
                EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.", Icons.Rounded.Search)
            }
            results.isEmpty() && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner() }
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.", Icons.Rounded.SearchOff)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                items(results.entries.toList(), key = { it.key.name }) { (section, list) ->
                    ContentRow(
                        "${section.title} (${list.size})", list, "search:${section.name}", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key }, vm::open, { menu.current = itemMenu(vm, it) },
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
                    TvButton("Anbieter entfernen", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Danger) { confirmLogout = true }
                } else {
                    TvButton("Wirklich entfernen?", icon = Icons.Rounded.PowerSettingsNew, style = ButtonStyle.Primary) { vm.logout(); Feedback.show("Anbieter entfernt") }
                }
            }
            // Weitere Anbieter: mit einem Klick wechseln; Favoriten und Verlauf bleiben je Anbieter getrennt.
            val others = vm.accounts.filter { it.id != vm.activeAccountId }
            Rule(Modifier.padding(vertical = 6.dp))
            others.forEach { saved ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(labelOf(saved.account), fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    TvButton("Wechseln", Modifier.testTag("account:switch:${saved.id}"), icon = Icons.Rounded.SwapHoriz) { vm.switchAccount(saved.id); Feedback.show("Gewechselt zu ${labelOf(saved.account)}") }
                }
            }
            TvButton("Anbieter hinzufügen", Modifier.testTag("account:add"), icon = Icons.Rounded.Add) { vm.addAccount() }
        }
        ComfortSettings(vm)
        UpdateSettings(vm)
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
        Text(
            "IPTV Player $version · Diese App ist ein reiner Player und enthält keine Inhalte.",
            color = Muted, fontSize = 15.sp, modifier = Modifier.padding(start = 48.dp, top = 24.dp),
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

/** Kategorien für die Neu-Reihen der Startseite (nur Xtream; ohne Erwachsenen-Kategorien). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardSettings(vm: AppState) {
    if (!vm.supportsDashboard) return
    Rule(Modifier.padding(vertical = 6.dp))
    Text("Startseite: Neues aus diesen Kategorien", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    Text("Neue Filme und Folgen der letzten 7 Tage erscheinen auf der Startseite. Neue Kategorien deines Anbieters sind nicht automatisch dabei.", color = Muted, fontSize = 15.sp)
    // Hunderte Kategorien: zugeklappt, sonst wären die Einstellungen darunter mit der Fernbedienung kaum erreichbar.
    var open by rememberSaveable { mutableStateOf<Section?>(null) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Section.MOVIES, Section.SERIES).forEach { s ->
            if (vm.dashboardCategoryOptions(s).isEmpty()) return@forEach
            val n = vm.dashboardCategories.count { it.startsWith("${s.name}:") }
            TvButton("${s.title} wählen ($n)", Modifier.testTag("dashpick:${s.name}"), selected = open == s) { open = if (open == s) null else s }
        }
    }
    open?.let { s ->
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.dashboardCategoryOptions(s).forEach { c ->
                val on = "${s.name}:${c.id}" in vm.dashboardCategories
                TvButton(if (on) "✓ ${c.name}" else c.name, Modifier.testTag("dashcat:${s.name}:${c.id}"), selected = on) { vm.toggleDashboardCategory(s, c.id) }
            }
        }
        TvButton("Fertig", Modifier.padding(top = 8.dp).testTag("dashpick:done")) { open = null }
    }
}

/** Länderfilter je Bereich: Kürzel des Anbieters als Knöpfe zum An- und Abwählen. Ohne Kürzel (z. B. M3U) kein Abschnitt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountrySettings(vm: AppState, s: Section) {
    val options = vm.availableCountries(s)
    val chosen = vm.countries(s)
    // Test-Tags für Live wie bisher, Filme/Serien mit Bereich davor.
    val tagPrefix = if (s == Section.LIVE) "country:" else "country:${s.name}:"
    if (options.isEmpty()) return
    Rule(Modifier.padding(vertical = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Länder in ${s.title}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (chosen.isNotEmpty()) TvButton("Alle zeigen", Modifier.testTag("${tagPrefix}clear")) { vm.clearCountries(s) }
    }
    Text("Nur Kategorien dieser Länder werden angezeigt. Kategorien ohne Kürzel bleiben sichtbar.", color = Muted, fontSize = 15.sp)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option.tag in chosen
            // ✓ im Text: im Fokus sind alle Knöpfe weiß, die Auswahl muss trotzdem erkennbar bleiben.
            val label = listOfNotNull("✓".takeIf { on }, flagOf(option.tag), option.tag, option.count.toString()).joinToString(" ")
            TvButton(label, Modifier.testTag("$tagPrefix${option.tag}"), selected = on) { vm.toggleCountry(s, option.tag) }
        }
    }
}

/** Komfort-Einstellungen für die Fernbedienung: Umschalter als Knöpfe „An“/„Aus“. */
@Composable
private fun ComfortSettings(vm: AppState) {
    SettingsPanel("Wiedergabe") {
        SettingToggle("Beim Start den letzten Sender abspielen", "Die App öffnet direkt den zuletzt gesehenen Live-Sender.", vm.startWithLastChannel, "setting:startLast") { vm.startWithLastChannel = it }
        SettingToggle("Nächste Folge automatisch abspielen", "Mit 10 Sekunden Countdown – Zurück bricht ab.", vm.autoNextEpisode, "setting:autoNext") { vm.autoNextEpisode = it }
    }
    SettingsPanel("Listen") {
        SettingToggle("Listen alphabetisch sortieren", "Sonst gilt die Reihenfolge deines Anbieters.", vm.sortAz, "setting:sort") { vm.sortAz = it }
        SettingToggle("Filme und Serien: neueste zuerst", "Nach dem Jahr im Titel; ohne Jahr ans Ende.", vm.sortByYear, "setting:sortYear") { vm.sortByYear = it }
        Rule(Modifier.padding(vertical = 6.dp))
        Text("Ausgeblendete Kategorien", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        val hidden = Section.entries.flatMap { s -> vm.hiddenCategories(s).map { s to it } }
        if (hidden.isEmpty()) {
            Text("Keine. Zum Ausblenden in einer Kategorienliste OK halten und „Kategorie ausblenden“ wählen.", color = Muted, fontSize = 16.sp)
        } else {
            hidden.forEach { (section, cat) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sectionIcon(section), tint = Muted, size = 20.dp)
                    val tagged = splitCategoryName(cat.name)
                    tagged.tag?.let { TagChip(it, Modifier.padding(start = 10.dp)) }
                    Text(tagged.name, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    TvButton("Einblenden", Modifier.testTag("unhide:${section.name}:${cat.id}")) {
                        vm.toggleHidden(section, cat.id)
                        Feedback.show("Wieder eingeblendet")
                    }
                }
            }
        }
        Section.entries.forEach { CountrySettings(vm, it) }
        DashboardSettings(vm)
    }
    SettingsPanel("Downloads") {
        Text(
            "${Downloads.all.size} Titel · ${formatBytes(Downloads.usedBytes())} belegt · noch ${formatBytes(Downloads.freeBytes())} frei",
            color = Muted, fontSize = 16.sp,
        )
        SettingToggle(
            "Downloads pausieren, während du schaust",
            "Nötig, wenn dein Anbieter nur eine Verbindung gleichzeitig erlaubt.",
            vm.pauseDownloadsWhilePlaying, "setting:pauseDownloads",
        ) { vm.pauseDownloadsWhilePlaying = it }
        TvButton("Zu den Downloads", icon = Icons.Rounded.DownloadForOffline) { vm.selectTab(Tab.DOWNLOADS) }
    }
}

/** App-Version und Updates (nur für selbst installierte Versionen – Store-Versionen aktualisiert der Store). */
@Composable
private fun UpdateSettings(vm: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(context) }
    val allowed = remember { Updater.allowed(context) }
    var checking by remember { mutableStateOf(false) }
    SettingsPanel("App") {
        Text("Version $current", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (!allowed) {
            Text("Updates bekommst du über den App-Store.", color = Muted, fontSize = 16.sp)
            return@SettingsPanel
        }
        SettingToggle(
            "Automatisch nach Updates suchen",
            "Einmal am Tag bei GitHub nachsehen. Installiert wird nur nach deiner Bestätigung.",
            vm.updateCheck, "setting:updateCheck",
        ) { vm.updateCheck = it }
        val update = vm.availableUpdate
        val progress = Updater.progress
        when {
            progress != null -> {
                Text("Update wird geladen … ${(progress * 100).toInt()} %", fontSize = 16.sp)
                ProgressBar(progress, Modifier.fillMaxWidth())
            }
            update != null -> TvButton("Auf Version ${update.version} aktualisieren", Modifier.testTag("update:install"), icon = Icons.Rounded.SystemUpdate, style = ButtonStyle.Primary) {
                scope.launch { Updater.install(context, update) }
            }
            else -> TvButton(if (checking) "Wird geprüft …" else "Jetzt nach Updates suchen", Modifier.testTag("update:check"), icon = Icons.Rounded.Refresh) {
                if (checking) return@TvButton
                scope.launch {
                    checking = true
                    val found = vm.checkForUpdate(current, UpdatePlatform.ANDROID, force = true)
                    checking = false
                    Feedback.show(found?.let { "Update auf Version ${it.version} verfügbar" } ?: "Du hast die neueste Version")
                }
            }
        }
        Updater.message?.let { Text(it, color = Muted, fontSize = 16.sp) }
    }
}

/** Ein Bereich der Einstellungen mit Überschrift. */
@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(start = 48.dp, top = 20.dp).width(720.dp).panel().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Kicker(title)
        content()
    }
}

@Composable
private fun SettingToggle(title: String, hint: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = Muted, fontSize = 15.sp)
        }
        TvButton(if (on) "An" else "Aus", Modifier.testTag(tag), selected = on) { onChange(!on) }
    }
}
