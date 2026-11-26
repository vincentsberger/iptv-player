package de.vincentsberger.iptvplayer.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/**
 * Das AWT-Vollbild (GraphicsDevice.setFullScreenWindow) braucht unter Windows und Linux ein Fenster ohne Rahmen:
 * - Windows legt es nur über den Bildschirm, die Titelleiste bleibt stehen.
 * - Linux (XWayland) stellt beim Verlassen die alte Größe her und rechnet die Titelleiste noch einmal drauf –
 *   das Fenster wuchs bei jedem Wechsel um deren Höhe und rutschte nach unten.
 * macOS (natives Vollbild) blendet den Rahmen selbst aus.
 */
internal fun needsBorderlessFullscreen(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
    osName.startsWith("Windows", ignoreCase = true) || osName.startsWith("Linux", ignoreCase = true)

/** Schaltet das Vollbild des Hauptfensters; unter Windows und Linux ohne Fensterrahmen. */
internal class FullscreenController(
    private val state: WindowState,
    private val borderless: Boolean = needsBorderlessFullscreen(),
) {
    var window: ComposeWindow? = null

    val isFullscreen: Boolean get() = state.placement == WindowPlacement.Fullscreen

    fun toggle() = set(!isFullscreen)

    /** Zustand vor dem Vollbild – ein maximiertes Fenster ist danach wieder maximiert. */
    private var before = WindowPlacement.Floating

    fun set(on: Boolean) {
        if (on == isFullscreen) return
        if (on) before = state.placement
        val target = if (on) WindowPlacement.Fullscreen else before
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
 * Das neue native Fenster bekommt nicht immer den Fokus zurück – ohne ihn kämen Esc und F nicht mehr an
 * und das Vollbild ließe sich nicht verlassen.
 */
private fun ComposeWindow.setDecorated(decorated: Boolean) {
    if (isUndecorated != decorated) return
    isVisible = false
    removeNotify()
    isUndecorated = !decorated
    isVisible = true
    takeFocus()
    // Direkt nach dem Neuaufbau ist das native Fenster oft noch nicht abgebildet – dann kurz darauf noch einmal.
    javax.swing.Timer(FOCUS_RETRY_MS) { if (!isFocused) takeFocus() }.apply { isRepeats = false }.start()
}

private fun ComposeWindow.takeFocus() {
    toFront()
    requestFocus()
}

private const val FOCUS_RETRY_MS = 150
