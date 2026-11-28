package de.vincentsberger.iptvplayer.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import javax.swing.Timer
import java.awt.Frame
import java.awt.Rectangle

/**
 * Windows legt das AWT-Vollbild (GraphicsDevice.setFullScreenWindow) nur über den Bildschirm, die Titelleiste bleibt
 * stehen – echtes Vollbild gibt es dort nur ohne Rahmen. Das baut das Fenster neu auf (siehe [setDecorated]).
 *
 * Linux bleibt bewusst beim Rahmen: Der Neuaufbau zerstört alle nativen Unterfenster (VLC muss neu starten), nimmt den
 * Fokus und warf das Fenster mit Overlay-Fenster oft aus dem Vollbild zurück. Dass XWayland beim Verlassen die
 * Titelleiste doppelt rechnet, behebt [FullscreenController] stattdessen, indem es die alte Lage selbst wiederherstellt.
 * macOS (natives Vollbild) blendet den Rahmen selbst aus.
 */
internal fun needsBorderlessFullscreen(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
    osName.startsWith("Windows", ignoreCase = true)

/** Schaltet das Vollbild des Hauptfensters; unter Windows ohne Fensterrahmen. */
internal class FullscreenController(
    private val state: WindowState,
    private val borderless: Boolean = needsBorderlessFullscreen(),
) {
    var window: ComposeWindow? = null

    /** Wird vor jedem Neuaufbau des Fensters (Rahmenwechsel) benachrichtigt – siehe [LocalWindowRebuild]. */
    val beforeRebuild = RebuildListeners()

    val isFullscreen: Boolean get() = state.placement == WindowPlacement.Fullscreen

    fun toggle() = set(!isFullscreen)

    /** Zustand vor dem Vollbild – ein maximiertes Fenster ist danach wieder maximiert. */
    private var before = WindowPlacement.Floating

    /** Lage und Größe vor dem Vollbild (Weg mit Rahmen): XWayland stellt sie beim Verlassen um die Titelleiste zu groß her. */
    private var floatingBounds: Rectangle? = null

    fun set(on: Boolean) {
        if (on == isFullscreen) return
        if (on) {
            before = state.placement
            floatingBounds = window?.bounds?.takeIf { before == WindowPlacement.Floating }
            // Mit Rahmen aus einem maximierten Fenster lag das Vollbild 37 px zu hoch (Titelleiste über dem Bildschirm):
            // vorher die Maximierung aufheben; beim Verlassen maximiert restoreLater() wieder.
            if (!borderless && before == WindowPlacement.Maximized) window?.extendedState = Frame.NORMAL
        }
        // Raus immer über Floating: ComposeWindow.setPlacement(Maximized) maximiert nur und lässt das Vollbild an – ein
        // vorher maximiertes Fenster blieb bildschirmfüllend, obwohl die App „kein Vollbild“ annahm (zweites Esc = Zurück).
        val target = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
        val w = window
        if (w != null && borderless) {
            // Reihenfolge zählt: Rahmen weg, bevor das Fenster den Bildschirm bekommt,
            // und erst zurück, wenn es wieder seine alte Größe hat.
            if (on) w.setDecorated(false, beforeRebuild)
            w.placement = target
            if (!on) w.setDecorated(true, beforeRebuild)
        }
        state.placement = target
        if (!on) restoreLater()
    }

    /**
     * Nach dem Verlassen den alten Zustand herstellen – erst, wenn der Fenstermanager das Vollbild beendet hat:
     * - vorher maximiert: wieder maximieren
     * - vorher normal (Weg mit Rahmen): alte Lage und Größe – XWayland rechnete die Titelleiste doppelt
     */
    private fun restoreLater() {
        val bounds = floatingBounds
        Timer(RESTORE_BOUNDS_MS) {
            if (state.placement != WindowPlacement.Floating) return@Timer
            if (before == WindowPlacement.Maximized) state.placement = WindowPlacement.Maximized
            else window?.takeIf { !borderless && bounds != null && it.bounds != bounds }?.bounds = bounds
        }.apply { isRepeats = false }.start()
    }

    private companion object {
        const val RESTORE_BOUNDS_MS = 300
    }
}

/**
 * AWT erlaubt den Rahmenwechsel nur ohne natives Fenster. ComposeWindow.dispose() würde den
 * Compose-Inhalt mit abbauen, removeNotify() nimmt nur das native Fenster weg – Compose hängt
 * seine Zeichenfläche beim nächsten addNotify() (über setVisible) wieder an.
 * Das neue native Fenster bekommt nicht immer den Fokus zurück – ohne ihn kämen Esc und F nicht mehr an
 * und das Vollbild ließe sich nicht verlassen.
 */
private fun ComposeWindow.setDecorated(decorated: Boolean, beforeRebuild: RebuildListeners) {
    if (isUndecorated != decorated) return
    beforeRebuild.fire()
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

/**
 * Wer vor dem Neuaufbau des Hauptfensters etwas loslassen muss. removeNotify() zerstört alle nativen Unterfenster –
 * auch den Canvas, in den VLC zeichnet; zeichnete VLC weiter hinein, beendete X11 den Prozess.
 */
internal class RebuildListeners {
    private val listeners = mutableListOf<() -> Unit>()

    fun add(listener: () -> Unit) { listeners += listener }

    fun remove(listener: () -> Unit) { listeners -= listener }

    fun fire() = listeners.toList().forEach { it() }
}

/** Benachrichtigung vor dem Neuaufbau des Hauptfensters (Main.kt stellt sie bereit, der Player meldet sich an). */
internal val LocalWindowRebuild = staticCompositionLocalOf<RebuildListeners?> { null }
