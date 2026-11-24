package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.design.Chip
import de.vincentsberger.iptvplayer.ios.ui.design.GroupRow
import de.vincentsberger.iptvplayer.ios.ui.design.ListGroup
import de.vincentsberger.iptvplayer.ios.ui.design.PosterCard
import de.vincentsberger.iptvplayer.ios.ui.design.RowIcon
import de.vincentsberger.iptvplayer.ios.ui.design.SearchField
import de.vincentsberger.iptvplayer.ios.ui.design.SectionHeader
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.TitleBar
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.posterFacts
import de.vincentsberger.iptvplayer.shared.seriesFacts

/**
 * Suche über Live TV, Filme und Serien (Entwurf „10a/10b“; ab zwei Buchstaben, je Bereich höchstens 60 Treffer –
 * siehe [AppState.searchResults]). Leer: zuletzt gesuchte Begriffe und Einstiege in Kategorien. Mit Treffern: Filter
 * je Bereich, Sender als Zeilen mit „Jetzt“, Filme als Poster, Serien als Zeilen mit markiertem Suchbegriff.
 */
@Composable
fun SearchTab(app: AppState) {
    val results = app.searchResults
    val loading = Section.entries.any { app.section(it).loading }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // Leeres Feld: gleich tippen können. Mit altem Suchbegriff nicht – sonst verdeckt die Tastatur die Treffer.
    LaunchedEffect(Unit) { if (app.query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        TitleBar("Suche")
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(app.query, { app.query = it }, "Sender, Filme oder Serien", Modifier.weight(1f).focusRequester(focus), onSearch = { app.rememberSearch() })
            if (app.query.isNotEmpty()) Box(
                Modifier.height(44.dp).pressable { app.query = ""; keyboard?.hide() }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Abbrechen", color = TextColor, fontSize = 15.sp) }
        }
        when {
            app.query.trim().length < 2 -> EmptySearch(app)
            results.isEmpty() && loading -> de.vincentsberger.iptvplayer.ios.ui.design.ChannelRowsSkeleton(count = 5)
            results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "Keine Treffer", "Versuche einen anderen Suchbegriff.")
            else -> SectionedItems(app, results.toList(), query = app.query.trim(), onOpen = { keyboard?.hide(); app.rememberSearch() })
        }
    }
}

/** Leere Suche: zuletzt gesucht (P16) und drei Einstiege in häufige Kategorien. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptySearch(app: AppState) {
    Column {
        if (app.recentSearches.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                de.vincentsberger.iptvplayer.ios.ui.design.H3("Zuletzt gesucht", Modifier.weight(1f))
                Box(Modifier.height(32.dp).pressable { app.clearRecentSearches() }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Löschen", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                app.recentSearches.forEach { term ->
                    Chip(term, false, { app.query = term }, leading = { Icon(Icons.Rounded.History, null, tint = Muted, modifier = Modifier.size(16.dp)) })
                }
            }
        }
        val entries = discoverEntries(app)
        if (entries.isNotEmpty()) {
            SectionHeader("Kategorien entdecken", Modifier.padding(top = 8.dp))
            ListGroup(entries, Modifier.padding(horizontal = 16.dp))
        }
        SmallText(
            "Mindestens zwei Buchstaben – gesucht wird in Live TV, Filmen und Serien. Die letzten Begriffe merkt sich die App je Anbieter.",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        )
    }
}

/** Einstiege: zuletzt gewählte Live-Kategorie, neue Filme (falls vorhanden), häufigstes Serien-Genre. */
private fun discoverEntries(app: AppState): List<@Composable () -> Unit> = buildList {
    val live = app.section(Section.LIVE).categories.firstOrNull { it.id == app.liveCategory } ?: app.section(Section.LIVE).categories.firstOrNull()
    live?.let { c ->
        add {
            GroupRow("Live TV · ${splitCategoryName(c.name).name}", leading = { RowIcon(Icons.Rounded.LiveTv) }, trailingChevron = true, onClick = {
                app.liveCategory = c.id; LiveNav.showChannels = true; app.selectTab(Tab.LIVE)
            })
        }
    }
    if (app.newItemsOf(Section.MOVIES).isNotEmpty()) add {
        GroupRow("Filme · Neu (7 Tage)", leading = { RowIcon(Icons.Rounded.Movie) }, trailingChevron = true, onClick = {
            BrowseNav.selected[Section.MOVIES] = AppState.BROWSE_NEW; app.selectTab(Tab.MOVIES)
        })
    }
    app.genreCategories(Section.SERIES).firstOrNull()?.let { g ->
        add {
            GroupRow("Serien · ${g.id.removePrefix(AppState.BROWSE_GENRE)}", leading = { RowIcon(Icons.Rounded.VideoLibrary) }, trailingChevron = true, onClick = {
                BrowseNav.selected[Section.SERIES] = g.id; app.selectTab(Tab.SERIES)
            })
        }
    }
}

