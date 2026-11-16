package de.tvplayer.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section

val Bg = Color(0xFF0B0D14)
val Surface1 = Color(0xFF161A26)
val Surface2 = Color(0xFF202638)
val Accent = Color(0xFF7C6CFF)
val Accent2 = Color(0xFF4FA3FF)
val Muted = Color(0xFF8B93A7)
val Danger = Color(0xFFFF6B7A)
val Gold = Color(0xFFFFC94D)
val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.White,
            secondary = Accent2,
            background = Bg, onBackground = Color.White,
            surface = Surface1, onSurface = Color.White,
            surfaceVariant = Surface2, onSurfaceVariant = Muted,
            error = Danger,
        ),
        content = content,
    )
}

@Composable
fun LogoTile(size: Dp = 40.dp) {
    Box(Modifier.size(size).background(AccentGradient, RoundedCornerShape(size / 3.5f)), Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp).padding(end = 0.dp))
        if (icon != null) Box(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick, shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (danger) Danger.copy(alpha = 0.6f) else Color(0x33FFFFFF)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Danger else Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Box(Modifier.width(8.dp)) }
        Text(text)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.height(height).background(Color(0x40FFFFFF), RoundedCornerShape(height))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AccentGradient, RoundedCornerShape(height)))
    }
}

@Composable
fun Loading(text: String = "Wird geladen …") {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Accent)
        Text(text, color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Das hat nicht geklappt", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        PrimaryButton("Erneut versuchen", onClick = onRetry)
    }
}

@Composable
fun EmptyView(title: String, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).width(440.dp))
    }
}

@Composable
fun ChannelLogo(item: Item, size: Dp, modifier: Modifier = Modifier) {
    if (item.logo != null) {
        AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).background(Surface2, RoundedCornerShape(8.dp)), Alignment.Center) {
            Text(item.name.trim().take(2).uppercase(), fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold, color = Muted)
        }
    }
}

@Composable
private fun BoxScope.FavoriteToggle(favorite: Boolean, visible: Boolean, onToggle: () -> Unit) {
    if (favorite || visible) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color(0xB3000000), CircleShape),
        ) {
            Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (favorite) "Favorit entfernen" else "Als Favorit merken", tint = if (favorite) Gold else Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Karte für Film, Serie, Folge oder Sender – mit Hover-Effekt und Stern zum Merken. */
@Composable
fun MediaCard(
    item: Item,
    favorite: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.05f else 1f, label = "scale")
    val live = item.section == Section.LIVE
    val wide = live || (item.section == Section.SERIES && !item.isSeriesFolder)
    val w = if (wide) 220.dp else 160.dp
    val h = if (live) 124.dp else if (wide) 124.dp else 236.dp

    Column(modifier.width(w).graphicsLayer { scaleX = scale; scaleY = scale }) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1),
            border = if (hovered) BorderStroke(2.dp, Color.White) else null,
            modifier = Modifier.size(w, h).hoverable(interaction),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    live -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { ChannelLogo(item, 64.dp) }
                    item.logo != null -> AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Text(item.name, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(12.dp))
                }
                if (hovered && !live) {
                    Box(Modifier.align(Alignment.Center).size(52.dp).background(Color(0xB3000000), CircleShape), Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                if (onToggleFavorite != null) FavoriteToggle(favorite, hovered, onToggleFavorite)
                if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
            }
        }
        Text(
            item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
            color = if (hovered) Color.White else Color(0xFFD0D4DE), modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
    }
}

/** Waagerechte Reihe mit Scrollbalken (Mausrad + Umschalt oder Balken ziehen). */
@Composable
fun ItemsRow(
    title: String,
    items: List<Item>,
    favorites: Set<String>,
    progressOf: (Item) -> Float,
    onOpen: (List<Item>, Int) -> Unit,
    onToggleFavorite: (Item) -> Unit,
) {
    val state = rememberLazyListState()
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(items) { i, item ->
                MediaCard(item, item.favKey in favorites, progressOf(item), { onOpen(items, i) }, { onToggleFavorite(item) })
            }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.padding(horizontal = 40.dp).fillMaxWidth())
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        trailing()
    }
}
