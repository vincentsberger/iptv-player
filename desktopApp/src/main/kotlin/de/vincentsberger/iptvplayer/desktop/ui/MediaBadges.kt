package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Info-Abzeichen in der Player-Leiste („FHD“, „5.1“, „DE“, „UT“) – kleine Mono-Kästchen wie das Länder-Etikett. */
@Composable
fun MediaBadges(badges: List<String>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach {
            Text(it, color = Color.White, fontFamily = Mono, fontSize = 12.sp,
                modifier = Modifier.border(1.dp, Color(0x80FFFFFF), SmallCorner).padding(horizontal = 6.dp, vertical = 1.dp))
        }
    }
}
