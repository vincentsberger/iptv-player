package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.ui.graphics.Color
import de.vincentsberger.iptvplayer.ios.ui.design.ActionGrid
import de.vincentsberger.iptvplayer.ios.ui.design.AppButton
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonSize
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonStyle
import de.vincentsberger.iptvplayer.ios.ui.design.Chip
import de.vincentsberger.iptvplayer.ios.ui.design.DetailHeader
import de.vincentsberger.iptvplayer.ios.ui.design.H1
import de.vincentsberger.iptvplayer.ios.ui.design.MetaLine
import de.vincentsberger.iptvplayer.ios.ui.design.PosterCard
import de.vincentsberger.iptvplayer.ios.ui.design.ProgressBar
import de.vincentsberger.iptvplayer.ios.ui.design.SectionHeader
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.TinyText
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.remainingLabel
import de.vincentsberger.iptvplayer.shared.trailerUrl
import de.vincentsberger.iptvplayer.shared.versionLabel
import de.vincentsberger.iptvplayer.shared.seriesFacts
import androidx.compose.material.icons.rounded.DownloadForOffline
import de.vincentsberger.iptvplayer.shared.downloadStartMessage
import de.vincentsberger.iptvplayer.shared.DownloadStartResult
import de.vincentsberger.iptvplayer.shared.DownloadRules
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.data.stableKeys
import de.vincentsberger.iptvplayer.shared.episodeMeta
import de.vincentsberger.iptvplayer.shared.episodePosition
import de.vincentsberger.iptvplayer.shared.movieFacts
import de.vincentsberger.iptvplayer.shared.playLabel

/**
 * Film-Detailseite (Entwurf „8“): Hintergrundbild als Kopf, Kennzeile, Versionswahl bei mehreren Quellen, Abspielen
 * bzw. Weiterschauen mit Fortschritt, Merken/Herunterladen/Trailer, Handlung, Mitwirkende, „Ähnliche Titel“.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieScreen(app: AppState, movie: Item) {
    BackHandler { app.back() }
    LaunchedEffect(movie.url) { app.requestMediaInfo(movie) }
    val info = app.mediaInfoFor(movie)
    val sources = remember(movie.url) { app.sourcesOf(movie) }
    var version by remember(movie.url) { mutableIntStateOf(0) }
    val chosen = sources.getOrElse(version) { movie }
    val resumeAt = app.resumePositionOf(chosen)
    val entry = app.history.firstOrNull { it.item.url == chosen.url && it.canResume }
    val tagged = remember(movie.name) { splitProviderTag(movie.name) }
    var message by remember(movie.url) { mutableStateOf<String?>(null) }
    val headerHeight = 270.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
        Box {
            DetailHeader(info?.backdrop ?: movie.logo, headerHeight, onBack = app::back)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = headerHeight - 62.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    de.vincentsberger.iptvplayer.ios.ui.design.Kicker("Film")
                    H1(tagged.name, maxLines = 3)
                    info?.originalTitle?.let { SmallText(it) }
                }
                MetaLine(
                    listOfNotNull(
                        tagged.year ?: info?.year, info?.durationSecs?.let(::durationText), info?.ratingLabel?.let { "★ $it" },
                        info?.genresLabel ?: movie.genre, info?.fskLabel,
                    ),
                    badges = splitProviderTag(chosen.name).badges,
                )
                if (sources.size > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallText("Version")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEachIndexed { i, src -> Chip(versionLabel(src), i == version, { version = i }, small = true) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppButton(playLabel(resumeAt), { app.play(listOf(chosen), 0) }, Modifier.fillMaxWidth(), ButtonStyle.Primary, icon = Icons.Rounded.PlayArrow)
                    if (entry != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar(entry.progress, Modifier.weight(1f))
                        remainingLabel(entry)?.let { TinyText(it.replaceFirstChar { c -> c.lowercase() }) }
                    }
                    if (resumeAt > 0) AppButton("Von vorn", { app.play(listOf(chosen), 0, fromStart = true) }, Modifier.fillMaxWidth(), size = ButtonSize.Small, icon = Icons.Rounded.Replay)
                }
                val fav = movie.favKey in app.favorites
                ActionGrid(
                    listOfNotNull(
                        Triple(if (fav) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (fav) "Gemerkt" else "Merken") { toggleFavoriteWithFeedback(app, movie) },
                        downloadAction(chosen) { message = it },
                        trailerUrl(info?.trailer ?: movie.trailer)?.let { url -> Triple(Icons.Rounded.Theaters, "Trailer") { openExternalUrl(url) } },
                    ),
                )
                message?.let { SmallText(it) }
                info?.plot?.let { Text(it, color = Color(0xFFC9CDD2), fontSize = 13.sp, lineHeight = 19.sp) }
                credits(info)?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp) }
                if (info == null) SmallText("Infos werden geladen …")
            }
        }
        val similar = remember(movie.url, info) { app.similarTitles(movie, info) }
        if (similar.isNotEmpty()) {
            SectionHeader("Ähnliche Titel")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(similar, key = { i, it -> "$i:${it.favKey}" }) { i, item ->
                    PosterCard(item, item.favKey in app.favorites, { app.open(similar, i) }, onLongClick = { toggleFavoriteWithFeedback(app, item) })
                }
            }
        }
    }
}

/** „Regie Johanna Brandt · Mit Lukas Wendt, Mira Okafor“ – Beschriftungen gedämpft; nichts davon → null. */
private fun credits(info: MediaInfo?): androidx.compose.ui.text.AnnotatedString? {
    if (info == null || (info.director == null && info.cast.isEmpty())) return null
    return androidx.compose.ui.text.buildAnnotatedString {
        val muted = androidx.compose.ui.text.SpanStyle(color = Muted)
        val text = androidx.compose.ui.text.SpanStyle(color = TextColor)
        info.director?.let { pushStyle(muted); append("Regie "); pop(); pushStyle(text); append(it); pop() }
        if (info.cast.isNotEmpty()) {
            if (info.director != null) { pushStyle(muted); append(" · "); pop() }
            pushStyle(muted); append("Mit "); pop(); pushStyle(text); append(info.cast.take(5).joinToString(", ")); pop()
        }
    }
}

