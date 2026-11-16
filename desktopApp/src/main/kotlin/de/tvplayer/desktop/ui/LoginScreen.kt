package de.tvplayer.desktop.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tvplayer.shared.data.Account
import de.tvplayer.shared.data.insecureServerHint
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

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF241D55), Bg), radius = 1400f))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 56.dp)) {
                LogoTile(64.dp)
                Text("TV Player", fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                Text("Live TV, Filme und Serien deines Anbieters – übersichtlich auf dem PC.", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(top = 8.dp).width(420.dp))
                Spacer(Modifier.height(28.dp))
                Feature(Icons.Rounded.LiveTv, "Live TV mit Programmvorschau")
                Feature(Icons.Rounded.Movie, "Filme mit „Weiterschauen“")
                Feature(Icons.Rounded.VideoLibrary, "Serien nach Staffeln sortiert")
            }
            Column(
                Modifier.width(500.dp).background(Surface1.copy(alpha = 0.94f), RoundedCornerShape(24.dp)).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Anmelden", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Gib die Zugangsdaten deines Anbieters ein.", color = Muted)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(xtream, { xtream = true; error = null }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Xtream Codes") }
                    SegmentedButton(!xtream, { xtream = false; error = null }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("M3U-Playlist") }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(if (busy) "Wird geprüft …" else "Anmelden") { submit() }
                    if (busy) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(28.dp), color = Accent, strokeWidth = 3.dp)
                }
                error?.let { Text(it, color = Danger) }
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
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color(0x33FFFFFF), focusedLabelColor = Accent),
        modifier = Modifier.fillMaxWidth().testTag("field:$label"),
    )
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
        Box(Modifier.background(Surface2, RoundedCornerShape(12.dp)).padding(10.dp)) { Icon(icon, null, tint = Accent2) }
        Text(text, fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp))
    }
}
