package de.tvplayer.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import de.tvplayer.shared.AppState
import de.tvplayer.shared.data.Item

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
            last?.let { l -> seasons.indexOfFirst { s -> s.episodes.any { it.url == l.item.url } }.takeIf { it >= 0 }?.let { seasonIndex = it } }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Hintergrundbild
        if (series.logo != null) {
            AsyncImage(series.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).fillMaxHeight(0.7f).alpha(0.45f))
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.4f to Bg.copy(alpha = 0.9f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.65f to Bg)))

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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
                        val target = last?.item?.let { l -> seasons.flatMap { it.episodes }.indexOfFirst { it.url == l.url } } ?: -1
                        if (season != null) {
                            PrimaryButton(if (target >= 0) "Weiterschauen" else "Abspielen", Icons.Rounded.PlayArrow) {
                                if (target >= 0) {
                                    val all = seasons.flatMap { it.episodes }
                                    val s = seasons.first { s -> s.episodes.any { it.url == all[target].url } }
                                    app.open(s.episodes, s.episodes.indexOfFirst { it.url == all[target].url })
                                } else {
                                    app.open(season.episodes, 0)
                                }
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
                    val gridState = rememberLazyGridState()
                    Box(Modifier.weight(1f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(230.dp),
                            state = gridState,
                            contentPadding = PaddingValues(start = 40.dp, end = 48.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            itemsIndexed(season.episodes) { i, ep ->
                                Column {
                                    MediaCard(ep.copy(name = "${i + 1}. ${ep.name}"), false, app.progressOf(ep), { app.open(season.episodes, i) }, null)
                                }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp))
                    }
                }
            }
        }
    }
}
