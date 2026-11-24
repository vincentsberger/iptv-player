package de.vincentsberger.iptvplayer.ios.ui

import de.vincentsberger.iptvplayer.ios.ui.design.ChannelRowsSkeleton
import de.vincentsberger.iptvplayer.ios.ui.design.H3
import de.vincentsberger.iptvplayer.ios.ui.design.SeasonTabs
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import de.vincentsberger.iptvplayer.shared.posterFacts
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import de.vincentsberger.iptvplayer.shared.versionLabels
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
    // Angefangene Quelle vorwählen – sonst stünde „Abspielen“ da, obwohl eine andere Quelle zur Hälfte gesehen ist.
    var version by remember(movie.url) { mutableIntStateOf(sources.indexOfFirst { app.resumePositionOf(it) > 0 }.coerceAtLeast(0)) }
    val versionNames = remember(sources) { versionLabels(sources) }
    val chosen = sources.getOrElse(version) { movie }
    val resumeAt = app.resumePositionOf(chosen)
    val entry = app.history.firstOrNull { it.item.url == chosen.url && it.canResume }
    val tagged = remember(movie.name) { splitProviderTag(movie.name) }
    var message by remember(movie.url) { mutableStateOf<String?>(null) }
    val headerHeight = 270.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp)) {
        Box {
            DetailHeader(info?.backdrop ?: movie.logo, headerHeight, onBack = app::back, onMore = { ContextMenu.show(movie, fromDetail = true) })
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
                        versionNames.forEachIndexed { i, label -> Chip(label, i == version, { version = i }, small = true) }
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
                        (trailerUrl(info?.trailer) ?: trailerUrl(movie.trailer))?.let { url -> Triple(Icons.Rounded.Theaters, "Trailer") { openExternalUrl(url) } },
                    ),
                )
                message?.let { SmallText(it) }
                info?.plot?.let { Text(it, color = TextSoft, fontSize = 13.sp, lineHeight = 19.sp) }
                credits(info)?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp) }
                if (info == null) SmallText("Infos werden geladen …")
            }
        }
        val similar = remember(movie.url, info) { app.similarTitles(movie, info) }
        if (similar.isNotEmpty()) {
            SectionHeader("Ähnliche Titel")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(similar, key = { i, it -> "$i:${it.favKey}" }) { i, item ->
                    PosterCard(item, item.favKey in app.favorites, { app.open(similar, i) }, onLongClick = { ContextMenu.show(item) })
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
 * Serien-Detailseite (Entwurf „9“): Kopf sofort aus der Liste (Bild, Kennzeile), „Weiterschauen · S2 F2“ mit Merken
 * und Trailer daneben, Handlung zum Aufklappen; Staffeln als Reiter mit „Staffel laden“, Folgen mit Vorschaubild,
 * Fortschritt und Download-Zustand.
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
    val info = app.seriesInfoFor(series)
    val tagged = remember(series.name) { splitProviderTag(series.name) }
    var message by remember(series.id) { mutableStateOf<String?>(null) }
    val headerHeight = 230.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "head") {
            Box {
                DetailHeader(series.backdrop ?: info?.backdrop ?: series.logo, headerHeight, onBack = app::back, onMore = { ContextMenu.show(series, fromDetail = true) })
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = headerHeight - 54.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    H1(tagged.name, maxLines = 3)
                    MetaLine(
                        listOfNotNull(
                            tagged.year ?: info?.year,
                            seasons.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "Staffel" else "Staffeln"}" },
                            (info?.ratingLabel ?: posterFacts(series)?.split(" · ")?.firstOrNull { it.startsWith("★") }?.removePrefix("★ "))?.let { "★ $it" },
                            series.genre?.split(',', '/')?.firstOrNull()?.trim() ?: info?.genres?.firstOrNull(),
                        ),
                        badges = tagged.badges,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = when {
                            target != null -> "Weiterschauen · S${seasons[target.season].number} F${target.episode + 1}"
                            else -> "Abspielen"
                        }
                        AppButton(label, {
                            if (target != null) app.open(seasons[target.season].episodes, target.episode)
                            else season?.let { app.open(it.episodes, 0) }
                        }, Modifier.weight(1f), ButtonStyle.Primary, icon = Icons.Rounded.PlayArrow, enabled = season != null)
                        val fav = series.favKey in app.favorites
                        BoxAction(if (fav) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (fav) "Gemerkt" else "Merken") { toggleFavoriteWithFeedback(app, series) }
                        (trailerUrl(series.trailer) ?: trailerUrl(info?.trailer))?.let { url -> BoxAction(Icons.Rounded.Theaters, "Trailer") { openExternalUrl(url) } }
                    }
                    message?.let { SmallText(it) }
                    info?.plot?.takeIf { it.isNotBlank() }?.let {
                        var open by remember { mutableStateOf(false) }
                        Text(
                            it, color = TextSoft, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (open) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { open = !open },
                        )
                    }
                }
            }
        }
        when {
            app.seasonsLoading -> item(key = "loading") { ChannelRowsSkeleton(Modifier.padding(top = 12.dp), count = 4) }
            app.seasonsError != null -> item(key = "error") { Text(app.seasonsError.orEmpty(), color = Danger, modifier = Modifier.padding(20.dp)) }
            season != null -> {
                item(key = "tabs") {
                    // Ganze Staffel offline: alle Folgen, die noch nicht in den Downloads sind, in die Warteschlange.
                    val loadable = season.episodes.filter { ep -> DownloadRules.canDownload(ep) && IosDownloads.infoFor(ep.url) == null }
                    val loadSeason: @Composable () -> Unit = {
                        if (loadable.isNotEmpty()) AppButton("Staffel laden", {
                            val results = loadable.map { IosDownloads.start(it) }
                            val ok = results.count { it == DownloadStartResult.STARTED }
                            message = if (ok > 0) "$ok ${if (ok == 1) "Folge wird" else "Folgen werden"} geladen – nacheinander, siehe „Downloads“."
                            else results.firstNotNullOfOrNull { downloadStartMessage(it, IosDownloads.freeBytes()) }
                        }, size = ButtonSize.Small, icon = Icons.Rounded.DownloadForOffline)
                    }
                    // Nur eine Staffel: keine Reiter-Leiste mit einem einzigen Reiter.
                    if (seasons.size > 1) SeasonTabs(seasons.map { "Staffel ${it.number}" }, seasonIndex, { seasonIndex = it }, Modifier.padding(top = 8.dp), trailing = loadSeason)
                    else Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        H3("Folgen", Modifier.weight(1f))
                        loadSeason()
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

/** Kastenknopf 46×46 neben dem Hauptknopf (Merken, Trailer). */
@Composable
private fun BoxAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.semantics { contentDescription = description }.pressable(onClick = onClick)
            .size(46.dp).clip(Corner).background(Surface1).border(1.dp, Line, Corner),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = TextColor, modifier = Modifier.size(21.dp)) }
}

/** Eine Folge: Vorschaubild 124×70 mit Fortschritt, Nummer + Titel, Dauer/Restzeit, Handlung (2 Zeilen), Download. */
@Composable
private fun EpisodeRow(
    number: Int, episode: Item, detail: EpisodeDetail?, progress: Float, current: Boolean,
    trailing: @Composable () -> Unit, onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(if (current) Surface1 else Bg).pressable(onClick = onPlay)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(124.dp, 70.dp).clip(Corner).background(Surface2)) {
            if (!episode.logo.isNullOrBlank()) AsyncImage(episode.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.PlayArrow, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(26.dp))
            if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$number. ${episode.name}", color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episodeMeta(detail?.durationSecs, progress)?.let { TinyText(it) }
            detail?.plot?.let { Text(it, color = TextSoft, fontSize = 11.5.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Box(Modifier.size(36.dp), Alignment.Center) { trailing() }
    }
}
