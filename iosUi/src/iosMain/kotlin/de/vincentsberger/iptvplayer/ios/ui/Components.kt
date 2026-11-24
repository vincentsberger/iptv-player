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

/** Kleine Rubrik-Zeile („Jetzt“, „Favoriten“) – Großbuchstaben mit Sperrung. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Muted) {
    Text(text.uppercase(), modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
}

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
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier, enabled = enabled, shape = Corner,
        colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = OnSignal),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier, shape = Corner, colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
}

/** Dünner Fortschrittsbalken (Sendung, Weiterschauen): rot gespielt, grau der Rest. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(SmallCorner).background(Line)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).background(Signal))
    }
}

/** Anbieter-Etikett („DE“, „4K“, „24/7“) als kleines Mono-Kästchen. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(
        tag, modifier.border(1.dp, Line, SmallCorner).padding(horizontal = 4.dp, vertical = 1.dp),
        color = Muted, fontFamily = Mono, fontSize = 11.sp,
    )
}

/** Landesflagge, wenn das Kürzel ein Land ist – sonst das Etikett. */
@Composable
fun FlagOrTag(tag: String, modifier: Modifier = Modifier) {
    val flag = flagOf(tag)
    if (flag != null) Text(flag, modifier, fontSize = 18.sp) else TagChip(tag, modifier)
}

/** Senderlogo; ohne Logo oder bei Ladefehler ein schlichtes TV-Zeichen. */
@Composable
fun ChannelLogo(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val box = modifier.size(size).clip(Corner).background(Surface1)
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Rounded.Tv, null, tint = Line, modifier = Modifier.size(size * 0.5f)) }
    }
    // AsyncImage statt SubcomposeAsyncImage: Subcomposition je Zeile wäre in langen Listen zu teuer.
    // Bis das Logo da ist (oder ohne Logo) zeigt der Kasten das TV-Zeichen darunter.
    Box(box) {
        placeholder()
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
    }
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

/** Live-Kennzeichen in allen Apps: roter Punkt, daneben „LIVE“ in Weiß. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Signal, androidx.compose.foundation.shape.CircleShape))
        Text(
            "LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 5.dp),
        )
    }
}
