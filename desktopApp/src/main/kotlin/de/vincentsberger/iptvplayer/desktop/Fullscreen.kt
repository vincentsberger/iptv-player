package de.vincentsberger.iptvplayer.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/**
 * Windows legt das AWT-Vollbild (GraphicsDevice.setFullScreenWindow) nur über den Bildschirm,
 * die Titelleiste bleibt dabei stehen. Echtes Vollbild gibt es dort nur ohne Fensterrahmen.
 * Linux (Fenstermanager) und macOS (natives Vollbild) blenden den Rahmen selbst aus.
 */
internal fun needsBorderlessFullscreen(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
    osName.startsWith("Windows", ignoreCase = true)

/** Schaltet das Vollbild des Hauptfensters; unter Windows ohne Fensterrahmen. */
internal class FullscreenController(
    private val state: WindowState,
    private val borderless: Boolean = needsBorderlessFullscreen(),
) {
    var window: ComposeWindow? = null

    val isFullscreen: Boolean get() = state.placement == WindowPlacement.Fullscreen

    fun toggle() = set(!isFullscreen)

    fun set(on: Boolean) {
        val target = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
        val w = window
        if (w != null && borderless) {
            // Reihenfolge zählt: Rahmen weg, bevor das Fenster den Bildschirm bekommt,
            // und erst zurück, wenn es wieder seine alte Größe hat.
            if (on) w.setDecorated(false)
            w.placement = target
            if (!on) w.setDecorated(true)
        }
        state.placement = target
    }
}

/**
 * AWT erlaubt den Rahmenwechsel nur ohne natives Fenster. ComposeWindow.dispose() würde den
 * Compose-Inhalt mit abbauen, removeNotify() nimmt nur das native Fenster weg – Compose hängt
 * seine Zeichenfläche beim nächsten addNotify() (über setVisible) wieder an.
 */
private fun ComposeWindow.setDecorated(decorated: Boolean) {
    if (isUndecorated != decorated) return
    isVisible = false
    removeNotify()
    isUndecorated = !decorated
    isVisible = true
}
