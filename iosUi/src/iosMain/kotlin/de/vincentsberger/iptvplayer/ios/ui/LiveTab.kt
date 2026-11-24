package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys

/** Live TV: Kategorien als waagerechte Leiste, darunter die Sender der gewählten Kategorie. */
@Composable
fun LiveTab(app: AppState) {
    val live = app.section(Section.LIVE)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Headline("Live TV", 26.sp, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        CategoryBar(app)
        val channels = app.liveChannels
        val listState = rememberLazyListState()
        LaunchedEffect(app.liveCategory) { listState.scrollToItem(0) }
        when {
            live.loading && channels.isEmpty() -> Centered { CircularProgressIndicator(color = Signal, trackColor = Line) }
            live.error != null && channels.isEmpty() -> Centered {
                Text(live.error.orEmpty(), color = Danger)
                SecondaryButton("Erneut versuchen", Modifier.padding(top = 12.dp), Icons.Rounded.Refresh) { app.reloadAll() }
            }
            channels.isEmpty() -> Centered {
                Text(
                    when (app.liveCategory) {
                        AppState.LIVE_FAV -> "Noch keine Favoriten – lange auf einen Sender drücken."
                        AppState.LIVE_RECENT -> "Noch keine Sender gesehen."
                        else -> "Keine Sender in dieser Kategorie."
                    },
                    color = Muted,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
                // M3U-Listen führen denselben Stream manchmal mehrfach – doppelte Schlüssel ließen Compose abstürzen.
                val keys = stableKeys(channels)
                itemsIndexed(channels, key = { i, _ -> keys[i] }) { i, item ->
                    ChannelRow(app, item, number = item.number ?: (i + 1), selected = false, onClick = { app.open(channels, i) })
                }
            }
        }
    }
}

private data class CategoryChip(val id: String, val tag: String?, val label: String, val hideable: Boolean = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryBar(app: AppState) {
    val chips = buildList {
        add(CategoryChip(AppState.LIVE_ALL, null, "Alle"))
        add(CategoryChip(AppState.LIVE_FAV, null, "Favoriten"))
        add(CategoryChip(AppState.LIVE_RECENT, null, "Zuletzt"))
        app.section(Section.LIVE).categories.forEach { c ->
            val t = splitProviderTag(c.name)
            add(CategoryChip(c.id, t.tag, t.name, hideable = true))
        }
    }
    val selected = app.liveCategory
    val state = rememberLazyListState()
    var hide by remember { mutableStateOf<CategoryChip?>(null) }
    // Gespeicherte Kategorie beim Öffnen sichtbar machen.
    LaunchedEffect(Unit) { chips.indexOfFirst { it.id == selected }.takeIf { it > 2 }?.let { state.scrollToItem(it - 1) } }
    LazyRow(
        state = state, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.id }) { chip ->
            val on = chip.id == selected
            Row(
                Modifier.background(if (on) Surface2 else Surface1, Corner)
                    .combinedClickable(onLongClick = if (chip.hideable) ({ hide = chip }) else null) { app.liveCategory = chip.id }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chip.tag?.let { FlagOrTag(it, Modifier.padding(end = 6.dp)) }
                Text(chip.label, color = if (on) TextColor else Muted, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
    hide?.let { chip ->
        HideCategoryDialog(chip.label, onDismiss = { hide = null }) {
            if (app.liveCategory == chip.id) app.liveCategory = AppState.LIVE_ALL
            app.toggleHidden(Section.LIVE, chip.id)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}
