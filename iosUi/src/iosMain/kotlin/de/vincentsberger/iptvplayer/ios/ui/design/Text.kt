package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Mono
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.TextColor

// Schriftstufen des v2-Entwurfs (phone.css: .h1 .h2 .h3 .kick .sm .xs .mono). Titel SemiBold mit −2 % Laufweite.

@Composable
private fun Title(text: String, size: TextUnit, modifier: Modifier, color: Color, maxLines: Int, lineHeight: Float) {
    Text(
        text, modifier, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, lineHeight = size * lineHeight,
        letterSpacing = (-0.02f * size.value).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun H1(text: String, modifier: Modifier = Modifier, color: Color = TextColor, maxLines: Int = 2) = Title(text, 32.sp, modifier, color, maxLines, 1.1f)

@Composable
fun H2(text: String, modifier: Modifier = Modifier, color: Color = TextColor, maxLines: Int = 2) = Title(text, 24.sp, modifier, color, maxLines, 1.15f)

@Composable
fun H3(text: String, modifier: Modifier = Modifier, color: Color = TextColor, maxLines: Int = 2) = Title(text, 18.sp, modifier, color, maxLines, 1.2f)

/** Rubrik über einem Titel: Großbuchstaben, 0,1 em gesperrt, gedämpft. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text.uppercase(), modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, lineHeight = 13.sp)
}

/** Fließtext klein (13 sp) – Hinweise, Untertitel. */
@Composable
fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = Muted, textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, fontSize = 13.sp, lineHeight = 18.sp, textAlign = textAlign, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Fußnoten (11,5 sp). */
@Composable
fun TinyText(text: String, modifier: Modifier = Modifier, color: Color = Muted, textAlign: TextAlign? = null) {
    Text(text, modifier, color = color, fontSize = 11.5.sp, lineHeight = 16.5.sp, textAlign = textAlign)
}

/** Zahlen, Uhrzeiten, Sendernummern in IBM Plex Mono. */
@Composable
fun MonoText(
    text: String, modifier: Modifier = Modifier, color: Color = TextColor, size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(text, modifier, color = color, fontSize = size, fontFamily = Mono, fontWeight = weight, maxLines = 1)
}
