package de.vincentsberger.iptvplayer.desktop.ui

import de.vincentsberger.iptvplayer.desktop.LocalWindowRebuild
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Bedienung über dem nativen Video: ein rahmenloses, durchsichtiges Fenster, deckungsgleich über [target] (dem Canvas,
 * in den VLC zeichnet). Native Fenster liegen unter AWT immer über Compose – deshalb ein eigenes Fenster statt einer Ebene.
 *
 * - gehört zum Hauptfenster: minimiert mit ihm und liegt nur über ihm, nicht über fremden Fenstern
 * - nimmt keinen Tastaturfokus (Tasten bleiben in der Player-Behandlung des Hauptfensters), fängt aber alle Mausereignisse;
 *   nach einem Klick holt sich das Hauptfenster den Fokus zurück (sonst ginge er unter GNOME verloren)
 * - ohne Modalität: `DialogWindow` von Compose wäre modal und blockierte die Tasten im Hauptfenster
 * - eigene Komposition: die CompositionLocals (Theme, Tastaturzustand) werden durchgereicht
 */
@Composable
internal fun PlayerOverlayWindow(target: Component, visible: Boolean, content: @Composable BoxScope.() -> Unit) {
    val owner = LocalMainWindow.current ?: return
    val locals = currentCompositionLocalContext
    val currentLocals by rememberUpdatedState(locals)
    val currentContent by rememberUpdatedState(content)
    val currentVisible by rememberUpdatedState(visible)
    val rebuild = LocalWindowRebuild.current
    DisposableEffect(owner, target) {
        val overlay = ComposeDialog(owner, Dialog.ModalityType.MODELESS).apply {
            isUndecorated = true
            isTransparent = true
            focusableWindowState = false
            // Popup statt normalem Fenster: Normale Fenster hält GNOME aus der oberen Leiste heraus – im Vollbild lag das
            // Overlay dann 32 px zu tief. Popups verwaltet der Fenstermanager nicht; über fremden Fenstern stehen bleibt es
            // trotzdem nicht, weil es nur bei aktivem Hauptfenster sichtbar ist (OverlayFollower).
            type = Window.Type.POPUP
            setContent {
                CompositionLocalProvider(currentLocals) {
                    // Fast unsichtbarer Grund: Unter Windows lassen Pixel mit Alpha 0 Klicks zum Fenster darunter durch.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = OVERLAY_HIT_ALPHA))) { currentContent() }
                }
            }
        }
        val follow = OverlayFollower(overlay, owner, target) { currentVisible }
        follow.start()
        // Beim Vollbild-Wechsel stört ein sichtbares Overlay den Fenstermanager: Das Hauptfenster sprang aus dem Vollbild
        // zurück (rahmenlos, alte Größe). Deshalb vorher ausblenden und erst nach dem Wechsel wieder zeigen.
        val pause: () -> Unit = { follow.pauseForRebuild() }
        rebuild?.add(pause)
        onDispose { rebuild?.remove(pause); follow.stop(); overlay.dispose() }
    }
    // Sichtbarkeit (Bildschirmwechsel) sofort übernehmen.
    LaunchedEffect(visible) { OverlayFollower.syncAll() }
}

/** Hauptfenster der App (Main.kt) – Besitzer des Overlay-Fensters. */
val LocalMainWindow = staticCompositionLocalOf<Window?> { null }

/** Fast unsichtbar, aber nicht 0: sonst fängt das Overlay unter Windows keine Klicks. */
private const val OVERLAY_HIT_ALPHA = 1f / 255f

/** Wo das Overlay liegen muss: genau über [target] in Bildschirmkoordinaten; null, solange [target] nicht sichtbar ist. */
internal fun overlayBounds(target: Component): Rectangle? {
    if (!target.isShowing || target.width <= 0 || target.height <= 0) return null
    val p = target.locationOnScreen
    return Rectangle(p.x, p.y, target.width, target.height)
}

