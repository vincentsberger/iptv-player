package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.posterFacts
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

/** So lange muss die Maus auf einer Kachel bleiben, bis die Info-Karte erscheint (und ein Film seine Beschreibung lädt). */
const val HOVER_CARD_DELAY_MS = 450L

/** Kurze Gnadenfrist beim Wechsel der Maus von der Kachel auf die Karte – sonst flackert die Karte. */
const val HOVER_CARD_GRACE_MS = 150L

/** Info-Karte nur für Filme und Serien (Live-Sender und Folgen haben keine Beschreibung in diesem Sinn). */
fun hasHoverCard(item: Item): Boolean = item.section == Section.MOVIES || item.isSeriesFolder

/** Über der Kachel zentriert, so verschoben, dass die Karte ganz im Fenster bleibt (am rechten Rand klappt sie nach links). */
private class OverTileProvider : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - (popupContentSize.height - anchorBounds.height) / 2)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * Schwebende Info-Karte über einer Film-/Serienkachel: Bild, voller Titel, Fakten, Beschreibung. [interaction] meldet
 * Hover auf der Karte selbst, damit sie offen bleibt, wenn die Maus von der Kachel auf die Karte wandert.
 */
@Composable
fun HoverInfoCard(
    item: Item,
    info: MediaInfo?,
    width: Dp,
    favorite: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    Popup(popupPositionProvider = OverTileProvider()) {
        val plot = item.plot ?: info?.plot
        Column(
            Modifier.width(width).shadow(16.dp, Corner).clip(Corner).background(Surface1)
                .border(BorderStroke(2.dp, Signal), Corner)
                .hoverable(interaction).handCursor().clickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxWidth().height(width * 0.5f)) {
                if (item.logo != null) AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(width * 0.5f))
                if (onToggleFavorite != null) FavoriteToggle(favorite, visible = true, onToggleFavorite)
            }
            Column(Modifier.padding(16.dp)) {
                Headline(splitProviderTag(item.name).name, 18.sp, maxLines = 3)
                posterFacts(item)?.let { Text(it, color = Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(top = 4.dp)) }
                when {
                    plot != null -> Text(plot, color = TextColor, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp))
                    item.section == Section.MOVIES && info == null -> Text("Beschreibung wird geladen …", color = Muted, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}
