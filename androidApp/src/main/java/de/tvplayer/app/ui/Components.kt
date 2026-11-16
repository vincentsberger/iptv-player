package de.tvplayer.app.ui

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Section

// ---------- Farben ----------
val Bg = Color(0xFF0B0D14)
val Surface1 = Color(0xFF161A26)
val Surface2 = Color(0xFF202638)
val Accent = Color(0xFF7C6CFF)
val Accent2 = Color(0xFF4FA3FF)
val Muted = Color(0xFF8B93A7)
val Danger = Color(0xFFFF6B7A)
val FocusBg = Color.White
val FocusFg = Color(0xFF0B0D14)
val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Bg,
            onSurface = Color.White, onBackground = Color.White,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = Bg, contentColor = Color.White),
        ) { content() }
    }
}

// ---------- Kleine Bausteine ----------

@Composable
fun Icon(vector: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Image(vector, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

@Composable
fun LogoTile(size: Dp = 48.dp) {
    Box(Modifier.size(size).background(AccentGradient, RoundedCornerShape(size / 3.5f)), Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, tint = Color.White, size = size * 0.6f)
    }
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
        style == ButtonStyle.Primary || selected -> Accent
        style == ButtonStyle.Danger -> Danger.copy(alpha = 0.18f)
        else -> Surface2
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = if (style == ButtonStyle.Danger) Danger else Color.White,
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
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Surface2 else Surface1, shape)
            .border(2.dp, if (focused) Accent else Color(0x22FFFFFF), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (leading != null) Icon(leading, tint = if (focused) Accent else Muted, size = 22.dp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 18.sp, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.05f, 0.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.background(Color.White.copy(alpha = a), RoundedCornerShape(12.dp)))
}

@Composable
fun Spinner(size: Dp = 52.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(size)) {
        drawArc(
            brush = Brush.sweepGradient(listOf(Accent2, Accent)),
            startAngle = angle, sweepAngle = 270f, useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.height(height).background(Color(0x40FFFFFF), RoundedCornerShape(height))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AccentGradient, RoundedCornerShape(height)))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Das hat nicht geklappt", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        TvButton("Erneut versuchen", style = ButtonStyle.Primary, onClick = onRetry)
    }
}

@Composable
fun EmptyView(title: String, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
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
    containerColor = Surface1, contentColor = Color.White,
    focusedContainerColor = Surface2, focusedContentColor = Color.White,
)

@Composable
private fun cardBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, Color.White)),
)

@Composable
private fun BoxScope.FavoriteBadge() {
    Box(
        Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color(0xCC000000), CircleShape),
        Alignment.Center,
    ) { Icon(Icons.Rounded.Star, tint = Color(0xFFFFC94D), size = 16.dp) }
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
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocused(onFocus).size(width = if (wide) 260.dp else 150.dp, height = if (wide) 146.dp else 222.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = cardColors(),
        border = cardBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        if (item.logo != null) {
            AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                item.name, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = cardColors(),
        border = cardBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ChannelLogo(item, 56.dp)
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (favorite) FavoriteBadge()
    }
}

@Composable
fun ChannelLogo(item: Item, size: Dp, modifier: Modifier = Modifier) {
    if (item.logo != null) {
        AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).background(Surface2, RoundedCornerShape(10.dp)), Alignment.Center) {
            Text(item.name.trim().take(2).uppercase(), fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold, color = Muted)
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
