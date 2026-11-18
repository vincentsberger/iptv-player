package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.app.download.DownloadInfo
import de.vincentsberger.iptvplayer.app.download.Downloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import java.util.Calendar

/** Begrüßung nach Tageszeit – die Startseite soll nicht wie eine Liste wirken. */
internal fun greeting(hour: Int): String = when (hour) {
    in 5..10 -> "Guten Morgen"
    in 11..17 -> "Guten Tag"
    else -> "Guten Abend"
}

/**
 * Startseite: alles, womit man meistens weitermacht, auf einen Blick –
 * angefangene Filme/Folgen, zuletzt gesehene Sender, Downloads und Favoriten.
 */
@Composable
fun StartScreen(vm: AppState) {
    val menu = LocalMenu.current
    val rf = rememberRowFocus(vm)
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val rows: List<Pair<String, List<Item>>> = buildList {
        add("Weiterschauen" to vm.continueWatchingAll.map { it.item })
        add("Zuletzt gesehene Sender" to vm.recentChannels)
        add("Offline verfügbar" to Downloads.all.filter { it.state == DownloadInfo.State.DONE }.map { it.item })
        Section.entries.forEach { s -> add("Favoriten · ${s.title}" to vm.favoriteItems(s)) }
    }.filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(greeting(hour), "Hier geht es dort weiter, wo du aufgehört hast.", Icons.Rounded.Home)
        if (rows.isEmpty()) {
            EmptyView(
                "Willkommen",
                "Sobald du etwas ansiehst oder Favoriten speicherst, findest du es hier wieder. Starte am besten mit Live TV.",
                Icons.Rounded.LiveTv,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(rows, key = { it.first }) { (title, list) ->
                    ContentRow(
                        title, list, "start:$title", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key }, vm::open, { menu.current = itemMenu(vm, it) },
                    )
                }
            }
        }
    }
}
