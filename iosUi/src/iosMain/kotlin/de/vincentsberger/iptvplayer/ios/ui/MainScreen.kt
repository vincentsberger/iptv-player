package de.vincentsberger.iptvplayer.ios.ui

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
        app.tab == Tab.SETTINGS -> {
            BackHandler { app.selectTab(Tab.HOME) }
            SettingsScreen(app, onBack = { app.selectTab(Tab.HOME) })
        }
        else -> Scaffold(
            containerColor = Bg,
            bottomBar = { TabBar(app.tab) { app.selectTab(it) } },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (app.tab) {
                    Tab.HOME -> HomeTab(app)
                    Tab.LIVE -> LiveTab(app)
                    Tab.MOVIES -> BrowseTab(app, Section.MOVIES)
                    Tab.SERIES -> BrowseTab(app, Section.SERIES)
                    else -> ComingSoon("Suche")
                }
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Surface1) {
        BAR_TABS.forEach { (tab, icon): Pair<Tab, ImageVector> ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, null) },
                label = { Text(tab.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextColor, selectedTextColor = TextColor, indicatorColor = Surface2,
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                ),
            )
        }
    }
}
