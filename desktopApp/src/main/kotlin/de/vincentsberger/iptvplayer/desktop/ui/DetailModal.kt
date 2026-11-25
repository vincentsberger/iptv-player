package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.episodeMeta
import de.vincentsberger.iptvplayer.shared.episodePosition
import de.vincentsberger.iptvplayer.shared.movieFacts
import de.vincentsberger.iptvplayer.shared.playLabel

/** Öffnen bzw. Schließen des Detail-Fensters (Schließen schneller – man will zurück ins Raster). */
private const val OPEN_MS = 240
private const val CLOSE_MS = 180

/** Startgröße des Fensters beim Aufblenden. */
private const val START_SCALE = 0.94f

/** Abdunkelung des Grunds: flache Farbe aus der Palette, kein Verlauf. */
private const val SCRIM_ALPHA = 0.78f

private val ModalMaxWidth = 960.dp
private const val MODAL_SHARE = 0.9f

/**
 * Detail-Fenster für Film oder Serie über der Startseite. Offen, solange `app.screen` ein Film oder eine Serie ist;
 * Schließen (✕, Esc, Klick auf den Grund, Maus-Zurück) ist immer `app.back()`. Das Fenster wächst von der angeklickten
 * Kachel aus ([LocalDetailAnchor]) auf.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailLayer(app: AppState) {
    val current = detailItemOf(app.screen)
    // Beim Schließen bleibt der Inhalt stehen, bis das Ausblenden fertig ist.
    val last = remember { object { var item: Item? = null } }
    if (current != null) last.item = current
    val anchor = LocalDetailAnchor.current
    val closeFocus = remember { FocusRequester() }
    // Geschlossen und zurück auf der Startseite: Fokus zurück auf die Kachel, von der aus geöffnet wurde.
    LaunchedEffect(current == null) {
        if (current == null && app.screen == Screen.Home) anchor.tileFocus?.let { runCatching { it.requestFocus() } }
    }
    AnimatedVisibility(
        current != null,
        enter = fadeIn(tween(OPEN_MS, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(CLOSE_MS, easing = FastOutSlowInEasing)),
    ) {
        val scale by transition.animateFloat(
            { tween(if (targetState == EnterExitState.Visible) OPEN_MS else CLOSE_MS, easing = FastOutSlowInEasing) },
            label = "detail-scale",
        ) { if (it == EnterExitState.Visible) 1f else START_SCALE }
        var bounds by remember { mutableStateOf(Rect.Zero) }
        val closing = transition.targetState != EnterExitState.Visible
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Bg.copy(alpha = SCRIM_ALPHA)).testTag("detail-scrim")
                .pointerInput(Unit) { detectTapGestures { app.back() } }
                // Mausrad bleibt beim Fenster – das Raster darunter scrollt nicht mit.
                .onPointerEvent(PointerEventType.Scroll) { ev -> ev.changes.forEach { it.consume() } },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth(MODAL_SHARE).widthIn(max = ModalMaxWidth).heightIn(max = maxHeight * MODAL_SHARE)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = modalTransformOrigin(anchor.tileCenter, bounds)
                    }
                    .clip(Corner).background(Surface1).border(1.dp, Line, Corner)
                    // Klicks ins Fenster erreichen den Grund nicht (der würde schließen).
                    .pointerInput(Unit) { detectTapGestures { } }
                    // Tab bleibt im offenen Fenster und läuft nicht in die verdeckte Startseite. Beim Schließen gibt die
                    // Falle den Fokus frei – sonst bekäme z. B. der Player oder das Suchfeld (Strg+F) ihn nicht.
                    .focusProperties { onExit = { if (detailItemOf(app.screen) != null) cancelFocusChange() } }.focusGroup()
                    .testTag("detail-modal"),
            ) {
                AnimatedContent(
                    last.item, label = "detail-item",
                    transitionSpec = { fadeIn(tween(CLOSE_MS)) togetherWith fadeOut(tween(CLOSE_MS)) },
                ) { item ->
                    if (item != null) DetailContent(app, item, closeFocus)
                }
                IconButton({ app.back() }, Modifier.align(Alignment.TopEnd).padding(8.dp).focusRequester(closeFocus).handCursor().testTag("detail-close")) {
                    Icon(Icons.Rounded.Close, "Schließen", tint = TextColor)
                }
            }
            // Beim Ausblenden nimmt das Fenster nichts mehr an: Ein Doppelklick auf „Abspielen“ startet nur einen Player.
            if (closing) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        }
    }
}

@Composable
private fun DetailContent(app: AppState, item: Item, closeFocus: FocusRequester) {
    val primary = remember(item) { FocusRequester() }
    // Enter spielt gleich ab: Die Hauptaktion hat beim Öffnen den Fokus. Gibt es sie noch nicht (Serie lädt ihre
    // Staffeln), solange ✕ – der Fokus darf nicht auf der verdeckten Startseite bleiben.
    LaunchedEffect(item) {
        val focused = runCatching { primary.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
        if (!focused) runCatching { closeFocus.requestFocus() }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (item.isSeriesFolder) SeriesDetail(app, item, primary) else MovieDetail(app, item, primary)
    }
}

/** Breitbild oben im Fenster; ohne Bild eine schmale ruhige Fläche, damit der Kopf nicht springt. */
@Composable
private fun Backdrop(url: String?) {
    Box(Modifier.fillMaxWidth().height(if (url != null) 300.dp else 72.dp).background(Surface2)) {
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun FavoriteButton(app: AppState, item: Item) {
    val fav = item.favKey in app.favorites
    SecondaryButton(if (fav) "Favorit" else "Merken", if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder) { app.toggleFavorite(item) }
}

/** Film: alles, um zu entscheiden, ob man ihn schauen will – dann Abspielen/Weiterschauen. */
@Composable
private fun MovieDetail(app: AppState, movie: Item, primary: FocusRequester) {
    val info = app.mediaInfoFor(movie)
    val resumeAt = app.resumePositionOf(movie)
    Backdrop(info?.backdrop ?: movie.logo)
    Column(Modifier.padding(horizontal = 36.dp).padding(top = 24.dp, bottom = 36.dp)) {
        Kicker("Film")
        Headline(movie.name, 36.sp, Modifier.padding(top = 4.dp), maxLines = 2)
        info?.originalTitle?.let { Text(it, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp)) }
        movieFacts(info).takeIf { it.isNotEmpty() }?.let { facts ->
            Text(facts.joinToString("  ·  "), color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
            PrimaryButton(playLabel(resumeAt), Icons.Rounded.PlayArrow, Modifier.focusRequester(primary)) { app.play(listOf(movie), 0) }
            if (resumeAt > 0) SecondaryButton("Von vorn", Icons.Rounded.Replay) { app.play(listOf(movie), 0, fromStart = true) }
            FavoriteButton(app, movie)
        }
        info?.plot?.let { Text(it, color = TextColor, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 24.dp)) }
        info?.director?.let { Credit("Regie", it) }
        info?.cast?.takeIf { it.isNotEmpty() }?.let { Credit("Besetzung", it.take(MAX_CAST).joinToString(", ")) }
        if (info == null) Text("Infos werden geladen …", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
    }
}

private const val MAX_CAST = 8

@Composable
private fun Credit(label: String, text: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Kicker(label)
        Text(text, color = TextColor, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Serie: Kopf, Staffel-Wahl und Folgen – alles in einem Scrollbereich mit dem Fenster. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesDetail(app: AppState, series: Item, primary: FocusRequester) {
    val seasons = app.seasons
    var seasonIndex by remember(series.id) { mutableIntStateOf(0) }
    var jumped by remember(series.id) { mutableStateOf(false) }
    val season = seasons.getOrNull(seasonIndex)
    val last = app.lastEpisodeOf(series)
    // Der Hauptknopf erscheint erst mit den Staffeln – dann bekommt er den Fokus.
    LaunchedEffect(season != null) { if (season != null) runCatching { primary.requestFocus() } }
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && !jumped) {
            jumped = true
            episodePosition(seasons, last?.item?.url)?.let { seasonIndex = it.season }
        }
    }
    Backdrop(series.backdrop ?: series.logo)
    Column(Modifier.padding(horizontal = 36.dp).padding(top = 24.dp)) {
        Kicker("Serie")
        Headline(series.name, 36.sp, Modifier.padding(top = 4.dp), maxLines = 2)
        if (seasons.isNotEmpty()) {
            val episodes = seasons.sumOf { it.episodes.size }
            Text("${seasons.size} ${if (seasons.size == 1) "Staffel" else "Staffeln"} · $episodes Folgen", color = Muted, fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        app.seriesInfoFor(series)?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            Text(plot, color = TextColor, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 18.dp)) {
            val target = episodePosition(seasons, last?.item?.url)
            if (season != null) {
                PrimaryButton(if (target != null) "Weiterschauen" else "Abspielen", Icons.Rounded.PlayArrow, Modifier.focusRequester(primary)) {
                    if (target != null) app.open(seasons[target.season].episodes, target.episode) else app.open(season.episodes, 0)
                }
            }
            FavoriteButton(app, series)
        }
    }
    when {
        app.seasonsLoading -> Box(Modifier.fillMaxWidth().height(160.dp)) { Loading("Folgen werden geladen …") }
        app.seasonsError != null -> Box(Modifier.fillMaxWidth().height(220.dp)) {
            EmptyView("Keine Folgen", app.seasonsError.orEmpty(), Icons.Rounded.VideoLibrary)
        }
        season != null -> {
            FlowRow(
                Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                seasons.forEachIndexed { i, s ->
                    FilterChip(
                        selected = i == seasonIndex,
                        modifier = Modifier.handCursor(),
                        onClick = { seasonIndex = i },
                        label = { Text("Staffel ${s.number}") },
                        shape = Corner,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Signal, selectedLabelColor = OnSignal, labelColor = TextColor),
                        border = FilterChipDefaults.filterChipBorder(true, i == seasonIndex, borderColor = Line, selectedBorderColor = Signal),
                    )
                }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                season.episodes.forEachIndexed { i, ep ->
                    EpisodeRow(i + 1, ep, app.episodeDetail(ep), app.progressOf(ep)) { app.open(season.episodes, i) }
                }
            }
        }
    }
}

/** Eine Folge: Bild (mit Fortschritt) links, rechts Nummer + Titel, Dauer/Restzeit und Handlung. */
@Composable
private fun EpisodeRow(number: Int, episode: Item, detail: EpisodeDetail?, progress: Float, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(Corner)
            .background(if (hovered) Surface2 else Surface1)
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .handCursor().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", color = Muted, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Box(Modifier.size(192.dp, 108.dp).clip(Corner).background(Surface2)) {
            if (episode.logo != null) AsyncImage(episode.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (hovered) Icon(Icons.Rounded.PlayArrow, "Abspielen", tint = TextColor, modifier = Modifier.align(Alignment.Center).size(44.dp))
            if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Column(Modifier.padding(start = 18.dp).weight(1f)) {
            Text(episode.name, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episodeMeta(detail?.durationSecs, progress)?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            detail?.plot?.let {
                Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
