package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.DownloadInfo
import de.vincentsberger.iptvplayer.shared.DownloadRules
import de.vincentsberger.iptvplayer.shared.downloadStartMessage
import de.vincentsberger.iptvplayer.shared.downloadStatus
import de.vincentsberger.iptvplayer.shared.formatBytes
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

/** Downloads: was offline auf dem iPhone liegt oder gerade lädt. Tippen auf einen fertigen Titel spielt ihn ab. */
@Composable
fun DownloadsScreen(app: AppState, onBack: () -> Unit) {
    LaunchedEffect(Unit) { IosDownloads.start() }
    val all = IosDownloads.all
    var confirm by remember { mutableStateOf<DownloadInfo?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Downloads", onBack)
        if (all.isEmpty()) {
            EmptyState(
                Icons.Rounded.DownloadForOffline, "Noch nichts heruntergeladen",
                "Filme lädst du auf ihrer Detailseite, Folgen und ganze Staffeln in der Serienansicht. So kannst du sie unterwegs ohne Internet ansehen.",
            )
            return@Column
        }
        Text(
            "${all.size} Titel · ${formatBytes(IosDownloads.usedBytes())} belegt · noch ${formatBytes(IosDownloads.freeBytes())} frei",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val done = all.filter { it.state == DownloadInfo.State.DONE }.map { it.item }
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(all, key = { it.item.url }) { info ->
                DownloadRow(
                    info,
                    onPlay = { app.play(done, done.indexOf(info.item).coerceAtLeast(0)) },
                    onRetry = { IosDownloads.retry(info.item.url) },
                    onRemove = { confirm = info },
                )
            }
        }
    }
    confirm?.let { info ->
        val finished = info.state == DownloadInfo.State.DONE
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (finished) "Download löschen?" else "Download abbrechen?") },
            text = { Text("„${splitProviderTag(info.item.name).name}“ wird vom iPhone entfernt.") },
            confirmButton = { TextButton({ confirm = null; IosDownloads.remove(info.item.url) }) { Text(if (finished) "Löschen" else "Abbrechen", color = Danger) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Behalten") } },
            containerColor = Surface1,
        )
    }
}

@Composable
private fun DownloadRow(info: DownloadInfo, onPlay: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val done = info.state == DownloadInfo.State.DONE
    Row(
        Modifier.fillMaxWidth().clickable(enabled = done, onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(Corner).background(Surface1)) {
            Icon(Icons.Rounded.Movie, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(20.dp))
            if (!info.item.logo.isNullOrBlank()) AsyncImage(info.item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                splitProviderTag(info.item.name).name, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(downloadStatus(info), color = if (info.state == DownloadInfo.State.FAILED) Danger else Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            if (info.state == DownloadInfo.State.LOADING) ProgressBar(info.progress ?: 0f, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        if (info.state == DownloadInfo.State.FAILED) IconButton(onRetry) { Icon(Icons.Rounded.Refresh, "Erneut laden", tint = TextColor) }
        IconButton(onRemove) { Icon(Icons.Rounded.DeleteOutline, if (done) "Löschen" else "Abbrechen", tint = Muted) }
    }
}

/**
 * Knopf „Herunterladen“ für Film und Folge: zeigt den Zustand (lädt mit Prozent, offline verfügbar, Fehler).
 * [compact] = nur Symbol (Folgenliste). Meldungen (zu wenig Speicher …) erscheinen über [onMessage].
 */
@Composable
fun DownloadButton(item: Item, compact: Boolean, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!DownloadRules.canDownload(item)) return
    val info = IosDownloads.all.firstOrNull { it.item.url == item.url }
    val click: () -> Unit = {
        when (info?.state) {
            null -> downloadStartMessage(IosDownloads.start(item), IosDownloads.freeBytes())?.let(onMessage)
            DownloadInfo.State.FAILED -> IosDownloads.retry(item.url)
            else -> onMessage(downloadStatus(info))
        }
        Unit
    }
    if (compact) {
        IconButton(click, modifier) {
            when (info?.state) {
                DownloadInfo.State.DONE -> Icon(Icons.Rounded.DownloadDone, "Offline verfügbar", tint = Signal)
                DownloadInfo.State.LOADING -> CircularProgressIndicator(
                    progress = { info.progress ?: 0f }, modifier = Modifier.size(20.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp,
                )
                DownloadInfo.State.QUEUED, DownloadInfo.State.PAUSED -> Icon(Icons.Rounded.Downloading, "Wartet", tint = Muted)
                DownloadInfo.State.FAILED -> Icon(Icons.Rounded.ErrorOutline, "Fehlgeschlagen – erneut laden", tint = Danger)
                null -> Icon(Icons.Rounded.DownloadForOffline, "Herunterladen", tint = Muted)
            }
        }
    } else {
        val label = when (info?.state) {
            null -> "Herunterladen"
            DownloadInfo.State.DONE -> "Offline verfügbar"
            DownloadInfo.State.LOADING -> "Lädt " + (info.progress?.let { "${(it * 100).toInt()} %" } ?: "…")
            DownloadInfo.State.QUEUED, DownloadInfo.State.PAUSED -> "Wartet"
            DownloadInfo.State.FAILED -> "Erneut laden"
        }
        val icon = when (info?.state) {
            DownloadInfo.State.DONE -> Icons.Rounded.DownloadDone
            DownloadInfo.State.FAILED -> Icons.Rounded.Refresh
            else -> Icons.Rounded.DownloadForOffline
        }
        SecondaryButton(label, modifier, icon, onClick = click)
    }
}
