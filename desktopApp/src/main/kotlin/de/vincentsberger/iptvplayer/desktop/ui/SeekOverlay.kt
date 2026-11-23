package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.SeekState
import de.vincentsberger.iptvplayer.shared.seekDeltaLabel
import de.vincentsberger.iptvplayer.shared.formatTime

/**
 * Anzeige beim Spulen mit Ziel: Sprungweite groß in der Mitte, unten ein Balken mit dem gespielten Teil, dem Bereich
 * bis zum Ziel und einer Markierung samt Zielzeit-Blase. Gesprungen wird erst, wenn keine Taste mehr kommt.
 */
@Composable
fun SeekOverlay(seek: SeekState, durationMs: Long, positionMs: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        val label = seekDeltaLabel(seek.deltaMs)
        Text(
            if (seek.deltaMs >= 0) "$label ▶▶" else "◀◀ $label",
            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
            modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(40.dp)).padding(horizontal = 24.dp, vertical = 10.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 22.dp),
        ) {
            if (durationMs > 0) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
                    fun x(ms: Long) = maxWidth * (ms.toFloat() / durationMs).coerceIn(0f, 1f)
                    val from = minOf(positionMs, seek.targetMs)
                    val to = maxOf(positionMs, seek.targetMs)
                    // Zielzeit-Blase über der Markierung (nicht über den Rand hinaus)
                    Text(
                        formatTime(seek.targetMs), color = OnSignal, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.offset(x = (x(seek.targetMs) - 34.dp).coerceIn(0.dp, maxWidth - 68.dp))
                            .background(Color.White, Corner).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Box(Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp).fillMaxWidth().height(5.dp).background(Color(0x40FFFFFF), Corner)) {
                        Box(Modifier.width(x(positionMs)).fillMaxHeight().background(Signal, Corner))
                        Box(Modifier.offset(x = x(from)).width(x(to) - x(from)).fillMaxHeight().background(Signal.copy(alpha = 0.35f)))
                    }
                    Box(Modifier.align(Alignment.BottomStart).offset(x = x(seek.targetMs) - 1.5.dp).width(3.dp).height(15.dp).background(Color.White, Corner))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(formatTime(positionMs), color = Muted, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                    Text(formatTime(durationMs), color = Muted, fontSize = 13.sp, fontFamily = Mono)
                }
            } else {
                Text(formatTime(seek.targetMs), color = Color.White, fontFamily = Mono, fontSize = 18.sp)
            }
        }
    }
}