/**
 * Serien-Detailseite: Kopf mit Poster, Staffelzahl, Beschreibung und „Weiterschauen“ (zuletzt gesehene Folge),
 * darunter Staffel-Auswahl und Folgenliste wie bei Streamingdiensten.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesScreen(app: AppState, series: Item) {
    BackHandler { app.back() }
    val seasons = app.seasons
    var seasonIndex by remember(series.id) { mutableIntStateOf(0) }
    var jumped by remember(series.id) { mutableStateOf(false) }
    val last = app.lastEpisodeOf(series)
    val target = episodePosition(seasons, last?.item?.url)
    // Beim Öffnen gleich die Staffel der zuletzt gesehenen Folge zeigen.
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && !jumped) { jumped = true; target?.let { seasonIndex = it.season } }
    }
    val season = seasons.getOrNull(seasonIndex)
    var message by remember(series.id) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Serie", onBack = app::back)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Poster(series.logo, Modifier.width(110.dp))
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Headline(splitProviderTag(series.name).name, 22.sp, maxLines = 3)
                            // Sofort aus der Liste – ohne auf die Detailabfrage zu warten.
                            seriesFacts(series)?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                            if (seasons.isNotEmpty()) Text(
                                "${seasons.size} ${if (seasons.size == 1) "Staffel" else "Staffeln"} · ${seasons.sumOf { it.episodes.size }} Folgen",
                                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (season != null) {
                        PrimaryButton(if (target != null) "Weiterschauen" else "Abspielen", Modifier.fillMaxWidth().padding(top = 16.dp), Icons.Rounded.PlayArrow) {
                            if (target != null) app.open(seasons[target.season].episodes, target.episode) else app.open(season.episodes, 0)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FavoriteButton(app, series)
                        // Ganze Staffel offline: alle Folgen, die noch nicht in den Downloads sind, in die Warteschlange.
                        val loadable = season?.episodes.orEmpty().filter { ep -> DownloadRules.canDownload(ep) && IosDownloads.infoFor(ep.url) == null }
                        if (season != null && loadable.isNotEmpty()) SecondaryButton("Staffel laden", icon = Icons.Rounded.DownloadForOffline) {
                            val results = loadable.map { IosDownloads.start(it) }
                            val ok = results.count { it == DownloadStartResult.STARTED }
                            message = if (ok > 0) "$ok ${if (ok == 1) "Folge wird" else "Folgen werden"} geladen – nacheinander, siehe „Downloads“."
                            else results.firstNotNullOfOrNull { downloadStartMessage(it, IosDownloads.freeBytes()) }
                        }
                    }
                    message?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    app.seriesInfoFor(series)?.plot?.takeIf { it.isNotBlank() }?.let {
                        var open by remember { mutableStateOf(false) }
                        Text(
                            it, color = TextColor, fontSize = 15.sp, lineHeight = 22.sp, maxLines = if (open) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp).clickable { open = !open },
                        )
                    }
                }
            }
            when {
                app.seasonsLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Signal, trackColor = Line) }
                }
                app.seasonsError != null -> item { Text(app.seasonsError.orEmpty(), color = Danger, modifier = Modifier.padding(20.dp)) }
                season != null -> {
                    if (seasons.size > 1) item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(seasons) { i, s ->
                                val on = i == seasonIndex
                                Text(
                                    "Staffel ${s.number}", color = if (on) TextColor else Muted, fontSize = 14.sp,
                                    modifier = Modifier.background(if (on) Surface2 else Surface1, Corner).clickable { seasonIndex = i }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    val keys = stableKeys(season.episodes)
                    itemsIndexed(season.episodes, key = { i, _ -> keys[i] }) { i, ep ->
                        EpisodeRow(
                            i + 1, ep, app.episodeDetail(ep), app.progressOf(ep), current = ep.url == last?.item?.url,
                            trailing = { DownloadButton(ep, compact = true, onMessage = { message = it }) },
                        ) { app.open(season.episodes, i) }
                    }
                }
            }
        }
    }
}

/** Eine Folge: Vorschaubild mit Fortschritt, Nummer + Titel, Dauer/Restzeit, kurze Handlung. */
@Composable
private fun EpisodeRow(
    number: Int, episode: Item, detail: EpisodeDetail?, progress: Float, current: Boolean,
    trailing: @Composable () -> Unit, onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(if (current) Surface1 else Bg).clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(Corner).background(Surface2)) {
            if (!episode.logo.isNullOrBlank()) AsyncImage(episode.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.PlayArrow, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(28.dp))
            if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("$number. ${episode.name}", color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episodeMeta(detail?.durationSecs, progress)?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
            detail?.plot?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
        trailing()
    }
}

@Composable
private fun Poster(url: String?, modifier: Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(Corner).background(Surface1)) {
        Icon(Icons.Rounded.Movie, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(32.dp))
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun FavoriteButton(app: AppState, item: Item) {
    val fav = item.favKey in app.favorites
    SecondaryButton(if (fav) "Favorit" else "Merken", icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder) { toggleFavoriteWithFeedback(app, item) }
}

@Composable
private fun Credit(label: String, text: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Kicker(label)
        Text(text, color = TextColor, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
