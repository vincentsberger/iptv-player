package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.Tab

/** Was eine Taste fensterweit auslöst (siehe [globalActionFor]). */
internal sealed interface GlobalAction {
    data object ExitFullscreen : GlobalAction
    data object Back : GlobalAction
    data object FocusSearch : GlobalAction
    data class SelectTab(val tab: Tab) : GlobalAction
}

/** Bereiche der Seitenleiste in der Reihenfolge von „Strg 1–7“ (Downloads gibt es am PC nicht). */
internal val desktopTabs: List<Tab> = Tab.entries.filter { it != Tab.DOWNLOADS }

/**
 * Tasten, die das Fenster abfängt (Main.kt) – sie wirken auch, wenn kein Element den Tastaturfokus hat. `null` = Taste
 * gehört den Bildschirmen. Detailseiten: Esc, Rücktaste und Alt+← gehen zurück (dort gibt es keine Textfelder);
 * Startseite: Strg 1–7 und Strg+F. Der Player regelt seine Tasten selbst.
 */
internal fun globalActionFor(screen: Screen, key: Key, ctrl: Boolean, alt: Boolean, fullscreen: Boolean): GlobalAction? {
    if (key == Key.Escape && fullscreen) return GlobalAction.ExitFullscreen
    return when (screen) {
        is Screen.Movie, is Screen.Series -> if (isBackKey(key, alt)) GlobalAction.Back else null
        Screen.Home -> when {
            !ctrl -> null
            key == Key.F -> GlobalAction.FocusSearch
            else -> digitOf(key)?.let { desktopTabs.getOrNull(it - 1) }?.let(GlobalAction::SelectTab)
        }
        else -> null
    }
}

private fun isBackKey(key: Key, alt: Boolean): Boolean =
    key == Key.Escape || key == Key.Backspace || (alt && key == Key.DirectionLeft)

/** Tastaturzustand des Fensters, gepflegt in Main.kt; Bildschirme lesen ihn über [LocalKeyboard]. */
@Stable
class KeyboardState {
    /** Strg gedrückt → Tastenkappen der Seitenleiste zeigen. */
    var ctrlHeld by mutableStateOf(false)

    /** Zuletzt mit der Tastatur bedient (nicht geklickt) → Fokusrahmen zeigen, wie `:focus-visible` im Web. */
    var keyboardMode by mutableStateOf(false)

    /** Zählt hoch, wenn Strg+F das Suchfeld fokussieren soll – auch wenn die Suche schon offen ist. */
    var searchFocusRequests by mutableIntStateOf(0)
}

val LocalKeyboard = staticCompositionLocalOf { KeyboardState() }

/** Gruppe der Kürzel-Übersicht in den Einstellungen: wo sie gelten, darin Taste → Wirkung. */
internal class ShortcutGroup(val title: String, val keys: List<Pair<String, String>>)

/** Übersicht der Tastenkürzel, nach Ort getrennt (dieselbe Taste wirkt je nach Ort anders, z. B. F). */
internal val shortcutHelp = listOf(
    ShortcutGroup(
        "Überall",
        listOf("Strg 1–7" to "Bereich wechseln", "Strg F" to "Suche", "Esc" to "zurück (Film, Serie)", "Alt ←" to "zurück (Film, Serie)"),
    ),
    ShortcutGroup(
        "Live-TV-Liste",
        listOf("↑ ↓" to "Sender wählen", "Bild ↑ ↓" to "zehn weiter", "← →" to "Kategorien / Sender", "Enter" to "ansehen", "F" to "Favorit"),
    ),
    ShortcutGroup(
        "Player",
        listOf(
            "Leertaste" to "Pause", "← →" to "spulen", "↑ ↓" to "Sender / Folge (einzelner Film: Lautstärke)", "+ −" to "Lautstärke",
            "M" to "Ton aus", "0–9" to "Sender wählen", "L" to "letzter Sender", "T" to "Ton & Untertitel", "I" to "Statistik",
            "S" to "Schlaf-Timer", "F" to "Vollbild", "Esc" to "zurück",
        ),
    ),
)
