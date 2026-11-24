package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.design.ChannelLogo
import de.vincentsberger.iptvplayer.ios.ui.design.IconAction
import de.vincentsberger.iptvplayer.ios.ui.design.MonoText
import de.vincentsberger.iptvplayer.ios.ui.design.ProgressBar
import de.vincentsberger.iptvplayer.ios.ui.design.Tag
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.epgProgress

/**
 * Ein Sender in einer Liste (Entwurf „5“, phone.css .cr): Nummer, Logo-Kachel, Name mit Kennzeichen, laufende
 * Sendung mit Uhrzeit und Fortschritt, rechts der Favoriten-Stern zum Antippen. [selected] = laufender Sender:
 * Fläche surface1 und Signal-Strich links (nie nur über die Fläche).
 * Das Programm wird erst angefragt, wenn die Zeile sichtbar wird – bei 10 000 Sendern sonst Tausende Anfragen.
 * Langes Drücken setzt/entfernt den Favoriten ebenfalls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(app: AppState, item: Item, number: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(item.url) { app.requestEpg(item) }
    val now = app.nowFor(item)
    val tagged = remember(item.name) { splitProviderTag(item.name) }
    val favorite = item.favKey in app.favorites
    Row(
        modifier.fillMaxWidth().background(if (selected) Surface1 else Bg)
            .drawBehind {
                if (selected) drawRect(Signal, size = Size(3.dp.toPx(), size.height))
                drawRect(Line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            .combinedClickable(remember { MutableInteractionSource() }, null, role = Role.Button, onLongClick = { toggleFavoriteWithFeedback(app, item) }, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) Box(Modifier.width(24.dp), Alignment.CenterEnd) {
            MonoText("$number", color = Muted, size = 13.sp)
        }
        ChannelLogo(item.logo, item.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tagged.name, Modifier.weight(1f, fill = false), color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Qualität aus dem Anbieternamen („ᴿᴬᵂ“, „ᴴᴰ“) als kleine Kennzeichen statt im Namen.
                tagged.badges.forEach { Tag(it) }
            }
            if (now != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MonoText(now.start, color = Muted, size = 13.sp)
                    Text(now.title, color = TextColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
                }
                ProgressBar(epgProgress(now), Modifier.fillMaxWidth().padding(top = 3.dp))
            }
        }
        IconAction(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            if (favorite) "Favorit entfernen" else "Als Favorit merken",
            { toggleFavoriteWithFeedback(app, item) },
            tint = if (favorite) Signal else Muted,
        )
    }
}
