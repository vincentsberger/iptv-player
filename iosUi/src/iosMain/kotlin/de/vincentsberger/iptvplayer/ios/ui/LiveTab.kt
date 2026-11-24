package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.design.BackBar
import de.vincentsberger.iptvplayer.ios.ui.design.ChannelRowsSkeleton
import de.vincentsberger.iptvplayer.ios.ui.design.GroupLabel
import de.vincentsberger.iptvplayer.ios.ui.design.H2
import de.vincentsberger.iptvplayer.ios.ui.design.IconAction
import de.vincentsberger.iptvplayer.ios.ui.design.MonoText
import de.vincentsberger.iptvplayer.ios.ui.design.SearchField
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.TitleBar
import de.vincentsberger.iptvplayer.ios.ui.design.groupSegment
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.filterChannels
import de.vincentsberger.iptvplayer.shared.formatCount

/**
 * Live TV in zwei Stufen, wie in iOS-Apps üblich: erst die Kategorien als ruhige Liste (Entwurf „4“), Tippen öffnet
 * die Sender der Kategorie (Entwurf „5“), Zurück (Pfeil oder Wischen vom Rand) führt wieder zur Liste.
 */
@Composable
fun LiveTab(app: AppState) {
    val live = app.section(Section.LIVE)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        when {
            live.loading && live.items.isEmpty() -> ChannelRowsSkeleton()
            live.error != null && live.items.isEmpty() -> EmptyState(null, "Live TV lässt sich nicht laden", live.error) {
                SecondaryButton("Erneut versuchen", icon = Icons.Rounded.Refresh) { app.reloadAll() }
            }
            LiveNav.showChannels -> ChannelList(app, onBack = { LiveNav.showChannels = false; LiveNav.categoryQuery = "" })
            else -> CategoryList(app, onOpen = { app.liveCategory = it; LiveNav.showChannels = true })
        }
    }
}

/** Ob die Senderliste offen ist und was gesucht wird – überlebt das Öffnen des Players (Live TV verlässt dabei die Komposition). */
internal object LiveNav {
    var showChannels by mutableStateOf(false)
    var query by mutableStateOf("")
    var categoryQuery by mutableStateOf("")
}

/** „nach Nummer“ / „nach Name“ – wie die Listen gerade sortiert sind. */
private fun sortLabel(app: AppState) = if (app.sortAz) "nach Name" else "nach Nummer"

private data class CategoryEntry(val id: String, val icon: ImageVector?, val tag: String?, val label: String, val count: Int, val hideable: Boolean)

@Composable
private fun CategoryList(app: AppState, onOpen: (String) -> Unit) {
    val live = app.section(Section.LIVE)
    val pinned = listOf(
        CategoryEntry(AppState.LIVE_ALL, Icons.Rounded.LiveTv, null, "Alle Sender", live.items.size, false),
        CategoryEntry(AppState.LIVE_FAV, Icons.Rounded.StarBorder, null, "Favoriten", app.liveChannelsOf(AppState.LIVE_FAV).size, false),
        CategoryEntry(AppState.LIVE_RECENT, Icons.Rounded.History, null, "Zuletzt gesehen", app.recentChannels.size, false),
    )
    val categories = live.categories.map { c ->
        val t = splitCategoryName(c.name)
        CategoryEntry(c.id, null, t.tag, t.name, live.byCategory[c.id]?.size ?: 0, true)
    }
    var hide by remember { mutableStateOf<CategoryEntry?>(null) }
    val state = rememberLazyListState()
    // Zuletzt geöffnete Kategorie in Sichtweite bringen (Index: 3 Sammelzeilen + Überschrift davor).
    LaunchedEffect(Unit) { categories.indexOfFirst { it.id == app.liveCategory }.takeIf { it > 3 }?.let { state.scrollToItem(it + 2) } }

    TitleBar("Live TV") {
        IconAction(Icons.AutoMirrored.Rounded.Sort, "Sortierung: ${sortLabel(app)}", {
            app.sortAz = !app.sortAz
            Feedback.show(if (app.sortAz) "Sortiert nach Name" else "Sortiert nach Nummer", Icons.AutoMirrored.Rounded.Sort)
        })
    }
    SearchField(LiveNav.query, { LiveNav.query = it }, "Sender suchen", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))

    if (LiveNav.query.isNotBlank()) {
        val results = remember(LiveNav.query, live.items) { filterChannels(live.items, LiveNav.query) }
        ChannelRows(app, results, emptyText = "Kein Sender mit „${LiveNav.query.trim()}“")
        return
    }
    PullToReload(app, Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        categoryGroup(pinned, onOpen, onHide = {})
        item(key = "label") { GroupLabel("Kategorien") }
        categoryGroup(categories, onOpen, onHide = { hide = it })
    } }
    hide?.let { e ->
        HideCategoryDialog(e.label, onDismiss = { hide = null }) {
            if (app.liveCategory == e.id) app.liveCategory = AppState.LIVE_ALL
            app.toggleHidden(Section.LIVE, e.id)
            Feedback.show("Kategorie ausgeblendet", Icons.Rounded.VisibilityOff, undo = { app.setHidden(Section.LIVE, e.id, false) })
        }
    }
}

