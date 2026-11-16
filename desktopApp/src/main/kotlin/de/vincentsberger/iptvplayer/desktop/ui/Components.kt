package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gestaltung „Graphit + Signalrot“: neutrales Graphit wie im Sende-Regieraum, kühles Weiß,
 * Rot nur als Signal (Auswahl, Live, Hauptaktion). Schrift Archivo, Ziffern in IBM Plex Mono.
 * Kontrast geprüft (WCAG ≥ 4,5:1): text und muted auf allen Flächen; signal und danger auf bg und surface1.
 * Signal auf surface2 schafft nur 3,95:1 – dort nie Signal als Textfarbe, nur als Markierung.
 */
class Palette(
    val bg: Color, val surface1: Color, val surface2: Color, val line: Color,
    val text: Color, val muted: Color, val signal: Color, val onSignal: Color, val danger: Color,
    val font: FontFamily, val mono: FontFamily,
)

private fun archivo(w: Int, weight: FontWeight) = Font("font/Archivo-$w.ttf", weight)

val Graphit = Palette(
    bg = Color(0xFF1B1F25), surface1 = Color(0xFF252A31), surface2 = Color(0xFF30363E), line = Color(0xFF3B424B),
    text = Color(0xFFE8EBEF), muted = Color(0xFFA3AAB3), signal = Color(0xFFFF5247), onSignal = Color(0xFF14171C), danger = Color(0xFFFF9270),
    font = FontFamily(archivo(400, FontWeight.Normal), archivo(500, FontWeight.Medium), archivo(600, FontWeight.SemiBold), archivo(700, FontWeight.Bold)),
    mono = FontFamily(Font("font/IBMPlexMono-400.ttf", FontWeight.Normal)),
)

val LocalPalette = staticCompositionLocalOf { Graphit }

/** Bild des gerade gewählten Inhalts – färbt bei Depth.Ambient/Glass den Hintergrund. */
class AmbientImage { var url by mutableStateOf<String?>(null) }
val LocalAmbient = staticCompositionLocalOf { AmbientImage() }

/**
 * Hintergrund mit Tiefe („Ambiente“): stark weichgezeichnetes Bild des gewählten Inhalts – die Farbe
 * kommt aus dem Inhalt statt aus der Palette – plus Lichtverlauf von oben links.
 */
@Composable
fun DepthBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Bg)) {
        Crossfade(LocalAmbient.current.url, animationSpec = tween(600), label = "ambient") { u ->
            if (u != null) AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.5f))
        }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), center = Offset(0f, 0f), radius = 1400f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.55f)))))
    }
}

/** Fläche für Info-Spalten, Listen und Formulare: schwebt mit weichem Schatten über dem Hintergrund. */
fun Modifier.panel(): Modifier = composed {
    shadow(18.dp, Corner, ambientColor = Color.Black, spotColor = Color.Black).background(Surface1.copy(alpha = 0.88f), Corner)
}

// Kurzzugriffe auf die aktive Palette. Bg/Surface1/Surface2 unterscheiden sich kaum –
// Auswahl deshalb nie nur über die Fläche, immer zusätzlich mit Signal-Markierung.
val Bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface2
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val TextColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
val Signal: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.signal
val OnSignal: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onSignal
val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
/** Ziffern (Sendernummern, Uhrzeiten) in Monospace. */
val Mono: FontFamily @Composable @ReadOnlyComposable get() = LocalPalette.current.mono

/** Ecken: bewusst knapp – Kästen statt Kapseln. */
val Corner = RoundedCornerShape(4.dp)
val SmallCorner = RoundedCornerShape(2.dp)

