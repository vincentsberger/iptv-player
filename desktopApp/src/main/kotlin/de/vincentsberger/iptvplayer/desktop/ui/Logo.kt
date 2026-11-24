package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import de.vincentsberger.iptvplayer.shared.brand.BRAND_MARK_STATIC
import de.vincentsberger.iptvplayer.shared.brand.BrandMark
import de.vincentsberger.iptvplayer.shared.brand.BrandMarkFrame
import de.vincentsberger.iptvplayer.shared.brand.LoadStep
import de.vincentsberger.iptvplayer.shared.brand.brandMarkFrame

/** Logo (Röhrenfernseher mit Antenne) fertig gezeichnet; [size] = Breite, die Höhe folgt dem ViewBox-Verhältnis 240 : 200. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) = LogoCanvas(size, BRAND_MARK_STATIC, modifier)

/** Logo mit Einschalt-Animation (Zyklus [BrandMark.CYCLE_MS]) und optionaler Beschriftung darunter – für große Ladestellen. */
@Composable
fun LogoLoader(size: Dp, label: String? = null, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(BrandMark.CYCLE_MS, easing = LinearEasing)), label = "t",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LogoCanvas(size, brandMarkFrame(t), Modifier.semantics { contentDescription = "Wird geladen" })
        if (label != null) Text(label, color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun LogoCanvas(size: Dp, frame: BrandMarkFrame, modifier: Modifier) {
    val body = TextColor
    val bar = Signal
    val barRest = Surface2
    Canvas(modifier.size(size, size * (BrandMark.VIEW_H / BrandMark.VIEW_W))) {
        val k = this.size.width / BrandMark.VIEW_W
        drawOutline(frame.drawn, body, k)
        if (frame.screenAlpha > 0f) drawScreen(frame, k)
        if (frame.barAlpha > 0f) drawBar(frame.barAlpha, bar, barRest, k)
    }
}

/** Antenne und Gehäuse als ein Linienzug, der bis [drawn] (0..1) seiner Gesamtlänge gezeichnet ist. */
private fun DrawScope.drawOutline(drawn: Float, color: Color, k: Float) {
    val antenna = Path().apply {
        moveTo(BrandMark.ANTENNA_X0 * k, BrandMark.ANTENNA_Y0 * k)
        lineTo(BrandMark.ANTENNA_X1 * k, BrandMark.ANTENNA_Y1 * k)
        lineTo(BrandMark.ANTENNA_X2 * k, BrandMark.ANTENNA_Y2 * k)
    }
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                BrandMark.BODY_LEFT * k, BrandMark.BODY_TOP * k, BrandMark.BODY_RIGHT * k, BrandMark.BODY_BOTTOM * k,
                CornerRadius(BrandMark.CORNER * k),
            ),
        )
    }
    val stroke = Stroke(BrandMark.STROKE * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val measures = listOf(antenna, body).map { PathMeasure().apply { setPath(it, false) } }
    var left = drawn.coerceIn(0f, 1f) * measures.sumOf { it.length.toDouble() }.toFloat()
    measures.forEach { m ->
        val part = minOf(left, m.length)
        if (part > 0f) drawPath(Path().apply { m.getSegment(0f, part, this, true) }, color, style = stroke)
        left -= part
    }
}

/** Bildfläche (Röhrenblitz): wächst aus der Mitte zuerst als Strich in die Breite, dann in die Höhe. */
private fun DrawScope.drawScreen(frame: BrandMarkFrame, k: Float) {
    val w = (BrandMark.SCREEN_RIGHT - BrandMark.SCREEN_LEFT) * frame.screenScaleX * k
    val h = (BrandMark.SCREEN_BOTTOM - BrandMark.SCREEN_TOP) * frame.screenScaleY * k
    val cx = (BrandMark.SCREEN_LEFT + BrandMark.SCREEN_RIGHT) / 2 * k
    val cy = (BrandMark.SCREEN_TOP + BrandMark.SCREEN_BOTTOM) / 2 * k
    drawRect(Color(Tokens.FocusBg).copy(alpha = frame.screenAlpha), Offset(cx - w / 2, cy - h / 2), Size(w, h))
}

