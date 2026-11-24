package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.ios.ui.design.AppButton
import de.vincentsberger.iptvplayer.ios.ui.design.ButtonStyle
import de.vincentsberger.iptvplayer.shared.flagOf
import platform.Foundation.NSBundle

/** Version aus dem App-Bundle (MARKETING_VERSION = appVersion aus gradle.properties). */
fun appVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"

@Composable
fun Headline(text: String, size: TextUnit = 24.sp, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text, modifier, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f,
        letterSpacing = (-0.02f * size.value).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Kleine Rubrik-Zeile („Jetzt“, „Favoriten“) – Großbuchstaben mit Sperrung (Baustein aus design/Text.kt). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Muted) =
    de.vincentsberger.iptvplayer.ios.ui.design.Kicker(text, modifier, color)

/** Kopfzeile mit Zurück-Pfeil (unter der Statusleiste). */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = TextColor) }
        else Spacer(Modifier.width(12.dp))
        Headline(title, 22.sp, Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) =
    AppButton(text, onClick, modifier, ButtonStyle.Primary, icon = icon, enabled = enabled)

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) =
    AppButton(text, onClick, modifier, ButtonStyle.Secondary, icon = icon)

/** Dünner Fortschrittsbalken (Sendung, Weiterschauen) – Baustein aus design/Progress.kt. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) =
    de.vincentsberger.iptvplayer.ios.ui.design.ProgressBar(progress, modifier, height)

/** Anbieter-Etikett („4K“, „RAW“, „24/7“) als kleines Kennzeichen – Baustein aus design/Badges.kt. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) = de.vincentsberger.iptvplayer.ios.ui.design.Tag(tag, modifier)

/** Landesflagge, wenn das Kürzel ein Land ist – sonst das Etikett. */
@Composable
fun FlagOrTag(tag: String, modifier: Modifier = Modifier) {
    val flag = flagOf(tag)
    if (flag != null) Text(flag, modifier, fontSize = 18.sp) else TagChip(tag, modifier)
}

/** Leerer oder noch nicht befüllter Bereich: großes Symbol, Überschrift, kurzer Hinweis, optional eine Aktion. */
@Composable
fun EmptyState(
    icon: ImageVector?, title: String, text: String?, modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(icon, null, tint = Line, modifier = Modifier.size(48.dp).padding(bottom = 4.dp))
        Headline(title, 20.sp, Modifier.padding(top = 8.dp), maxLines = 2)
        if (text != null) Text(text, color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (action != null) Box(Modifier.padding(top = 16.dp)) { action() }
    }
}

/** Rückfrage vor dem Ausblenden einer Kategorie (langes Drücken auf die Kategorie). Einblenden in den Einstellungen. */
@Composable
fun HideCategoryDialog(name: String, onDismiss: () -> Unit, onHide: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("„$name“ ausblenden?") },
        text = { Text("Die Kategorie verschwindet aus den Listen und der Suche. Wieder einblenden kannst du sie in den Einstellungen.") },
        confirmButton = { androidx.compose.material3.TextButton({ onDismiss(); onHide() }) { Text("Ausblenden", color = Danger) } },
        dismissButton = { androidx.compose.material3.TextButton(onDismiss) { Text("Abbrechen") } },
        containerColor = Surface1,
    )
}

/** Live-Kennzeichen in allen Apps: roter Punkt, daneben „LIVE“ in Weiß – Baustein aus design/Badges.kt. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, onImage: Boolean = false) =
    de.vincentsberger.iptvplayer.ios.ui.design.LiveBadge(modifier, onImage)

/**
 * Wischaktion nach links (wie in iOS-Listen): Hinter der Zeile erscheint [label] mit [icon]; weit genug gewischt löst
 * [onSwipe] aus und die Zeile springt zurück (sie verschwindet nie von selbst – Löschen fragt vorher nach).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeAction(label: String, icon: ImageVector, color: androidx.compose.ui.graphics.Color, onSwipe: () -> Unit, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value -> if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) onSwipe(); false },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(Surface2).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = color, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
        },
    ) { content() }
}

/** Nach unten ziehen lädt die Listen neu (wie „Listen neu laden“ in den Einstellungen). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PullToReload(app: de.vincentsberger.iptvplayer.shared.AppState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val loading = de.vincentsberger.iptvplayer.shared.data.Section.entries.any { app.section(it).loading }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(loading, onRefresh = { app.reloadAll() }, modifier = modifier) { content() }
}
