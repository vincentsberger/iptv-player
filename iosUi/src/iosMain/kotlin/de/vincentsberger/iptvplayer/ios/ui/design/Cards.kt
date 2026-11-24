package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.epgProgress
import de.vincentsberger.iptvplayer.shared.posterFacts

/** Dunkle Unterlage für Text über Bildern (#0A0B0D). */
private val Shade = Color(0xFF0A0B0D)

/**
 * Senderkachel der Startseite (phone.css .ct): Logo, Name, LIVE, darunter die laufende Sendung mit Fortschritt.
 * Das Programm wird erst angefragt, wenn die Kachel sichtbar wird.
 */
@Composable
fun ChannelTile(app: AppState, item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(item.url) { app.requestEpg(item) }
    val now = app.nowFor(item)
    val name = remember(item.name) { splitProviderTag(item.name).name }
    Column(
        modifier.width(150.dp).pressable(onClick = onClick).clip(Corner).background(Surface1).border(1.dp, Line, Corner).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelLogo(item.logo, item.name, size = 34.dp)
            Column(Modifier.weight(1f)) {
                Text(name, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LiveBadge()
            }
        }
        if (now != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MonoText(now.start, color = Muted, size = 11.5.sp)
                Text(now.title, color = TextColor, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressBar(epgProgress(now), Modifier.fillMaxWidth())
        }
    }
}

/** Bild einer Karte: Cover zugeschnitten. Ohne Bild eine Fläche mit [fallbackTitle] (Poster) bzw. Film-Symbol. */
@Composable
private fun CardImage(url: String?, modifier: Modifier, fallbackTitle: String? = null) {
    Box(modifier.clip(Corner).background(Surface1)) {
        if (url.isNullOrBlank() && fallbackTitle != null) Text(
            fallbackTitle, Modifier.align(Alignment.Center).padding(8.dp), color = TextColor, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, lineHeight = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
        ) else Icon(Icons.Rounded.Movie, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(28.dp))
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * Querkarte (phone.css .lc/.th): Bild 150×84 mit Abspiel-Symbol und Fortschritt am unteren Rand, darunter Titel und
 * Untertitel („Noch 48 Min.“, „S2 · F2 · Treibgut“).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandscapeCard(
    item: Item, title: String, subtitle: String?, progress: Float?, onClick: () -> Unit,
    modifier: Modifier = Modifier, showPlay: Boolean = true, onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(150.dp).combinedClickable(remember { MutableInteractionSource() }, null, role = Role.Button, onLongClick = onLongClick, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            CardImage(item.logo, Modifier.fillMaxSize())
            if (showPlay) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp))
            if (progress != null && progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.55f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Signal))
            }
        }
        Text(title, Modifier.padding(top = 7.dp), color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, Modifier.padding(top = 2.dp), color = Muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Poster 2:3 (phone.css .po): Bild zugeschnitten, unten ein Verlauf mit Jahr links und Bewertung rechts (P4),
 * oben rechts der Stern bei Favoriten.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: Item, favorite: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, width: Dp = 104.dp,
) {
    val facts = remember(item) { posterFacts(item)?.split(" · ").orEmpty() }
    val title = remember(item.name) { splitProviderTag(item.name).name }
    Box(
        modifier.width(width).aspectRatio(2f / 3f).clip(Corner)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .combinedClickable(remember { MutableInteractionSource() }, null, role = Role.Button, onLongClick = onLongClick, onClick = onClick),
    ) {
        CardImage(item.logo, Modifier.fillMaxSize(), fallbackTitle = title)
        if (facts.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Shade.copy(alpha = 0f), Shade.copy(alpha = 0.9f))))
                .padding(start = 7.dp, end = 7.dp, top = 22.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            facts.forEach { Text(it, color = TextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp, maxLines = 1) }
        }
        if (favorite) Icon(Icons.Rounded.Star, "Favorit", tint = Signal, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp))
    }
}