private fun appTypography(font: FontFamily) = Typography().let { t ->
    // Tabellenziffern: Uhrzeiten und Sendernummern stehen sauber untereinander.
    fun TextStyle.f() = copy(fontFamily = font, fontFeatureSettings = "tnum")
    t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun AppTheme(palette: Palette = Graphit, content: @Composable () -> Unit) {
    val p = palette
    val scheme = darkColorScheme(
        primary = p.signal, onPrimary = p.onSignal, secondary = p.signal, onSecondary = p.onSignal,
        background = p.bg, onBackground = p.text, surface = p.surface1, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.danger,
    )
    CompositionLocalProvider(LocalPalette provides p, LocalAmbient provides remember { AmbientImage() }) {
        MaterialTheme(colorScheme = scheme, typography = remember(p) { appTypography(p.font) }, content = content)
    }
}

/** Überschrift: Archivo SemiBold, leicht enger gesetzt. */
@Composable
fun Headline(text: String, size: TextUnit = 28.sp, modifier: Modifier = Modifier, maxLines: Int = 1, color: Color = TextColor) {
    Text(
        text, modifier, color = color, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.12f,
        letterSpacing = (-0.02f * size.value).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Kleine Rubrik-Zeile („Jetzt“, „Danach“) – Kapitälchen-Ersatz mit Sperrung. */
@Composable
fun Kicker(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
}

/** Feine Trennlinie wie im gedruckten Programm. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))
}

/** Einheitliche Farben für Eingabefelder: Signal nur im Fokus, sonst feine Linie. */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Signal, unfocusedBorderColor = Line,
    focusedLabelColor = Signal, unfocusedLabelColor = Muted, cursorColor = Signal,
    focusedLeadingIconColor = Signal, unfocusedLeadingIconColor = Muted,
)

/** Signal-Markierung links an ausgewählten Zeilen (die Fläche allein reicht als Hinweis nicht). */
@Composable
fun SelectionMark(visible: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.width(3.dp).height(20.dp).background(if (visible) Signal else Color.Transparent))
}

/** Wortmarke: kleines rotes „Sendelicht“ plus Titel. */
@Composable
fun Masthead(size: TextUnit = 21.sp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((size.value * 0.4f).dp).background(Signal))
        Headline("IPTV Player", size, Modifier.padding(start = (size.value * 0.45f).dp))
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick, modifier, shape = Corner,
        colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = OnSignal),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Box(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick, shape = Corner,
        border = BorderStroke(1.dp, if (danger) Danger else Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Danger else TextColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Box(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Medium)
    }
}

/** Fortschritt als gerader Balken – kein Verlauf, keine Kapsel. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).background(Line)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Signal))
    }
}

@Composable
fun Loading(text: String = "Wird geladen …") {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Signal, trackColor = Line, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
        Text(text, color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.CloudOff, null, tint = Danger, modifier = Modifier.padding(bottom = 16.dp).size(44.dp))
        Headline("Das hat nicht geklappt", 28.sp)
        Text(message, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        PrimaryButton("Erneut versuchen", onClick = onRetry)
    }
}

@Composable
fun EmptyView(title: String, text: String, icon: ImageVector? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) Icon(icon, null, tint = Muted, modifier = Modifier.padding(bottom = 16.dp).size(48.dp))
        Headline(title, 28.sp)
        Text(text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).width(440.dp))
    }
}

/** Senderlogo; ohne Logo der Anfangsbuchstabe statt einer Kürzel-Kachel. */
@Composable
fun ChannelLogo(item: Item, size: Dp, modifier: Modifier = Modifier) {
    if (item.logo != null) {
        // Senderlogos sind meist breiter als hoch → breiter Rahmen, sonst bleiben sie winzig.
        AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = modifier.size(width = size * 1.6f, height = size))
    } else {
        Box(modifier.size(size), Alignment.Center) {
            Headline(item.name.trim().take(1).uppercase(), (size.value * 0.55f).sp, color = Muted)
        }
    }
}

@Composable
private fun BoxScope.FavoriteToggle(favorite: Boolean, visible: Boolean, onToggle: () -> Unit) {
    if (favorite || visible) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).background(Bg.copy(alpha = 0.8f), SmallCorner),
        ) {
            Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (favorite) "Favorit entfernen" else "Als Favorit merken", tint = if (favorite) Signal else TextColor, modifier = Modifier.size(18.dp))
        }
    }
}

/** Karte für Film, Serie, Folge oder Sender – Hover hebt mit Signal-Rahmen hervor, Stern zum Merken. */
@Composable
fun MediaCard(
    item: Item,
    favorite: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.03f else 1f, tween(160, easing = FastOutSlowInEasing), label = "scale")
    val ambient = LocalAmbient.current
    LaunchedEffect(hovered) { if (hovered && item.logo != null) ambient.url = item.logo }
    val live = item.section == Section.LIVE
    val wide = live || (item.section == Section.SERIES && !item.isSeriesFolder)
    val w = if (wide) 220.dp else 160.dp
    val h = if (live) 124.dp else if (wide) 124.dp else 236.dp

    Column(modifier.width(w).graphicsLayer { scaleX = scale; scaleY = scale }) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = Corner,
            colors = CardDefaults.cardColors(containerColor = Surface1),
            border = BorderStroke(if (hovered) 2.dp else 1.dp, if (hovered) Signal else Line),
            modifier = Modifier.size(w, h).shadow(if (hovered) 16.dp else 6.dp, Corner).hoverable(interaction),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    live -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { ChannelLogo(item, 64.dp) }
                    item.logo != null -> AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> {
                        // Ohne Cover: Art des Inhalts als Symbol plus Titel, damit die Karte nicht leer wirkt.
                        Icon(sectionIcon(item.section), null, tint = Line, modifier = Modifier.align(Alignment.TopStart).padding(14.dp).size(36.dp))
                        Headline(item.name, 18.sp, Modifier.align(Alignment.BottomStart).padding(14.dp), maxLines = 4)
                    }
                }
                if (onToggleFavorite != null) FavoriteToggle(favorite, hovered, onToggleFavorite)
                if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        }
        // Ohne Cover steht der Titel schon groß in der Karte – dann keine zweite Unterschrift.
        if (live || item.logo != null) Text(
            item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
            color = if (hovered) TextColor else Muted, modifier = Modifier.padding(top = 8.dp, start = 1.dp),
        )
    }
}

