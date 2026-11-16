package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.insecureServerHint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: suspend (Account) -> Unit) {
    var xtream by remember { mutableStateOf(true) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var m3u by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        val account = if (xtream) {
            if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Bitte Server, Benutzername und Passwort eingeben."; return }
            Account.Xtream(server.trim(), user.trim(), pass.trim())
        } else {
            if (m3u.isBlank()) { error = "Bitte den Link zur Playlist eingeben."; return }
            Account.M3u(m3u.trim())
        }
        scope.launch {
            busy = true; error = null
            try { onLogin(account) } catch (e: Exception) { error = e.message ?: "Anmeldung fehlgeschlagen" } finally { busy = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        DepthBackground()
        Row(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 64.dp)) {
                Masthead(64.sp)
                Text(
                    "Live TV, Filme und Serien deines Anbieters – übersichtlich auf dem PC.", color = Muted, fontSize = 20.sp, lineHeight = 28.sp,
                    modifier = Modifier.padding(top = 18.dp).widthIn(max = 520.dp),
                )
                Spacer(Modifier.height(40.dp))
                Column(Modifier.widthIn(max = 520.dp)) {
                    Feature("01", Icons.Rounded.LiveTv, "Live TV mit Programmvorschau")
                    Feature("02", Icons.Rounded.Movie, "Filme mit „Weiterschauen“")
                    Feature("03", Icons.Rounded.VideoLibrary, "Serien nach Staffeln sortiert")
                    Rule()
                }
            }
            Column(
                Modifier.width(480.dp).panel().padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Headline("Anmelden", 32.sp)
                Text("Gib die Zugangsdaten deines Anbieters ein.", color = Muted)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Signal, activeContentColor = OnSignal, activeBorderColor = Signal,
                        inactiveContainerColor = Color.Transparent, inactiveContentColor = TextColor, inactiveBorderColor = Line,
                    )
                    SegmentedButton(xtream, { xtream = true; error = null }, SegmentedButtonDefaults.itemShape(0, 2, Corner), colors = colors) { Text("Xtream Codes") }
                    SegmentedButton(!xtream, { xtream = false; error = null }, SegmentedButtonDefaults.itemShape(1, 2, Corner), colors = colors) { Text("M3U-Playlist") }
                }
                if (xtream) {
                    Field(server, { server = it }, "Server", "http://anbieter.example:8080", Icons.Rounded.Dns)
                    Field(user, { user = it }, "Benutzername", null, Icons.Rounded.Person)
                    Field(
                        pass, { pass = it }, "Passwort", null, Icons.Rounded.Lock,
                        password = !showPass, onDone = ::submit,
                        trailing = {
                            IconButton({ showPass = !showPass }) {
                                Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Passwort anzeigen")
                            }
                        },
                    )
                    insecureServerHint(server)?.let { Text(it, color = Muted, fontSize = 13.sp) }
                } else {
                    Field(m3u, { m3u = it }, "Link zur M3U-Playlist", "http://anbieter.example/get.php?…", Icons.Rounded.Link, onDone = ::submit)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    PrimaryButton(if (busy) "Wird geprüft …" else "Anmelden") { submit() }
                    if (busy) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp)
                }
                error?.let { Text(it, color = Danger) }
                Rule(Modifier.padding(top = 4.dp))
                Text(
                    "Diese App enthält keine Sender oder Inhalte. Du brauchst ein eigenes Abo bei einem Anbieter, der zur Verbreitung berechtigt ist.",
                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    icon: ImageVector,
    password: Boolean = false,
    onDone: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Muted) } },
        leadingIcon = { Icon(icon, null) },
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = Corner,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("field:$label"),
    )
}

/** Nummerierte Zeile wie in einem Inhaltsverzeichnis. */
@Composable
private fun Feature(number: String, icon: ImageVector, text: String) {
    Rule()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
        Text(number, color = Signal, fontSize = 14.sp, fontFamily = Mono, modifier = Modifier.width(48.dp))
        Icon(icon, null, tint = Muted, modifier = Modifier.padding(end = 14.dp).size(22.dp))
        Text(text, color = TextColor, fontSize = 18.sp)
    }
}
