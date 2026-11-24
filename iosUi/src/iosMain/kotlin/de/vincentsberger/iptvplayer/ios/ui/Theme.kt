package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import androidx.compose.ui.unit.dp

// Farben wie PC- und TV-App (Graphit, .tastemaker/style-lock.md): Signalrot nur als Markierung, nie als Fläche
// hinter Text. Schrift wie am PC: Archivo, Uhrzeiten und Nummern in IBM Plex Mono (beide OFL, aus dem App-Bundle –
// eingetragen in iosApp/xcode/project.yml). Fehlen die Dateien, bleibt es bei der Systemschrift.

val Bg = Color(0xFF1B1F25)
val Surface1 = Color(0xFF252A31)
val Surface2 = Color(0xFF30363E)
val Line = Color(0xFF3B424B)
val TextColor = Color(0xFFE8EBEF)
val Muted = Color(0xFFA3AAB3)
val Signal = Color(0xFFFF5247)
val OnSignal = Color(0xFF14171C)
val Danger = Color(0xFFFF9270)

/** Schriftdatei aus dem App-Bundle, oder null (z. B. in Vorschauen ohne Bundle). */
private fun bundleFont(name: String, weight: FontWeight): Font? {
    val path = NSBundle.mainBundle.pathForResource(name, ofType = "ttf") ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return Font(name, bytes, weight)
}

/** Archivo 400–700 wie in der PC-App. */
val AppFont: FontFamily by lazy {
    listOfNotNull(
        bundleFont("Archivo-400", FontWeight.Normal), bundleFont("Archivo-500", FontWeight.Medium),
        bundleFont("Archivo-600", FontWeight.SemiBold), bundleFont("Archivo-700", FontWeight.Bold),
    ).takeIf { it.isNotEmpty() }?.let { FontFamily(it) } ?: FontFamily.Default
}

/** Ziffern, die sauber untereinanderstehen: Sendernummern, Uhrzeiten, Spielzeit. */
val Mono: FontFamily by lazy { bundleFont("IBMPlexMono-400", FontWeight.Normal)?.let { FontFamily(it) } ?: FontFamily.Monospace }

/** Ecken: bewusst knapp – Kästen statt Kapseln. */
val Corner = RoundedCornerShape(4.dp)
val SmallCorner = RoundedCornerShape(2.dp)

private fun typography() = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = AppFont, fontFeatureSettings = "tnum")
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
            primary = Signal, onPrimary = OnSignal,
            secondary = Muted, onSecondary = Bg,
            background = Bg, onBackground = TextColor,
            surface = Bg, onSurface = TextColor,
            surfaceVariant = Surface1, onSurfaceVariant = Muted,
            surfaceContainer = Surface1, surfaceContainerHigh = Surface2,
            outline = Line, outlineVariant = Line, error = Danger,
        ),
        typography = typography(),
        content = content,
    )
}
