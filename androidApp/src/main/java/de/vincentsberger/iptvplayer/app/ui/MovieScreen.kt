package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import de.vincentsberger.iptvplayer.app.download.Downloads
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.movieFacts
import de.vincentsberger.iptvplayer.shared.playLabel
import kotlinx.coroutines.delay

/** Film-Detailseite: erst entscheiden, dann abspielen. Fokus startet auf „Abspielen“/„Weiterschauen“. */
@Composable
fun MovieScreen(vm: AppState, movie: Item) {
    val info = vm.mediaInfoFor(movie)
    val resumeAt = vm.resumePositionOf(movie)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.url) { delay(100); runCatching { playFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Backdrop(info?.backdrop ?: movie.logo)
        KeyHintBar(Modifier.align(Alignment.BottomStart).zIndex(1f))
        Row(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 56.dp, bottom = 72.dp)) {
            if (movie.logo != null) {
                AsyncImage(movie.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(220.dp, 326.dp).clip(Corner))
            }
            Column(Modifier.padding(start = 40.dp).width(820.dp)) {
                Kicker("Film", Signal)
                Headline(movie.name, 40.sp, Modifier.padding(top = 6.dp), maxLines = 2)
                info?.originalTitle?.let { Text(it, color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp)) }
                movieFacts(info).takeIf { it.isNotEmpty() }?.let { facts ->
                    Text(facts.joinToString("  ·  "), color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 22.dp)) {
                    TvButton(playLabel(resumeAt), Modifier.focusRequester(playFocus).testTag("movie:play"), Icons.Rounded.PlayArrow, ButtonStyle.Primary) {
                        vm.play(listOf(movie), 0)
                    }
                    if (resumeAt > 0) TvButton("Von vorn", Modifier.testTag("movie:restart"), Icons.Rounded.Replay) { vm.play(listOf(movie), 0, fromStart = true) }
                    val fav = movie.favKey in vm.favorites
                    TvButton(if (fav) "Favorit" else "Merken", icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, selected = fav) { vm.toggleFavorite(movie) }
                    if (Downloads.canDownload(movie)) {
                        val done = Downloads.infoFor(movie.url) != null
                        TvButton(if (done) "In Downloads" else "Herunterladen", icon = Icons.Rounded.Download, selected = done) {
                            Feedback.show(
                                when (Downloads.start(movie)) {
                                    Downloads.StartResult.STARTED -> "Download gestartet – Fortschritt unter „Downloads“"
                                    Downloads.StartResult.NO_SPACE -> "Zu wenig Speicher frei (noch ${formatBytes(Downloads.freeBytes())})"
                                    Downloads.StartResult.NOT_POSSIBLE -> "Dieser Titel lässt sich nicht herunterladen"
                                    Downloads.StartResult.ALREADY -> "Ist schon in den Downloads"
                                }
                            )
                        }
                    }
                }
                info?.plot?.let {
                    Text(it, color = TextColor, fontSize = 18.sp, lineHeight = 27.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 26.dp))
                }
                info?.director?.let { Credit("Regie", it) }
                info?.cast?.takeIf { it.isNotEmpty() }?.let { Credit("Besetzung", it.take(8).joinToString(", ")) }
                if (info == null) Text("Infos werden geladen …", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 26.dp))
            }
        }
    }
}

@Composable
private fun Credit(label: String, text: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Kicker(label)
        Text(text, color = TextColor, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}
