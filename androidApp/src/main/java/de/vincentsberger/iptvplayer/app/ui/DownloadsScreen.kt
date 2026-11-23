package de.vincentsberger.iptvplayer.app.ui

import de.vincentsberger.iptvplayer.shared.downloadStatus
import de.vincentsberger.iptvplayer.shared.formatBytes
import de.vincentsberger.iptvplayer.shared.DownloadStartResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.vincentsberger.iptvplayer.shared.DownloadInfo
import de.vincentsberger.iptvplayer.app.download.Downloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item

/** Downloads: alles, was offline auf dem Gerät liegt oder gerade lädt. */
@Composable
fun DownloadsScreen(vm: AppState) {
    val all = Downloads.all
    val done = all.filter { it.state == DownloadInfo.State.DONE }.map { it.item }
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Downloads",
            "${all.size} Titel · ${formatBytes(Downloads.usedBytes())} belegt · noch ${formatBytes(Downloads.freeBytes())} frei",
            Icons.Rounded.DownloadForOffline,
        )
        if (all.isEmpty()) {
            EmptyView(
                "Noch nichts heruntergeladen",
                "Filme und Folgen lädst du im Player über ☰ herunter, ganze Staffeln in der Serienansicht. So kannst du sie unterwegs ohne Internet ansehen.",
                Icons.Rounded.Download,
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(all, key = { it.item.url }) { info ->
                DownloadRow(info, onPlay = { vm.play(done, done.indexOf(info.item)) })
            }
        }
    }
}

@Composable
private fun DownloadRow(info: DownloadInfo, onPlay: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.width(900.dp).panel().padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(sectionIcon(info.item.section), tint = Muted, size = 24.dp)
        Column(Modifier.weight(1f)) {
            Text(info.item.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                downloadStatus(info), fontSize = 16.sp,
                color = if (info.state == DownloadInfo.State.FAILED) Danger else Muted,
            )
            if (info.state == DownloadInfo.State.LOADING) ProgressBar(info.progress ?: 0f, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        when (info.state) {
            DownloadInfo.State.DONE -> TvButton("Abspielen", Modifier.testTag("download:play:${info.item.id}"), icon = Icons.Rounded.PlayArrow, style = ButtonStyle.Primary) { onPlay() }
            DownloadInfo.State.FAILED -> TvButton("Erneut laden", icon = Icons.Rounded.Refresh) { Downloads.retry(info) }
            else -> Unit
        }
        if (!confirm) {
            TvButton(if (info.state == DownloadInfo.State.DONE) "Download löschen" else "Download abbrechen", Modifier.testTag("download:remove:${info.item.id}"), icon = Icons.Rounded.DeleteOutline) { confirm = true }
        } else {
            TvButton("Wirklich?", style = ButtonStyle.Danger) {
                Downloads.remove(info.item.url)
                Feedback.show(if (info.state == DownloadInfo.State.DONE) "Download gelöscht" else "Download abgebrochen")
            }
        }
    }
}

/** Eintrag im Player-Menü: diesen Film / diese Folge herunterladen bzw. Zustand zeigen. */
@Composable
internal fun ColumnScope.DownloadOption(item: Item) {
    if (!Downloads.canDownload(item)) return
    var message by remember(item.url) { mutableStateOf<String?>(null) }
    var confirm by remember(item.url) { mutableStateOf(false) }
    val info = Downloads.infoFor(item.url)
    Kicker("Offline ansehen", modifier = Modifier.padding(top = 12.dp))
    // Immer derselbe Button, nur Beschriftung und Aktion wechseln – sonst verliert die Fernbedienung den Fokus.
    val done = info?.state == DownloadInfo.State.DONE
    val label = when {
        info == null -> "Herunterladen"
        info.state == DownloadInfo.State.FAILED -> "Fehlgeschlagen – neuer Versuch"
        done && !confirm -> "Heruntergeladen – löschen?"
        done -> "Wirklich löschen"
        !confirm -> "Download abbrechen"
        else -> "Wirklich abbrechen"
    }
    TvButton(
        label, Modifier.fillMaxWidth().testTag("option:download"),
        icon = if (done) Icons.Rounded.DownloadForOffline else Icons.Rounded.Download,
        style = if (confirm) ButtonStyle.Danger else ButtonStyle.Secondary,
        selected = done && !confirm,
    ) {
        when {
            info == null -> message = when (Downloads.start(item)) {
                DownloadStartResult.STARTED -> "Wird geladen – den Fortschritt siehst du unter „Downloads“."
                DownloadStartResult.NO_SPACE -> "Zu wenig Speicher frei (noch ${formatBytes(Downloads.freeBytes())})."
                DownloadStartResult.NOT_POSSIBLE -> "Dieser Titel lässt sich nicht herunterladen."
                DownloadStartResult.ALREADY -> null
            }
            info.state == DownloadInfo.State.FAILED -> Downloads.retry(info)
            !confirm -> confirm = true
            else -> { Downloads.remove(item.url); confirm = false; message = null }
        }
    }
    if (info != null && !done) Text(downloadStatus(info), color = Muted, fontSize = 16.sp)
    message?.let { Text(it, color = Muted, fontSize = 15.sp) }
}
