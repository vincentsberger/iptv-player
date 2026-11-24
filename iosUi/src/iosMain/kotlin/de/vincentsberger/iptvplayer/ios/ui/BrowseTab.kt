package de.vincentsberger.iptvplayer.ios.ui

import de.vincentsberger.iptvplayer.shared.posterFacts
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import androidx.compose.runtime.remember

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys

/**
 * Katalog Filme bzw. Serien: Kategorien als waagerechte Leiste (Weiterschauen, Neu, Favoriten, Alle, Anbieter),
 * darunter ein Raster mit Postern. Tippen öffnet die Detailseite, bei „Weiterschauen“ geht es direkt weiter.
 * Langes Drücken merkt den Titel als Favorit.
 */
@Composable
fun BrowseTab(app: AppState, section: Section) {
    val state = app.section(section)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Headline(section.title, 26.sp, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
        when {
            state.loading && state.items.isEmpty() -> Centered { CircularProgressIndicator(color = Signal, trackColor = Line) }
            state.error != null && state.items.isEmpty() -> Centered {
                Text(state.error.orEmpty(), color = Danger)
                SecondaryButton("Erneut versuchen", Modifier.padding(top = 12.dp), Icons.Rounded.Refresh) { app.reloadAll() }
            }
            state.items.isEmpty() -> Centered { Text("Dein Anbieter stellt hier keine ${section.title} bereit.", color = Muted) }
            else -> Catalog(app, section)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Catalog(app: AppState, section: Section) {
    val cats = app.browseCategories(section)
    var selected by rememberSaveable(section) { mutableStateOf(app.initialBrowseCategory(section)) }
    // Kategorie verschwunden (z. B. letzter Favorit entfernt): zurück auf den Start.
    if (cats.none { it.id == selected }) selected = app.initialBrowseCategory(section)
    LaunchedEffect(selected) { app.rememberBrowseCategory(section, selected) }
    val items = app.browseItems(section, selected)
    val resume = selected == AppState.BROWSE_CONTINUE

    val chipState = rememberLazyListState()
    val providerIds = remember(cats) { app.section(section).categories.mapTo(HashSet()) { it.id } }
    var hide by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { cats.indexOfFirst { it.id == selected }.takeIf { it > 2 }?.let { chipState.scrollToItem(it - 1) } }
    LazyRow(state = chipState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cats, key = { it.id }) { c ->
            val on = c.id == selected
            val t = splitCategoryName(c.name)
            Row(
                Modifier.background(if (on) Surface2 else Surface1, Corner)
                    .combinedClickable(onLongClick = if (c.id in providerIds) ({ hide = c.id }) else null) { selected = c.id }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                t.tag?.let { FlagOrTag(it, Modifier.padding(end = 6.dp)) }
                Text(t.name, color = if (on) TextColor else Muted, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
    hide?.let { id ->
        HideCategoryDialog(splitCategoryName(cats.firstOrNull { it.id == id }?.name.orEmpty()).name, onDismiss = { hide = null }) {
            app.toggleHidden(section, id)
            Feedback.show("Kategorie ausgeblendet – einblenden in den Einstellungen")
        }
    }
    Text("${formatCount(items.size)} Titel", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))

    if (items.isEmpty()) {
        Centered {
            Text(
                if (selected == AppState.BROWSE_FAV) "Noch keine Favoriten – lange auf einen Titel drücken." else "Hier gibt es keine Titel.",
                color = Muted,
            )
        }
        return
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(selected) { gridState.scrollToItem(0) }
    val keys = stableKeys(items)
    PullToReload(app, Modifier.fillMaxSize()) { LazyVerticalGrid(
        GridCells.Adaptive(108.dp), Modifier.fillMaxSize(), gridState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items, key = { i, _ -> keys[i] }) { i, item ->
            PosterTile(
                item, app.progressOf(item), item.favKey in app.favorites,
                onClick = { if (resume) app.play(items, i) else app.open(items, i) },
                onLongClick = { toggleFavoriteWithFeedback(app, item) },
            )
        }
    } }
}

/** Poster (2:3) mit Fortschritt und Favoriten-Stern, darunter der Titel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterTile(item: Item, progress: Float, favorite: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(Corner).background(Surface1)) {
            Icon(Icons.Rounded.Movie, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(32.dp))
            if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (favorite) Icon(Icons.Rounded.Star, "Favorit", tint = Signal, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
            if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Text(
            splitProviderTag(item.name).name, color = TextColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        posterFacts(item)?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, fontFamily = Mono) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}
