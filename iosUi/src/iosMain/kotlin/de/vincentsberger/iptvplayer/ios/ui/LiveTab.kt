package de.vincentsberger.iptvplayer.ios.ui

import de.vincentsberger.iptvplayer.ios.ui.design.ChannelRowsSkeleton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.formatCount

/**
 * Live TV in zwei Stufen, wie in iOS-Apps üblich: erst die Kategorien als Liste (mit Flagge und Senderzahl), Tippen
 * öffnet die Sender der Kategorie, Zurück (Pfeil oder Wischen vom Rand) führt wieder zur Liste.
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
            LiveNav.showChannels -> ChannelList(app, onBack = { LiveNav.showChannels = false })
            else -> CategoryList(app, onOpen = { app.liveCategory = it; LiveNav.showChannels = true })
        }
    }
}

/** Ob die Senderliste offen ist – überlebt das Öffnen des Players (Live TV verlässt dabei die Komposition). */
internal object LiveNav {
    var showChannels by mutableStateOf(false)
}

private data class CategoryEntry(val id: String, val icon: ImageVector?, val tag: String?, val label: String, val count: Int, val hideable: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryList(app: AppState, onOpen: (String) -> Unit) {
    val live = app.section(Section.LIVE)
    val entries = buildList {
        add(CategoryEntry(AppState.LIVE_ALL, Icons.Rounded.LiveTv, null, "Alle Sender", live.items.size, false))
        add(CategoryEntry(AppState.LIVE_FAV, Icons.Rounded.Star, null, "Favoriten", app.liveChannelsOf(AppState.LIVE_FAV).size, false))
        add(CategoryEntry(AppState.LIVE_RECENT, Icons.Rounded.History, null, "Zuletzt gesehen", app.recentChannels.size, false))
        live.categories.forEach { c ->
            val t = splitCategoryName(c.name)
            add(CategoryEntry(c.id, null, t.tag, t.name, live.byCategory[c.id]?.size ?: 0, true))
        }
    }
    var hide by remember { mutableStateOf<CategoryEntry?>(null) }
    val state = rememberLazyListState()
    // Zuletzt geöffnete Kategorie in Sichtweite bringen.
    LaunchedEffect(Unit) { entries.indexOfFirst { it.id == app.liveCategory }.takeIf { it > 3 }?.let { state.scrollToItem(it - 2) } }

    Headline("Live TV", 28.sp, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
    PullToReload(app, Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 12.dp)) {
        items(entries, key = { it.id }) { e ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onLongClick = if (e.hideable) ({ hide = e }) else null) { onOpen(e.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(30.dp), Alignment.CenterStart) {
                    when {
                        e.icon != null -> Icon(e.icon, null, tint = if (e.id == AppState.LIVE_FAV) Signal else Muted, modifier = Modifier.size(20.dp))
                        e.tag != null -> FlagOrTag(e.tag)
                    }
                }
                Text(e.label, Modifier.weight(1f), color = TextColor, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatCount(e.count), color = Muted, fontSize = 14.sp, fontFamily = Mono, modifier = Modifier.padding(horizontal = 8.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Line, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(color = Line, thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
        }
    } }
    hide?.let { e ->
        HideCategoryDialog(e.label, onDismiss = { hide = null }) {
            if (app.liveCategory == e.id) app.liveCategory = AppState.LIVE_ALL
            app.toggleHidden(Section.LIVE, e.id)
            Feedback.show("Kategorie ausgeblendet", Icons.Rounded.VisibilityOff, undo = { app.toggleHidden(Section.LIVE, e.id) })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelList(app: AppState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val title = when (app.liveCategory) {
        AppState.LIVE_ALL -> "Alle Sender"
        AppState.LIVE_FAV -> "Favoriten"
        AppState.LIVE_RECENT -> "Zuletzt gesehen"
        else -> splitCategoryName(app.section(Section.LIVE).categories.firstOrNull { it.id == app.liveCategory }?.name.orEmpty()).name
    }
    val channels = app.liveChannels
    ScreenHeader(title, onBack = onBack)
    Text(
        "${formatCount(channels.size)} Sender", color = Muted, fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
    )
    if (channels.isEmpty()) {
        EmptyState(
            null,
            when (app.liveCategory) {
                AppState.LIVE_FAV -> "Noch keine Favoriten"
                AppState.LIVE_RECENT -> "Noch keine Sender gesehen"
                else -> "Keine Sender in dieser Kategorie"
            },
            if (app.liveCategory == AppState.LIVE_FAV) "Lange auf einen Sender drücken, um ihn zu merken." else null,
        )
        return
    }
    val listState = rememberLazyListState()
    PullToReload(app, Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
        // M3U-Listen führen denselben Stream manchmal mehrfach – doppelte Schlüssel ließen Compose abstürzen.
        val keys = stableKeys(channels)
        itemsIndexed(channels, key = { i, _ -> keys[i] }) { i, item ->
            val fav = item.favKey in app.favorites
            // Nach links wischen: Favorit setzen bzw. entfernen.
            SwipeAction(if (fav) "Entfernen" else "Favorit", if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star, Signal, onSwipe = { toggleFavoriteWithFeedback(app, item) }) {
                ChannelRow(app, item, number = item.number ?: (i + 1), selected = false, onClick = { app.open(channels, i) })
            }
        }
    } }
}
