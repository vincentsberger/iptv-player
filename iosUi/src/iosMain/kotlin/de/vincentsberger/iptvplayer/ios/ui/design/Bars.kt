package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.TextColor

// Kopfzeilen und Überschriften des v2-Entwurfs (phone.css .nav .ib .back .sec .more .gh).

/** Kopfzeile eines Hauptbereichs: großer Titel links, Symbol-Knöpfe rechts. */
@Composable
fun TitleBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        H1(title, Modifier.weight(1f).semantics { heading() }, maxLines = 1)
        actions()
    }
}

/** Kopfzeile einer Unterseite: „‹ Live TV“ zurück, Symbol-Knöpfe rechts. */
@Composable
fun BackBar(label: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(44.dp).semantics { contentDescription = "Zurück zu $label" }.pressable(onClick = onBack).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = TextColor, modifier = Modifier.size(26.dp))
            Text(label, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        actions()
    }
}

/** Symbol-Knopf 44×44 (Mindestgröße zum Antippen). */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = TextColor) {
    Box(modifier.size(44.dp).semantics { contentDescription = description }.pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Überschrift einer Reihe, rechts optional „Alle ›“. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onAll: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        H3(title, Modifier.weight(1f).semantics { heading() }, maxLines = 1)
        if (onAll != null) Row(
            Modifier.heightIn(min = 32.dp).semantics { contentDescription = "Alle: $title" }.pressable(onClick = onAll).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Alle", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** Kleine Gruppen-Überschrift über einer [ListGroup] („KATEGORIEN“). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, start: androidx.compose.ui.unit.Dp = 30.dp) {
    Text(
        text.uppercase(), modifier.padding(start = start, end = 30.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.72.sp,
    )
}
