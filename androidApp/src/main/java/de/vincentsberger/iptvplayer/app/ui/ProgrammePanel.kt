package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.isOnAir
import de.vincentsberger.iptvplayer.shared.programmeDays

/**
 * Programm des laufenden Senders als Liste über dem Bild (Fernbedienung: ▲ ▼ blättern, OK spielt eine vergangene
 * Sendung aus dem Archiv ab, Zurück schließt). Der Fokus startet bei der laufenden Sendung.
 */
@Composable
internal fun ProgrammePanel(vm: AppState, channel: Item, onPlayed: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(channel.url) { vm.requestProgramme(channel) }
    val programme = vm.programmes[channel.url]
    val now = vm.localNow()
    Column(modifier.width(560.dp).heightIn(max = 560.dp).panel().padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Headline("Programm · ${splitProviderTag(channel.name).name}", 22.sp)
        if (channel.archiveDays > 0) Text("↺ = nachträglich ansehen (letzte ${channel.archiveDays} Tage)", color = Muted, fontSize = 15.sp)
        when {
            programme == null -> Text("Wird geladen …", color = Muted, fontSize = 16.sp)
            programme.isEmpty() -> Text("Für diesen Sender gibt es kein Programm.", color = Muted, fontSize = 16.sp)
            else -> {
                val rows: List<Any> = programmeDays(programme, now).flatMap { d -> listOf<Any>(d.label) + d.entries }
                val current = rows.indexOfFirst { it is EpgEntry && isOnAir(it, now) }.coerceAtLeast(0)
                val state = rememberLazyListState((current - 2).coerceAtLeast(0))
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                LazyColumn(state = state, contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows.size) { i ->
                        when (val r = rows[i]) {
                            is String -> Kicker(r, modifier = Modifier.padding(top = 10.dp))
                            is EpgEntry -> {
                                val past = r.endAt.isNotEmpty() && r.endAt <= now
                                val catchup = past && vm.canCatchUp(channel, r)
                                val label = "${r.start}  ${r.title}" + if (isOnAir(r, now)) "  · läuft" else ""
                                TvButton(
                                    label,
                                    Modifier.fillMaxWidth().then(if (i == current) Modifier.focusRequester(focus) else Modifier).testTag("programme:$i"),
                                    icon = if (catchup) Icons.Rounded.History else null,
                                    selected = isOnAir(r, now),
                                ) { if (catchup) { onPlayed(); vm.playCatchup(channel, r) } }
                            }
                        }
                    }
                }
            }
        }
    }
}
