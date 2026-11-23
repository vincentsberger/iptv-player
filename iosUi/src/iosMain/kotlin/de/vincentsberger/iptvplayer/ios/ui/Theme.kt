package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Farben wie PC- und TV-App (Graphit, .tastemaker/style-lock.md): Signalrot nur als Markierung, nie als Fläche
// hinter Text. Schrift: Systemschrift des iPhones; Ziffern in Monospace stehen sauber untereinander.

val Bg = Color(0xFF1B1F25)
val Surface1 = Color(0xFF252A31)
val Surface2 = Color(0xFF30363E)
val Line = Color(0xFF3B424B)
val TextColor = Color(0xFFE8EBEF)
val Muted = Color(0xFFA3AAB3)
val Signal = Color(0xFFFF5247)
val OnSignal = Color(0xFF14171C)
val Danger = Color(0xFFFF9270)
val Mono = FontFamily.Monospace

/** Ecken: bewusst knapp – Kästen statt Kapseln. */
val Corner = RoundedCornerShape(4.dp)
val SmallCorner = RoundedCornerShape(2.dp)

private fun typography() = Typography().let { t ->
    fun TextStyle.f() = copy(fontFeatureSettings = "tnum")
    t.copy(
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
