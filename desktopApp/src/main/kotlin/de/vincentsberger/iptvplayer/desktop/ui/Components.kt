package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import de.vincentsberger.iptvplayer.shared.posterFacts
import de.vincentsberger.iptvplayer.shared.flagOf
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Refresh
import de.vincentsberger.iptvplayer.shared.OverviewWarning
import de.vincentsberger.iptvplayer.shared.OverviewRow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.depth
import androidx.compose.material3.LocalContentColor
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
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
import de.vincentsberger.iptvplayer.shared.overviewNote
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.standLabel
import de.vincentsberger.iptvplayer.shared.countLabel
import de.vincentsberger.iptvplayer.shared.brand.LoadStep
import de.vincentsberger.iptvplayer.shared.brand.loadStep
import de.vincentsberger.iptvplayer.shared.brand.loadingHeadline

/**
 * Gestaltung „Anthrazit & Bernstein“: warmes Anthrazit wie ein abgedunkelter Kinosaal, warmweißer Text,
 * Bernstein nur als Signal (Auswahl, Live, Hauptaktion). Schrift Archivo, Ziffern in IBM Plex Mono.
 * Farbwerte und Kontrastprüfung (WCAG ≥ 4,5:1 auf allen Flächen): shared/Palette.kt und PaletteTest.
 */
class Palette(
    val bg: Color, val surface1: Color, val surface2: Color, val line: Color,
    val text: Color, val muted: Color, val signal: Color, val onSignal: Color, val danger: Color,
    val font: FontFamily, val mono: FontFamily,
)

private fun archivo(w: Int, weight: FontWeight) = Font("font/Archivo-$w.ttf", weight)

val Anthrazit = Palette(
    // Farben aus der gemeinsamen Palette (shared/Palette.kt, Kontraste dort per Test geprüft) – wie TV und iPhone.
    bg = Color(Tokens.Bg), surface1 = Color(Tokens.Surface1), surface2 = Color(Tokens.Surface2), line = Color(Tokens.Line),
    text = Color(Tokens.Text), muted = Color(Tokens.Muted), signal = Color(Tokens.Signal), onSignal = Color(Tokens.OnSignal), danger = Color(Tokens.Danger),
    font = FontFamily(archivo(400, FontWeight.Normal), archivo(500, FontWeight.Medium), archivo(600, FontWeight.SemiBold), archivo(700, FontWeight.Bold)),
    mono = FontFamily(Font("font/IBMPlexMono-400.ttf", FontWeight.Normal)),
)

val LocalPalette = staticCompositionLocalOf { Anthrazit }

/** Schmales Fenster (< 1280 dp): Seitenleiste, Kategorien und Info-Spalte rücken zusammen, damit die Senderliste lesbar bleibt. */
val LocalCompact = staticCompositionLocalOf { false }

/**
 * Gleichbleibender Hintergrund: Anthrazit mit leichtem Licht von oben links – bewusst ohne Bilder des Inhalts,
 * damit Text überall gleich gut lesbar ist.
 */
@Composable
fun DepthBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Bg)) {
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
fun AppTheme(palette: Palette = Anthrazit, content: @Composable () -> Unit) {
    val p = palette
    val scheme = darkColorScheme(
        primary = p.signal, onPrimary = p.onSignal, secondary = p.signal, onSecondary = p.onSignal,
        background = p.bg, onBackground = p.text, surface = p.surface1, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, outlineVariant = p.line, error = p.danger,
    )
    // LocalContentColor: Material 3 färbt Text ohne Farbangabe sonst schwarz – auf dunklem Grund unsichtbar.
    // Rechtsklick-Menüs in den App-Farben statt im hellen Standard.
    val menu = remember(p) { DefaultContextMenuRepresentation(backgroundColor = p.surface2, textColor = p.text, itemHoverColor = p.surface1) }
    CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides p.text, LocalContextMenuRepresentation provides menu) {
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
    // Wächst aus der Mitte auf, statt hart zu erscheinen – zeigt, wohin die Auswahl gesprungen ist.
    val h by animateDpAsState(if (visible) 20.dp else 0.dp, tween(220, easing = Smooth), label = "mark")
    Box(modifier.width(3.dp).height(20.dp), Alignment.Center) { Box(Modifier.fillMaxWidth().height(h).background(Signal)) }
}

/** Wortmarke: Logo plus Titel. */
@Composable
fun Masthead(size: TextUnit = 21.sp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LogoMark(maxOf(size.value * 1.6f, 30f).dp) // mind. 30 dp: sonst Strich < 1,5 dp
        Headline("IPTV Player", size, Modifier.padding(start = (size.value * 0.45f).dp))
    }
}

