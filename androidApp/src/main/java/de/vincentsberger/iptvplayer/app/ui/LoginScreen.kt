package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.PhoneAndroid
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

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Entwurf „01 Login“ (1920 × 1080 px = 960 × 540 dp): Formular links, Handy-Anmeldung als gleichwertige
        // Karte rechts. Ränder wie die übrigen Seiten (oben 48, rechts 32, unten 24, links 60 dp).
        Row(
            Modifier.fillMaxSize().padding(start = 60.dp, end = 32.dp, top = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            Column(
                Modifier.weight(1f).widthIn(max = 430.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(30.dp).border(1.dp, Line, Corner), contentAlignment = Alignment.Center) {
                        Text("IP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Kicker("IPTV Player")
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Headline(if (onCancel != null) "Anbieter hinzufügen" else "Anmelden", 28.sp)
                    Text("Gib die Zugangsdaten deines Anbieters ein – oder melde dich bequem mit dem Handy an.", color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Startfokus auf der Auswahl statt im Textfeld: sonst öffnet sich sofort die Tastatur
                    // und verdeckt den QR-Code für die Anmeldung vom Handy.
                    TvButton("Xtream Codes", Modifier.testTag("login:mode:xtream").focusRequester(firstField), selected = xtreamMode) { xtreamMode = true; error = null }
                    TvButton("M3U-Playlist", Modifier.testTag("login:mode:m3u"), selected = !xtreamMode) { xtreamMode = false; error = null }
                }
                if (xtreamMode) {
                    LabeledField("Server") { TvTextField(server, { server = it }, "z. B. anbieter.example:8080", Modifier.testTag("login:server").fillMaxWidth(), leading = Icons.Rounded.Dns) }
                    LabeledField("Benutzername") { TvTextField(user, { user = it }, "Benutzername", Modifier.testTag("login:user").fillMaxWidth(), leading = Icons.Rounded.Person, keyboardType = KeyboardType.Text) }
                    LabeledField("Passwort") { TvTextField(pass, { pass = it }, "Passwort", Modifier.testTag("login:pass").fillMaxWidth(), leading = Icons.Rounded.Lock, password = true, imeAction = ImeAction.Done, onDone = ::submit) }
                    insecureServerHint(server)?.let { Text(it, color = Muted, fontSize = 14.sp) }
                } else {
                    LabeledField("Link zur M3U-Playlist") { TvTextField(m3u, { m3u = it }, "http://anbieter.example/get.php?…", Modifier.testTag("login:m3u").fillMaxWidth(), leading = Icons.Rounded.Link, imeAction = ImeAction.Done, onDone = ::submit) }
                }
                error?.let { Text(it, color = Danger, fontSize = 15.sp) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TvButton(if (busy) "Wird geprüft …" else "Anmelden", Modifier.widthIn(min = 150.dp), style = ButtonStyle.Primary) { submit() }
                    if (onCancel != null && !busy) TvButton("Abbrechen", Modifier.testTag("login:cancel")) { onCancel() }
                    if (busy) Spinner(28.dp) else Text("OK öffnet die Bildschirmtastatur", color = Muted, fontSize = 13.sp)
                }
                Text(
                    "Diese App enthält keine Sender oder Inhalte. Du brauchst ein eigenes Abo bei einem Anbieter, der zur Verbreitung berechtigt ist.",
                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Rechts: Handy-Anmeldung als Karte (nicht fokussierbar – das Steuerkreuz bleibt im Formular).
            PhoneLoginCard(phoneUrl, Modifier.width(340.dp).padding(top = 60.dp))
        }
    }
}

/** Beschriftung über einem Feld (Entwurf `.fld label`). */
@Composable
private fun LabeledField(label: String, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        field()
    }
}

/**
 * Karte „Mit dem Handy anmelden“: QR-Code, drei Schritte, Adresse in Mono. Ohne Netzwerk (kein [url]) ein Hinweis
 * statt leerer Adresse.
 */
@Composable
private fun PhoneLoginCard(url: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Surface1, Corner).border(1.dp, Line, Corner).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.PhoneAndroid, size = 18.dp)
            Headline("Mit dem Handy anmelden", 17.sp)
        }
        if (url == null) {
            Text("Nicht verfügbar – der Fernseher ist mit keinem WLAN verbunden.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            QrCode(url, 130.dp, Modifier.testTag("login:qr"))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Kamera auf den Code richten", "Zugangsdaten am Handy eintippen", "Der Fernseher meldet sich selbst an").forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${i + 1}", color = Signal, fontFamily = Mono, fontSize = 14.sp)
                        Text(step, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Oder im Browser öffnen", color = Muted, fontSize = 12.sp)
            Text(url.removePrefix("http://"), fontFamily = Mono, fontSize = 13.sp, maxLines = 1)
            Text("Handy und Fernseher müssen im selben WLAN sein.", color = Muted, fontSize = 12.sp)
        }
    }
}
