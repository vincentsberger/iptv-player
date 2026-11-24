package de.vincentsberger.iptvplayer.app.ui

import de.vincentsberger.iptvplayer.shared.posterFacts
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Refresh
import de.vincentsberger.iptvplayer.shared.OverviewWarning
import de.vincentsberger.iptvplayer.shared.OverviewRow
import de.vincentsberger.iptvplayer.shared.DownloadInfo

import androidx.compose.material.icons.rounded.DownloadForOffline
import de.vincentsberger.iptvplayer.app.download.Downloads

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Typography
import de.vincentsberger.iptvplayer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.depth
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import de.vincentsberger.iptvplayer.shared.flagOf
import de.vincentsberger.iptvplayer.shared.overviewNote
import de.vincentsberger.iptvplayer.shared.formatCount
import de.vincentsberger.iptvplayer.shared.standLabel
import de.vincentsberger.iptvplayer.shared.countLabel
import de.vincentsberger.iptvplayer.shared.Palette

// ---------- Gestaltung: Farben aus der gemeinsamen Palette (shared/Palette.kt, Kontraste dort per Test geprüft) ----------
val BgDeep = Color(Palette.BgDeep)
val Bg = Color(Palette.Bg)
val Surface0 = Color(Palette.Surface0)
val Surface1 = Color(Palette.Surface1)
val Surface2 = Color(Palette.Surface2)
val Line = Color(Palette.Line)
val TextColor = Color(Palette.Text)
val TextSoft = Color(Palette.TextSoft)
val Muted = Color(Palette.Muted)
val Signal = Color(Palette.Signal)
val OnSignal = Color(Palette.OnSignal)
val Danger = Color(Palette.Danger)
val FocusBg = Color(Palette.FocusBg)
val FocusFg = Color(Palette.FocusFg)
val Shade = Color(Palette.Shade)


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

/** Fläche für Info-Spalten und Formulare: schwebt mit weichem Schatten über dem Hintergrund. */
fun Modifier.panel(): Modifier = this
    .shadow(18.dp, Corner, ambientColor = Color.Black, spotColor = Color.Black)
    .background(Surface1.copy(alpha = 0.88f), Corner)

/** Ecken: knapp – Kästen statt Kapseln. */
val Corner = RoundedCornerShape(4.dp)
val SmallCorner = RoundedCornerShape(2.dp)

val Archivo = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal), Font(R.font.archivo_500, FontWeight.Medium),
    Font(R.font.archivo_600, FontWeight.SemiBold), Font(R.font.archivo_700, FontWeight.Bold),
)
/** Ziffern (Sendernummern, Uhrzeiten) in Monospace. */
val Mono = FontFamily(Font(R.font.ibmplexmono_400, FontWeight.Normal))

private val AppTypography = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = Archivo, fontFeatureSettings = "tnum")
    t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Signal, onPrimary = OnSignal, background = Bg, surface = Bg,
            onSurface = TextColor, onBackground = TextColor, error = Danger,
        ),
        typography = AppTypography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = Bg, contentColor = TextColor),
        ) { content() }
    }
}

// ---------- Kleine Bausteine ----------

@Composable
fun Icon(vector: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Image(vector, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

/** Überschrift: Archivo SemiBold, leicht enger gesetzt. */
@Composable
fun Headline(text: String, size: TextUnit = 28.sp, modifier: Modifier = Modifier, maxLines: Int = 1, color: Color = TextColor) {
    Text(
        text, modifier, color = color, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.12f,
        letterSpacing = (-0.02f * size.value).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Seitentitel mit rotem Symbol davor. */
@Composable
fun TitleWithIcon(title: String, icon: ImageVector?, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, Modifier.padding(end = 12.dp), tint = Signal, size = (size.value * 0.8f).dp)
        Headline(title, size)
    }
}

/** Kleine Rubrik-Zeile („Jetzt live“, „Danach“) in Versalien mit Sperrung. */
@Composable
fun Kicker(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
}

/** Feine Trennlinie. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))
}

/** Rotes „Sendelicht“ – das Markenzeichen. */
@Composable
fun SignalMark(size: Dp = 12.dp) {
    Box(Modifier.size(size).background(Signal))
}

/** Wortmarke: Sendelicht plus Titel. */
@Composable
fun Masthead(size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SignalMark((size.value * 0.4f).dp)
        Headline("IPTV Player", size, Modifier.padding(start = (size.value * 0.45f).dp))
    }
}

/** Symbol je Inhaltsart – dieselben wie in der Navigation. */
fun sectionIcon(section: Section): ImageVector = when (section) {
    Section.LIVE -> Icons.Rounded.LiveTv
    Section.MOVIES -> Icons.Rounded.Movie
    Section.SERIES -> Icons.Rounded.VideoLibrary
}

/** Pulsiert sanft, solange etwas live läuft – bewusst nur an dieser Stelle. */
@Composable
fun LiveDot(size: Dp = 9.dp, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha",
    )
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha }.background(Signal, CircleShape))
}