/** Hält [overlay] deckungsgleich und sichtbar, solange [target] es ist und [wanted] gilt. */
private class OverlayFollower(
    private val overlay: Window,
    private val owner: Window,
    private val target: Component,
    private val wanted: () -> Boolean,
) {
    // Nach dem Layout ein zweites Mal: Beim Vollbild-Wechsel stimmt die Größe im ersten Ereignis noch nicht (37 px).
    private val late = Timer(LATE_SYNC_MS) { sync() }.apply { isRepeats = false }

    /** Bis hierhin (ms) bleibt das Overlay nach einem Neuaufbau des Hauptfensters verborgen. */
    private var pausedUntil = 0L
    private val resume = Timer(REBUILD_SETTLE_MS) { sync() }.apply { isRepeats = false }
    // Zusätzlich regelmäßig: Nicht jede Lageänderung meldet ein Ereignis (z. B. die Titelleiste verschwindet im Vollbild).
    private val poll = Timer(POLL_MS) { sync() }

    /** Gleich wird das Hauptfenster neu aufgebaut (Vollbild-Wechsel): ausblenden, nach [REBUILD_SETTLE_MS] wieder abgleichen. */
    fun pauseForRebuild() {
        pausedUntil = System.currentTimeMillis() + REBUILD_SETTLE_MS
        overlay.isVisible = false
        resume.restart()
    }

    fun sync() {
        if (System.currentTimeMillis() < pausedUntil) return
        // Nur bei aktivem Hauptfenster: Als Popup läge das Overlay sonst nach Alt+Tab über fremden Fenstern.
        val bounds = overlayBounds(target)?.takeIf { wanted() && owner.isActive }
        if (bounds == null) {
            overlay.isVisible = false
            return
        }
        if (overlay.bounds != bounds) overlay.bounds = bounds
        if (!overlay.isVisible) overlay.isVisible = true
    }

    private fun syncTwice() { sync(); late.restart() }

    private val onComponent = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = syncTwice()
        override fun componentResized(e: ComponentEvent) = syncTwice()
        override fun componentShown(e: ComponentEvent) = syncTwice()
        override fun componentHidden(e: ComponentEvent) = syncTwice()
    }
    private val onState = WindowStateListener { EventQueue.invokeLater { syncTwice() } }
    private val onActivation = object : WindowAdapter() {
        override fun windowActivated(e: WindowEvent) = syncTwice()
        override fun windowDeactivated(e: WindowEvent) = sync()
    }
    private val onHierarchy = HierarchyListener { e ->
        if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) EventQueue.invokeLater { syncTwice() }
    }
    private val onPress = AWTEventListener { e ->
        val source = e.source as? Component ?: return@AWTEventListener
        if (e.id == MouseEvent.MOUSE_PRESSED && SwingUtilities.getWindowAncestor(source) === overlay) {
            EventQueue.invokeLater { owner.toFront(); owner.requestFocus() }
        }
    }

    fun start() {
        owner.addComponentListener(onComponent); target.addComponentListener(onComponent)
        owner.addWindowStateListener(onState); target.addHierarchyListener(onHierarchy)
        owner.addWindowListener(onActivation)
        Toolkit.getDefaultToolkit().addAWTEventListener(onPress, AWTEvent.MOUSE_EVENT_MASK)
        active += this
        poll.start()
        EventQueue.invokeLater { syncTwice() }
    }

    fun stop() {
        active -= this
        owner.removeComponentListener(onComponent); target.removeComponentListener(onComponent)
        owner.removeWindowStateListener(onState); target.removeHierarchyListener(onHierarchy)
        owner.removeWindowListener(onActivation)
        Toolkit.getDefaultToolkit().removeAWTEventListener(onPress)
        late.stop()
        resume.stop()
        poll.stop()
    }

    companion object {
        private const val LATE_SYNC_MS = 150
        private const val REBUILD_SETTLE_MS = 500
        private const val POLL_MS = 250
        private val active = mutableSetOf<OverlayFollower>()

        /** Alle Overlays neu abgleichen (z. B. nach einem Wechsel der Sichtbarkeit). */
        fun syncAll() = active.toList().forEach { it.sync() }
    }
}
