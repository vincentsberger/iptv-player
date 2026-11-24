package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item

/**
 * Zapp-Karte oben links: sofort beim Senderwechsel Nummer, Logo, Name und laufende Sendung –
 * noch bevor das erste Bild da ist. Ohne Programmdaten nur die Namenszeile.
 */
/** Abstand der Zapp-Karte vom oberen Rand des Players. */
val ZapCardTop = 28.dp

@Composable
fun ZapCardView(item: Item, number: Int, now: EpgEntry?, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier.width(380.dp).clip(Corner).background(Surface1.copy(alpha = 0.92f))) {
        Box(Modifier.width(3.dp).height(if (now != null) 86.dp else 56.dp).background(Signal))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number.toString(), fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextColor)
                ChannelLogo(item, 28.dp, Modifier.padding(start = 10.dp))
                Text(item.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextColor, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
            }
            if (now != null) {
                Text("${now.title} · bis ${now.end}", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp))
                ProgressBar(progress, Modifier.padding(top = 6.dp).fillMaxWidth(), height = 4.dp)
            }
        }
    }
}
