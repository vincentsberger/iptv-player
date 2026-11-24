package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import de.vincentsberger.iptvplayer.shared.player.stats.ConnectionQuality
import de.vincentsberger.iptvplayer.shared.player.stats.PlaybackStats
import de.vincentsberger.iptvplayer.shared.player.stats.STATS_DASH
import de.vincentsberger.iptvplayer.shared.player.stats.audioLine
import de.vincentsberger.iptvplayer.shared.player.stats.formatBitrate
import de.vincentsberger.iptvplayer.shared.player.stats.failuresLine
import de.vincentsberger.iptvplayer.shared.player.stats.formatBuffer
import de.vincentsberger.iptvplayer.shared.player.stats.qualityDots
import de.vincentsberger.iptvplayer.shared.player.stats.qualityLabel
import de.vincentsberger.iptvplayer.shared.player.stats.streamLine
import de.vincentsberger.iptvplayer.shared.player.stats.videoLine

private val PanelWidth = 420.dp
private val LabelWidth = 84.dp
private val CurveWidth = 120.dp

/** Farbe der Punkte: schlecht klar rot, unbekannt gedämpft, sonst Textfarbe (Bernstein bleibt der Auswahl vorbehalten). */
@Composable
private fun qualityColor(q: ConnectionQuality): Color = when (q) {
    ConnectionQuality.POOR -> Danger
    ConnectionQuality.UNKNOWN -> Muted
    else -> TextColor
}

/** Kleiner Indikator oben rechts: Punkte und Wort; mit [onClick] öffnet ein Klick das Statistik-Fenster. */
@Composable
fun ConnectionIndicator(q: ConnectionQuality, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val base = modifier.background(Color(Tokens.Shade).copy(alpha = 0.6f), Corner)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(qualityDots(q), color = qualityColor(q), fontSize = 13.sp)
        Text(qualityLabel(q), color = TextColor, fontSize = 13.sp)
    }
}

/** Statistik-Fenster oben links; [s] = null, solange noch kein Messpunkt vorliegt. Texte kommen aus den gemeinsamen Formatierern. */
@Composable
fun StatsPanel(s: PlaybackStats?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(PanelWidth).background(Color(Tokens.Shade).copy(alpha = 0.78f), Corner).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Statistik", color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.width(28.dp).height(28.dp)) {
                Icon(Icons.Rounded.Close, "Schließen", tint = Muted)
            }
        }
        val q = s?.quality ?: ConnectionQuality.UNKNOWN
        StatsRow("Verbindung") {
            Text(qualityDots(q), color = qualityColor(q), fontSize = 14.sp)
            Text(qualityLabel(q), color = TextColor, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        StatsRow("Puffer") {
            Value(formatBuffer(s?.engine?.bufferedMs), Modifier.weight(1f))
            Curve(s?.history?.map { it.bufferMs?.toFloat() }.orEmpty())
        }
        StatsRow("Datenrate") {
            Value(formatBitrate(s?.history?.lastOrNull()?.bitrateBps), Modifier.weight(1f))
            Curve(s?.history?.map { it.bitrateBps?.toFloat() }.orEmpty())
        }
        StatsRow("Ausfälle") { Value(if (s == null) STATS_DASH else failuresLine(s)) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        StatsRow("Video") { Value(s?.let { videoLine(it.engine) } ?: STATS_DASH) }
        StatsRow("Audio") { Value(s?.let { audioLine(it.engine) } ?: STATS_DASH) }
        StatsRow("Stream") { Value(s?.let { streamLine(it) } ?: STATS_DASH) }
        StatsRow("Server") { Value(s?.server ?: STATS_DASH) }
    }
}

@Composable
private fun StatsRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(LabelWidth))
        content()
    }
}

@Composable
private fun Value(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextColor, fontSize = 14.sp, modifier = modifier)
}

/** Verlauf der letzten 60 Sekunden als Linie; fehlende Werte unterbrechen sie, nichts wird geschätzt. */
@Composable
private fun Curve(values: List<Float?>) {
    val color = Signal
    Canvas(Modifier.width(CurveWidth).height(18.dp)) {
        val max = values.filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: return@Canvas
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            val p = Offset(i * stepX, size.height - (v / max) * size.height)
            if (open) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
            open = true
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
    }
}
