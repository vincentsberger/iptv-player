package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.ios.ui.design.ChannelTile
import de.vincentsberger.iptvplayer.ios.ui.design.GroupRow
import de.vincentsberger.iptvplayer.ios.ui.design.IconAction
import de.vincentsberger.iptvplayer.ios.ui.design.LandscapeCard
import de.vincentsberger.iptvplayer.ios.ui.design.ListGroup
import de.vincentsberger.iptvplayer.ios.ui.design.PosterCard
import de.vincentsberger.iptvplayer.ios.ui.design.SectionHeader
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.TitleBar
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.HomeRow
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.accountSummary
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.remainingLabel
import de.vincentsberger.iptvplayer.shared.standLabel
import de.vincentsberger.iptvplayer.shared.DownloadInfo

/**
 * Startseite (Entwurf „3 Start“): Begrüßung, Abo-Zeile, darunter Reihen – Sender als Kacheln mit „Jetzt“,
 * Weiterschauen/Folgen/Offline als Querkarten, Filme und Serien als Poster. Reihenfolge und Auswahl aus den
 * Einstellungen („Startseite“); leere Reihen erscheinen nicht.
 */
@Composable
fun HomeTab(app: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = 16.dp),
    ) {
        TitleBar(greeting()) {
            IconAction(Icons.Rounded.StarBorder, "Favoriten", { app.selectTab(Tab.FAVORITES) })
            IconAction(Icons.Rounded.DownloadForOffline, "Downloads", { app.selectTab(Tab.DOWNLOADS) })
            IconAction(Icons.Rounded.Settings, "Einstellungen", { app.selectTab(Tab.SETTINGS) })
        }
        AccountLine(app)

        val recent = app.recentChannels
        val resume = app.continueWatchingAll
        val favoriteLive = app.favoriteItems(Section.LIVE)
        app.homeRows.forEach { row ->
            when (row) {
                HomeRow.RECENT_CHANNELS -> ChannelTileRow(app, row.title, recent)
                HomeRow.FAVORITE_LIVE -> ChannelTileRow(app, row.title, favoriteLive)
                HomeRow.CONTINUE -> CardRow(row.title, resume.map { it.item }) { i, item ->
                    val subtitle = (if (item.section == Section.SERIES) app.episodeLabel(item) else null) ?: remainingLabel(resume[i])
                    LandscapeCard(item, titleOf(item), subtitle, resume[i].progress, { app.play(listOf(item), 0) })
                }
                HomeRow.NEW_EPISODES -> app.newEpisodes.let { l ->
                    CardRow(row.title, l, onAll = { BrowseNav.selected[Section.SERIES] = AppState.BROWSE_NEW; app.showNewInBrowse(Section.SERIES) }) { i, item ->
                        LandscapeCard(item, titleOf(item), app.episodeLabel(item), null, { app.play(l, i) })
                    }
                }
                HomeRow.OFFLINE -> IosDownloads.all.filter { it.state == DownloadInfo.State.DONE }.map { it.item }.let { l ->
                    CardRow(row.title, l, onAll = { app.selectTab(Tab.DOWNLOADS) }) { i, item ->
                        LandscapeCard(item, titleOf(item), app.episodeLabel(item), null, { app.play(l, i) })
                    }
                }
                HomeRow.NEW_MOVIES -> app.newMovies.let { l -> PosterRow(app, row.title, l, onAll = { BrowseNav.selected[Section.MOVIES] = AppState.BROWSE_NEW; app.showNewInBrowse(Section.MOVIES) }) }
                HomeRow.FAVORITE_MOVIES -> PosterRow(app, row.title, app.favoriteItems(Section.MOVIES))
                HomeRow.FAVORITE_SERIES -> PosterRow(app, row.title, app.favoriteItems(Section.SERIES))
            }
        }
        // Neuheiten gibt es erst, wenn Kategorien gewählt sind – sonst bliebe die Funktion unentdeckt.
        if (app.supportsDashboard && !app.dashboardConfigured) ListGroup(
            listOf {
                GroupRow(
                    "Wähle, was dich interessiert",
                    subtitle = "Dann zeigt die Startseite neue Filme und Folgen der letzten 7 Tage aus diesen Kategorien. Einstellungen → Listen.",
                    onClick = { app.selectTab(Tab.SETTINGS) },
                )
            },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )

        if (recent.isEmpty() && favoriteLive.isEmpty() && resume.isEmpty()) SmallText(
            "Noch nichts gesehen – in Live TV einen Sender wählen. Favoriten setzt du mit langem Drücken.",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
    }
}

private fun titleOf(item: Item) = splitProviderTag(item.name).name

/** „Abo bis 14.03.2027 · 1 von 2 Verbindungen“ – Warnung, wenn es in ≤ 7 Tagen endet; „Stand vom …“, wenn veraltet. */
@Composable
private fun AccountLine(app: AppState) {
    val status = app.accountStatus ?: return
    val stale = if (app.accountStatusStale) " · Stand vom ${standLabel(status.fetchedAt)}" else ""
    val warn = app.accountExpiresSoon()
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (warn) Icon(Icons.Rounded.Warning, null, tint = Danger, modifier = Modifier.padding(end = 6.dp).size(16.dp))
        SmallText(accountSummary(status.info) + stale, color = if (warn) Danger else Muted)
    }
}

/** Reihe mit Überschrift und waagerechter Liste; leer → nichts. */
@Composable
private fun CardRow(title: String, items: List<Item>, onAll: (() -> Unit)? = null, card: @Composable (Int, Item) -> Unit) {
    if (items.isEmpty()) return
    SectionHeader(title, onAll = onAll)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items, key = { i, it -> "$i:${it.url}" }) { i, item -> card(i, item) }
    }
}

@Composable
private fun ChannelTileRow(app: AppState, title: String, items: List<Item>) =
    CardRow(title, items) { i, item -> ChannelTile(app, item, { app.open(items, i) }) }

@Composable
private fun PosterRow(app: AppState, title: String, items: List<Item>, onAll: (() -> Unit)? = null) =
    CardRow(title, items, onAll) { i, item ->
        PosterCard(item, item.favKey in app.favorites, { app.open(items, i) }, onLongClick = { toggleFavoriteWithFeedback(app, item) })
    }

/** „Guten Morgen“ bis 11 Uhr, „Guten Tag“ bis 18 Uhr, danach „Guten Abend“ – nach der Uhr des iPhones. */
private fun greeting(): String {
    val hour = localDateTime(currentTimeMillis()).substringAfter(' ').substringBefore(':').toIntOrNull() ?: 12
    return when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        else -> "Guten Abend"
    }
}
