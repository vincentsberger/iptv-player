package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.material.icons.rounded.Home

import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.ui.platform.LocalContext
import de.vincentsberger.iptvplayer.shared.DownloadInfo
import de.vincentsberger.iptvplayer.app.download.Downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.size
import androidx.tv.material3.LocalContentColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section

private val RailCollapsed = 84.dp
private val RailExpanded = 280.dp

private fun Tab.icon(): ImageVector = when (this) {
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.LIVE -> Icons.Rounded.LiveTv
    Tab.MOVIES -> Icons.Rounded.Movie
    Tab.SERIES -> Icons.Rounded.VideoLibrary
    Tab.FAVORITES -> Icons.Rounded.Favorite
    Tab.HOME -> Icons.Rounded.Home
    Tab.DOWNLOADS -> Icons.Rounded.DownloadForOffline
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun HomeScreen(vm: AppState) {
    // Unterwegs ohne Internet (z. B. im Hotel): gleich zu den Downloads statt zu einer Fehlermeldung.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!Downloads.isOnline(context) && Downloads.all.any { it.state == DownloadInfo.State.DONE }) vm.selectTab(Tab.DOWNLOADS)
    }
    Box(Modifier.fillMaxSize()) {
        DepthBackground()
        // Nach Anmeldung/Start: Ladebildschirm, bis der aktuelle Bereich da ist – dann weich überblenden.
        // Erst nach 300 ms zeigen: Bei schnellen Servern soll nichts kurz aufblitzen.
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(vm.startupDone) { slow = false; if (!vm.startupDone) { delay(300); slow = true } }
        // Danach die Übersicht (bis „Weiter“), dann die App.
        val overview = vm.overview
        val phase = when { !vm.startupDone -> if (slow) 1 else 0; overview != null -> 2; else -> 3 }
        Crossfade(phase, animationSpec = tween(400), label = "startup") { p ->
            if (p == 0) Box(Modifier.fillMaxSize())
            else if (p == 1) PlaylistLoadingScreen(vm)
            else if (p == 2) PlaylistOverviewScreen(vm, overview ?: emptyList())
            else Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    vm.tab, Modifier.fillMaxSize().padding(start = RailCollapsed),
                    transitionSpec = { tabTransition() }, label = "tab",
                ) { tab ->
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            Tab.HOME -> StartScreen(vm)
                            Tab.LIVE -> LiveTvScreen(vm)
                            Tab.MOVIES -> BrowseScreen(vm, Section.MOVIES)
                            Tab.SERIES -> BrowseScreen(vm, Section.SERIES)
                            Tab.FAVORITES -> FavoritesScreen(vm)
                            Tab.SEARCH -> SearchScreen(vm)
                            Tab.SETTINGS -> SettingsScreen(vm)
                            Tab.DOWNLOADS -> DownloadsScreen(vm)
                        }
                    }
                }
                var railOpen by remember { mutableStateOf(false) }
                NavRail(current = vm.tab, onSelect = vm::selectTab, onExpanded = { railOpen = it })
                // Über der Leiste und ihrer Abdunkelung, bei ausgeklappter Leiste daneben.
                KeyHintBar(Modifier.align(Alignment.BottomStart), start = if (railOpen) RailExpanded + 24.dp else 108.dp)
            }
        }
    }
}

/**
 * Navigationsleiste links: eingeklappt nur Symbole, sobald sie den Fokus bekommt,
 * klappt sie mit Beschriftungen über den Inhalt auf.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(current: Tab, onSelect: (Tab) -> Unit, onExpanded: (Boolean) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { onExpanded(expanded) }
    val width by animateDpAsState(if (expanded) RailExpanded else RailCollapsed, label = "rail")
    val selectedRequester = remember { FocusRequester() }
    // Ausgeklappt: Inhalt dahinter abdunkeln, damit die Leiste klar im Vordergrund steht und nichts durchscheint.
    val scrim by animateFloatAsState(if (expanded) 0.6f else 0f, label = "scrim")
    if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))

    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (expanded) SolidColor(Bg)
                else Brush.horizontalGradient(listOf(Bg.copy(alpha = 0.75f), Bg.copy(alpha = 0.6f)))
            ),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .onFocusChanged { expanded = it.hasFocus }
                .focusProperties { enter = { selectedRequester } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                Box(Modifier.size(48.dp), Alignment.Center) { LogoMark(40.dp) }
                AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
                    Column(Modifier.padding(start = 8.dp)) {
                        Headline("IPTV Player", 21.sp)
                        // Kurz („Di., 24.11. · 13:00“), damit es in der ausgeklappten Leiste in eine Zeile passt.
                        Clock(Modifier.padding(top = 2.dp), pattern = "EE, dd.MM. · HH:mm")
                    }
                }
            }
            Tab.entries.forEach { t ->
                if (t == Tab.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(
                    tab = t,
                    selected = t == current,
                    expanded = expanded,
                    modifier = if (t == current) Modifier.focusRequester(selectedRequester) else Modifier,
                    onClick = { onSelect(t) },
                )
            }
        }
    }
}

@Composable
private fun RailItem(tab: Tab, selected: Boolean, expanded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("tab:${tab.name}").fillMaxWidth().height(48.dp)
            .keyHints(KeyHint(RemoteKey.UP_DOWN, "Bereich wählen"), KeyHint(RemoteKey.OK, "Öffnen"), KeyHint(RemoteKey.LEFT_RIGHT, "zum Inhalt")),
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Surface1 else Color.Transparent,
            contentColor = if (selected) TextColor else Muted,
            focusedContainerColor = FocusBg,
            focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon(), size = 26.dp, tint = if (selected) Signal else LocalContentColor.current)
            if (expanded) {
                Text(tab.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 16.dp))
            }
        }
        SelectionMark(selected, Modifier.align(Alignment.CenterStart), height = 22.dp)
    }
}
