package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.design.AppButton
import de.vincentsberger.iptvplayer.ios.ui.design.AppTextField
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonSize
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonStyle
import de.vincentsberger.iptvplayer.ios.ui.design.H1
import de.vincentsberger.iptvplayer.ios.ui.design.LogoMark
import de.vincentsberger.iptvplayer.ios.ui.design.Segmented
import de.vincentsberger.iptvplayer.ios.ui.design.TinyText
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Anmeldung bei einem Anbieter (Entwurf „1 Login“): Xtream Codes (Server, Benutzer, Passwort) oder M3U-Link,
 * dazu optional ein eigener Name für den Anbieter.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(app: AppState) {
    var xtream by remember { mutableStateOf(true) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canCancel = app.canCancelLogin
    if (canCancel) BackHandler { app.back() }

    val complete = if (xtream) server.isNotBlank() && user.isNotBlank() && pass.isNotBlank() else m3u.isNotBlank()
    fun submit() {
        if (!complete || busy) return
        focus.clearFocus()
        busy = true; error = null
        scope.launch {
            try {
                val account = if (xtream) Account.Xtream(server.trim(), user.trim(), pass.trim()) else Account.M3u(m3u.trim())
                app.login(account, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Meldungen aus shared enthalten nie die Adresse (Zugangsdaten).
                error = e.message ?: "Anmeldung fehlgeschlagen."
            } finally {
                busy = false
            }
        }
    }
    val next = { focus.moveFocus(FocusDirection.Down); Unit }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = if (canCancel) 4.dp else 36.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (canCancel) AppButton("Abbrechen", { app.back() }, style = ButtonStyle.Secondary, size = ButtonSize.Small)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LogoMark(72.dp, Modifier.padding(bottom = 10.dp))
            H1("IPTV Player")
            Text("Melde dich bei deinem Anbieter an. Die Zugangsdaten bleiben auf diesem iPhone.", color = Muted, fontSize = 16.sp, lineHeight = 22.sp)
        }

        Segmented(listOf("Xtream Codes", "M3U-Link"), if (xtream) 0 else 1, { xtream = it == 0; error = null })

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (xtream) {
                AppTextField(server, { server = it }, "Server", placeholder = "http://anbieter.example:8080", icon = Icons.Rounded.Dns, keyboard = KeyboardType.Uri, onIme = next)
                AppTextField(user, { user = it }, "Benutzername", placeholder = "Benutzername", icon = Icons.Rounded.Person, keyboard = KeyboardType.Ascii, onIme = next)
                AppTextField(pass, { pass = it }, "Passwort", placeholder = "Passwort", icon = Icons.Rounded.Lock, secret = true, keyboard = KeyboardType.Password, onIme = next)
            } else {
                AppTextField(m3u, { m3u = it }, "Link zur M3U-Playlist", placeholder = "http://anbieter.example/get.php?…", icon = Icons.Rounded.Link, keyboard = KeyboardType.Uri, onIme = next)
            }
        }

        AppTextField(name, { name = it }, "Name in der App (optional)", placeholder = "Mein Anbieter", ime = ImeAction.Done, onIme = { submit() })

        error?.let { Text(it, color = Danger, fontSize = 15.sp) }

        AppButton(
            if (busy) "Wird angemeldet …" else "Anmelden", { submit() }, Modifier.fillMaxWidth(),
            style = ButtonStyle.Primary, enabled = complete && !busy,
        )
        TinyText("Diese App ist ein reiner Player und enthält keine Inhalte.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
