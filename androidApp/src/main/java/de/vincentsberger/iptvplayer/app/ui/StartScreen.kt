package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.ui.text.font.FontWeight
import de.vincentsberger.iptvplayer.shared.Tab

import androidx.tv.material3.Text

import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Row

import androidx.compose.material.icons.rounded.SystemUpdate

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
    StartScreenContent(vm)
}

/** Neu-Reihen zeigen höchstens so viele Einträge; „Alle anzeigen“ öffnet den Rest. */
private const val NEW_ROW_LIMIT = 30

/** „Abo: läuft bis 14.03.2027 · 1 von 2 Verbindungen“ – rot, wenn das Abo in höchstens 7 Tagen abläuft. */
@Composable
private fun AccountLine(vm: AppState) {
    val status = vm.accountStatus ?: return
    val info = status.info
    val until = info.expiresAt?.let { "läuft bis " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) } ?: "unbegrenzt"
    val cons = if (info.maxConnections != null) " · ${info.activeConnections ?: 0} von ${info.maxConnections} Verbindungen" else ""
    val stale = if (vm.accountStatusStale) " · Stand vom ${standLabel(status.fetchedAt)}" else ""
    Text("Abo: $until$cons$stale", color = if (vm.accountExpiresSoon()) Danger else Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
}

/** Solange keine Kategorie gewählt ist: Hinweis statt der Neu-Reihen. */
@Composable
private fun ChooseCategoriesCard(vm: AppState) {
    Row(
        Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp).panel().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wähle, was dich interessiert", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Dann zeigt die Startseite neue Filme und Folgen aus diesen Kategorien.", color = Muted, fontSize = 15.sp)
        }
        TvButton("Kategorien wählen", Modifier.testTag("start:choose"), style = ButtonStyle.Primary) { vm.selectTab(Tab.SETTINGS) }
    }
}

@Composable
private fun StartScreenContent(vm: AppState) {
    val menu = LocalMenu.current
    val rf = rememberRowFocus(vm)
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    // Reihen mit optionalem „Alle anzeigen“ (Neu-Reihen öffnen Filme/Serien mit „Neu (7 Tage)“).
    val rows: List<Triple<String, List<Item>, (() -> Unit)?>> = buildList {
        add(Triple("Weiterschauen", vm.continueWatchingAll.map { it.item }, null))
        if (vm.supportsDashboard) {
            add(Triple("Neue Filme", vm.newMovies.take(NEW_ROW_LIMIT), { vm.showNewInBrowse(Section.MOVIES) }))
            add(Triple("Neue Folgen", vm.newEpisodes.take(NEW_ROW_LIMIT), { vm.showNewInBrowse(Section.SERIES) }))
        }
        add(Triple("Zuletzt gesehene Sender", vm.recentChannels, null))
        add(Triple("Offline verfügbar", Downloads.all.filter { it.state == DownloadInfo.State.DONE }.map { it.item }, null))
        Section.entries.forEach { s -> add(Triple("Favoriten · ${s.title}", vm.favoriteItems(s), null)) }
    }.filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(greeting(hour), "Hier geht es dort weiter, wo du aufgehört hast.", Icons.Rounded.Home)
        // Neue Version verfügbar: Hinweis mit Abkürzung zu den Einstellungen (dort wird installiert).
        vm.availableUpdate?.let { update ->
            Row(
                Modifier.padding(start = 48.dp, bottom = 8.dp).panel().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.SystemUpdate, tint = Signal, size = 26.dp)
                Text("Version ${update.version} ist verfügbar.", fontSize = 18.sp)
                TvButton("Zum Update", Modifier.testTag("start:update"), style = ButtonStyle.Primary) { vm.selectTab(Tab.SETTINGS) }
            }
        }
        if (vm.supportsDashboard) {
            AccountLine(vm)
            if (!vm.dashboardConfigured) ChooseCategoriesCard(vm)
        }
        if (rows.isEmpty()) {
            EmptyView(
                "Willkommen",
                "Sobald du etwas ansiehst oder Favoriten speicherst, findest du es hier wieder. Starte am besten mit Live TV.",
                Icons.Rounded.LiveTv,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(rows, key = { it.first }) { (title, list, showAll) ->
                    ContentRow(
                        title, list, "start:$title", rf.restoreKey, rf.requester,
                        vm.favorites, vm::progressOf, { _, key -> vm.focusKey = key },
                        // „Weiterschauen“ spielt direkt – die Entscheidung ist schon gefallen; sonst Film → Detailseite.
                        if (title == "Weiterschauen") { l: List<Item>, i: Int -> vm.play(l, i) } else vm::open,
                        { menu.current = itemMenu(vm, it) },
                        showAll,
                    )
                }
            }
        }
    }
}
