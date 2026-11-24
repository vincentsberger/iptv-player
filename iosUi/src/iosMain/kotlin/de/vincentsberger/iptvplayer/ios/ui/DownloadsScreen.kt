package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import de.vincentsberger.iptvplayer.ios.ui.design.AppButton
import de.vincentsberger.iptvplayer.ios.ui.design.BackBar
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonSize
import de.vincentsberger.iptvplayer.ios.ui.design.GroupLabel
import de.vincentsberger.iptvplayer.ios.ui.design.H1
import de.vincentsberger.iptvplayer.ios.ui.design.IconAction
import de.vincentsberger.iptvplayer.ios.ui.design.SmallText
import de.vincentsberger.iptvplayer.ios.ui.design.TinyText
import de.vincentsberger.iptvplayer.shared.data.Section
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

/**
 * Downloads (Entwurf „12“): Speicherbalken (belegt/frei), darunter die Einträge nach Zustand – läuft, Fehler (oben,
 * damit sie nicht untergehen), wartet, fertig. Tippen auf einen fertigen Titel spielt ihn ab; Wischen löscht.
 */
@Composable
fun DownloadsScreen(app: AppState, onBack: () -> Unit) {
    LaunchedEffect(Unit) { IosDownloads.start() }
    val all = IosDownloads.all
    var confirm by remember { mutableStateOf<DownloadInfo?>(null) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        BackBar("Start", onBack)
        H1("Downloads", Modifier.padding(horizontal = 16.dp))
        if (all.isEmpty()) {
            EmptyState(
                Icons.Rounded.DownloadForOffline, "Noch nichts heruntergeladen",
                "Filme lädst du auf ihrer Detailseite, Folgen und ganze Staffeln in der Serienansicht. So kannst du sie unterwegs ohne Internet ansehen.",
            )
            return@Column
        }
        StorageBar(all.size, IosDownloads.usedBytes(), IosDownloads.freeBytes())
        val done = all.filter { it.state == DownloadInfo.State.DONE }.map { it.item }
        val groups = listOf(
            "Läuft" to all.filter { it.state == DownloadInfo.State.LOADING },
            "Fehler" to all.filter { it.state == DownloadInfo.State.FAILED },
            "Wartet" to all.filter { it.state == DownloadInfo.State.QUEUED || it.state == DownloadInfo.State.PAUSED },
            "Fertig" to all.filter { it.state == DownloadInfo.State.DONE },
        ).filter { it.second.isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentPadding = PaddingValues(bottom = 16.dp)) {
            groups.forEach { (title, list) ->
                item(key = "g:$title") { GroupLabel(title, Modifier.padding(start = 0.dp)) ; HorizontalDivider(color = Line, thickness = 1.dp) }
                items(list, key = { it.item.url }) { info ->
                    // Nach links wischen: löschen bzw. abbrechen (mit Rückfrage).
                    SwipeAction(if (info.state == DownloadInfo.State.DONE) "Löschen" else "Abbrechen", Icons.Rounded.DeleteOutline, Danger, onSwipe = { confirm = info }) {
                        DownloadRow(
                            info, queuePlace = list.indexOf(info) + 1,
                            onPlay = { app.play(done, done.indexOf(info.item).coerceAtLeast(0)) },
                            onRetry = { IosDownloads.retry(info.item.url) },
                            onRemove = { confirm = info },
                        )
                    }
                }
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

/** Speicherzeile: Balken belegt (hell) / frei (surface2), darunter „6 Titel · 12,4 GB belegt“ und „noch 38,1 GB frei“. */
@Composable
private fun StorageBar(count: Int, used: Long, free: Long) {
    val total = (used + free).coerceAtLeast(1)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(SmallCorner).background(Surface2)) {
            Box(Modifier.fillMaxWidth((used.toFloat() / total).coerceIn(0f, 1f)).height(6.dp).background(TextColor))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallText("$count Titel · ${formatBytes(used)} belegt")
            SmallText("noch ${formatBytes(free)} frei")
        }
    }
}

/** Zeile: Poster 44×64 (Film) bzw. Bild 78×44 (Folge), Titel, Status, Fortschritt; rechts die passende Aktion. */
@Composable
private fun DownloadRow(info: DownloadInfo, queuePlace: Int, onPlay: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val done = info.state == DownloadInfo.State.DONE
    val episode = info.item.section == Section.SERIES
    Row(
        Modifier.fillMaxWidth().background(Bg).clickable(enabled = done, onClick = onPlay).padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(if (episode) 78.dp else 44.dp, if (episode) 44.dp else 64.dp).clip(Corner).background(Surface1)) {
            Icon(Icons.Rounded.Movie, null, tint = Line, modifier = Modifier.align(Alignment.Center).size(18.dp))
            if (!info.item.logo.isNullOrBlank()) AsyncImage(info.item.logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(splitProviderTag(info.item.name).name, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = downloadStatus(info) + if (info.state == DownloadInfo.State.QUEUED) " · Platz $queuePlace" else ""
            TinyText(status, color = if (info.state == DownloadInfo.State.FAILED) Danger else Muted)
            if (info.state == DownloadInfo.State.LOADING) ProgressBar(info.progress ?: 0f, Modifier.fillMaxWidth().padding(top = 3.dp))
        }
        when (info.state) {
            DownloadInfo.State.LOADING -> IconAction(Icons.Rounded.Close, "Abbrechen", onRemove, tint = Muted)
            DownloadInfo.State.FAILED -> AppButton("Erneut", onRetry, Modifier.padding(end = 10.dp), size = ButtonSize.Small)
            DownloadInfo.State.QUEUED, DownloadInfo.State.PAUSED -> Icon(Icons.Rounded.Schedule, "Wartet", tint = Muted, modifier = Modifier.padding(end = 12.dp).size(20.dp))
            DownloadInfo.State.DONE -> Icon(Icons.Rounded.CheckCircle, "Offline verfügbar", tint = TextColor, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        }
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
        val (icon, label) = downloadLabel(info)
        SecondaryButton(label, modifier, icon, onClick = click)
    }
}

/** Symbol und Text zum Download-Zustand („Herunterladen“, „Lädt 42 %“, „Offline verfügbar“ …). */
private fun downloadLabel(info: DownloadInfo?): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
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
    return icon to label
}

/** Download als Eintrag einer Aktionsleiste (Symbol, Text, Klick) – null, wenn der Titel nicht ladbar ist. */
fun downloadAction(item: Item, onMessage: (String) -> Unit): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>? {
    if (!DownloadRules.canDownload(item)) return null
    val info = IosDownloads.all.firstOrNull { it.item.url == item.url }
    val (icon, label) = downloadLabel(info)
    return Triple(icon, label) {
        when (info?.state) {
            null -> downloadStartMessage(IosDownloads.start(item), IosDownloads.freeBytes())?.let(onMessage)
            DownloadInfo.State.FAILED -> IosDownloads.retry(item.url)
            else -> onMessage(downloadStatus(info))
        }
        Unit
    }
}
