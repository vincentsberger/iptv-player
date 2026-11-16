package de.tvplayer.app.ui

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
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Tab
import de.tvplayer.shared.data.Section

private val RailCollapsed = 84.dp
private val RailExpanded = 250.dp

private fun Tab.icon(): ImageVector = when (this) {
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.LIVE -> Icons.Rounded.LiveTv
    Tab.MOVIES -> Icons.Rounded.Movie
    Tab.SERIES -> Icons.Rounded.VideoLibrary
    Tab.FAVORITES -> Icons.Rounded.Favorite
    Tab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun HomeScreen(vm: AppState) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = RailCollapsed)) {
            key(vm.tab) {
                when (vm.tab) {
                    Tab.LIVE -> LiveTvScreen(vm)
                    Tab.MOVIES -> BrowseScreen(vm, Section.MOVIES)
                    Tab.SERIES -> BrowseScreen(vm, Section.SERIES)
                    Tab.FAVORITES -> FavoritesScreen(vm)
                    Tab.SEARCH -> SearchScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
        NavRail(current = vm.tab, onSelect = vm::selectTab)
    }
}

/**
 * Navigationsleiste links: eingeklappt nur Symbole, sobald sie den Fokus bekommt,
 * klappt sie mit Beschriftungen über den Inhalt auf.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavRail(current: Tab, onSelect: (Tab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailExpanded else RailCollapsed, label = "rail")
    val selectedRequester = remember { FocusRequester() }

    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Color(0xF2080A10), Color(0xE6080A10)))
                else Brush.horizontalGradient(listOf(Color(0xFF080A10), Color(0xFF080A10)))
            ),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 28.dp)
                .onFocusChanged { expanded = it.hasFocus }
                .focusProperties { enter = { selectedRequester } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 28.dp)) {
                LogoTile(48.dp)
                AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
                    Text("TV Player", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
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
        modifier = modifier.testTag("tab:${tab.name}").fillMaxWidth().height(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Surface2 else Color.Transparent,
            contentColor = if (selected) Color.White else Muted,
            focusedContainerColor = FocusBg,
            focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon(), size = 26.dp)
            if (expanded) {
                Text(tab.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 16.dp))
            }
        }
        if (selected && !expanded) {
            Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(22.dp).background(AccentGradient, RoundedCornerShape(2.dp)))
        }
    }
}