/**
 * Laufende Uhr („Mo., 16. Nov. · 14:52“). Takt auf Dispatchers.Default (echte Zeit): ein delay-Loop
 * im Compose-Takt hielte UI-Tests endlos „beschäftigt“.
 */
@Composable
fun Clock(modifier: Modifier = Modifier, pattern: String = "EE, d. MMM · HH:mm") {
    val fmt = remember(pattern) { SimpleDateFormat(pattern, Locale.GERMAN) }
    val now by produceState(Date()) {
        withContext(Dispatchers.Default) {
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L)
                value = Date()
            }
        }
    }
    Text(fmt.format(now), modifier, color = Muted, fontFamily = Mono, fontSize = 15.sp, maxLines = 1, softWrap = false)
}

enum class ButtonStyle { Primary, Secondary, Danger }

/** Schaltfläche: im Fokus immer weiß mit dunkler Schrift – überall gleich, damit man sofort sieht, wo man ist. */
@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Secondary,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val container = when {
        style == ButtonStyle.Primary || selected -> Signal
        style == ButtonStyle.Danger -> Danger.copy(alpha = 0.18f)
        else -> Surface2
    }
    Surface(
        onClick = onClick,
        modifier = modifier.keyHints(KeyHint(RemoteKey.OK, "Auswählen")),
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = when { style == ButtonStyle.Danger -> Danger; style == ButtonStyle.Primary || selected -> OnSignal; else -> TextColor },
            focusedContainerColor = FocusBg,
            focusedContentColor = FocusFg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, size = 20.dp)
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Texteingabe für die Fernbedienung. OK öffnet die Bildschirmtastatur. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    /** Wird bei „Fertig“ auf der Bildschirmtastatur ausgelöst (z. B. Anmelden). */
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = Corner
    // Der Fokus kommt per Fernbedienung, bevor die Tastatur aufgeht. Schrumpft der sichtbare Bereich danach,
    // scrollt das Feld sich selbst wieder ins Bild. Die Höhe als Schlüssel startet das bei jedem Animationsschritt
    // neu, so dass am Ende die endgültige Tastaturhöhe zählt.
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused && imeBottom > 0) bringIntoView.bringIntoView() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextColor, fontSize = 18.sp, fontFamily = Archivo),
        cursorBrush = SolidColor(Signal),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            // Fernbedienung: Ein Textfeld würde die Pfeiltasten für den Cursor behalten und den Fokus
            // nie wieder hergeben. Getippt wird auf dem TV ohnehin über die Bildschirmtastatur,
            // daher bewegen die Pfeiltasten hier immer den Fokus zum nächsten Element.
            .onPreviewKeyEvent { ev -> ev.type == KeyEventType.KeyDown && dpadDirection(ev.key)?.let(focusManager::moveFocus) == true }
            .onFocusChanged { focused = it.isFocused }
            .keyHints(KeyHint(RemoteKey.OK, "Tastatur öffnen"), KeyHint(RemoteKey.UP_DOWN, "nächstes Feld"))
            .background(if (focused) Surface2 else Surface1, shape)
            .border(2.dp, if (focused) Signal else Line, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (leading != null) Icon(leading, tint = if (focused) Signal else Muted, size = 22.dp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 18.sp, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** Pfeiltaste → Fokusrichtung; andere Tasten → null. */
internal fun dpadDirection(key: Key): FocusDirection? = when (key) {
    Key.DirectionUp -> FocusDirection.Up
    Key.DirectionDown -> FocusDirection.Down
    Key.DirectionLeft -> FocusDirection.Left
    Key.DirectionRight -> FocusDirection.Right
    else -> null
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.05f, 0.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.background(Color.White.copy(alpha = a), Corner))
}

@Composable
fun Spinner(size: Dp = 52.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(size)) {
        drawArc(
            color = Signal,
            startAngle = angle, sweepAngle = 270f, useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Butt),
        )
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Line) {
    Box(modifier.height(height).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Signal))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Headline(text, 20.sp, modifier)
}

