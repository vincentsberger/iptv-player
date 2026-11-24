package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.SmallCorner
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.initialsOf

/** Kennzeichen, die im Entwurf hell erscheinen (Bildqualität); RAW, HEVC, 60 FPS bleiben gedämpft. */
fun isQualityBadge(tag: String): Boolean = tag.uppercase() in setOf("HD", "FHD", "UHD", "4K", "8K")

/** Kleines Kennzeichen (phone.css .tag): 16 dp hoch, Haarlinie, 9,5 sp gesperrt. [highlight] = hell (HD/4K). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, highlight: Boolean = isQualityBadge(text)) {
    Box(
        modifier.height(16.dp).border(1.dp, if (highlight) TextColor.copy(alpha = 0.45f) else Line, SmallCorner).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (highlight) TextColor else Muted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, lineHeight = 10.sp, maxLines = 1)
    }
}

/** LIVE-Kennzeichen: roter Punkt, „LIVE“ in Weiß. [onImage] = mit dunkler Unterlage für Bilder. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, onImage: Boolean = false) {
    val base = if (onImage) modifier.background(Color(0xB80A0B0D), SmallCorner).padding(horizontal = 6.dp, vertical = 4.dp) else modifier
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(Signal, CircleShape))
        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, lineHeight = 11.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Hintergrund der Logo-Kachel: hell, damit auch dunkle (transparente) Logos sichtbar bleiben (P18). */
private val LogoTile = TextColor
private val LogoInk = Color(0xFF16181B)

/**
 * Senderlogo auf heller Kachel. Bis das Bild da ist – oder ohne/bei kaputtem Logo – stehen die Initialen des
 * Sendernamens darauf (ohne Länderkürzel: „DE: Das Erste“ → „DE“ von „Das Erste“).
 */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.size(size).clip(Corner).background(LogoTile), contentAlignment = Alignment.Center) {
        if (!loaded) {
            val initials = remember(name) { initialsOf(splitProviderTag(name).name) }
            Text(initials, color = LogoInk, fontSize = (size.value * 13f / 44f).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02f * size.value * 13f / 44f).sp, maxLines = 1)
        }
        if (!url.isNullOrBlank()) {
            // AsyncImage statt SubcomposeAsyncImage: Subcomposition je Zeile wäre in langen Listen zu teuer.
            AsyncImage(
                url, null, Modifier.fillMaxSize().padding(size * 3f / 44f), contentScale = ContentScale.Fit,
                onSuccess = { loaded = true }, onError = { loaded = false },
            )
        }
    }
}
