package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.design.GlassChip
import de.vincentsberger.iptvplayer.ios.ui.design.GroupRow
import de.vincentsberger.iptvplayer.ios.ui.design.H2
import de.vincentsberger.iptvplayer.ios.ui.design.H3
import de.vincentsberger.iptvplayer.ios.ui.design.Hero
import de.vincentsberger.iptvplayer.ios.ui.design.LandscapeCard
import de.vincentsberger.iptvplayer.ios.ui.design.PosterCard
import de.vincentsberger.iptvplayer.ios.ui.design.PosterRowsSkeleton
import de.vincentsberger.iptvplayer.ios.ui.design.SectionHeader
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.groupSegment
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.posterFacts
import de.vincentsberger.iptvplayer.shared.remainingLabel
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitCategoryName
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys

/**
 * Gewählte Kategorie je Bereich (null = Übersicht mit Titelbild und Reihen). Lebt außerhalb der Komposition, damit
 * sie das Öffnen des Players übersteht; „Alle“ auf der Startseite setzt sie direkt (siehe HomeTab).
 */
internal object BrowseNav {
    val selected = mutableStateMapOf<Section, String?>()
}

/**
 * Filme bzw. Serien (Entwurf „6“/„7“): ohne Auswahl Titelbild (neuester Titel) und Reihen – Weiterschauen, Neu, je
 * Genre. Oben „Genre ▾“ und „Kategorien ▾“; mit Auswahl ein Poster-Raster dieser Kategorie (Chip „Thriller ×“).
 * Tippen öffnet die Detailseite, bei „Weiterschauen“ geht es direkt weiter; langes Drücken merkt den Titel.
 */
@Composable
fun BrowseTab(app: AppState, section: Section) {
    val state = app.section(section)
    when {
        state.loading && state.items.isEmpty() -> PosterRowsSkeleton()
        state.error != null && state.items.isEmpty() -> Centered {
            Text(state.error.orEmpty(), color = Danger)
            SecondaryButton("Erneut versuchen", Modifier.padding(top = 12.dp), Icons.Rounded.Refresh) { app.reloadAll() }
        }
        state.items.isEmpty() -> Centered { Text("Dein Anbieter stellt hier keine ${section.title} bereit.", color = Muted) }
        else -> {
            val selected = BrowseNav.selected[section]?.takeIf { key -> app.browseCategories(section).any { it.id == key } }
            if (selected == null) Overview(app, section) else Catalog(app, section, selected)
        }
    }
}

private enum class Picker { GENRE, CATEGORY }

/** Kopf über dem Titelbild bzw. über dem Raster: Bereichsname und die beiden Auswahl-Chips. */
@Composable
private fun TopBar(app: AppState, section: Section, selected: String?, modifier: Modifier = Modifier) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    var hide by remember { mutableStateOf<String?>(null) }
    val genres = app.genreCategories(section)
    val isGenre = selected?.startsWith(AppState.BROWSE_GENRE) == true
    val name = selected?.let { key -> app.browseCategories(section).firstOrNull { it.id == key }?.let { categoryLabel(it) } }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        H2(section.title, Modifier.weight(1f), maxLines = 1)
        if (genres.isNotEmpty()) {
            if (isGenre) GlassChip(name.orEmpty(), { BrowseNav.selected[section] = null }, Icons.Rounded.Close, active = true)
            else GlassChip("Genre", { picker = Picker.GENRE }, Icons.Rounded.KeyboardArrowDown)
        }
        if (selected != null && !isGenre) GlassChip(name.orEmpty(), { BrowseNav.selected[section] = null }, Icons.Rounded.Close, active = true)
        else GlassChip("Kategorien", { picker = Picker.CATEGORY }, Icons.Rounded.KeyboardArrowDown)
    }
    picker?.let { p ->
        val options = if (p == Picker.GENRE) genres else app.browseCategories(section).filterNot { it.id.startsWith(AppState.BROWSE_GENRE) }
        val providerIds = app.section(section).categories.mapTo(HashSet()) { it.id }
        PickerSheet(
            if (p == Picker.GENRE) "Genre" else "Kategorien", options, onDismiss = { picker = null },
            canHide = { it in providerIds }, onHide = { hide = it },
        ) { key ->
            picker = null
            BrowseNav.selected[section] = key
        }
    }
    hide?.let { id ->
        HideCategoryDialog(splitCategoryName(app.section(section).categories.firstOrNull { it.id == id }?.name.orEmpty()).name, onDismiss = { hide = null }) {
            app.toggleHidden(section, id)
            Feedback.show("Kategorie ausgeblendet", Icons.Rounded.VisibilityOff, undo = { app.setHidden(section, id, false) })
        }
    }
}

/** „Genre · Thriller“ → „Thriller“, Anbieter-Kategorien ohne Länderkürzel. */
private fun categoryLabel(c: Category): String = splitCategoryName(c.name.removePrefix("Genre · ")).name

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PickerSheet(
    title: String, options: List<Category>, onDismiss: () -> Unit,
    canHide: (String) -> Boolean, onHide: (String) -> Unit, onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = Bg) {
        H3(title, Modifier.padding(start = 20.dp, bottom = 10.dp))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            itemsIndexed(options, key = { _, c -> c.id }) { i, c ->
                val t = splitCategoryName(c.name.removePrefix("Genre · "))
                // Langes Drücken blendet eine Anbieter-Kategorie aus (wie bisher die Chips).
                GroupRow(
                    t.name,
                    Modifier.groupSegment(first = i == 0, last = i == options.lastIndex)
                        .combinedClickable(onLongClick = if (canHide(c.id)) ({ onHide(c.id) }) else null) { onPick(c.id) },
                    leading = t.tag?.let { tag -> { FlagOrTag(tag) } },
                )
            }
        }
    }
}

