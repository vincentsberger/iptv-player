package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.epgProgress

/**
 * Ein Sender in einer Liste: Logo, Nummer, Name mit Flagge, laufende Sendung mit Fortschritt, Favoriten-Stern.
 * Das Programm wird erst angefragt, wenn die Zeile sichtbar wird – bei 10 000 Sendern sonst Tausende Anfragen.
 * Langes Drücken setzt/entfernt den Favoriten.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(app: AppState, item: Item, number: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(item.url) { app.requestEpg(item) }
    val now = app.nowFor(item)
    val tagged = splitProviderTag(item.name)
    val favorite = item.favKey in app.favorites
    Row(
        modifier.fillMaxWidth().background(if (selected) Surface2 else Bg)
            .combinedClickable(onLongClick = { app.toggleFavorite(item) }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Auswahl nie nur über die Fläche: zusätzlich ein roter Strich.
        Box(Modifier.width(3.dp).height(40.dp).background(if (selected) Signal else Bg))
        ChannelLogo(item.logo, 44.dp, Modifier.padding(start = 6.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) Text("$number", color = Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                tagged.tag?.let { FlagOrTag(it, Modifier.padding(end = 6.dp)) }
                Text(tagged.name, color = TextColor, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (now != null) {
                Text(now.title, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressBar(epgProgress(now), Modifier.padding(top = 4.dp).width(120.dp), height = 2.dp)
            }
        }
        if (favorite) Icon(Icons.Rounded.Star, "Favorit", tint = Signal, modifier = Modifier.size(18.dp))
    }
}