/**
 * Treffer bzw. Favoriten nach Bereich mit Filter-Chips („Alle 10 · Live TV 3 · …“): Sender als Zeilen (mit „Jetzt“),
 * Filme als Posterreihe, Serien als Zeilen. Tippen öffnet wie überall; langes Drücken öffnet das Kontextmenü.
 * [query] markiert den Suchbegriff in Seriennamen.
 */
@Composable
fun SectionedItems(
    app: AppState,
    rows: List<Pair<Section, List<Item>>>,
    modifier: Modifier = Modifier,
    query: String? = null,
    onOpen: () -> Unit = {},
) {
    var filter by remember { mutableStateOf<Section?>(null) }
    val shown = rows.filter { filter == null || it.first == filter }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item(key = "filter") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Alle ${rows.sumOf { it.second.size }}", filter == null, { filter = null }, small = true) }
                rows.forEach { (s, list) -> item { Chip("${s.title} ${list.size}", filter == s, { filter = s }, small = true) } }
            }
        }
        shown.forEach { (section, list) ->
            item(key = "title:${section.name}") {
                SectionHeader(section.title, onAll = if (filter == null && rows.size > 1) ({ filter = section }) else null)
            }
            val keys = stableKeys(list)
            when (section) {
                Section.LIVE -> itemsIndexed(list, key = { i, _ -> "live:${keys[i]}" }) { i, item ->
                    ChannelRow(app, item, number = item.number, selected = false, onClick = { onOpen(); app.open(list, i) })
                }
                Section.MOVIES -> item(key = "row:${section.name}") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(list, key = { i, _ -> keys[i] }) { i, item ->
                            PosterCard(item, item.favKey in app.favorites, { onOpen(); app.open(list, i) }, onLongClick = { ContextMenu.show(item) })
                        }
                    }
                }
                Section.SERIES -> itemsIndexed(list, key = { i, _ -> "series:${keys[i]}" }) { i, item ->
                    SeriesResultRow(item, query) { onOpen(); app.open(list, i) }
                }
            }
        }
    }
}

/** Serie als Zeile: Bild 64×40, Name (Suchbegriff markiert), Kennzeile, Pfeil. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesResultRow(item: Item, query: String?, onClick: () -> Unit) {
    val name = remember(item.name) { splitProviderTag(item.name).name }
    Row(
        Modifier.fillMaxWidth().combinedClickable(remember { MutableInteractionSource() }, null, role = Role.Button, onLongClick = { ContextMenu.show(item) }, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp, 40.dp).clip(Corner).background(Surface1)) {
            if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(highlight(name, query), color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (seriesFacts(item) ?: posterFacts(item))?.let { SmallText(it, maxLines = 1) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(16.dp))
    }
}

/** Suchbegriff im Namen markieren: fett und mit rotem Unterstrich (Entwurf „10b“). */
private fun highlight(text: String, query: String?): AnnotatedString {
    val q = query?.trim().orEmpty()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
        append(text.substring(at, at + q.length))
        pop()
        append(text.substring(at + q.length))
    }
}