@Composable
fun ErrorView(message: String, onDownloads: (() -> Unit)? = null, onRetry: () -> Unit) {
    val context = LocalContext.current
    val offline = remember { !Downloads.isOnline(context) }
    val hasDownloads = Downloads.all.any { it.state == DownloadInfo.State.DONE }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.CloudOff, Modifier.padding(bottom = 16.dp), tint = Danger, size = 48.dp)
        Headline(if (offline) "Keine Internetverbindung" else "Das hat nicht geklappt", 26.sp)
        Text(
            if (offline && hasDownloads) "Deine heruntergeladenen Filme und Folgen kannst du trotzdem ansehen." else message,
            color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Offline mit Downloads: das ist der sinnvollste nächste Schritt, also zuerst.
            if (onDownloads != null && hasDownloads) {
                TvButton("Zu den Downloads", Modifier.testTag("error:downloads"), icon = Icons.Rounded.DownloadForOffline, style = ButtonStyle.Primary, onClick = onDownloads)
            }
            TvButton("Erneut versuchen", style = if (onDownloads != null && hasDownloads) ButtonStyle.Secondary else ButtonStyle.Primary, onClick = onRetry)
        }
    }
}

@Composable
fun EmptyView(title: String, text: String, icon: ImageVector? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (icon != null) Icon(icon, Modifier.padding(bottom = 16.dp), tint = Muted, size = 52.dp)
        Headline(title, 26.sp)
        Text(text, color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).width(460.dp))
    }
}

// ---------- Karten ----------

private fun Modifier.onFocused(action: () -> Unit) = onFocusChanged { if (it.isFocused) action() }

@Composable
private fun cardColors() = ClickableSurfaceDefaults.colors(
    containerColor = Surface1, contentColor = TextColor,
    focusedContainerColor = Surface2, focusedContentColor = TextColor,
)

@Composable
private fun cardBorder() = ClickableSurfaceDefaults.border(
    border = Border(BorderStroke(1.dp, Line)),
    focusedBorder = Border(BorderStroke(3.dp, FocusBg)),
)

@Composable
private fun BoxScope.FavoriteBadge() {
    Box(
        Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Bg.copy(alpha = 0.85f), SmallCorner),
        Alignment.Center,
    ) { Icon(Icons.Rounded.Star, tint = Signal, size = 16.dp) }
}

/** Heruntergeladen: auch ohne Internet abspielbar. */
@Composable
private fun BoxScope.OfflineBadge() {
    Row(
        Modifier.align(Alignment.TopStart).padding(8.dp).background(Bg.copy(alpha = 0.88f), SmallCorner).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DownloadForOffline, tint = Signal, size = 16.dp)
        Text("Offline", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Hochformat-Cover für Filme und Serien. */
@Composable
fun PosterCard(
    item: Item,
    favorite: Boolean,
    progress: Float,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    /** Im Grid: Zellbreite füllen und das Seitenverhältnis halten (feste Größe würde das Cover beschneiden). */
    fillCell: Boolean = false,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocused(onFocus).itemHints(item).then(
            if (fillCell) Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f)
            else Modifier.size(width = if (wide) 260.dp else 150.dp, height = if (wide) 146.dp else 222.dp),
        ),
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = cardColors(),
        border = cardBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        if (item.logo != null) {
            AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Ohne Cover: Art des Inhalts als Symbol plus Titel, damit die Karte nicht leer wirkt.
            Box(Modifier.fillMaxSize()) {
                Icon(sectionIcon(item.section), Modifier.align(Alignment.TopStart).padding(12.dp), tint = Line, size = 34.dp)
                Headline(item.name, 17.sp, Modifier.align(Alignment.BottomStart).padding(12.dp), maxLines = 4)
            }
        }
        if (favorite) FavoriteBadge()
        if (Downloads.isDone(item.url)) OfflineBadge()
        // Jahr und Bewertung aus der Liste, auf dunklem Grund unten links (ohne Detailabfrage).
        if (item.logo != null) posterFacts(item)?.let {
            Box(Modifier.fillMaxSize()) {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xCC000000), SmallCorner).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
    }
}

/** Querformat-Kachel mit Senderlogo. */
@Composable
fun LogoCard(
    item: Item,
    favorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocused(onFocus).itemHints(item).size(width = 190.dp, height = 112.dp),
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = cardColors(),
        border = cardBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ChannelLogo(item, 56.dp)
            Text(splitProviderTag(item.name).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (favorite) FavoriteBadge()
    }
}

