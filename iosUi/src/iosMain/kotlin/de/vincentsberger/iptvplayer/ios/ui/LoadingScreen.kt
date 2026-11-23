package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.OverviewRow
import de.vincentsberger.iptvplayer.shared.countLabel
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.overviewNote
import de.vincentsberger.iptvplayer.shared.standLabel

/** Ladebildschirm nach Anmeldung/Start: Fortschritt je Bereich, damit lange Playlists nicht wie ein Hänger wirken. */
@Composable
fun LoadingScreen(app: AppState) {
    Page {
        Headline("Playlist wird geladen", 26.sp)
        Text(app.accountLabel, color = Muted, fontSize = 14.sp)
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Signal, trackColor = Line)
        Section.entries.forEach { s ->
            val raw = app.sections[s]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, Modifier.weight(1f))
                when {
                    raw == null || raw.loading -> CircularProgressIndicator(Modifier.size(14.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp)
                    raw.error != null -> Text("Fehler", color = Danger, fontSize = 14.sp)
                    else -> {
                        Text("${formatCount(app.section(s).items.size)} ${countLabel(s)}", color = Muted, fontSize = 14.sp, fontFamily = Mono)
                        Icon(Icons.Rounded.Check, null, tint = Signal, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                    }
                }
            }
        }
        Text("Große Playlists brauchen beim ersten Laden etwas länger.", color = Muted, fontSize = 13.sp)
    }
}

/** Übersicht nach dem Laden: Anzahl je Bereich, Veränderung, Warnungen – wartet auf „Weiter“. */
@Composable
fun OverviewScreen(app: AppState, rows: List<OverviewRow>) {
    Page {
        Headline("Playlist", 26.sp)
        val stand = rows.mapNotNull { it.loadedAt }.minOrNull()?.let { " · Stand: ${standLabel(it)}" }.orEmpty()
        Text(app.accountLabel + stand, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        rows.forEach { row ->
            val warn = row.warning != null
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.section.title, Modifier.width(90.dp))
                    Text("${formatCount(row.count)} ${countLabel(row.section)}", Modifier.weight(1f), fontFamily = Mono)
                    Icon(if (warn) Icons.Rounded.Warning else Icons.Rounded.Check, null, tint = if (warn) Danger else Signal, modifier = Modifier.size(18.dp))
                }
                val note = overviewNote(row)
                if (note.isNotEmpty()) Text(note, color = if (warn) Danger else Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 90.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Neu laden", Modifier.weight(1f), Icons.Rounded.Refresh) { app.reloadFromNetwork() }
            PrimaryButton("Weiter", Modifier.weight(1f), Icons.AutoMirrored.Rounded.ArrowForward) { app.continueFromOverview() }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) { content() }
}