/** Waagerechte Reihe mit Scrollbalken (Mausrad + Umschalt oder Balken ziehen). */
@Composable
fun ItemsRow(
    title: String,
    items: List<Item>,
    favorites: Set<String>,
    progressOf: (Item) -> Float,
    onOpen: (List<Item>, Int) -> Unit,
    onToggleFavorite: (Item) -> Unit,
) {
    val state = rememberLazyListState()
    Column(Modifier.padding(bottom = 28.dp)) {
        Rule(Modifier.padding(horizontal = 40.dp))
        Headline(title, 21.sp, Modifier.padding(start = 40.dp, top = 14.dp, bottom = 10.dp))
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(items) { i, item ->
                MediaCard(item, item.favKey in favorites, progressOf(item), { onOpen(items, i) }, { onToggleFavorite(item) })
            }
        }
        HorizontalScrollbar(rememberScrollbarAdapter(state), Modifier.padding(horizontal = 40.dp).fillMaxWidth())
    }
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, icon: ImageVector? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            TitleWithIcon(title, icon, 38.sp)
            if (subtitle != null) Text(subtitle, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        trailing()
    }
}

/** Seitentitel mit rotem Symbol davor. */
@Composable
fun TitleWithIcon(title: String, icon: ImageVector?, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = Signal, modifier = Modifier.padding(end = 12.dp).size((size.value * 0.8f).dp))
        Headline(title, size)
    }
}

/** Symbol je Inhaltsart – dieselben wie in der Seitenleiste. */
fun sectionIcon(section: Section): ImageVector = when (section) {
    Section.LIVE -> Icons.Rounded.LiveTv
    Section.MOVIES -> Icons.Rounded.Movie
    Section.SERIES -> Icons.Rounded.VideoLibrary
}

/**
 * „Sendelicht“: pulsiert sanft, solange etwas live läuft. Bewusst nur an dieser einen Stelle –
 * Dauerbewegung an vielen Stellen würde bei täglicher Nutzung stören.
 */
@Composable
fun LiveDot(size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha",
    )
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha }.background(Signal, CircleShape))
}

/** Taste als kleine Kappe, z. B. für Tastenkürzel. */
@Composable
fun KeyCap(label: String) {
    Text(
        label, color = TextColor, fontFamily = Mono, fontSize = 13.sp,
        modifier = Modifier.background(Surface1, SmallCorner).border(1.dp, Line, SmallCorner).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Laufende Uhr („Mo., 16. Nov. · 14:52“), aktualisiert sich zur vollen Minute. */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    val fmt = remember { DateTimeFormatter.ofPattern("EE, d. MMM · HH:mm", Locale.GERMAN) }
    // Takt auf Dispatchers.Default (echte Zeit): Ein delay-Loop im Compose-Takt hielte UI-Tests
    // endlos „beschäftigt“, weil deren virtuelle Uhr jeden ausstehenden delay abarbeiten will.
    val now by produceState(LocalDateTime.now()) {
        withContext(Dispatchers.Default) {
            while (true) {
                delay((60 - LocalDateTime.now().second) * 1000L)
                value = LocalDateTime.now()
            }
        }
    }
    Text(now.format(fmt), color = Muted, fontFamily = Mono, fontSize = 12.sp, modifier = modifier)
}

/** Anbieter-Etikett („DE“, „4K“, „24/7“) als kleines Mono-Kästchen vor Kategorie- und Sendernamen. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(tag, modifier.border(1.dp, Line, SmallCorner).padding(horizontal = 5.dp, vertical = 1.dp), color = Muted, fontFamily = Mono, fontSize = 11.sp)
}
