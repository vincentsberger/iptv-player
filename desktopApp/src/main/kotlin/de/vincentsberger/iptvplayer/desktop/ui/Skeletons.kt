package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Puls zwischen zwei Flächenfarben der Palette – Platzhalter „lädt“, ohne Verlauf. */
fun Modifier.shimmer(): Modifier = composed {
    val color by rememberInfiniteTransition(label = "shimmer")
        .animateColor(Surface1, Surface2, infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse), label = "shimmer-color")
    background(color, Corner)
}

private const val PULSE_MS = 1200

/** Live TV lädt: Kategorien, Senderkarten und Info-Spalte als Platzhalter (Muster TV `LiveSkeleton`). */
@Composable
fun LiveSkeleton() {
    Row(
        Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 24.dp).testTag("live-skeleton"),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(160.dp).height(40.dp).shimmer())
            repeat(SKELETON_CATEGORIES) { Box(Modifier.fillMaxWidth().height(42.dp).shimmer()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(220.dp).height(40.dp).padding(bottom = 8.dp).shimmer())
            repeat(SKELETON_CHANNELS) { Box(Modifier.fillMaxWidth().height(64.dp).shimmer()) }
        }
        Box(Modifier.width(320.dp).fillMaxHeight().shimmer())
    }
}

/** Filme/Serien laden: Kategorien und Kartenraster als Platzhalter (Muster TV `GridSkeleton`). */
@Composable
fun GridSkeleton(wide: Boolean = false) {
    Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 28.dp).testTag("grid-skeleton")) {
        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(SKELETON_CATEGORIES) { Box(Modifier.fillMaxWidth().height(36.dp).shimmer()) }
        }
        Column(Modifier.padding(start = 32.dp, top = 56.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    repeat(if (wide) 3 else 4) { Box(Modifier.size(if (wide) 210.dp else 150.dp, if (wide) 118.dp else 225.dp).shimmer()) }
                }
            }
        }
    }
}

private const val SKELETON_CATEGORIES = 8
private const val SKELETON_CHANNELS = 8

/** Schlichter Kreisel in Bernstein (Fortschritt) – für kurze Wartezeiten ohne eigenes Layout, z. B. die Suche. */
@Composable
fun Spinner(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition(label = "spinner")
        .animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)), label = "angle")
    val signal = Signal
    Canvas(modifier.size(size).testTag("spinner")) {
        drawArc(signal, startAngle = angle, sweepAngle = ARC, useCenter = false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Butt))
    }
}

private const val FULL_TURN = 360f
private const val ARC = 270f
private const val SPIN_MS = 900