/** Balken unten im Bild: 60 % `Signal`, Rest `Surface2`. */
private fun DrawScope.drawBar(alpha: Float, fill: Color, rest: Color, k: Float) {
    val split = BrandMark.BAR_LEFT + (BrandMark.BAR_RIGHT - BrandMark.BAR_LEFT) * BrandMark.BAR_FILL
    val h = (BrandMark.BAR_BOTTOM - BrandMark.BAR_TOP) * k
    drawRect(fill.copy(alpha = alpha), Offset(BrandMark.BAR_LEFT * k, BrandMark.BAR_TOP * k), Size((split - BrandMark.BAR_LEFT) * k, h))
    drawRect(rest.copy(alpha = alpha), Offset(split * k, BrandMark.BAR_TOP * k), Size((BrandMark.BAR_RIGHT - split) * k, h))
}

/** Kästchen (20 dp) einer Ladezeile: leer = wartet, mit Spinner = lädt, gefüllt mit Häkchen = geladen, rot mit ✕ = Fehler. */
@Composable
fun LoadCheck(step: LoadStep) {
    val description = when (step) {
        LoadStep.WAITING -> "wartet"
        LoadStep.LOADING -> "lädt"
        LoadStep.DONE -> "geladen"
        LoadStep.FAILED -> "Fehler"
    }
    val frame = when (step) {
        LoadStep.DONE -> Modifier.background(Signal)
        LoadStep.FAILED -> Modifier.border(1.5.dp, Danger)
        else -> Modifier.border(1.5.dp, Muted)
    }
    Box(Modifier.size(20.dp).then(frame).semantics { contentDescription = description }, Alignment.Center) {
        when (step) {
            LoadStep.LOADING -> CircularProgressIndicator(Modifier.size(12.dp), color = Signal, trackColor = Line, strokeWidth = 1.5.dp)
            LoadStep.DONE -> Icon(Icons.Rounded.Check, null, tint = OnSignal, modifier = Modifier.size(14.dp))
            LoadStep.FAILED -> Text("✕", color = Danger, fontSize = 12.sp)
            LoadStep.WAITING -> Unit
        }
    }
}

/**
 * Puffer-Anzeige: bis zum ersten Bild eines Titels das animierte Logo, danach (erneutes Puffern, Neuverbinden) der kleine Spinner.
 * [titleKey] wechselt mit dem Titel und setzt „erstes Bild gesehen“ zurück.
 */
@Composable
internal fun BufferingOverlay(titleKey: Any, hasPicture: Boolean, visible: Boolean, reconnecting: Boolean, modifier: Modifier = Modifier) {
    var pictureSeen by remember { mutableStateOf(false) }
    // Erst nach dem Titelwechsel auswerten: das alte Bild muss weg sein, bevor das erste Bild des neuen zählt.
    val picture by rememberUpdatedState(hasPicture)
    LaunchedEffect(titleKey) {
        pictureSeen = false
        snapshotFlow { picture }.first { !it }
        snapshotFlow { picture }.first { it }
        pictureSeen = true
    }
    if (!visible) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (pictureSeen) {
            CircularProgressIndicator(
                Modifier.size(56.dp).semantics { contentDescription = "Puffert" }, color = Signal,
                trackColor = Color(Tokens.Text).copy(alpha = 0.25f), strokeWidth = 3.dp,
            )
        } else {
            LogoLoader(72.dp)
        }
        if (reconnecting) {
            Text(
                "Verbindung wird wiederhergestellt …", color = Color.White,
                modifier = Modifier.padding(top = 14.dp).background(Color(0x99000000), Corner).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
