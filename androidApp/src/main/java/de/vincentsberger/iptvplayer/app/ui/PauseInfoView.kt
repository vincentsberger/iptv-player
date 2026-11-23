package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.PauseStage
import de.vincentsberger.iptvplayer.shared.data.MediaInfo

/**
 * Pause-Info über dem angehaltenen Bild: kompakt unten (Titel, Jahr, Bewertung, Fortschritt), nach einigen Sekunden
 * ausführlich links unten (Bewertung, Jahr, Dauer, FSK, Genres, Handlung). Ohne Infos bleibt es bei kompakt.
 */
@Composable
fun PauseInfoView(
    title: String,
    subtitle: String?,
    info: MediaInfo?,
    stage: PauseStage,
    position: Long,
    duration: Long,
    endsAt: String,
    modifier: Modifier = Modifier,
) {
    if (stage == PauseStage.NONE) return
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    Box(modifier.fillMaxSize()) {
        if (stage == PauseStage.FULL && info != null) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD9000000), 0.45f to Color(0x80000000), 0.75f to Color.Transparent)))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp).widthIn(max = 760.dp)) {
                Text("PAUSIERT", color = Muted, fontSize = 14.sp, letterSpacing = 1.5.sp)
                Headline(title, 38.sp, Modifier.padding(top = 4.dp), color = Color.White, maxLines = 2)
                subtitle?.let { Text(it, color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(top = 2.dp)) }
                val meta = listOfNotNull(info.ratingLabel?.let { "★ $it" }, info.year, info.durationLabel, info.fskLabel, info.genresLabel)
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                info.plot?.let {
                    Text(it, color = Muted, fontSize = 18.sp, lineHeight = 25.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                }
                if (duration > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    Text(formatTime(position), color = Muted, fontSize = 16.sp, fontFamily = Mono)
                    ProgressBar(progress, Modifier.weight(1f).padding(horizontal = 10.dp), height = 4.dp)
                    Text("noch ${formatTime(duration - position)}", color = Muted, fontSize = 16.sp, fontFamily = Mono)
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 56.dp, end = 56.dp, top = 56.dp, bottom = 36.dp),
            ) {
                val line = listOfNotNull(title, info?.year, info?.ratingLabel?.let { "★ $it" }).joinToString(" · ")
                Text(line, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
                if (duration > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text(formatTime(position), color = Muted, fontSize = 16.sp, fontFamily = Mono)
                    ProgressBar(progress, Modifier.weight(1f).padding(horizontal = 10.dp), height = 4.dp)
                    Text("endet um $endsAt", color = Muted, fontSize = 16.sp)
                }
            }
        }
    }
}
