package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import de.vincentsberger.iptvplayer.ios.ui.design.pressable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.delay
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * Kurze Rückmeldung nach einer Aktion („Zu Favoriten hinzugefügt“): Einblendung über der Tab-Leiste plus leichter
 * Vibrationsimpuls – langes Drücken soll nie stumm bleiben. Mit [undo] steht rechts „Rückgängig“ und die Einblendung
 * bleibt länger stehen. Gezeigt von [FeedbackHost] (in IosApp über allem).
 */
object Feedback {
    var message by mutableStateOf<String?>(null); private set
    var icon by mutableStateOf<ImageVector?>(null); private set
    var undo by mutableStateOf<(() -> Unit)?>(null); private set
    internal var seq by mutableIntStateOf(0); private set

    fun show(text: String, icon: ImageVector? = null, undo: (() -> Unit)? = null) {
        message = text
        this.icon = icon
        this.undo = undo
        seq++
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }

    internal fun clear() { message = null; undo = null }
}

/** Sichtdauer: 2 s, mit „Rückgängig“ 4 s – genug Zeit, den Knopf zu treffen. */
private const val SHOW_MS = 2000L
private const val SHOW_WITH_UNDO_MS = 4000L

@Composable
fun FeedbackHost(modifier: Modifier = Modifier) {
    LaunchedEffect(Feedback.seq) {
        if (Feedback.message != null) { delay(if (Feedback.undo != null) SHOW_WITH_UNDO_MS else SHOW_MS); Feedback.clear() }
    }
    // Inhalt merken, damit die Ausblende-Animation nicht leer läuft.
    var shown by remember { mutableStateOf(Triple<String, ImageVector?, (() -> Unit)?>("", null, null)) }
    Feedback.message?.let { shown = Triple(it, Feedback.icon, Feedback.undo) }
    AnimatedVisibility(Feedback.message != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        val (text, icon, undo) = shown
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp).clip(Corner).background(Surface1).border(1.dp, Line, Corner)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = Signal, modifier = Modifier.size(20.dp))
            Text(text, Modifier.weight(1f), color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            if (undo != null) {
                Box(
                    // Den aktuellen Wert auslösen (nach clear() null) – ein zweites Tippen beim Ausblenden tut nichts.
                    Modifier.height(44.dp).pressable { Feedback.undo?.invoke(); Feedback.clear() }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Rückgängig", color = Signal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Favorit umschalten mit Rückmeldung. */
fun toggleFavoriteWithFeedback(app: AppState, item: Item) {
    val was = item.favKey in app.favorites
    app.toggleFavorite(item)
    val what = when {
        item.section == Section.LIVE -> "Sender"
        item.isSeriesFolder -> "Serie"
        item.section == Section.MOVIES -> "Film"
        else -> "Folge"
    }
    Feedback.show(
        if (was) "$what aus den Favoriten entfernt" else "$what zu den Favoriten hinzugefügt",
        if (was) Icons.Rounded.StarBorder else Icons.Rounded.Star,
        undo = { app.setFavorite(item, was) },
    )
}
