package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import kotlinx.coroutines.delay

/** Serienansicht: großes Titelbild, Staffeln als Reiter, Folgen als Kacheln mit Fortschritt. */
@Composable
fun SeriesScreen(vm: AppState, series: Item) {
    val seasons = vm.seasons
    var seasonIndex by rememberSaveable(series.id) { mutableIntStateOf(0) }
    var focusEpisode by rememberSaveable(series.id) { mutableIntStateOf(0) }
    var jumped by rememberSaveable(series.id) { mutableStateOf(false) }
    val episodeFocus = remember { FocusRequester() }
    val episodeState = rememberLazyListState()
    val season = seasons.getOrNull(seasonIndex)

    // Beim ersten Öffnen zur zuletzt gesehenen Folge springen.
    LaunchedEffect(seasons) {
        if (seasons.isEmpty()) return@LaunchedEffect
        if (!jumped) {
            jumped = true
            vm.lastEpisodeOf(series)?.let { last ->
                seasons.forEachIndexed { si, s ->
                    val ei = s.episodes.indexOfFirst { it.url == last.item.url }
                    if (ei >= 0) { seasonIndex = si; focusEpisode = ei }
                }
            }
        }
        delay(100)
        runCatching { episodeState.scrollToItem(focusEpisode) }
        delay(50)
        runCatching { episodeFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(series.logo)
        Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
            Column(Modifier.padding(horizontal = 64.dp)) {
                Kicker("Serie", Signal)
                Headline(series.name, 42.sp, Modifier.width(760.dp).padding(top = 6.dp), maxLines = 2)
                if (seasons.isNotEmpty()) {
                    val count = seasons.sumOf { it.episodes.size }
                    Text(
                        "${seasons.size} ${if (seasons.size == 1) "Staffel" else "Staffeln"} · $count Folgen",
                        color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            when {
                vm.seasonsLoading -> Box(Modifier.padding(64.dp)) { Spinner() }
                vm.seasonsError != null -> Text(vm.seasonsError.orEmpty(), color = Danger, fontSize = 18.sp, modifier = Modifier.padding(64.dp))
                else -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 60.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(seasons) { i, s ->
                            SeasonChip("Staffel ${s.number}", selected = i == seasonIndex) {
                                if (seasonIndex != i) { seasonIndex = i; focusEpisode = 0 }
                            }
                        }
                    }
                    if (season != null) {
                        LazyRow(
                            state = episodeState,
                            contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 16.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            itemsIndexed(season.episodes) { i, ep ->
                                EpisodeCard(
                                    number = i + 1,
                                    episode = ep,
                                    progress = vm.progressOf(ep),
                                    modifier = (if (i == focusEpisode) Modifier.focusRequester(episodeFocus) else Modifier).testTag("episode:$i"),
                                    onFocus = { focusEpisode = i },
                                    onClick = { vm.open(season.episodes, i) },
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
private fun SeasonChip(text: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Signal else Surface2,
            contentColor = if (selected) OnSignal else TextColor,
            focusedContainerColor = FocusBg,
            focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp))
    }
}

@Composable
private fun EpisodeCard(number: Int, episode: Item, progress: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(280.dp)) {
        PosterCard(
            item = episode, favorite = false, progress = progress, onFocus = onFocus,
            onClick = onClick, onLongClick = {}, modifier = modifier, wide = true,
        )
        Text("Folge ${number.toString().padStart(2, '0')}", color = Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Text(episode.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        if (progress > 0f) Text("Weiterschauen", color = Signal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}
