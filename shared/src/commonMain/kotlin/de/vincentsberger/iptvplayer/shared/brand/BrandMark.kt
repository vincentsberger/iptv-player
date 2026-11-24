package de.vincentsberger.iptvplayer.shared.brand

import kotlin.math.floor

/** Geometrie des Logos im ViewBox 240 × 200 (wie das App-Icon) und Zyklusdauer der Einschalt-Animation. */
object BrandMark {
    const val VIEW_W = 240f
    const val VIEW_H = 200f
    const val STROKE = 12f
    const val CORNER = 16f

    /** Dauer eines Zyklus der Animation in Millisekunden. */
    const val CYCLE_MS = 4_000

    /** Antenne als Linienzug `M84 20 L120 52 L156 20`: Start, Spitze, Ende. */
    const val ANTENNA_X0 = 84f
    const val ANTENNA_Y0 = 20f
    const val ANTENNA_X1 = 120f
    const val ANTENNA_Y1 = 52f
    const val ANTENNA_X2 = 156f
    const val ANTENNA_Y2 = 20f

    /** Gehäuse (Strichmitte): x 24..216, y 56..188. */
    const val BODY_LEFT = 24f
    const val BODY_TOP = 56f
    const val BODY_RIGHT = 216f
    const val BODY_BOTTOM = 188f

    /** Bildfläche innen (Gehäuse abzüglich halber Strichstärke). */
    const val SCREEN_LEFT = BODY_LEFT + STROKE / 2
    const val SCREEN_TOP = BODY_TOP + STROKE / 2
    const val SCREEN_RIGHT = BODY_RIGHT - STROKE / 2
    const val SCREEN_BOTTOM = BODY_BOTTOM - STROKE / 2

    /** Balken: x 52..188, y 140..158; die ersten 60 % in `Signal`, der Rest `Surface2`. */
    const val BAR_LEFT = 52f
    const val BAR_TOP = 140f
    const val BAR_RIGHT = 188f
    const val BAR_BOTTOM = 158f
    const val BAR_FILL = 0.6f
}

/**
 * Ein Bild der Animation. [drawn] = Anteil der gezeichneten Umrisse, [screenScaleX]/[screenScaleY] = Größe der Bildfläche
 * (Röhrenblitz), [screenAlpha] = Deckkraft der Bildfläche, [barAlpha] = Deckkraft des Balkens. Alle Werte 0..1.
 */
data class BrandMarkFrame(val drawn: Float, val screenScaleX: Float, val screenScaleY: Float, val screenAlpha: Float, val barAlpha: Float)

private const val DRAW_END = 0.35f
private const val CRT_START = 0.30f
private const val CRT_LINE = 0.38f
private const val CRT_END = 0.46f
private const val BAR_START = 0.44f
private const val BAR_END = 0.50f
private const val SCREEN_IDLE_ALPHA = 0.06f
private const val CRT_LINE_HEIGHT = 0.04f

/** Linear zwischen [from] und [to] bei [x] in [x0]..[x1] (außerhalb geklemmt). */
private fun ramp(x: Float, x0: Float, x1: Float, from: Float, to: Float): Float =
    when {
        x <= x0 -> from
        x >= x1 -> to
        else -> from + (to - from) * ((x - x0) / (x1 - x0))
    }

/** Bild der Animation bei [t] = Anteil im Zyklus; Werte außerhalb 0..1 werden modulo 1 genommen. */
fun brandMarkFrame(t: Float): BrandMarkFrame {
    val p = if (t in 0f..1f) t else t - floor(t)
    return BrandMarkFrame(
        drawn = ramp(p, 0f, DRAW_END, 0f, 1f),
        screenScaleX = ramp(p, CRT_START, CRT_LINE, 0f, 1f),
        screenScaleY = if (p < CRT_LINE) CRT_LINE_HEIGHT else ramp(p, CRT_LINE, CRT_END, CRT_LINE_HEIGHT, 1f),
        screenAlpha = if (p < CRT_LINE) ramp(p, CRT_START, CRT_LINE, 0f, 1f) else ramp(p, CRT_END, BAR_END, 1f, SCREEN_IDLE_ALPHA),
        barAlpha = ramp(p, BAR_START, BAR_END, 0f, 1f),
    )
}

/** Fertig gezeichnetes Logo ohne Bewegung: Bild aus, Balken sichtbar. */
val BRAND_MARK_STATIC = BrandMarkFrame(drawn = 1f, screenScaleX = 1f, screenScaleY = 1f, screenAlpha = SCREEN_IDLE_ALPHA, barAlpha = 1f)
