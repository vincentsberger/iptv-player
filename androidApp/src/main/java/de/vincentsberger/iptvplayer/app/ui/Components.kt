package de.vincentsberger.iptvplayer.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// ---------- Gestaltung „Graphit + Signalrot“ (siehe .tastemaker/style-lock.md) ----------
// Kontrast geprüft (WCAG ≥ 4,5:1): TextColor und Muted auf allen Flächen; Signal und Danger auf Bg und Surface1.
// Signal auf Surface2 schafft nur 3,95:1 – dort nie als Textfarbe. Fokus bleibt Weiß: auf 3 m Abstand das deutlichste Signal.
val Bg = Color(0xFF1B1F25)
val Surface1 = Color(0xFF252A31)
val Surface2 = Color(0xFF30363E)
val Line = Color(0xFF3B424B)
val TextColor = Color(0xFFE8EBEF)
val Muted = Color(0xFFA3AAB3)
val Signal = Color(0xFFFF5247)
val OnSignal = Color(0xFF14171C)
val Danger = Color(0xFFFF9270)
val FocusBg = Color.White
val FocusFg = Color(0xFF14171C)

/** Bild des gerade fokussierten Inhalts – färbt den Hintergrund („Ambiente“). */
class AmbientImage { var url by mutableStateOf<String?>(null) }
val LocalAmbient = staticCompositionLocalOf { AmbientImage() }

/**
 * Hintergrund mit Tiefe: stark weichgezeichnetes Bild des fokussierten Inhalts (die Farbe kommt aus dem
 * Inhalt) plus Lichtverlauf von oben links. Das Bild wird winzig geladen (24 px) und hochskaliert –
 * das zeichnet auch auf Android < 12 weich, wo Modifier.blur wirkungslos ist, und kostet fast nichts.
 */
@Composable
fun DepthBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(Bg)) {
        Crossfade(LocalAmbient.current.url, animationSpec = tween(600), label = "ambient") { u ->
            if (u != null) AsyncImage(
                ImageRequest.Builder(context).data(u).size(24).build(), null,
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.5f),
            )
        }
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
      CompositionLocalProvider(LocalAmbient provides remember { AmbientImage() }) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = Bg, contentColor = TextColor),
        ) { content() }
      }
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
    Text(text.uppercase(), modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
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
    Text(fmt.format(now), modifier, color = Muted, fontFamily = Mono, fontSize = 13.sp)
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
        modifier = modifier,
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
            // Fernbedienung: Ein Textfeld würde die Pfeiltasten für den Cursor behalten und den Fokus
            // nie wieder hergeben. Getippt wird auf dem TV ohnehin über die Bildschirmtastatur,
            // daher bewegen die Pfeiltasten hier immer den Fokus zum nächsten Element.
            .onPreviewKeyEvent { ev -> ev.type == KeyEventType.KeyDown && dpadDirection(ev.key)?.let(focusManager::moveFocus) == true }
            .onFocusChanged { focused = it.isFocused }
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
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.height(height).background(Line)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Signal))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Headline(text, 20.sp, modifier)
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.CloudOff, Modifier.padding(bottom = 16.dp), tint = Danger, size = 48.dp)
        Headline("Das hat nicht geklappt", 26.sp)
        Text(message, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        TvButton("Erneut versuchen", style = ButtonStyle.Primary, onClick = onRetry)
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

/** Großes, weich ausgeblendetes Hintergrundbild des gerade fokussierten Titels. */
@Composable
fun Backdrop(url: String?) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(400).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).fillMaxHeight(0.8f).alpha(0.55f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Bg, 0.4f to Bg.copy(alpha = 0.9f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.75f to Bg)))
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
        modifier = modifier.onFocused(onFocus).then(
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
        modifier = modifier.onFocused(onFocus).size(width = 190.dp, height = 112.dp),
        shape = ClickableSurfaceDefaults.shape(Corner),
        colors = cardColors(),
        border = cardBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ChannelLogo(item, 56.dp)
            Text(splitProviderTag(item.name).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
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
        }
    }
}

/** Anbieter-Etikett („DE“, „4K“, „24/7“) als kleines Mono-Kästchen vor Kategorie- und Sendernamen. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(tag, modifier.border(1.dp, Line, SmallCorner).padding(horizontal = 5.dp, vertical = 1.dp), color = Muted, fontFamily = Mono, fontSize = 11.sp)
}
