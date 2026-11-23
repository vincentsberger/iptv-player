package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import de.vincentsberger.iptvplayer.shared.episodeMeta
import de.vincentsberger.iptvplayer.shared.episodePosition
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item

@Composable
fun SeriesScreen(app: AppState, series: Item) {
    val seasons = app.seasons
    var seasonIndex by remember(series.id) { mutableIntStateOf(0) }
    var jumped by remember(series.id) { mutableStateOf(false) }
    val season = seasons.getOrNull(seasonIndex)
    val last = app.lastEpisodeOf(series)

    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && !jumped) {
            jumped = true
            episodePosition(seasons, last?.item?.url)?.let { seasonIndex = it.season }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {

        Column(Modifier.fillMaxSize()) {
            IconButton({ app.back() }, Modifier.padding(16.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = TextColor) }
            Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.Bottom) {
                if (series.logo != null) {
                    AsyncImage(series.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp, 222.dp).clip(Corner))
                }
                Column(Modifier.padding(start = 28.dp).weight(1f)) {
                    Kicker("Serie", Signal)
                    Headline(series.name, 44.sp, Modifier.padding(top = 4.dp), maxLines = 2)
                    if (seasons.isNotEmpty()) {
                        Text("${seasons.size} ${if (seasons.size == 1) "Staffel" else "Staffeln"} · ${seasons.sumOf { it.episodes.size }} Folgen", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    app.seriesInfoFor(series)?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                        Text(plot, color = TextColor, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp).widthIn(max = 720.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
                        val target = episodePosition(seasons, last?.item?.url)
                        if (season != null) {
                            PrimaryButton(if (target != null) "Weiterschauen" else "Abspielen", Icons.Rounded.PlayArrow) {
                                if (target != null) app.open(seasons[target.season].episodes, target.episode)
                                else app.open(season.episodes, 0)
                            }
                        }
                        val fav = series.favKey in app.favorites
                        SecondaryButton(if (fav) "Favorit" else "Merken", if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder) { app.toggleFavorite(series) }
                    }
                }
            }

            when {
                app.seasonsLoading -> Loading("Folgen werden geladen …")
                app.seasonsError != null -> EmptyView("Keine Folgen", app.seasonsError.orEmpty(), Icons.Rounded.VideoLibrary)
                season != null -> {
                    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(seasons) { i, s ->
                            FilterChip(
                                selected = i == seasonIndex,
                                onClick = { seasonIndex = i },
                                label = { Text("Staffel ${s.number}") },
                                shape = Corner,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Signal, selectedLabelColor = OnSignal, labelColor = TextColor),
                                border = FilterChipDefaults.filterChipBorder(true, i == seasonIndex, borderColor = Line, selectedBorderColor = Signal),
                            )
                        }
                    }
                    val listState = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        // Folgenliste wie bei Streamingdiensten: Vorschaubild, Titel, Dauer, kurze Handlung.
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 28.dp, end = 48.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            itemsIndexed(season.episodes, key = { _, ep -> ep.url }) { i, ep ->
                                EpisodeRow(i + 1, ep, app.episodeDetail(ep), app.progressOf(ep)) { app.open(season.episodes, i) }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp))
                    }
                }
            }
        }
    }
}

/** Eine Folge in der Liste: Bild (mit Fortschritt) links, rechts Nummer + Titel, Dauer/Restzeit und Handlung. */
@Composable
private fun EpisodeRow(number: Int, episode: Item, detail: EpisodeDetail?, progress: Float, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.widthIn(max = 1100.dp).fillMaxWidth().clip(Corner)
            .background(if (hovered) Surface1 else Bg)
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .pointerHoverIcon(PointerIcon.Hand).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", color = Muted, fontSize = 22.sp, modifier = Modifier.width(40.dp))
        Box(Modifier.size(224.dp, 126.dp).clip(Corner).background(Surface2)) {
            if (episode.logo != null) AsyncImage(episode.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (hovered) Icon(Icons.Rounded.PlayArrow, "Abspielen", tint = TextColor, modifier = Modifier.align(Alignment.Center).size(48.dp))
            if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Column(Modifier.padding(start = 20.dp).weight(1f)) {
            Text(episode.name, color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episodeMeta(detail?.durationSecs, progress)?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            detail?.plot?.let {
                Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
