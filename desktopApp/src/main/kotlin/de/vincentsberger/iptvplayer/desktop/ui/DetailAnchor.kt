package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.Item

/**
 * Wo zuletzt eine Kachel angeklickt wurde (Mittelpunkt in Fensterkoordinaten, px). Das Detail-Fenster wächst von dort
 * aus auf. Reiner PC-Zustand – `AppState` bleibt unberührt.
 */
class DetailAnchor {
    var tileCenter: Offset? by mutableStateOf(null)

    /** Fokus der angeklickten Kachel – nach dem Schließen kehrt die Tastatur dorthin zurück. */
    var tileFocus: FocusRequester? = null
}

val LocalDetailAnchor = staticCompositionLocalOf { DetailAnchor() }

/** Grund-Ebene unter dem Detail-Fenster: Film und Serie liegen über der Startseite, die dabei stehen bleibt. */
internal fun baseScreen(screen: Screen): Screen = if (screen is Screen.Movie || screen is Screen.Series) Screen.Home else screen

/** Titel, den das Detail-Fenster zeigt – null, wenn kein Fenster offen ist. */
internal fun detailItemOf(screen: Screen): Item? = when (screen) {
    is Screen.Movie -> screen.movie
    is Screen.Series -> screen.series
    else -> null
}

/** Ursprung der Skalierung relativ zum Fenster [modal]: dort, wo die Kachel lag; ohne Kachel die Mitte. */
internal fun modalTransformOrigin(tileCenter: Offset?, modal: Rect): TransformOrigin {
    if (tileCenter == null || modal.width <= 0f || modal.height <= 0f) return TransformOrigin.Center
    return TransformOrigin(
        ((tileCenter.x - modal.left) / modal.width).coerceIn(0f, 1f),
        ((tileCenter.y - modal.top) / modal.height).coerceIn(0f, 1f),
    )
}
