package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberUpdatedState
import de.vincentsberger.iptvplayer.app.PhoneLoginServer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
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
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.insecureServerHint
import kotlinx.coroutines.launch

/** Anmeldung. Mit [onCancel] (weiterer Anbieter) gibt es „Abbrechen“ und Zurück führt zur Startseite. */
@Composable
fun LoginScreen(onLogin: suspend (Account) -> Unit, onCancel: (() -> Unit)? = null) {
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
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

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

    // Anmeldung vom Handy: Daten landen im Formular (sichtbar auf dem TV) und werden gleich geprüft.
    var phoneUrl by remember { mutableStateOf<String?>(null) }
    val fromPhone by rememberUpdatedState<(Account) -> Unit> { account ->
        when (account) {
            is Account.Xtream -> { xtreamMode = true; server = account.server; user = account.username; pass = account.password }
            is Account.M3u -> { xtreamMode = false; m3u = account.url }
        }
        submit()
    }
    DisposableEffect(Unit) {
        val srv = PhoneLoginServer { fromPhone(it) }
        phoneUrl = srv.start()
        onDispose { srv.stop() }
    }

    Box(
        Modifier
            .fillMaxSize()
    ) {
        DepthBackground()
        // Ränder = TV-Sicherheitsbereich (5 %); 1080p-Fernseher haben nur 960 × 540 dp.
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), verticalAlignment = Alignment.CenterVertically) {
            // Linke Seite: Marke und Vorteile
            Column(Modifier.weight(1f).padding(end = 40.dp)) {
                Masthead(44.sp)
                Text(
                    "Dein Fernsehen. Übersichtlich, schnell und mit der Fernbedienung gemacht.",
                    fontSize = 18.sp, color = Muted, lineHeight = 26.sp, modifier = Modifier.padding(top = 14.dp).widthIn(max = 460.dp),
                )
                Spacer(Modifier.height(28.dp))
                val url = phoneUrl
                if (url != null) {
                    PhoneLoginHint(url)
                } else {
                    Column(Modifier.widthIn(max = 460.dp)) {
                        Feature("01", Icons.Rounded.LiveTv, "Live TV mit Programmvorschau")
                        Feature("02", Icons.Rounded.Movie, "Filme mit „Weiterschauen“")
                        Feature("03", Icons.Rounded.VideoLibrary, "Serien nach Staffeln sortiert")
                        Rule()
                    }
                }
            }

            // Rechte Seite: Anmeldekarte
            // Karte: wächst mit dem Bildschirm (Verhältnis) bis max. 560 dp und scrollt, falls die Höhe nicht reicht.
            Column(
                Modifier
                    .weight(1.2f)
                    .widthIn(max = 560.dp)
                    .panel()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Headline(if (onCancel != null) "Anbieter hinzufügen" else "Anmelden", 28.sp)
                Text("Gib die Zugangsdaten deines Anbieters ein.", color = Muted, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    // Startfokus auf der Auswahl statt im Textfeld: sonst öffnet sich sofort die Tastatur
                    // und verdeckt den QR-Code für die Anmeldung vom Handy.
                    TvButton("Xtream Codes", Modifier.testTag("login:mode:xtream").focusRequester(firstField), selected = xtreamMode) { xtreamMode = true; error = null }
                    TvButton("M3U-Playlist", Modifier.testTag("login:mode:m3u"), selected = !xtreamMode) { xtreamMode = false; error = null }
                }
                if (xtreamMode) {
                    TvTextField(server, { server = it }, "Server, z. B. anbieter.example:8080", Modifier.testTag("login:server").fillMaxWidth(), leading = Icons.Rounded.Dns)
                    TvTextField(user, { user = it }, "Benutzername", Modifier.testTag("login:user").fillMaxWidth(), leading = Icons.Rounded.Person, keyboardType = KeyboardType.Text)
                    TvTextField(pass, { pass = it }, "Passwort", Modifier.testTag("login:pass").fillMaxWidth(), leading = Icons.Rounded.Lock, password = true, imeAction = ImeAction.Done, onDone = ::submit)
                    insecureServerHint(server)?.let { Text(it, color = Muted, fontSize = 13.sp) }
                } else {
                    TvTextField(m3u, { m3u = it }, "Link zur M3U-Playlist", Modifier.testTag("login:m3u").fillMaxWidth(), leading = Icons.Rounded.Link, imeAction = ImeAction.Done, onDone = ::submit)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    TvButton(if (busy) "Wird geprüft …" else "Anmelden", style = ButtonStyle.Primary) { submit() }
                    if (onCancel != null && !busy) TvButton("Abbrechen", Modifier.padding(start = 12.dp).testTag("login:cancel")) { onCancel() }
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

/** QR-Code für die Anmeldung vom Handy (öffnet ein Formular auf dem kleinen Server im TV). */
@Composable
private fun PhoneLoginHint(url: String) {
    Column(Modifier.widthIn(max = 460.dp)) {
        Rule()
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, 132.dp, Modifier.testTag("login:qr"))
            Column(Modifier.padding(start = 20.dp)) {
                Kicker("Mit dem Handy", Signal)
                Text(
                    "Code mit der Handy-Kamera scannen und die Zugangsdaten dort eintippen.",
                    fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Text(url.removePrefix("http://"), color = Muted, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Handy und Fernseher müssen im selben WLAN sein.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Feature(number: String, icon: ImageVector, text: String) {
    Rule()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Text(number, color = Signal, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.width(40.dp))
        Icon(icon, Modifier.padding(end = 14.dp), tint = Muted, size = 22.dp)
        Text(text, fontSize = 18.sp)
    }
}
