package de.vincentsberger.iptvplayer.ios.ui.design

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Danger
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.OnSignal
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface2
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.Palette
import de.vincentsberger.iptvplayer.shared.brand.BRAND_MARK_STATIC
import de.vincentsberger.iptvplayer.shared.brand.BrandMark
import de.vincentsberger.iptvplayer.shared.brand.BrandMarkFrame
import de.vincentsberger.iptvplayer.shared.brand.LoadStep
import de.vincentsberger.iptvplayer.shared.brand.brandMarkFrame
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

private val Flash = Color(Palette.FocusBg)
private val CHECK_BOX = 20.dp
private const val CHECK_RADIUS = 4

/** Logo (Fernseher mit Antenne) als fertiges, ruhendes Bild; [size] = Breite, Höhe = size × 200/240. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    BrandCanvas(size, BRAND_MARK_STATIC, modifier)
}

/**
 * Logo mit Einschalt-Animation für große Ladestellen, optional mit [label] darunter. „Bewegung reduzieren“ (iOS) zeigt
 * das fertige Logo ohne Bewegung.
 */
@Composable
fun LogoLoader(size: Dp, label: String? = null, modifier: Modifier = Modifier) {
    val reduce = remember { UIAccessibilityIsReduceMotionEnabled() }
    val frame = if (reduce) BRAND_MARK_STATIC else {
        val spec = infiniteRepeatable<Float>(tween(BrandMark.CYCLE_MS, easing = LinearEasing), RepeatMode.Restart)
        val t by rememberInfiniteTransition().animateFloat(0f, 1f, spec)
        brandMarkFrame(t)
    }
    Column(
        modifier.semantics { contentDescription = "Wird geladen" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandCanvas(size, frame)
        if (label != null) Text(label, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BrandCanvas(size: Dp, frame: BrandMarkFrame, modifier: Modifier = Modifier) {
    Canvas(modifier.width(size).height(size * BrandMark.VIEW_H / BrandMark.VIEW_W)) { drawBrand(frame) }
}

private fun DrawScope.drawBrand(f: BrandMarkFrame) {
    val k = size.width / BrandMark.VIEW_W
    val stroke = Stroke(BrandMark.STROKE * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Bildfläche (Röhrenblitz): um die Mitte skaliert.
    val cx = (BrandMark.SCREEN_LEFT + BrandMark.SCREEN_RIGHT) / 2
    val cy = (BrandMark.SCREEN_TOP + BrandMark.SCREEN_BOTTOM) / 2
    val w = (BrandMark.SCREEN_RIGHT - BrandMark.SCREEN_LEFT) * f.screenScaleX
    val h = (BrandMark.SCREEN_BOTTOM - BrandMark.SCREEN_TOP) * f.screenScaleY
    if (f.screenAlpha > 0f) drawRect(Flash.copy(alpha = f.screenAlpha), Offset((cx - w / 2) * k, (cy - h / 2) * k), Size(w * k, h * k))
    // Umriss: erst Antenne, dann Gehäuse, zusammen bis zum Anteil `drawn`.
    val antenna = Path().apply {
        moveTo(BrandMark.ANTENNA_X0 * k, BrandMark.ANTENNA_Y0 * k)
        lineTo(BrandMark.ANTENNA_X1 * k, BrandMark.ANTENNA_Y1 * k)
        lineTo(BrandMark.ANTENNA_X2 * k, BrandMark.ANTENNA_Y2 * k)
    }
    val body = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                BrandMark.BODY_LEFT * k, BrandMark.BODY_TOP * k, BrandMark.BODY_RIGHT * k, BrandMark.BODY_BOTTOM * k,
                CornerRadius(BrandMark.CORNER * k),
            ),
        )
    }
    val mA = PathMeasure().apply { setPath(antenna, false) }
    val mB = PathMeasure().apply { setPath(body, false) }
    var left = (mA.length + mB.length) * f.drawn
    for (m in listOf(mA, mB)) {
        val part = minOf(left, m.length)
        if (part > 0f) drawPath(Path().also { m.getSegment(0f, part, it, true) }, TextColor, style = stroke)
        left -= part
    }
    if (f.barAlpha > 0f) {
        val bw = (BrandMark.BAR_RIGHT - BrandMark.BAR_LEFT) * k
        val signalW = bw * BrandMark.BAR_FILL
        val top = BrandMark.BAR_TOP * k
        val bh = (BrandMark.BAR_BOTTOM - BrandMark.BAR_TOP) * k
        drawRect(Surface2.copy(alpha = f.barAlpha), Offset(BrandMark.BAR_LEFT * k + signalW, top), Size(bw - signalW, bh))
        drawRect(Signal.copy(alpha = f.barAlpha), Offset(BrandMark.BAR_LEFT * k, top), Size(signalW, bh))
    }
}

/**
 * Kästchen (20 dp) für einen Ladeschritt: leer (wartet, lädt), gefüllt `Signal` mit Häkchen (fertig), Rand `Danger`
 * mit ✕ (Fehler). Die Beschriftung für Sprachausgabe steht im `contentDescription`.
 */
@Composable
fun LoadCheck(step: LoadStep, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CHECK_RADIUS.dp)
    val desc = when (step) { LoadStep.WAITING -> "wartet"; LoadStep.LOADING -> "lädt"; LoadStep.DONE -> "geladen"; LoadStep.FAILED -> "Fehler" }
    val box = modifier.size(CHECK_BOX).semantics { contentDescription = desc }
    when (step) {
        LoadStep.DONE -> Box(box.background(Signal, shape), Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = OnSignal, modifier = Modifier.size(14.dp))
        }
        LoadStep.FAILED -> Box(box.border(1.5.dp, Danger, shape), Alignment.Center) {
            Icon(Icons.Rounded.Close, null, tint = Danger, modifier = Modifier.size(14.dp))
        }
        else -> Box(box.border(1.5.dp, Muted, shape))
    }
}
