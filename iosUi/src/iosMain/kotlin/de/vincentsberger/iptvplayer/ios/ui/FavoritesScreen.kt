package de.vincentsberger.iptvplayer.ios.ui

import de.vincentsberger.iptvplayer.ios.ui.design.BackBar
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

/** Favoriten (Entwurf „11“): alles Gemerkte nach Bereich, per Chip gefiltert – erreichbar über den Stern auf der Startseite. */
@Composable
fun FavoritesScreen(app: AppState, onBack: () -> Unit) {
    val rows = Section.entries.map { it to app.favoriteItems(it) }.filter { it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        BackBar("Start", onBack)
        de.vincentsberger.iptvplayer.ios.ui.design.H1("Favoriten", Modifier.padding(horizontal = 16.dp))
        if (rows.isEmpty()) EmptyState(
            Icons.Rounded.StarBorder, "Noch keine Favoriten",
            "Stern antippen oder lange auf einen Sender, Film oder eine Serie drücken – dann erscheint er hier.",
        )
        else SectionedItems(app, rows, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
