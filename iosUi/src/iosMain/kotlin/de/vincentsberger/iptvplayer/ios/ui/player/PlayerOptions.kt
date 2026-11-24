package de.vincentsberger.iptvplayer.ios.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.Kicker
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.Surface2
import de.vincentsberger.iptvplayer.ios.ui.TextColor
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.MediaTrack
import de.vincentsberger.iptvplayer.shared.SubtitleChoice
import de.vincentsberger.iptvplayer.shared.VideoFit
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.preferredAudioTrack
import de.vincentsberger.iptvplayer.shared.preferredSubtitles
import kotlinx.coroutines.delay

/**
 * Gespeicherte Sprachen auf einen neu gestarteten Titel anwenden, sobald VLC die Spuren kennt (wie am PC:
 * die VLC-Optionen :audio-language/:sub-language greifen bei vielen Dateien nicht). Einmal je [key], höchstens 30 s.
 */
@Composable
internal fun ApplyTrackPreferences(app: AppState, player: PlayerEngine, key: Any) {
    LaunchedEffect(key) {
        repeat(60) {
            delay(500)
            val audio = player.audioTracks
            if (audio.isEmpty()) return@repeat
            preferredAudioTrack(audio, app.audioLanguage)?.takeIf { !it.selected }?.let { player.selectAudio(it.id) }
            when (val sub = preferredSubtitles(player.subtitleTracks, app.subtitleLanguage)) {
                SubtitleChoice.Keep -> Unit
                SubtitleChoice.Off -> player.selectSubtitles(null)
                is SubtitleChoice.Track -> player.selectSubtitles(sub.id)
            }
            return@LaunchedEffect
        }
    }
}

/**
 * Optionen im Player (Knopf oben rechts): Tonspur, Untertitel, Bildformat. Die Wahl gilt auch für den nächsten Titel –
 * Sprachen und Bildformat werden gespeichert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerOptionsSheet(app: AppState, player: PlayerEngine, onFit: (VideoFit) -> Unit, onDismiss: () -> Unit) {
    // Spuren sind kein Compose-State: nach jeder Wahl neu lesen (VLC übernimmt sie nicht sofort).
    var refresh by remember { mutableIntStateOf(0) }
    var audio by remember { mutableStateOf(player.audioTracks) }
    var subtitles by remember { mutableStateOf(player.subtitleTracks) }
    LaunchedEffect(refresh) {
        repeat(6) {
            audio = player.audioTracks; subtitles = player.subtitleTracks
            delay(250)
        }
    }
    var fit by remember { mutableStateOf(VideoFit.of(app.videoFit)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface1) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Kicker("Tonspur")
            if (audio.isEmpty()) Text("Keine Auswahl verfügbar", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            audio.forEach { t ->
                OptionRow(t.label, t.selected) {
                    player.selectAudio(t.id)
                    t.language?.let { app.audioLanguage = it }
                    refresh++
                }
            }

            Spacer(Modifier.size(12.dp))
            Kicker("Untertitel")
            if (subtitles.isEmpty()) Text("Keine Untertitel im Stream", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            else {
                OptionRow("Aus", subtitles.none(MediaTrack::selected)) {
                    player.selectSubtitles(null)
                    app.subtitleLanguage = Storage.SUBTITLES_OFF
                    refresh++
                }
                subtitles.forEach { t ->
                    OptionRow(t.label, t.selected) {
                        player.selectSubtitles(t.id)
                        app.subtitleLanguage = t.language
                        refresh++
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Kicker("Bildformat")
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoFit.entries.forEach { f ->
                    FilterChip(
                        selected = f == fit,
                        onClick = { fit = f; app.videoFit = f.key; onFit(f) },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Surface2, selectedLabelColor = TextColor),
                    )
                }
            }
            Text(
                when (fit) {
                    VideoFit.FIT -> "Ganzes Bild, ggf. mit schwarzen Rändern."
                    VideoFit.ZOOM -> "Füllt den Bildschirm, schneidet Ränder ab."
                    VideoFit.FILL -> "Füllt den Bildschirm, verzerrt das Bild."
                },
                color = Muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (selected) TextColor else Muted)
        if (selected) Icon(Icons.Rounded.Check, "gewählt", tint = Signal, modifier = Modifier.size(20.dp))
        else Spacer(Modifier.width(20.dp))
    }
}
