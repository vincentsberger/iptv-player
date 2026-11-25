package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.formatTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Abstand des Schiebereglers zum Rand der Fläche (halbe Griffbreite) – dort beginnt die Spur. */
private val TimelineInset = 10.dp
private val TimeLabelWidth = 76.dp

/** Zeitleiste: Überfahren zeigt die Zeit an der Mausposition, Ziehen die Zielzeit. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Timeline(value: Float, seeking: Float?, durationMs: Long, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit) {
    var hoverX by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val insetPx = with(density) { TimelineInset.toPx() }
        Slider(
            value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Signal, inactiveTrackColor = Color(0x40FFFFFF)),
            modifier = Modifier.fillMaxWidth()
                .onPointerEvent(PointerEventType.Move) { hoverX = it.changes.first().position.x }
                .onPointerEvent(PointerEventType.Exit) { hoverX = null },
        )
        val fraction = seeking ?: hoverX?.let { timelineFraction(it, widthPx, insetPx) }
        if (fraction != null && durationMs > 0) {
            val labelPx = with(density) { TimeLabelWidth.toPx() }
            val x = (insetPx + fraction * (widthPx - 2 * insetPx) - labelPx / 2).coerceIn(0f, (widthPx - labelPx).coerceAtLeast(0f))
            Text(
                formatTime((fraction * durationMs).toLong()), color = Color.White, fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), -TimeLabelWidth.roundToPx() / 2) }.width(TimeLabelWidth)
                    .background(Color(0xCC000000), Corner).padding(vertical = 4.dp).testTag("timeline-time"),
            )
        }
    }
}

/** − [Wert] + und „Zurücksetzen“ für die Ton-Verzögerung. */
@Composable
internal fun AudioDelayStepper(ms: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp).widthIn(min = 280.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onChange(stepAudioDelay(ms, -1)) }, Modifier.handCursor(), enabled = ms > -AUDIO_DELAY_MAX_MS) {
            Icon(Icons.Rounded.Remove, "Ton früher", tint = TextColor)
        }
        Text(audioDelayLabel(ms), fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.weight(1f).testTag("audio-delay"), textAlign = TextAlign.Center)
        IconButton({ onChange(stepAudioDelay(ms, 1)) }, Modifier.handCursor(), enabled = ms < AUDIO_DELAY_MAX_MS) {
            Icon(Icons.Rounded.Add, "Ton später", tint = TextColor)
        }
    }
    if (ms != 0) Text(
        "Zurücksetzen", color = Signal, fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp).clip(Corner).handCursor().clickable { onChange(0) }.padding(vertical = 4.dp),
    )
}

/** Lautstärke beim Ändern per Taste/Rad: Symbol statt Emoji, Wert in Mono. */
@Composable
internal fun VolumeToast(percent: Int) {
    Row(
        Modifier.background(Color(0xCC000000), Corner).padding(horizontal = 16.dp, vertical = 8.dp).testTag("volume-toast"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (percent == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
        Icon(icon, "Lautstärke", tint = Color.White, modifier = Modifier.size(22.dp))
        Text("$percent %", color = Color.White, fontSize = 20.sp, fontFamily = Mono, modifier = Modifier.padding(start = 10.dp))
    }
}
