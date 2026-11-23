package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Section

/** Alle Favoriten des Anbieters nach Bereich – erreichbar über den Stern auf der Startseite. */
@Composable
fun FavoritesScreen(app: AppState, onBack: () -> Unit) {
    val rows = Section.entries.map { it to app.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Favoriten", onBack)
        if (rows.isEmpty()) EmptyState(
            Icons.Rounded.StarBorder, "Noch keine Favoriten",
            "Lange auf einen Sender, Film oder eine Serie drücken – dann erscheint er hier.",
        )
        else SectionedItems(app, rows, countInTitle = true, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
