package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Danger
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Shade
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.ios.ui.design.H3
import de.vincentsberger.iptvplayer.ios.ui.design.IconAction
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
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

private val PanelWidth = 360.dp
private val LabelWidth = 84.dp
private val CurveWidth = 96.dp

/** Farbe der Punkte: schlecht klar rot, unbekannt gedämpft, sonst Textfarbe (Bernstein bleibt der Auswahl vorbehalten). */
private fun qualityColor(q: ConnectionQuality): Color = when (q) {
    ConnectionQuality.POOR -> Danger
    ConnectionQuality.UNKNOWN -> Muted
    else -> TextColor
}

/** Kleiner Indikator oben rechts: Punkte und Wort; Antippen öffnet das Statistik-Fenster. */
@Composable
internal fun ConnectionIndicator(q: ConnectionQuality, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Shade.copy(alpha = 0.6f), Corner).pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(qualityDots(q), color = qualityColor(q), fontSize = 13.sp)
        Text(qualityLabel(q), color = TextColor, fontSize = 13.sp)
    }
}

/** Statistik-Fenster im Querformat (oben links über dem Bild); [s] = null, solange noch kein Messpunkt vorliegt. */
@Composable
internal fun StatsPanel(s: PlaybackStats?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(PanelWidth).background(Shade.copy(alpha = 0.78f), Corner).padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Statistik", color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            IconAction(Icons.Rounded.Close, "Schließen", onClose, tint = Muted)
        }
        Column(Modifier.padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { StatsRows(s) }
    }
}

/** Statistik im Hochformat als Blatt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsSheet(s: PlaybackStats?, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface1) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                H3("Statistik", Modifier.weight(1f))
                IconAction(Icons.Rounded.Close, "Schließen", onClose)
            }
            StatsRows(s)
        }
    }
}

/** Die Zeilen des Fensters; Texte kommen aus den gemeinsamen Formatierern, fehlende Werte als „–“. */
@Composable
private fun StatsRows(s: PlaybackStats?) {
    val q = s?.quality ?: ConnectionQuality.UNKNOWN
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        StatsRow("Ausfälle") { Value(if (s == null) STATS_DASH else failuresLine(s), Modifier.weight(1f)) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        StatsRow("Video") { Value(s?.let { videoLine(it.engine) } ?: STATS_DASH, Modifier.weight(1f)) }
        StatsRow("Audio") { Value(s?.let { audioLine(it.engine) } ?: STATS_DASH, Modifier.weight(1f)) }
        StatsRow("Stream") { Value(s?.let(::streamLine) ?: STATS_DASH, Modifier.weight(1f)) }
        StatsRow("Server") { Value(s?.server ?: STATS_DASH, Modifier.weight(1f)) }
    }
}

@Composable
private fun StatsRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
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
        drawPath(path, Signal, style = Stroke(width = 1.5.dp.toPx()))
    }
}
