package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.SmallCorner
import de.vincentsberger.iptvplayer.ios.ui.Surface2
import de.vincentsberger.iptvplayer.ios.ui.TextColor

/** Fortschritt (Sendung, Weiterschauen, Laden): Spur text @16 %, gespielter Teil in Signal. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(SmallCorner).background(TextColor.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Signal))
    }
}

/** Ring-Fortschritt (Ladebildschirm). [progress] null = unbestimmt, ein Bogen dreht sich. */
@Composable
fun ProgressRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val turn = if (progress == null) {
        val t = rememberInfiniteTransition()
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart)).value
    } else 0f
    Canvas(modifier.size(size)) {
        val stroke = 3.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(Line, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (progress == null) drawArc(Signal, turn - 90f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        else drawArc(Signal, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke))
    }
}

/** Schalter im Entwurf: eckig 46×28, an = Signal-Fläche mit hellem Knopf, aus = surface2 mit grauem Knopf. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val x by animateDpAsState(if (checked) 18.dp else 0.dp)
    Box(
        modifier.size(46.dp, 28.dp).clip(Corner).background(if (checked) Signal else Surface2)
            .border(1.dp, if (checked) Signal else Line, Corner)
            .semantics {
                if (label != null) contentDescription = label
                stateDescription = if (checked) "an" else "aus"
            }
            .pressable(role = Role.Switch) { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(20.dp).clip(SmallCorner).background(if (checked) TextColor else Muted))
    }
}
