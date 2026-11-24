package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.SmallCorner
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.Surface2

/** Leiser Schimmer (phone.css .sk): Verlauf surface1 → surface2 → surface1 wandert in 1,6 s hin und her. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition()
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse)).value
    drawBehind {
        val w = size.width * 3f // Verlauf dreimal so breit wie die Fläche, wie background-size: 300 %
        val x = -w * 2f / 3f * (1f - p)
        drawRect(Brush.horizontalGradient(0f to Surface1, 0.4f to Surface2, 0.8f to Surface1, 1f to Surface1, startX = x, endX = x + w))
    }
}

/** Grauer Umriss eines Elements, das gleich geladen ist. [small] = 2-dp-Ecke (Textzeilen). */
@Composable
fun SkeletonBox(width: Dp?, height: Dp, modifier: Modifier = Modifier, small: Boolean = false) {
    val sized = if (width != null) modifier.width(width) else modifier
    Box(sized.height(height).clip(if (small) SmallCorner else Corner).shimmer())
}

/** Textbalken mit Breite in Prozent der Zeile. */
@Composable
private fun SkeletonLine(fraction: Float, height: Dp) =
    Box(Modifier.fillMaxWidth(fraction).height(height).clip(SmallCorner).shimmer())

private val ROW_WIDTHS = listOf(48 to 70, 36 to 82, 55 to 64, 42 to 76, 30 to 88, 50 to 60, 38 to 72, 46 to 80, 34 to 66)

/** Platzhalter in der Form von Senderzeilen: Nummer, Logo, Name, Sendung, Fortschritt, Stern (P12). */
@Composable
fun ChannelRowsSkeleton(modifier: Modifier = Modifier, count: Int = 9) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Wird geladen" }) {
        repeat(count) { i ->
            val (a, b) = ROW_WIDTHS[i % ROW_WIDTHS.size]
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(18.dp, 12.dp, Modifier.padding(start = 6.dp), small = true)
                SkeletonBox(44.dp, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(a / 100f, 13.dp)
                    SkeletonLine(b / 100f, 10.dp)
                    SkeletonLine(1f, 3.dp)
                }
                SkeletonBox(20.dp, 20.dp, Modifier.padding(horizontal = 12.dp), small = true)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

/** Platzhalter für Filme/Serien: Startbereich, eine Reihe Querkarten, eine Reihe Poster – das Layout springt nicht. */
@Composable
fun PosterRowsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = false).semantics { contentDescription = "Wird geladen" }) {
        Column(
            Modifier.fillMaxWidth().height(410.dp).background(Color(0xFF1C1F22)).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            SkeletonLine(0.3f, 11.dp)
            Box(Modifier.fillMaxWidth(0.7f).height(40.dp).clip(Corner).shimmer())
            SkeletonLine(0.55f, 13.dp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(46.dp).clip(Corner).shimmer())
                Box(Modifier.weight(1f).height(46.dp).clip(Corner).shimmer())
            }
        }
        SkeletonBox(130.dp, 16.dp, Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp), small = true)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { SkeletonBox(140.dp, 78.dp) }
        }
        SkeletonBox(90.dp, 16.dp, Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp), small = true)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { SkeletonBox(104.dp, 154.dp) }
        }
    }
}
