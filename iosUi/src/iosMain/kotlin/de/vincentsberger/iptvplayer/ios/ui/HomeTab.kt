package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.localDateTime
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.standLabel

/**
 * Startseite: Abo-Info, zuletzt gesehene Sender, Favoriten, Weiterschauen und Neues der letzten 7 Tage.
 * Filme und Folgen starten hier direkt (Detailseiten kommen mit Etappe 3). Leere Reihen erscheinen nicht.
 */
@Composable
fun HomeTab(app: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Headline("IPTV Player", 26.sp, Modifier.weight(1f))
            IconButton({ app.selectTab(Tab.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Einstellungen", tint = TextColor) }
        }
        AccountInfo(app)

        val recent = app.recentChannels
        TileRow("Zuletzt gesehen", recent) { i -> app.open(recent, i) }
        val favorites = app.favoriteItems(Section.LIVE)
        TileRow("Favoriten", favorites) { i -> app.open(favorites, i) }
        val resume = app.continueWatchingAll
        TileRow("Weiterschauen", resume.map { it.item }, progress = resume.map { it.progress }) { i -> app.play(listOf(resume[i].item), 0) }
        val newMovies = app.newMovies
        TileRow("Neue Filme (7 Tage)", newMovies) { i -> app.play(newMovies, i) }
        val newEpisodes = app.newEpisodes
        TileRow("Neue Folgen (7 Tage)", newEpisodes) { i -> app.play(newEpisodes, i) }

        if (recent.isEmpty() && favorites.isEmpty() && resume.isEmpty()) {
            Text(
                "Noch nichts gesehen – in Live TV einen Sender wählen. Favoriten setzt du mit langem Drücken.",
                color = Muted, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** „Abo läuft bis 31.12.2026 · 1 von 2 Verbindungen“ – Warnung, wenn es in ≤ 7 Tagen endet. */
@Composable
private fun AccountInfo(app: AppState) {
    val status = app.accountStatus ?: return
    val info = status.info
    val until = info.expiresAt?.let { "läuft bis " + dateLabel(it) } ?: "unbegrenzt"
    val cons = if (info.maxConnections != null) " · ${info.activeConnections ?: 0} von ${info.maxConnections} Verbindungen" else ""
    val stale = if (app.accountStatusStale) " · Stand vom ${standLabel(status.fetchedAt)}" else ""
    val warn = app.accountExpiresSoon()
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (warn) Icon(Icons.Rounded.Warning, null, tint = Danger, modifier = Modifier.padding(end = 6.dp).size(16.dp))
        Text("Abo $until$cons$stale", color = if (warn) Danger else Muted, fontSize = 13.sp)
    }
}

/** „2026-12-31 …“ → „31.12.2026“ in der Zeitzone des Geräts. */
private fun dateLabel(millis: Long): String {
    val d = localDateTime(millis).substringBefore(' ').split('-')
    return if (d.size == 3) "${d[2]}.${d[1]}.${d[0]}" else ""
}

@Composable
private fun TileRow(title: String, items: List<Item>, progress: List<Float>? = null, onOpen: (Int) -> Unit) {
    if (items.isEmpty()) return
    Column {
        Kicker(title, Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { i, it -> "$i:${it.url}" }) { i, item ->
                Column(Modifier.width(112.dp).clickable { onOpen(i) }) {
                    ChannelLogo(item.logo, 112.dp)
                    progress?.getOrNull(i)?.let { ProgressBar(it, Modifier.fillMaxWidth().padding(top = 4.dp)) }
                    Text(
                        splitProviderTag(item.name).name, color = TextColor, fontSize = 13.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
