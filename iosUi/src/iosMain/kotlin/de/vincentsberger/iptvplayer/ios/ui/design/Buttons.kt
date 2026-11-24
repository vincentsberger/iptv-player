package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Bg
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.OnSignal
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.Surface2
import de.vincentsberger.iptvplayer.ios.ui.TextColor

enum class ButtonStyle { Primary, Secondary, Glass }

/** Regular 46 dp / 16 sp, Small 34 dp / 14 sp (phone.css .btn, .btn.sm). */
enum class ButtonSize { Regular, Small }

/**
 * Antippen wie unter iOS: kein Wellen-Effekt, beim Drücken kurz abgeblendet. [role] für VoiceOver.
 */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    // Steht pressable vor background/border in der Kette, blendet das Abdunkeln die ganze Fläche ab (alpha wirkt
    // nur auf das, was in der Kette danach kommt).
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    this.alpha(if (pressed) 0.6f else 1f).clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** 2-dp-Signal-Strich unten – so zeigt der Entwurf eine Auswahl (nie nur über die Fläche). */
fun Modifier.selectionUnderline(selected: Boolean): Modifier =
    if (!selected) this else drawBehind {
        val h = 2.dp.toPx()
        drawRect(Signal, Offset(0f, size.height - h), Size(size.width, h))
    }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    size: ButtonSize = ButtonSize.Regular,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val (bg, border, fg) = when (style) {
        ButtonStyle.Primary -> Triple(Signal, Signal, OnSignal)
        ButtonStyle.Secondary -> Triple(Surface1, Line, TextColor)
        ButtonStyle.Glass -> Triple(Color(0xB82B2F34), Color(0x33E6E8EB), TextColor)
    }
    val small = size == ButtonSize.Small
    Row(
        modifier.alpha(if (enabled) 1f else 0.45f).pressable(enabled, onClick = onClick)
            .height(if (small) 34.dp else 46.dp).clip(Corner).background(bg).border(1.dp, border, Corner)
            .padding(horizontal = if (small) 12.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (small) 16.dp else 20.dp))
        Text(
            text, color = fg, fontSize = if (small) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = (if (small) -0.14 else -0.16).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Umschalter aus gleich breiten Feldern (Login: Xtream/M3U). Aktives Feld: surface2 und Signal-Strich unten. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(40.dp).clip(Corner).background(Bg).border(1.dp, Line, Corner)) {
        options.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.fillMaxHeight().size(width = 1.dp, height = 40.dp).background(Line))
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().semantics { this.selected = on }.pressable(role = Role.Tab) { onSelect(i) }
                    .background(if (on) Surface2 else Color.Transparent).selectionUnderline(on),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) TextColor else Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Auswahl-Chip (Kategorien, Länder). Aktiv: Rand und Strich in Signal. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.semantics { this.selected = selected }.pressable(role = Role.Checkbox, onClick = onClick)
            .height(if (small) 30.dp else 34.dp).clip(Corner).background(Surface1)
            .border(1.dp, if (selected) Signal else Line, Corner).selectionUnderline(selected)
            .padding(horizontal = if (small) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, color = TextColor, fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
