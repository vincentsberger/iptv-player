package de.tvplayer.app.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.tvplayer.shared.AppState
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section
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

// ---------- Filme und Serien ----------

@Composable
fun BrowseScreen(vm: AppState, section: Section) {
    val state = vm.section(section)
    var focused by remember { mutableStateOf<Item?>(null) }
    val rf = rememberRowFocus(vm)
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(state.loaded) {
        if (state.loaded && rf.restoreKey == null) {
            delay(150)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(focused?.logo)
        when {
            state.loading -> RowsSkeleton()
            state.error != null -> ErrorView(state.error.orEmpty()) { vm.reloadAll() }
            state.items.isEmpty() -> EmptyView("Keine ${section.title}", "Dein Anbieter stellt hier keine Inhalte bereit.")
            else -> Column(Modifier.fillMaxSize()) {
                Hero(focused, section, state.categories.firstOrNull { it.id == focused?.categoryId }?.name)
                val continueList = vm.continueWatching(section)
                val favList = vm.favoriteItems(section)
                val firstRow = when {
                    continueList.isNotEmpty() -> "continue"
                    favList.isNotEmpty() -> "fav"
                    else -> "cat:${state.categories.first().id}"
                }
                @Composable
                fun ItemsRow(title: String, list: List<Item>, rowKey: String) {
                    val initial = rf.restoreKey == null && rowKey == firstRow
                    ContentRow(
                        title, list, rowKey,
                        if (initial) "$rowKey:0" else rf.restoreKey,
                        if (initial) firstFocus else rf.requester,
                        vm.favorites, vm::progressOf, { item, key -> focused = item; vm.focusKey = key },
                        vm::open, vm::toggleFavorite,
                    )
                }
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp)) {
                    if (continueList.isNotEmpty()) item(key = "continue") {
                        ItemsRow("Weiterschauen", continueList.map { it.item }, "continue")
                    }
                    if (favList.isNotEmpty()) item(key = "fav") {
                        ItemsRow("Meine Favoriten", favList, "fav")
                    }
                    items(state.categories, key = { "cat:" + it.id }) { cat ->
                        ItemsRow(cat.name, state.byCategory[cat.id].orEmpty(), "cat:${cat.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(item: Item?, section: Section, category: String?) {
    Column(Modifier.fillMaxWidth().height(230.dp).padding(start = 48.dp, top = 40.dp, end = 48.dp)) {
        Text(section.title.uppercase(), color = Accent2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            item?.name ?: "Entdecken",
            fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 46.sp, modifier = Modifier.width(720.dp).padding(top = 6.dp),
        )
        if (category != null) Text(category, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        Text(
            if (item?.isSeriesFolder == true) "OK  Folgen ansehen     ·     OK halten  Favorit" else "OK  abspielen     ·     OK halten  Favorit",
            color = Muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun RowsSkeleton() {
    Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 40.dp)) {
        Shimmer(Modifier.width(120.dp).height(16.dp))
        Spacer(Modifier.height(12.dp))
        Shimmer(Modifier.width(520.dp).height(48.dp))
        Spacer(Modifier.height(80.dp))
        repeat(2) {
            Shimmer(Modifier.width(200.dp).height(22.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(7) { Shimmer(Modifier.size(width = 150.dp, height = 222.dp)) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------- Favoriten ----------

@Composable
fun FavoritesScreen(vm: AppState) {
    val rf = rememberRowFocus(vm)
    val rows = Section.entries.map { it to vm.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Favoriten", "Halte OK auf einem Sender, Film oder einer Serie gedrückt, um sie hier zu sammeln.")
        if (rows.isEmpty()) {
            EmptyView("Noch keine Favoriten", "Deine Lieblingssender, Filme und Serien erscheinen hier.")
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
        PageHeader("Suche", null)
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
            vm.query.trim().length < 2 -> EmptyView("Was möchtest du sehen?", "Gib mindestens zwei Buchstaben ein. Gesucht wird in Live TV, Filmen und Serien.")
            results.isEmpty() && loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner() }
            results.isEmpty() -> EmptyView("Keine Treffer", "Versuche einen anderen Suchbegriff.")
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
        PageHeader("Einstellungen", null)
        Column(
            Modifier.padding(horizontal = 48.dp).width(720.dp).background(Surface1, RoundedCornerShape(20.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Konto", color = Muted, fontSize = 14.sp)
            Text(vm.accountLabel, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
private fun PageHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 48.dp, top = 40.dp, bottom = 20.dp)) {
        Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
