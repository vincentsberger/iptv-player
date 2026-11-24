package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.Bg
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor

// Titelbild, Detailkopf und Zubehör der Filme/Serien-Seiten (phone.css .hero .meta .dh .act .stab).

/** Kennzeile „2025 · Thriller · ★ 7,3 · 1 Std. 52 Min. [4K]“: Teile mit kleinen Punkten getrennt, Kennzeichen hinten. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, badges: List<String> = emptyList(), color: Color = TextColor) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.align(Alignment.CenterVertically).size(3.dp).background(Muted, CircleShape))
            Text(p, Modifier.align(Alignment.CenterVertically), color = color, fontSize = 13.sp, maxLines = 1)
        }
        badges.forEach { Tag(it, Modifier.align(Alignment.CenterVertically)) }
    }
}

/** Bild mit Ersatz: ohne Adresse eine ruhige Fläche (surface1) statt Leere. */
@Composable
private fun BoxScope.CoverImage(url: String?) {
    Box(Modifier.matchParentSize().background(Surface1))
    if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
}

/**
 * Startbereich der Filme/Serien (P10a): Bild 410 dp mit Verlauf oben (für die Kopfzeile) und unten (für den Text),
 * Rubrik, großer Titel, Kennzeile, „Abspielen“ und „Mehr“.
 */
@Composable
fun Hero(
    imageUrl: String?, kicker: String, title: String, meta: List<String>, badges: List<String>,
    primary: String, onPrimary: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(410.dp)) {
        CoverImage(imageUrl)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Bg.copy(alpha = 0.8f), 0.26f to Bg.copy(alpha = 0f), 0.40f to Bg.copy(alpha = 0f),
                    0.78f to Bg.copy(alpha = 0.86f), 1f to Bg,
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Kicker(kicker)
            Text(
                title.uppercase(), Modifier.padding(top = 6.dp, bottom = 10.dp).semantics { heading() },
                color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp,
                letterSpacing = (-1.38).sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            MetaLine(meta, badges = badges)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(primary, onPrimary, Modifier.weight(1f), ButtonStyle.Primary, icon = Icons.Rounded.PlayArrow)
                AppButton("Mehr", onMore, Modifier.weight(1f), ButtonStyle.Glass, icon = Icons.Rounded.Info)
            }
        }
    }
}

/** Filter-Chip über dem Titelbild: Glas-Fläche, rechts ▾ (Auswahl öffnen) bzw. × (aktiv, aufheben). */
@Composable
fun GlassChip(text: String, onClick: () -> Unit, trailing: ImageVector, modifier: Modifier = Modifier, active: Boolean = false) {
    Row(
        modifier.semantics { this.selected = active }.pressable(role = Role.Button, onClick = onClick)
            .height(30.dp).clip(Corner).background(Bg.copy(alpha = 0.5f))
            .border(1.dp, if (active) de.vincentsberger.iptvplayer.ios.ui.Signal else TextColor.copy(alpha = 0.22f), Corner)
            .selectionUnderline(active).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Icon(trailing, null, tint = TextColor, modifier = Modifier.size(16.dp))
    }
}

/** Kopf einer Detailseite (P10b): Hintergrundbild bildschirmbreit mit Verlauf, Glas-Knopf „Zurück“ oben links. */
@Composable
fun DetailHeader(imageUrl: String?, height: Dp, onBack: () -> Unit, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth().height(height)) {
        CoverImage(imageUrl)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Bg.copy(alpha = 0.6f), 0.30f to Bg.copy(alpha = 0f), 0.55f to Bg.copy(alpha = 0f), 1f to Bg),
            ),
        )
        Box(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, top = 4.dp).size(40.dp)
                .semantics { contentDescription = "Zurück" }.pressable(onClick = onBack)
                .clip(Corner).background(Bg.copy(alpha = 0.55f)).border(1.dp, TextColor.copy(alpha = 0.2f), Corner),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = TextColor, modifier = Modifier.size(24.dp)) }
        if (onMore != null) Box(
            Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 16.dp, top = 4.dp).size(40.dp)
                .semantics { contentDescription = "Mehr" }.pressable(onClick = onMore)
                .clip(Corner).background(Bg.copy(alpha = 0.55f)).border(1.dp, TextColor.copy(alpha = 0.2f), Corner),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, null, tint = TextColor, modifier = Modifier.size(24.dp)) }
    }
}

/** Gleichrangige Aktionen unter dem Hauptknopf (Merken, Herunterladen, Trailer): Symbol über Beschriftung. */
@Composable
fun ActionGrid(actions: List<Triple<ImageVector, String, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { (icon, label, onClick) ->
            Column(
                Modifier.weight(1f).pressable(onClick = onClick).height(58.dp).clip(Corner).background(Surface1).border(1.dp, Line, Corner),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = TextColor, modifier = Modifier.size(21.dp))
                Text(label, color = TextColor, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        // Weniger als drei Aktionen: Platz bleibt frei, die Knöpfe behalten ihre Breite.
        repeat((3 - actions.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
    }
}

/** Staffel-Reiter: aktiv hell mit Signal-Strich, sonst gedämpft; rechts Platz für „Staffel laden“. */
@Composable
fun SeasonTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                labels.forEachIndexed { i, l ->
                    val on = i == selected
                    Box(
                        Modifier.semantics { this.selected = on }.pressable(role = Role.Tab) { onSelect(i) }.height(44.dp).selectionUnderline(on),
                        contentAlignment = Alignment.Center,
                    ) { Text(l, color = if (on) TextColor else Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                }
            }
            Box(Modifier.padding(start = 12.dp)) { trailing() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}
