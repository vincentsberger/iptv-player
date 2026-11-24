package de.vincentsberger.iptvplayer.app.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.brand.BRAND_MARK_STATIC
import de.vincentsberger.iptvplayer.shared.brand.BrandMark
import de.vincentsberger.iptvplayer.shared.brand.BrandMarkFrame
import de.vincentsberger.iptvplayer.shared.brand.LoadStep
import de.vincentsberger.iptvplayer.shared.brand.brandMarkFrame

/** Ist „Animationen aus“ (Entwickleroptionen / Barrierefreiheit) gesetzt? Dann steht das Logo still. */
@Composable
private fun animationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

private val CAP_BODY = StrokeCap.Round

/** Zeichnet das Logo im ViewBox 240 × 200 für ein Bild der Animation. */
private fun DrawScope.drawBrandMark(f: BrandMarkFrame) {
    val s = size.width / BrandMark.VIEW_W
    val stroke = Stroke(BrandMark.STROKE * s, cap = CAP_BODY, join = StrokeJoin.Round)
    // Bildfläche (Röhrenblitz), skaliert um die Mitte.
    val cx = (BrandMark.SCREEN_LEFT + BrandMark.SCREEN_RIGHT) / 2 * s
    val cy = (BrandMark.SCREEN_TOP + BrandMark.SCREEN_BOTTOM) / 2 * s
    val w = (BrandMark.SCREEN_RIGHT - BrandMark.SCREEN_LEFT) * s * f.screenScaleX
    val h = (BrandMark.SCREEN_BOTTOM - BrandMark.SCREEN_TOP) * s * f.screenScaleY
    if (f.screenAlpha > 0f) drawRect(FocusBg.copy(alpha = f.screenAlpha), Offset(cx - w / 2, cy - h / 2), Size(w, h))
    // Balken: Rest Surface2, die ersten 60 % Signal.
    if (f.barAlpha > 0f) {
        val bw = (BrandMark.BAR_RIGHT - BrandMark.BAR_LEFT) * s
        val top = Offset(BrandMark.BAR_LEFT * s, BrandMark.BAR_TOP * s)
        val bh = (BrandMark.BAR_BOTTOM - BrandMark.BAR_TOP) * s
        drawRect(Surface2.copy(alpha = f.barAlpha), top, Size(bw, bh))
        drawRect(Signal.copy(alpha = f.barAlpha), top, Size(bw * BrandMark.BAR_FILL, bh))
    }
    // Umrisse: Antenne und Gehäuse, bis zum gezeichneten Anteil.
    val antenna = Path().apply {
        moveTo(BrandMark.ANTENNA_X0 * s, BrandMark.ANTENNA_Y0 * s)
        lineTo(BrandMark.ANTENNA_X1 * s, BrandMark.ANTENNA_Y1 * s)
        lineTo(BrandMark.ANTENNA_X2 * s, BrandMark.ANTENNA_Y2 * s)
    }
    val body = Path().apply {
        val r = BrandMark.CORNER * s
        addRoundRect(
            RoundRect(
                Rect(BrandMark.BODY_LEFT * s, BrandMark.BODY_TOP * s, BrandMark.BODY_RIGHT * s, BrandMark.BODY_BOTTOM * s),
                CornerRadius(r, r),
            ),
        )
    }
    for (p in listOf(antenna, body)) {
        if (f.drawn >= 1f) {
            drawPath(p, TextColor, style = stroke)
        } else if (f.drawn > 0f) {
            val m = PathMeasure().apply { setPath(p, false) }
            val part = Path()
            m.getSegment(0f, m.length * f.drawn, part, true)
            drawPath(part, TextColor, style = stroke)
        }
    }
}

/** Logo (Röhrenfernseher mit Antenne) in Breite [size]; Höhe = size × 200/240. Statisch, immer fertig gezeichnet (Animation: [LogoLoader]). */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * (BrandMark.VIEW_H / BrandMark.VIEW_W))) { drawBrandMark(BRAND_MARK_STATIC) }
}

/** Logo mit laufender Einschalt-Animation (Zyklus [BrandMark.CYCLE_MS]) und optionaler Beschriftung; bei „Animationen aus“ statisch. */
@Composable
fun LogoLoader(size: Dp, label: String? = null, modifier: Modifier = Modifier) {
    val still = animationsDisabled()
    val t = if (still) null else rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(BrandMark.CYCLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "t",
    ).value
    val frame = if (t == null) BRAND_MARK_STATIC else brandMarkFrame(t)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(size, size * (BrandMark.VIEW_H / BrandMark.VIEW_W)).semantics { contentDescription = "Wird geladen" },
        ) { drawBrandMark(frame) }
        if (label != null) Text(label, color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Kästchen (20 dp) einer Ladezeile: leer (wartet/lädt), gefüllt mit Häkchen (fertig) oder Rand `Danger` mit ✕ (Fehler). */
@Composable
fun LoadCheck(step: LoadStep) {
    val desc = when (step) {
        LoadStep.WAITING -> "wartet"
        LoadStep.LOADING -> "lädt"
        LoadStep.DONE -> "geladen"
        LoadStep.FAILED -> "Fehler"
    }
    val base = Modifier.size(20.dp).semantics { contentDescription = desc }
    when (step) {
        LoadStep.DONE -> Box(base.background(Signal), Alignment.Center) { Icon(Icons.Rounded.Check, tint = OnSignal, size = 16.dp) }
        LoadStep.FAILED -> Box(base.border(2.dp, Danger), Alignment.Center) { Icon(Icons.Rounded.Close, tint = Danger, size = 14.dp) }
        else -> Box(base.border(2.dp, Muted))
    }
}

/** Zeile rechts neben dem Bereichssymbol: Zusatz je Zustand (Zähler/„lädt …“/„wartet“/„Fehler“) plus [LoadCheck]. */
@Composable
internal fun LoadStatus(step: LoadStep, count: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (step) {
            LoadStep.WAITING -> Text("wartet", color = Muted, fontSize = 16.sp)
            LoadStep.LOADING -> {
                Spinner(14.dp)
                Text("lädt …", color = Muted, fontSize = 16.sp)
            }
            LoadStep.DONE -> Text(count, color = Muted, fontSize = 16.sp, fontFamily = Mono)
            LoadStep.FAILED -> Text("Fehler", color = Danger, fontSize = 16.sp)
        }
        LoadCheck(step)
    }
}
