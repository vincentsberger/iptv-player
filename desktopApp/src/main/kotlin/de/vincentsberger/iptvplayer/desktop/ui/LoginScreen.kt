package de.vincentsberger.iptvplayer.desktop.ui

import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import de.vincentsberger.iptvplayer.shared.xtreamFromLink
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
fun LoginScreen(onLogin: suspend (Account) -> Unit, onCancel: (() -> Unit)? = null) {
    var xtream by remember { mutableStateOf(true) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var m3u by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < 1280.dp
    Row(Modifier.fillMaxSize().background(Bg)) {
        // Links (Entwurf „01 Login“): ruhige Bildfläche mit Verlauf, Rubrik, Überschrift, drei nummerierte Punkte.
        // Schmales Fenster: nur das Formular.
        if (!narrow) Box(
            Modifier.width(620.dp).fillMaxHeight()
                .background(Brush.linearGradient(listOf(Color(Tokens.Surface2), Color(Tokens.Surface0), Bg), start = Offset.Zero, end = Offset(900f, 1400f))),
        ) {
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Bg.copy(alpha = 0.2f), 0.7f to Bg.copy(alpha = 0.6f), 1f to Bg)))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.3f to Bg.copy(alpha = 0f), 1f to Bg.copy(alpha = 0.92f))))
            Column(Modifier.align(Alignment.BottomStart).padding(56.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Kicker("IPTV Player")
                Text(
                    "Live TV, Filme und Serien deines Anbieters.", color = Color.White, fontSize = 44.sp, lineHeight = 46.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.88).sp, modifier = Modifier.widthIn(max = 500.dp),
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Feature("01", "Live TV mit ganzem Programm von Jetzt und Danach")
                    Feature("02", "Filme und Serien mit Startbild und Reihen")
                    Feature("03", "Weiterschauen, wo du aufgehört hast")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(440.dp).verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Mit onCancel: weiterer Anbieter, zurück zur Startseite möglich.
                    Headline(if (onCancel != null) "Anbieter hinzufügen" else "Anmelden", 28.sp)
                    Text("Gib die Zugangsdaten deines Anbieters ein.", color = Muted, fontSize = 14.sp)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Surface2, activeContentColor = TextColor, activeBorderColor = Line,
                        inactiveContainerColor = Bg, inactiveContentColor = Muted, inactiveBorderColor = Line,
                    )
                    SegmentedButton(xtream, { xtream = true; error = null }, SegmentedButtonDefaults.itemShape(0, 2, Corner), colors = colors, icon = {}) { Text("Xtream Codes", fontSize = 14.sp) }
                    SegmentedButton(!xtream, { xtream = false; error = null }, SegmentedButtonDefaults.itemShape(1, 2, Corner), colors = colors, icon = {}) { Text("M3U-Playlist", fontSize = 14.sp) }
                }
                if (xtream) {
                    // Ein eingefügter Xtream-Link (Strg V oder Rechtsklick) verteilt sich auf Server, Benutzer und Passwort.
                    Field(server, { v ->
                        val link = xtreamFromLink(v)
                        if (link != null) { server = link.server; user = link.username; pass = link.password; error = null } else server = v
                    }, "Server", "http://anbieter.example:8080", Icons.Rounded.Dns, focus = firstField)
                    Field(user, { user = it }, "Benutzername", null, Icons.Rounded.Person)
                    Field(
                        pass, { pass = it }, "Passwort", null, Icons.Rounded.Lock,
                        password = !showPass, onDone = ::submit,
                        trailing = {
                            IconButton({ showPass = !showPass }, Modifier.handCursor()) {
                                Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Passwort anzeigen", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                    insecureServerHint(server)?.let { Text(it, color = Muted, fontSize = 13.sp) }
                } else {
                    Field(m3u, { m3u = it }, "Link zur M3U-Playlist", "http://anbieter.example/get.php?…", Icons.Rounded.Link, onDone = ::submit)
                }
                error?.let { Text(it, color = Danger, fontSize = 14.sp) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(
                        { submit() }, Modifier.weight(1f).height(46.dp), enabled = !busy, shape = Corner,
                        colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = OnSignal, disabledContainerColor = Signal.copy(alpha = 0.45f), disabledContentColor = OnSignal),
                    ) {
                        Text(if (busy) "Wird geprüft …" else "Anmelden", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (!busy) ShortcutCap("Enter", Modifier.padding(start = 8.dp), onPrimary = true)
                    }
                    if (onCancel != null && !busy) SecondaryButton("Abbrechen") { onCancel() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyHint(listOf("Tab"), "nächstes Feld")
                    KeyHint(listOf("Strg", "V"), "Link mit Zugangsdaten ins Server-Feld einfügen – füllt alles aus")
                }
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Text(
                        "Diese App enthält keine Sender oder Inhalte. Du brauchst ein eigenes Abo bei einem Anbieter, der zur Verbreitung berechtigt ist.",
                        color = Muted, fontSize = 11.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}
}

/** „[Strg] [V] Link … einfügen“ – Tastenkappen mit Erklärung (Entwurf `.kh`). */
@Composable
private fun KeyHint(keys: List<String>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { ShortcutCap(it) }
        Text(text, color = Muted, fontSize = 12.sp, maxLines = 1)
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
    focus: FocusRequester? = null,
) {
    // Beschriftung über dem Feld, Rahmen im Fokus in Signal (Entwurf `.fld/.inp`).
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder?.let { { Text(it, color = Muted, fontSize = 15.sp) } },
            leadingIcon = { Icon(icon, null, tint = Muted, modifier = Modifier.size(18.dp)) },
            trailingIcon = trailing,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            shape = Corner,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("field:$label").semantics { contentDescription = label }
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        )
    }
}

/** Nummerierter Punkt der Bildfläche: Mono-Nummer in Signal, Text hell. */
@Composable
private fun Feature(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, color = Signal, fontSize = 14.sp, fontFamily = Mono)
        Text(text, color = Color(Tokens.TextSoft), fontSize = 15.sp)
    }
}
