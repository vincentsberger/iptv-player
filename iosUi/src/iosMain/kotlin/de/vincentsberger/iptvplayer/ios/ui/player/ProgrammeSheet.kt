package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Bg
import de.vincentsberger.iptvplayer.ios.ui.Headline
import de.vincentsberger.iptvplayer.ios.ui.Kicker
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.LiveBadge
import de.vincentsberger.iptvplayer.ios.ui.Mono
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.epgExcerpt
import de.vincentsberger.iptvplayer.shared.isOnAir
import de.vincentsberger.iptvplayer.shared.programmeDays

/**
 * Programm eines Senders über mehrere Tage, nach Tag gruppiert; die laufende Sendung ist markiert und wird beim
 * Öffnen angezeigt. Vergangene Sendungen im Archiv des Anbieters („Sendung verpasst“) spielen per Tippen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgrammeSheet(app: AppState, channel: Item, onDismiss: () -> Unit) {
    LaunchedEffect(channel.url) { app.requestProgramme(channel) }
    val programme = app.programmes[channel.url]
    val now = app.localNow()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface1) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Headline("Programm · ${splitProviderTag(channel.name).name}", 20.sp, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
            if (channel.archiveDays > 0) Text(
                "Sendungen der letzten ${channel.archiveDays} Tage mit ↺ lassen sich nachträglich ansehen.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp),
            )
            when {
                programme == null -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Signal, trackColor = Line) }
                programme.isEmpty() -> Text("Für diesen Sender gibt es kein Programm.", color = Muted, modifier = Modifier.padding(20.dp))
                else -> {
                    val days = programmeDays(programme, now)
                    // Flache Liste aus Tagesüberschriften und Sendungen – so lässt sich zur laufenden Sendung springen.
                    val rows = days.flatMap { d -> listOf<Any>(d.label) + d.entries }
                    val state = rememberLazyListState()
                    LaunchedEffect(programme) {
                        rows.indexOfFirst { it is de.vincentsberger.iptvplayer.shared.data.EpgEntry && isOnAir(it, now) }
                            .takeIf { it > 1 }?.let { state.scrollToItem(it - 1) }
                    }
                    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(rows.size) { i ->
                            when (val r = rows[i]) {
                                is String -> Kicker(r, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
                                is de.vincentsberger.iptvplayer.shared.data.EpgEntry -> {
                                    val onAir = isOnAir(r, now)
                                    val past = r.endAt.isNotEmpty() && r.endAt <= now
                                    val catchup = past && app.canCatchUp(channel, r)
                                    Row(
                                        Modifier.fillMaxWidth().background(if (onAir) Bg else Surface1)
                                            .clickable(enabled = catchup) { onDismiss(); app.playCatchup(channel, r) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(r.start, color = if (past && !catchup) Line else Muted, fontFamily = Mono, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                                        Column(Modifier.weight(1f)) {
                                            if (onAir) LiveBadge(Modifier.padding(bottom = 2.dp))
                                            Text(
                                                r.title, color = if (past && !catchup) Muted else TextColor, fontSize = 15.sp,
                                                fontWeight = if (onAir) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                            epgExcerpt(r, max = 120)?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                        }
                                        if (catchup) Icon(Icons.Rounded.History, "Nachträglich ansehen", tint = Signal, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