@Composable
fun ChannelLogo(item: Item, size: Dp, modifier: Modifier = Modifier) {
    if (item.logo != null) {
        // Senderlogos sind meist breiter als hoch → breiter Rahmen, sonst bleiben sie winzig.
        AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = modifier.size(width = size * 1.6f, height = size))
    } else {
        // Ohne Logo der Anfangsbuchstabe statt einer Kürzel-Kachel.
        Box(modifier.size(size), Alignment.Center) {
            Headline(item.name.trim().take(1).uppercase(), (size.value * 0.55f).sp, color = Muted)
        }
    }
}

/** Höhe, auf die „Alle anzeigen“ in einer Kartenreihe mittig gesetzt wird. */
private val PosterHeightForShowAll = 200.dp

/** Liste mit Rubrik-Titel und waagerecht scrollenden Karten (wie bei Streamingdiensten). */
@Composable
fun ContentRow(
    title: String,
    items: List<Item>,
    rowKey: String,
    restoreKey: String?,
    restoreRequester: FocusRequester,
    favorites: Set<String>,
    progressOf: (Item) -> Float,
    onFocus: (Item, String) -> Unit,
    onClick: (List<Item>, Int) -> Unit,
    onLongClick: (Item) -> Unit,
    /** Optional: „Alle anzeigen“ als letzter Knopf der Reihe (auf der Fernbedienung einfach weiter nach rechts). */
    onShowAll: (() -> Unit)? = null,
) {
    Column(Modifier.padding(top = 6.dp)) {
        SectionTitle(title, Modifier.padding(start = 48.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items.size) { i ->
                val item = items[i]
                val key = "$rowKey:$i"
                val mod = (if (key == restoreKey) Modifier.focusRequester(restoreRequester) else Modifier).testTag("card:$key")
                if (item.section == Section.LIVE) {
                    LogoCard(item, item.favKey in favorites, { onFocus(item, key) }, { onClick(items, i) }, { onLongClick(item) }, mod)
                } else {
                    PosterCard(
                        item, item.favKey in favorites, progressOf(item), { onFocus(item, key) },
                        { onClick(items, i) }, { onLongClick(item) }, mod,
                        wide = item.section == Section.SERIES && !item.isSeriesFolder,
                    )
                }
            }
            if (onShowAll != null) item {
                Box(Modifier.height(PosterHeightForShowAll), contentAlignment = Alignment.Center) {
                    TvButton("Alle anzeigen", Modifier.testTag("$rowKey:showall"), onClick = onShowAll)
                }
            }
        }
    }
}

/** Bei Kategorien: Landesflagge, wenn das Kürzel ein Land ist – sonst das Etikett („4K“, „24/7“). */
@Composable
fun FlagOrTag(tag: String, modifier: Modifier = Modifier) {
    val flag = flagOf(tag)
    if (flag != null) Text(flag, modifier, fontSize = 20.sp) else TagChip(tag, modifier)
}

/** Anbieter-Etikett („DE“, „4K“, „24/7“) als kleines Mono-Kästchen vor Kategorie- und Sendernamen. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(tag, modifier.border(1.dp, Line, SmallCorner).padding(horizontal = 5.dp, vertical = 1.dp), color = Muted, fontFamily = Mono, fontSize = 13.sp)
}

// ---------- Bewegung ----------
// Kurz und zielgerichtet: Bewegung zeigt Richtung und Zusammenhang, nie Selbstzweck. UI-Übergänge ≤ 320 ms.

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Bildschirmwechsel: vorwärts leicht von rechts, zurück von links; zum Player nur Überblenden. */
fun AnimatedContentTransitionScope<Screen>.screenTransition(): ContentTransform {
    if (targetState is Screen.Player || initialState is Screen.Player) {
        return fadeIn(tween(220)) togetherWith fadeOut(tween(160))
    }
    val dir = if (targetState.depth >= initialState.depth) 1 else -1
    return (fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(320, easing = Smooth)) { dir * it / 14 }) togetherWith
        (fadeOut(tween(140)) + slideOutHorizontally(tween(320, easing = Smooth)) { -dir * it / 20 })
}

/** Tabwechsel: kurzes Überblenden mit kleinem Versatz von unten. */
fun <T> AnimatedContentTransitionScope<T>.tabTransition(): ContentTransform =
    (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(280, easing = Smooth)) { 18 }) togetherWith fadeOut(tween(120))

