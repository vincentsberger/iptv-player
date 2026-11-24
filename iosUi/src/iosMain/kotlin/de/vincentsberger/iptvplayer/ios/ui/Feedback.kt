package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
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
 * Kurze Rückmeldung nach einer Aktion („Zu Favoriten hinzugefügt“): Einblendung unten für 2 s plus leichter
 * Vibrationsimpuls – langes Drücken soll nie stumm bleiben. Gezeigt von [FeedbackHost] (in IosApp über allem).
 */
object Feedback {
    var message by mutableStateOf<String?>(null); private set
    internal var seq by mutableIntStateOf(0); private set

    fun show(text: String) {
        message = text
        seq++
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }

    internal fun clear() { message = null }
}

@Composable
fun FeedbackHost(modifier: Modifier = Modifier) {
    LaunchedEffect(Feedback.seq) { if (Feedback.message != null) { delay(2000); Feedback.clear() } }
    AnimatedVisibility(Feedback.message != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Text(
            Feedback.message.orEmpty(), color = TextColor, fontSize = 14.sp,
            modifier = Modifier.background(Surface2, Corner).border(0.5.dp, Line, Corner).padding(horizontal = 16.dp, vertical = 10.dp),
        )
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
    Feedback.show(if (was) "$what aus den Favoriten entfernt" else "$what zu den Favoriten hinzugefügt")
}
