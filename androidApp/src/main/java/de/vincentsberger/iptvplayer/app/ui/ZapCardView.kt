package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item

/**
 * Zapp-Karte oben links: sofort beim Senderwechsel Nummer, Logo, Name und laufende Sendung –
 * noch bevor das erste Bild da ist. Ohne Programmdaten nur die Namenszeile.
 */
/** Abstand der Zapp-Karte vom oberen Rand des Players. */
val ZapCardTop = 40.dp

@Composable
fun ZapCardView(item: Item, number: Int, now: EpgEntry?, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier.width(460.dp).clip(Corner).background(Surface1.copy(alpha = 0.92f))) {
        Box(Modifier.width(4.dp).height(if (now != null) 104.dp else 68.dp).background(Signal))
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number.toString(), fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextColor)
                ChannelLogo(item, 34.dp, Modifier.padding(start = 12.dp))
                Text(item.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = TextColor, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
            if (now != null) {
                Text("${now.title} · bis ${now.end}", color = Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp))
                ProgressBar(progress, Modifier.padding(top = 8.dp).fillMaxWidth(), height = 5.dp)
            }
        }
    }
}