/** Fokusrahmen: warmweiß wie der Fokus der TV-App (Style-Lock `focus-bg`). */
val FocusRing = Color(Tokens.FocusBg)

/**
 * Rahmen um das fokussierte Element – nur nach Tastaturbedienung ([KeyboardState.keyboardMode]), nicht nach Klicks
 * (wie `:focus-visible` im Web). Vor `clickable` setzen: [onFocusChanged] beobachtet das nächste fokussierbare Element.
 */
fun Modifier.focusRing(shape: Shape = Corner): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val visible = focused && LocalKeyboard.current.keyboardMode
    onFocusChanged { focused = it.isFocused }.then(if (visible) Modifier.border(2.dp, FocusRing, shape) else Modifier)
}

/** Hand als Mauszeiger – für alles Klickbare der PC-App (Textfelder behalten den Textcursor). */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick, modifier.handCursor(), shape = Corner,
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
        onClick, Modifier.handCursor(), shape = Corner,
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
        LogoLoader(56.dp, text)
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
internal fun BoxScope.FavoriteToggle(
    favorite: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
) {
    if (favorite || visible) {
        IconButton(
            onClick = onToggle,
            interactionSource = interactionSource,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).handCursor().background(Bg.copy(alpha = 0.8f), SmallCorner),
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
    /** Detail-Infos (Beschreibung eines Films), sobald geladen – für die Info-Karte. */
    info: MediaInfo? = null,
    /** Maus verweilt auf der Kachel: hier die Detail-Infos anfordern (nie schon beim bloßen Überfahren). */
    onDwell: () -> Unit = {},
    /** Detailabfrage beendet (auch ohne Ergebnis, z. B. M3U) – `app.mediaInfoLoaded(item)`. */
    infoLoaded: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val cardInteraction = remember { MutableInteractionSource() }
    val cardHovered by cardInteraction.collectIsHoveredAsState()
    // Wer auf den Stern der Kachel zielt, will merken – dort kein Verweilen, sonst öffnet der Klick die Karte (= den Titel).
    val starInteraction = remember { MutableInteractionSource() }
    val starHovered by starInteraction.collectIsHoveredAsState()
    var showCard by remember(item.favKey) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (hovered) 1.03f else 1f, tween(160, easing = FastOutSlowInEasing), label = "scale")
    val live = item.section == Section.LIVE
    val wide = live || (item.section == Section.SERIES && !item.isSeriesFolder)
    val w = if (wide) 220.dp else 160.dp
    val h = if (live) 124.dp else if (wide) 124.dp else 236.dp
    if (hasHoverCard(item)) {
        val anyHover = (hovered && !starHovered) || cardHovered
        LaunchedEffect(anyHover) {
            if (anyHover) {
                delay(HOVER_CARD_DELAY_MS)
                showCard = true
                if (item.section == Section.MOVIES) onDwell()
            } else {
                delay(HOVER_CARD_GRACE_MS)
                showCard = false
            }
        }
    }

    val menu = listOfNotNull(
        ContextMenuItem(if (live) "Ansehen" else "Öffnen", onClick),
        onToggleFavorite?.let { ContextMenuItem(favoriteMenuLabel(favorite), it) },
    )
    Column(modifier.width(w).graphicsLayer { scaleX = scale; scaleY = scale }) {
        WithContextMenu(menu) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = Corner,
            colors = CardDefaults.cardColors(containerColor = Surface1),
            border = BorderStroke(if (hovered) 2.dp else 1.dp, if (hovered) Signal else Line),
            modifier = Modifier.size(w, h).handCursor().shadow(if (hovered) 16.dp else 6.dp, Corner).hoverable(interaction),
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
                // Steht die Info-Karte, trägt sie den Stern – sonst gäbe es ihn doppelt.
                if (onToggleFavorite != null) FavoriteToggle(favorite, hovered && !showCard, onToggleFavorite, starInteraction)
                if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
            if (showCard) HoverInfoCard(item, info, infoLoaded, w * 2.1f, favorite, cardInteraction, onClick, onToggleFavorite)
        }
        }
        // Ohne Cover steht der Titel schon groß in der Karte – dann keine zweite Unterschrift.
        if (live || item.logo != null) Text(
            splitProviderTag(item.name).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
            color = if (hovered) TextColor else Muted, modifier = Modifier.padding(top = 8.dp, start = 1.dp),
        )
        if (!live) posterFacts(item)?.let { Text(it, color = Muted, fontSize = 12.sp, fontFamily = Mono, maxLines = 1, modifier = Modifier.padding(start = 1.dp)) }
    }
}

