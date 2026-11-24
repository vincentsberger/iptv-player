package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.delay

/** Tabs der Leiste unten; Einstellungen erreicht man über das Zahnrad der Startseite. */
private val BAR_TABS = listOf(
    Tab.HOME to Icons.Rounded.Home,
    Tab.LIVE to Icons.Rounded.LiveTv,
    Tab.MOVIES to Icons.Rounded.Movie,
    Tab.SERIES to Icons.Rounded.VideoLibrary,
    Tab.SEARCH to Icons.Rounded.Search,
)

/** Startseite nach der Anmeldung: Ladebildschirm, Übersicht, dann die Tabs. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(app: AppState) {
    // Ladebildschirm erst nach 300 ms – aus dem Zwischenspeicher geht es meist schneller, dann kein Aufblitzen.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(app.startupDone) { slow = false; if (!app.startupDone) { delay(300); slow = true } }
    val overview = app.overview
    when {
        !app.startupDone -> if (slow) LoadingScreen(app) else Box(Modifier.fillMaxSize())
        overview != null -> OverviewScreen(app, overview)
        // Favoriten, Downloads und Einstellungen öffnen sich ganzseitig über die Symbole der Startseite.
        app.tab == Tab.SETTINGS || app.tab == Tab.FAVORITES || app.tab == Tab.DOWNLOADS -> {
            val home = { app.selectTab(Tab.HOME) }
            BackHandler(onBack = home)
            when (app.tab) {
                Tab.FAVORITES -> FavoritesScreen(app, onBack = home)
                Tab.DOWNLOADS -> DownloadsScreen(app, onBack = home)
                else -> SettingsScreen(app, onBack = home)
            }
        }
        else -> Scaffold(
            containerColor = Bg,
            bottomBar = {
                TabBar(app.tab) {
                    // Nochmals auf Live TV tippen: zurück zur Kategorieliste (wie in iOS-Apps üblich).
                    if (it == Tab.LIVE && app.tab == Tab.LIVE) LiveNav.showChannels = false
                    app.selectTab(it)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (app.tab) {
                    Tab.HOME -> HomeTab(app)
                    Tab.LIVE -> LiveTab(app)
                    Tab.MOVIES -> BrowseTab(app, Section.MOVIES)
                    Tab.SERIES -> BrowseTab(app, Section.SERIES)
                    else -> SearchTab(app)
                }
            }
        }
    }
}

/**
 * Tab-Leiste wie unter iOS (Entwurf „21 Tab-Leiste“): durchscheinende Fläche, Haarlinie oben, Symbol 26 pt über
 * Beschriftung 10,5 pt. Auswahl nur über Bernstein in Symbol und Text – keine Material-Pille, keine Fläche.
 */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Bg.copy(alpha = 0.92f))) {
        HorizontalDivider(color = Line, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 7.dp).height(46.dp)) {
            BAR_TABS.forEach { (tab, icon): Pair<Tab, ImageVector> ->
                val selected = current == tab
                val color = if (selected) Signal else Muted
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(tab) }
                        .semantics { this.selected = selected },
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Top),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
                    Text(tab.title, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, lineHeight = 11.5.sp, maxLines = 1)
                }
            }
        }
    }
}
