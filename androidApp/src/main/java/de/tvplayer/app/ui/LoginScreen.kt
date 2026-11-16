package de.tvplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import de.tvplayer.shared.data.Account
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLogin: suspend (Account) -> Unit) {
    var xtreamMode by rememberSaveable { mutableStateOf(true) }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3u by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstField = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    fun submit() {
        if (busy) return
        val account = if (xtreamMode) {
            if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                error = "Bitte Server, Benutzername und Passwort eingeben."; return
            }
            Account.Xtream(server.trim(), user.trim(), pass.trim())
        } else {
            if (m3u.isBlank()) { error = "Bitte den Link zur Playlist eingeben."; return }
            Account.M3u(m3u.trim())
        }
        scope.launch {
            busy = true; error = null
            try {
                onLogin(account)
            } catch (e: Exception) {
                error = e.message ?: "Anmeldung fehlgeschlagen"
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF241D55), Bg), radius = 1600f)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            // Linke Seite: Marke und Vorteile
            Column(Modifier.weight(1f).padding(end = 64.dp)) {
                LogoTile(72.dp)
                Text("TV Player", fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "Dein Fernsehen. Übersichtlich, schnell und mit der Fernbedienung gemacht.",
                    fontSize = 20.sp, color = Muted, lineHeight = 28.sp, modifier = Modifier.padding(top = 10.dp).width(460.dp),
                )
                Spacer(Modifier.height(36.dp))
                Feature(Icons.Rounded.LiveTv, "Live TV mit Programmvorschau")
                Feature(Icons.Rounded.Movie, "Filme mit „Weiterschauen“")
                Feature(Icons.Rounded.VideoLibrary, "Serien nach Staffeln sortiert")
            }

            // Rechte Seite: Anmeldekarte
            Column(
                Modifier.width(560.dp).background(Surface1.copy(alpha = 0.92f), RoundedCornerShape(28.dp)).padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Anmelden", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Gib die Zugangsdaten deines Anbieters ein.", color = Muted, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    TvButton("Xtream Codes", selected = xtreamMode) { xtreamMode = true; error = null }
                    TvButton("M3U-Playlist", selected = !xtreamMode) { xtreamMode = false; error = null }
                }
                if (xtreamMode) {
                    TvTextField(server, { server = it }, "Server, z. B. http://anbieter.example:8080", Modifier.testTag("login:server").fillMaxWidth().focusRequester(firstField), leading = Icons.Rounded.Dns)
                    TvTextField(user, { user = it }, "Benutzername", Modifier.testTag("login:user").fillMaxWidth(), leading = Icons.Rounded.Person, keyboardType = KeyboardType.Text)
                    TvTextField(pass, { pass = it }, "Passwort", Modifier.testTag("login:pass").fillMaxWidth(), leading = Icons.Rounded.Lock, password = true, imeAction = ImeAction.Done)
                } else {
                    TvTextField(m3u, { m3u = it }, "Link zur M3U-Playlist", Modifier.testTag("login:m3u").fillMaxWidth().focusRequester(firstField), leading = Icons.Rounded.Link, imeAction = ImeAction.Done)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    TvButton(if (busy) "Wird geprüft …" else "Anmelden", style = ButtonStyle.Primary) { submit() }
                    if (busy) Box(Modifier.padding(start = 16.dp)) { Spinner(32.dp) }
                }
                error?.let { Text(it, color = Danger, fontSize = 15.sp) }
                Text(
                    "Diese App enthält keine Sender oder Inhalte. Du brauchst ein eigenes Abo bei einem Anbieter, der zur Verbreitung berechtigt ist.",
                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.background(Surface2, RoundedCornerShape(12.dp)).padding(10.dp)) { Icon(icon, tint = Accent2, size = 24.dp) }
        Text(text, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
    }
}