/** Rechtsklick-Menü um [content]; ohne Einträge gibt es keins. */
@Composable
fun WithContextMenu(items: List<ContextMenuItem>, content: @Composable () -> Unit) {
    if (items.isEmpty()) content() else ContextMenuArea(items = { items }, content = content)
}

fun favoriteMenuLabel(favorite: Boolean) = if (favorite) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen"

/** Waagerechte Reihe mit Scrollbalken (Mausrad + Umschalt oder Balken ziehen). */
@Composable
fun ItemsRow(
    title: String,
    items: List<Item>,
    favorites: Set<String>,
    progressOf: (Item) -> Float,
    onOpen: (List<Item>, Int) -> Unit,
    onToggleFavorite: (Item) -> Unit,
    /** Optional: „Alle anzeigen“ rechts neben dem Titel (z. B. für die Neu-Reihen). */
    onShowAll: (() -> Unit)? = null,
    /** Detail-Infos für die Info-Karte (`app::mediaInfoFor`) und deren Anforderung beim Verweilen (`app::requestMediaInfo`). */
    infoOf: (Item) -> MediaInfo? = { null },
    onDwell: (Item) -> Unit = {},
    infoLoadedOf: (Item) -> Boolean = { true },
) {
    val state = rememberLazyListState()
    Column(Modifier.padding(bottom = 28.dp)) {
        Rule(Modifier.padding(horizontal = 40.dp))
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Headline(title, 21.sp, Modifier.weight(1f))
            if (onShowAll != null) SecondaryButton("Alle anzeigen", onClick = onShowAll)
        }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(items) { i, item ->
                MediaCard(item, item.favKey in favorites, progressOf(item), { onOpen(items, i) }, { onToggleFavorite(item) },
                    info = infoOf(item), onDwell = { onDwell(item) }, infoLoaded = infoLoadedOf(item))
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


/** Anbieter-Etikett („DE“, „4K“, „24/7“) als kleines Mono-Kästchen vor Kategorie- und Sendernamen. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(tag, modifier.border(1.dp, Line, SmallCorner).padding(horizontal = 5.dp, vertical = 1.dp), color = Muted, fontFamily = Mono, fontSize = 11.sp)
}

/**
 * Mitgelieferte Flaggen (Twemoji, nur Länderflaggen, CC-BY 4.0): Windows hat keine Flaggen-Emojis – Segoe UI Emoji
 * zeigt dort nur die Buchstaben. Mit eigenem Font sehen die Flaggen unter Windows, Linux und macOS gleich aus.
 */
private val FlagFont = FontFamily(Font("font/TwemojiCountryFlags.ttf"))

/** Landesflagge, wenn das Kürzel ein Land ist – sonst das Etikett ([TagChip]). */
@Composable
fun FlagOrTag(tag: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp) {
    val flag = flagOf(tag)
    if (flag != null) Text(flag, modifier, fontFamily = FlagFont, fontSize = size) else TagChip(tag, modifier)
}

// ---------- Bewegung ----------
// Kurz und zielgerichtet: Bewegung zeigt Richtung und Zusammenhang, nie Selbstzweck. UI-Übergänge ≤ 320 ms.

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Bildschirmwechsel: vorwärts leicht von rechts, zurück von links; zum Player nur Überblenden.
 * Alt und neu blenden gleichzeitig (ohne Verzögerung) – eine Lücke, in der beide fast durchsichtig sind, wirkt wie
 * ein Flackern. Der feste Anthrazit-Grund in Main.kt sorgt dafür, dass dabei nichts Helles durchscheint.
 */
fun AnimatedContentTransitionScope<Screen>.screenTransition(): ContentTransform {
    if (targetState is Screen.Player || initialState is Screen.Player) {
        return fadeIn(tween(280, easing = Smooth)) togetherWith fadeOut(tween(280, easing = Smooth))
    }
    val dir = if (targetState.depth >= initialState.depth) 1 else -1
    return (fadeIn(tween(280, easing = Smooth)) + slideInHorizontally(tween(320, easing = Smooth)) { dir * it / 24 }) togetherWith
        (fadeOut(tween(220, easing = Smooth)) + slideOutHorizontally(tween(320, easing = Smooth)) { -dir * it / 32 })
}

/** Tabwechsel: weiches Überblenden mit kleinem Versatz von unten, alt und neu gleichzeitig. */
fun <T> AnimatedContentTransitionScope<T>.tabTransition(): ContentTransform =
    (fadeIn(tween(240, easing = Smooth)) + slideInVertically(tween(280, easing = Smooth)) { 14 }) togetherWith
        fadeOut(tween(180, easing = Smooth))

/** Unbestimmter Fortschrittsbalken: ein Segment läuft durch – zeigt „es passiert etwas“, ohne Prozent vorzutäuschen. */
@Composable
fun IndeterminateBar(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "bar").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = Smooth)), label = "t")
    BoxWithConstraints(modifier.fillMaxWidth().height(3.dp).background(Line).clipToBounds()) {
        Box(Modifier.width(maxWidth * 0.32f).fillMaxHeight().offset(x = maxWidth * (t * 1.32f - 0.32f)).background(Signal))
    }
}