/** Kategorien als Gruppe (Entwurf .grp/.li): Symbol oder Flagge, Name, Anzahl, Pfeil; langes Drücken blendet aus. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.categoryGroup(entries: List<CategoryEntry>, onOpen: (String) -> Unit, onHide: (CategoryEntry) -> Unit) {
    itemsIndexed(entries, key = { _, e -> "cat:${e.id}" }) { i, e ->
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().groupSegment(first = i == 0, last = i == entries.lastIndex)
                .combinedClickable(remember { MutableInteractionSource() }, null, role = Role.Button, onLongClick = if (e.hideable) ({ onHide(e) }) else null) { onOpen(e.id) }
                .heightIn(min = 50.dp).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(22.dp), Alignment.Center) {
                when {
                    e.icon != null -> Icon(e.icon, null, tint = Muted, modifier = Modifier.size(20.dp))
                    e.tag != null -> FlagOrTag(e.tag)
                }
            }
            Text(e.label, Modifier.weight(1f), color = TextColor, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MonoText(formatCount(e.count), color = Muted, size = 14.sp)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Senderzeilen (Kategorie oder Suchtreffer); Wischen nach links setzt/entfernt den Favoriten. */
@Composable
private fun ChannelRows(app: AppState, channels: List<Item>, emptyText: String) {
    if (channels.isEmpty()) {
        SmallText(emptyText, Modifier.padding(16.dp))
        return
    }
    val listState = rememberLazyListState()
    PullToReload(app, Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
        // M3U-Listen führen denselben Stream manchmal mehrfach – doppelte Schlüssel ließen Compose abstürzen.
        val keys = stableKeys(channels)
        itemsIndexed(channels, key = { i, _ -> keys[i] }) { i, item ->
            val fav = item.favKey in app.favorites
            SwipeAction(if (fav) "Entfernen" else "Favorit", if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star, Signal, onSwipe = { toggleFavoriteWithFeedback(app, item) }) {
                ChannelRow(app, item, number = item.number ?: (i + 1), selected = false, onClick = { app.open(channels, i) })
            }
        }
    } }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelList(app: AppState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val category = app.section(Section.LIVE).categories.firstOrNull { it.id == app.liveCategory }?.let { splitCategoryName(it.name) }
    val title = when (app.liveCategory) {
        AppState.LIVE_ALL -> "Alle Sender"
        AppState.LIVE_FAV -> "Favoriten"
        AppState.LIVE_RECENT -> "Zuletzt gesehen"
        else -> category?.name.orEmpty()
    }
    val all = app.liveChannels
    var searching by remember { mutableStateOf(LiveNav.categoryQuery.isNotEmpty()) }
    val channels = remember(all, LiveNav.categoryQuery) { filterChannels(all, LiveNav.categoryQuery) }

    BackBar("Live TV", onBack) {
        IconAction(Icons.Rounded.Search, "In Kategorie suchen", { searching = !searching; if (!searching) LiveNav.categoryQuery = "" })
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            category?.tag?.let { FlagOrTag(it) }
            H2(title, maxLines = 1)
        }
        SmallText("${formatCount(all.size)} Sender · ${sortLabel(app)}", Modifier.padding(top = 4.dp))
        if (searching) SearchField(LiveNav.categoryQuery, { LiveNav.categoryQuery = it }, "In „$title“ suchen", Modifier.padding(top = 10.dp))
    }
    HorizontalDivider(color = Line, thickness = 1.dp)
    if (all.isEmpty()) {
        EmptyState(
            null,
            when (app.liveCategory) {
                AppState.LIVE_FAV -> "Noch keine Favoriten"
                AppState.LIVE_RECENT -> "Noch keine Sender gesehen"
                else -> "Keine Sender in dieser Kategorie"
            },
            if (app.liveCategory == AppState.LIVE_FAV) "Stern antippen oder lange auf einen Sender drücken, um ihn zu merken." else null,
        )
        return
    }
    ChannelRows(app, channels, emptyText = "Kein Sender mit „${LiveNav.categoryQuery.trim()}“")
}
