package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.player.stats.ConnectionQuality
import de.vincentsberger.iptvplayer.shared.player.stats.PlaybackStats
import de.vincentsberger.iptvplayer.shared.player.stats.STATS_DASH
import de.vincentsberger.iptvplayer.shared.player.stats.audioLine
import de.vincentsberger.iptvplayer.shared.player.stats.failuresLine
import de.vincentsberger.iptvplayer.shared.player.stats.formatBitrate
import de.vincentsberger.iptvplayer.shared.player.stats.formatBuffer
import de.vincentsberger.iptvplayer.shared.player.stats.qualityDots
import de.vincentsberger.iptvplayer.shared.player.stats.qualityLabel
import de.vincentsberger.iptvplayer.shared.player.stats.streamLine
import de.vincentsberger.iptvplayer.shared.player.stats.videoLine

private val PanelWidth = 560.dp
private val LabelWidth = 120.dp
private val CurveWidth = 160.dp

/** Farbe der Punkte: schlecht klar rot, unbekannt gedämpft, sonst Textfarbe (Bernstein bleibt der Auswahl vorbehalten). */
private fun qualityColor(q: ConnectionQuality): Color = when (q) {
    ConnectionQuality.POOR -> Danger
    ConnectionQuality.UNKNOWN -> Muted
    else -> TextColor
}

/** Kleiner Indikator oben rechts: Punkte und Wort; nur Anzeige, ohne Fokus. */
@Composable
fun ConnectionIndicator(q: ConnectionQuality, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("stats:indicator").background(Shade.copy(alpha = 0.6f), Corner).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(qualityDots(q), color = qualityColor(q), fontSize = 18.sp)
        Text(qualityLabel(q), color = TextColor, fontSize = 18.sp)
    }
}

/** Statistik-Fenster oben links; [s] = null, solange noch kein Messpunkt vorliegt. Geschlossen wird über Zurück oder das Menü. */
@Composable
fun StatsPanel(s: PlaybackStats?, modifier: Modifier = Modifier) {
    Column(
        modifier.testTag("stats:panel").width(PanelWidth).background(Shade.copy(alpha = 0.78f), Corner).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Statistik", color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        val q = s?.quality ?: ConnectionQuality.UNKNOWN
        StatsRow("Verbindung") {
            Text(qualityDots(q), color = qualityColor(q), fontSize = 18.sp)
            Text(qualityLabel(q), color = TextColor, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
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
        Text(label, color = Muted, fontSize = 17.sp, modifier = Modifier.width(LabelWidth))
        content()
    }
}

@Composable
private fun Value(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextColor, fontSize = 18.sp, modifier = modifier)
}

/** Verlauf der letzten 60 Sekunden als Linie; fehlende Werte unterbrechen sie, nichts wird geschätzt. */
@Composable
private fun Curve(values: List<Float?>) {
    Canvas(Modifier.width(CurveWidth).height(22.dp)) {
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
        drawPath(path, Signal, style = Stroke(width = 2.dp.toPx()))
    }
}