/** Ladebildschirm nach Anmeldung/Start: Fortschritt je Bereich, damit lange Playlists nicht wie ein Hänger wirken. */
@Composable
fun PlaylistLoadingScreen(app: AppState) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.width(440.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoLoader(64.dp)
                Headline("IPTV Player", 30.sp, Modifier.padding(start = 20.dp))
            }
            // Überschrift des ersten noch offenen Bereichs; sind alle fertig, bleibt die letzte stehen.
            val headline = loadingHeadline(Section.entries.associateWith { app.sections[it] })
            var shown by remember { mutableStateOf(headline ?: "Sender werden gesucht") }
            LaunchedEffect(headline) { if (headline != null) shown = headline }
            Headline(shown, 28.sp, Modifier.padding(top = 32.dp))
            Text(app.accountLabel, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
            IndeterminateBar()
            Spacer(Modifier.height(14.dp))
            val active = Section.entries.firstOrNull { app.sections[it].let { raw -> raw == null || raw.loading } }
            Section.entries.forEach { s -> LoadingStep(s, loadStep(app.sections[s], s == active), app.section(s).items.size) }
            Text(
                "Große Playlists brauchen beim ersten Laden etwas länger.", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

/** Übersicht nach dem Laden: Anzahl je Bereich, Veränderung, Warnungen – wartet immer auf „Weiter“. */
@Composable
fun PlaylistOverviewScreen(app: AppState, rows: List<OverviewRow>) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.width(560.dp)) {
            Masthead(30.sp)
            Headline("Playlist", 28.sp, Modifier.padding(top = 32.dp))
            val stand = rows.mapNotNull { it.loadedAt }.minOrNull()?.let { " · Stand: ${standLabel(it)}" }.orEmpty()
            Text(app.accountLabel + stand, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            rows.forEach { OverviewLine(it) }
            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                SecondaryButton("Neu laden", Icons.Rounded.Refresh) { app.reloadFromNetwork() }
                PrimaryButton("Weiter", Icons.Rounded.ChevronRight) { app.continueFromOverview() }
            }
        }
    }
}

@Composable
private fun OverviewLine(row: OverviewRow) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Icon(sectionIcon(row.section), null, tint = Muted, modifier = Modifier.size(20.dp))
        Text(row.section.title, modifier = Modifier.width(110.dp).padding(start = 12.dp))
        Text("${formatCount(row.count)} ${countLabel(row.section)}", fontFamily = Mono, modifier = Modifier.weight(1f))
        val warn = row.warning != null
        if (warn) Icon(Icons.Rounded.Warning, null, tint = Danger, modifier = Modifier.size(18.dp))
        else Icon(Icons.Rounded.Check, null, tint = Signal, modifier = Modifier.size(18.dp))
        Text(overviewNote(row), color = if (warn) Danger else Muted, fontSize = 14.sp, modifier = Modifier.width(250.dp).padding(start = 10.dp))
    }
}

@Composable
private fun LoadingStep(section: Section, step: LoadStep, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Icon(sectionIcon(section), null, tint = Muted, modifier = Modifier.size(20.dp))
        Text(section.title, modifier = Modifier.weight(1f).padding(start = 12.dp))
        val note = when (step) {
            LoadStep.WAITING -> "wartet"
            LoadStep.LOADING -> "lädt …"
            LoadStep.DONE -> "$count ${countLabel(section)}"
            LoadStep.FAILED -> "Fehler"
        }
        Text(
            note, color = if (step == LoadStep.FAILED) Danger else Muted, fontSize = 14.sp,
            fontFamily = if (step == LoadStep.DONE) Mono else null, modifier = Modifier.padding(end = 10.dp),
        )
        LoadCheck(step)
    }
}

/** Live-Kennzeichen in allen Apps: roter Punkt, daneben „LIVE“ in Weiß. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(Signal, androidx.compose.foundation.shape.CircleShape))
        Text(
            "LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 6.dp),
        )
    }
}
