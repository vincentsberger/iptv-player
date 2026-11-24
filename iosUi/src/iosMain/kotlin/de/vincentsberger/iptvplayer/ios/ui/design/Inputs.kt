package de.vincentsberger.iptvplayer.ios.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vincentsberger.iptvplayer.ios.ui.AppFont
import de.vincentsberger.iptvplayer.ios.ui.Corner
import de.vincentsberger.iptvplayer.ios.ui.Line
import de.vincentsberger.iptvplayer.ios.ui.Muted
import de.vincentsberger.iptvplayer.ios.ui.Signal
import de.vincentsberger.iptvplayer.ios.ui.Surface1
import de.vincentsberger.iptvplayer.ios.ui.TextColor

/**
 * Eingabefeld des Entwurfs (phone.css .fld/.inp): Beschriftung darüber, Feld 48 dp auf surface1, Rand im Fokus in
 * Signal, Symbol links. [secret] verdeckt den Inhalt und zeigt rechts ein Auge zum Aufdecken.
 */
@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    icon: ImageVector? = null,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value, onChange,
            singleLine = true,
            interactionSource = source,
            textStyle = TextStyle(color = TextColor, fontSize = 16.sp, fontFamily = AppFont),
            cursorBrush = SolidColor(Signal),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onAny = { onIme() }),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(Corner).background(Surface1)
                        .border(1.dp, if (focused) Signal else Line, Corner).padding(start = 12.dp, end = if (secret) 4.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Icon(icon, null, tint = Muted, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, color = Muted, fontSize = 16.sp, maxLines = 1)
                        inner()
                    }
                    if (secret) {
                        Box(Modifier.size(40.dp).pressable { revealed = !revealed }, contentAlignment = Alignment.Center) {
                            Icon(
                                if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (revealed) "Passwort verbergen" else "Passwort anzeigen",
                                tint = Muted, modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/** Kleines Suchfeld ohne Beschriftung (phone.css .inp.s): 40 dp, Lupe links, rechts Löschen, sobald etwas drinsteht. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    val source = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value, onChange,
        modifier.semantics { contentDescription = placeholder },
        singleLine = true,
        interactionSource = source,
        textStyle = TextStyle(color = TextColor, fontSize = 15.sp, fontFamily = AppFont),
        cursorBrush = SolidColor(Signal),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        // Ohne eigene Aktion tut die Such-Taste nichts – die Tastatur bliebe über den Treffern.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(Corner).background(Surface1)
                    .border(1.dp, if (focused) Signal else Line, Corner).padding(start = 12.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Muted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 15.sp, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) Box(
                    Modifier.size(36.dp).semantics { contentDescription = "Suche löschen" }.pressable { onChange("") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(18.dp)) }
            }
        },
    )
}
