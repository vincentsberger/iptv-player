package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.ui.zIndex

import de.vincentsberger.iptvplayer.app.download.Downloads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import de.vincentsberger.iptvplayer.shared.episodeMeta
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
import androidx.compose.foundation.lazy.LazyColumn
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
    val menu = LocalMenu.current
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
        KeyHintBar(Modifier.align(Alignment.BottomStart).zIndex(1f))
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
                vm.seriesInfoFor(series)?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Text(plot, color = TextColor, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp).width(760.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

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
                        // Ganze Staffel für unterwegs laden (lädt nacheinander, eine Folge nach der anderen).
                        if (season != null && season.episodes.any(Downloads::canDownload)) item {
                            val eps = season.episodes.filter(Downloads::canDownload)
                            val loaded = eps.count { Downloads.infoFor(it.url) != null }
                            // Bewusst TvButton statt SeasonChip: der Chip wählt schon beim Fokussieren aus.
                            TvButton(
                                if (loaded == eps.size) "Staffel heruntergeladen" else "Staffel herunterladen",
                                Modifier.testTag("season:download"),
                                icon = Icons.Rounded.Download,
                                selected = loaded == eps.size,
                            ) { eps.forEach { Downloads.start(it) } }
                        }
                    }
                    if (season != null) {
                        // Folgenliste wie bei Streamingdiensten: ▲▼ durch die Folgen, Bild + Titel + Dauer + Handlung.
                        LazyColumn(
                            state = episodeState,
                            contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            itemsIndexed(season.episodes) { i, ep ->
                                EpisodeRow(
                                    number = i + 1,
                                    episode = ep,
                                    detail = vm.episodeDetail(ep),
                                    progress = vm.progressOf(ep),
                                    modifier = (if (i == focusEpisode) Modifier.focusRequester(episodeFocus) else Modifier).testTag("episode:$i"),
                                    onFocus = { focusEpisode = i },
                                    onClick = { vm.open(season.episodes, i) },
                                    onOptions = { menu.current = itemMenu(vm, ep) },
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
private fun EpisodeRow(
    number: Int, episode: Item, detail: EpisodeDetail?, progress: Float, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onOptions: () -> Unit,
) {
    Row(Modifier.width(1150.dp), verticalAlignment = Alignment.CenterVertically) {
        PosterCard(
            item = episode, favorite = false, progress = progress, onFocus = onFocus,
            onClick = onClick, onLongClick = onOptions, modifier = modifier, wide = true,
        )
        Column(Modifier.padding(start = 28.dp).weight(1f)) {
            Text("Folge ${number.toString().padStart(2, '0')}", color = Muted, fontFamily = Mono, fontSize = 15.sp)
            Text(episode.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            episodeMeta(detail?.durationSecs, progress)?.let {
                Text(it, color = if (progress > 0f) Signal else Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            } ?: run { if (progress > 0f) Text("Weiterschauen", color = Signal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
            detail?.plot?.let {
                Text(it, color = Muted, fontSize = 16.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
