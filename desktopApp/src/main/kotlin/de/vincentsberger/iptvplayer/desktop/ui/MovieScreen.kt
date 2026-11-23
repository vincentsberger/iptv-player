package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.movieFacts
import de.vincentsberger.iptvplayer.shared.playLabel

/** Film-Detailseite: alles, um zu entscheiden, ob man den Film schauen will – dann Abspielen/Weiterschauen. */
@Composable
fun MovieScreen(app: AppState, movie: Item) {
    val info = app.mediaInfoFor(movie)
    val resumeAt = app.resumePositionOf(movie)
    Box(Modifier.fillMaxSize().background(Bg)) {

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            IconButton({ app.back() }, Modifier.padding(16.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = TextColor) }
            Row(Modifier.padding(horizontal = 40.dp).padding(bottom = 40.dp), verticalAlignment = Alignment.Top) {
                if (movie.logo != null) {
                    AsyncImage(movie.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(200.dp, 296.dp).clip(Corner))
                }
                Column(Modifier.padding(start = 32.dp).weight(1f).widthIn(max = 820.dp)) {
                    Kicker("Film", Signal)
                    Headline(movie.name, 44.sp, Modifier.padding(top = 4.dp), maxLines = 2)
                    info?.originalTitle?.let { Text(it, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp)) }
                    movieFacts(info).takeIf { it.isNotEmpty() }?.let { facts ->
                        Text(facts.joinToString("  ·  "), color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
                        PrimaryButton(playLabel(resumeAt), Icons.Rounded.PlayArrow) { app.play(listOf(movie), 0) }
                        if (resumeAt > 0) SecondaryButton("Von vorn", Icons.Rounded.Replay) { app.play(listOf(movie), 0, fromStart = true) }
                        val fav = movie.favKey in app.favorites
                        SecondaryButton(if (fav) "Favorit" else "Merken", if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder) { app.toggleFavorite(movie) }
                    }
                    info?.plot?.let { Text(it, color = TextColor, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 24.dp)) }
                    info?.director?.let { Credit("Regie", it) }
                    info?.cast?.takeIf { it.isNotEmpty() }?.let { Credit("Besetzung", it.take(8).joinToString(", ")) }
                    if (info == null) Text("Infos werden geladen …", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun Credit(label: String, text: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Kicker(label)
        Text(text, color = TextColor, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