/** Übersicht: Titelbild, dann die Reihen. Die Kopfzeile liegt über dem Bild (wie im Entwurf). */
@Composable
private fun Overview(app: AppState, section: Section) {
    val hero = app.heroItem(section)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (hero != null) Box {
            HeroFor(app, hero)
            TopBar(app, section, null, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
        } else TopBar(app, section, null, Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))

        val resume = app.continueWatching(section)
        if (resume.isNotEmpty()) {
            SectionHeader("Weiterschauen", Modifier.padding(top = 0.dp), onAll = { BrowseNav.selected[section] = AppState.BROWSE_CONTINUE })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(resume, key = { i, e -> "$i:${e.item.url}" }) { _, e ->
                    val subtitle = (if (section == Section.SERIES) app.episodeLabel(e.item) else null) ?: remainingLabel(e)
                    LandscapeCard(e.item, splitProviderTag(e.item.name).name, subtitle, e.progress, { app.play(listOf(e.item), 0) }, width = 140.dp, imageHeight = 78.dp)
                }
            }
        }
        PosterRow(app, if (section == Section.SERIES) "Neue Folgen" else "Neu", app.newItemsOf(section)) { BrowseNav.selected[section] = AppState.BROWSE_NEW }
        app.genreRows(section).forEach { (genre, items) ->
            PosterRow(app, genre, items) { BrowseNav.selected[section] = AppState.BROWSE_GENRE + genre }
        }
        if (app.genreRows(section).isEmpty() && app.newItemsOf(section).isEmpty()) {
            PosterRow(app, "Alle ${section.title}", app.section(section).items.take(20)) { BrowseNav.selected[section] = AppState.BROWSE_ALL }
        }
    }
}

/** Titelbild für einen Film (Bild und Dauer aus dem Detailabruf) bzw. eine Serie (Bild aus der Liste). */
@Composable
private fun HeroFor(app: AppState, hero: Item) {
    LaunchedEffect(hero.url, hero.id) { if (hero.section == Section.MOVIES) app.requestMediaInfo(hero) }
    val info = app.mediaInfoFor(hero)
    val tagged = remember(hero.name) { splitProviderTag(hero.name) }
    val genre = hero.genre?.split(',', '/')?.firstOrNull()?.trim() ?: info?.genres?.firstOrNull()
    val meta = listOfNotNull(
        tagged.year ?: info?.year, genre,
        posterFacts(hero)?.split(" · ")?.firstOrNull { it.startsWith("★") },
        info?.durationSecs?.let(::durationText),
    )
    val last = if (hero.section == Section.SERIES) app.lastEpisodeOf(hero) else null
    Hero(
        imageUrl = hero.backdrop ?: info?.backdrop ?: hero.logo,
        kicker = "Neu hinzugefügt", title = tagged.name, meta = meta, badges = tagged.badges,
        primary = when {
            hero.section == Section.MOVIES -> "Abspielen"
            last != null -> "Weiterschauen"
            else -> "Ansehen"
        },
        onPrimary = {
            when {
                hero.section == Section.MOVIES -> app.play(listOf(hero), 0)
                last != null -> app.play(listOf(last.item), 0)
                else -> app.open(listOf(hero), 0)
            }
        },
        onMore = { app.open(listOf(hero), 0) },
    )
}

/** „1 Std. 52 Min.“ bzw. „48 Min.“ */
internal fun durationText(secs: Int): String? {
    if (secs <= 0) return null
    val min = (secs + 30) / 60
    return if (min >= 60) "${min / 60} Std.${if (min % 60 > 0) " ${min % 60} Min." else ""}" else "$min Min."
}

@Composable
private fun PosterRow(app: AppState, title: String, items: List<Item>, onAll: () -> Unit) {
    if (items.isEmpty()) return
    SectionHeader(title, onAll = onAll)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items, key = { i, it -> "$i:${it.favKey}" }) { i, item ->
            PosterCard(item, item.favKey in app.favorites, { app.open(items, i) }, onLongClick = { ContextMenu.show(item) })
        }
    }
}

/** Raster einer gewählten Kategorie (Weiterschauen startet direkt). Ausblenden geht per langem Drücken in der Auswahl. */
@Composable
private fun Catalog(app: AppState, section: Section, selected: String) {
    val items = app.browseItems(section, selected)
    val resume = selected == AppState.BROWSE_CONTINUE
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        TopBar(app, section, selected)
        SmallText("${formatCount(items.size)} Titel", Modifier.padding(start = 16.dp, bottom = 8.dp))
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
            GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { i, _ -> keys[i] }) { i, item ->
                PosterCard(
                    item, item.favKey in app.favorites,
                    onClick = { if (resume) app.play(items, i) else app.open(items, i) },
                    onLongClick = { ContextMenu.show(item) }, width = null,
                )
            }
        } }
    }
}

/** Poster (2:3) mit Fortschritt und Favoriten-Stern, darunter der Titel – für die Suche. */
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

