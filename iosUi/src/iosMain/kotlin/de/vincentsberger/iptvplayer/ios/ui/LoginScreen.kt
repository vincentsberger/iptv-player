package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Anmeldung bei einem Anbieter: Xtream Codes (Server, Benutzer, Passwort) oder M3U-Link. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(app: AppState) {
    var xtream by remember { mutableStateOf(true) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
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
                app.login(if (xtream) Account.Xtream(server.trim(), user.trim(), pass.trim()) else Account.M3u(m3u.trim()))
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

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (canCancel) TextButton({ app.back() }) { Text("Abbrechen", color = Muted) } else Spacer(Modifier.height(24.dp))
        Headline("IPTV Player", 30.sp)
        Text("Melde dich bei deinem Anbieter an. Die Zugangsdaten bleiben auf diesem iPhone.", color = Muted)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(xtream, { xtream = true; error = null }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Xtream Codes") }
            SegmentedButton(!xtream, { xtream = false; error = null }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("M3U-Link") }
        }

        if (xtream) {
            Field(server, { server = it }, "Server", "http://anbieter.example:8080", KeyboardType.Uri, ImeAction.Next) { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
            Field(user, { user = it }, "Benutzername", null, KeyboardType.Ascii, ImeAction.Next) { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
            Field(pass, { pass = it }, "Passwort", null, KeyboardType.Password, ImeAction.Done, secret = true) { submit() }
        } else {
            Field(m3u, { m3u = it }, "Link zur M3U-Playlist", "http://anbieter.example/get.php?…", KeyboardType.Uri, ImeAction.Done) { submit() }
        }

        error?.let { Text(it, color = Danger) }

        Row {
            PrimaryButton(if (busy) "Wird angemeldet …" else "Anmelden", Modifier.fillMaxWidth(), enabled = complete && !busy) { submit() }
        }
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    keyboard: KeyboardType,
    ime: ImeAction,
    secret: Boolean = false,
    onIme: () -> Unit,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Muted) } },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        shape = Corner,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Signal, unfocusedBorderColor = Line, cursorColor = Signal, focusedLabelColor = TextColor),
    )
}