/** Roter Auswahlstrich, der aus der Mitte aufwächst, statt hart zu erscheinen. */
@Composable
fun SelectionMark(visible: Boolean, modifier: Modifier = Modifier, height: Dp = 20.dp) {
    val h by animateDpAsState(if (visible) height else 0.dp, tween(220, easing = Smooth), label = "mark")
    Box(modifier.width(3.dp).height(height), Alignment.Center) { Box(Modifier.fillMaxWidth().height(h).background(Signal)) }
}

/** Unbestimmter Fortschrittsbalken: ein Segment läuft durch – zeigt „es passiert etwas“, ohne Prozent vorzutäuschen. */
@Composable
fun IndeterminateBar(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "bar").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = Smooth)), label = "t")
    BoxWithConstraints(modifier.fillMaxWidth().height(3.dp).background(Line).clipToBounds()) {
        Box(Modifier.width(maxWidth * 0.32f).fillMaxHeight().offset(x = maxWidth * (t * 1.32f - 0.32f)).background(Signal))
    }
}

/** Übersicht nach dem Laden: Anzahl je Bereich, Veränderung, Warnungen – wartet immer auf „Weiter“ (hat den Fokus). */
@Composable
fun PlaylistOverviewScreen(vm: AppState, rows: List<OverviewRow>) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { continueFocus.requestFocus() } }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.width(620.dp)) {
            Masthead(30.sp)
            Headline("Playlist", 28.sp, Modifier.padding(top = 32.dp))
            val stand = rows.mapNotNull { it.loadedAt }.minOrNull()?.let { " · Stand: ${standLabel(it)}" }.orEmpty()
            Text(vm.accountLabel + stand, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Icon(sectionIcon(row.section), tint = Muted, size = 22.dp)
                    Text(row.section.title, fontSize = 17.sp, modifier = Modifier.width(120.dp).padding(start = 12.dp))
                    Text("${formatCount(row.count)} ${countLabel(row.section)}", fontFamily = Mono, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    val warn = row.warning != null
                    Icon(if (warn) Icons.Rounded.Warning else Icons.Rounded.Check, tint = if (warn) Danger else Signal, size = 20.dp)
                    Text(overviewNote(row), color = if (warn) Danger else Muted, fontSize = 15.sp, modifier = Modifier.width(270.dp).padding(start = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TvButton("Neu laden", Modifier.testTag("overview:reload"), icon = Icons.Rounded.Refresh) { vm.reloadFromNetwork() }
                TvButton("Weiter", Modifier.focusRequester(continueFocus).testTag("overview:continue"), style = ButtonStyle.Primary) { vm.continueFromOverview() }
            }
        }
    }
}

/** Ladebildschirm nach Anmeldung/Start: Fortschritt je Bereich, damit lange Playlists nicht wie ein Hänger wirken. */
@Composable
fun PlaylistLoadingScreen(vm: AppState) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.width(440.dp)) {
            Masthead(30.sp)
            Headline("Playlist wird geladen", 28.sp, Modifier.padding(top = 32.dp))
            Text(vm.accountLabel, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
            IndeterminateBar()
            Spacer(Modifier.height(14.dp))
            Section.entries.forEach { s -> LoadingStep(s, vm.sections[s], vm.section(s).items.size) }
            Text("Große Playlists brauchen beim ersten Laden etwas länger.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun LoadingStep(section: Section, raw: SectionState?, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Icon(sectionIcon(section), tint = Muted, size = 20.dp)
        Text(section.title, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        // raw == null: noch nicht gestartet; leer, aber fertig (z. B. Anbieter ohne Serien) zählt als erledigt.
        val status = when {
            raw == null || raw.loading -> 0
            raw.error != null -> 2
            else -> 1
        }
        AnimatedContent(status, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "step") { st ->
            when (st) {
                0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Spinner(16.dp)
                    Text("lädt …", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$count ${countLabel(section)}", color = Muted, fontSize = 16.sp, fontFamily = Mono)
                    Icon(Icons.Rounded.Check, Modifier.padding(start = 8.dp), tint = Signal, size = 18.dp)
                }
                else -> Text("Fehler", color = Danger, fontSize = 16.sp)
            }
        }
    }
}

/** Live-Kennzeichen in allen Apps: roter Punkt, daneben „LIVE“ in Weiß. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Signal, androidx.compose.foundation.shape.CircleShape))
        Text(
            "LIVE", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 6.dp),
        )
    }
}
