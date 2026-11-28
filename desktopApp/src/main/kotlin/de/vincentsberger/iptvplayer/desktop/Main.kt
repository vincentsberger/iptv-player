package de.vincentsberger.iptvplayer.desktop

import kotlinx.coroutines.Dispatchers
import de.vincentsberger.iptvplayer.desktop.player.initNativeVideoThreads
import de.vincentsberger.iptvplayer.desktop.ui.LocalMainWindow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.vincentsberger.iptvplayer.desktop.ui.AppTheme
import de.vincentsberger.iptvplayer.desktop.ui.Anthrazit
import de.vincentsberger.iptvplayer.desktop.ui.PlayerScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.FileKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.FilePlaylistCache
import de.vincentsberger.iptvplayer.shared.data.Storage
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventPass as PassThrough
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import de.vincentsberger.iptvplayer.desktop.ui.GlobalAction
import de.vincentsberger.iptvplayer.desktop.ui.KeyboardState
import de.vincentsberger.iptvplayer.desktop.ui.LocalKeyboard
import de.vincentsberger.iptvplayer.desktop.ui.globalActionFor
import de.vincentsberger.iptvplayer.desktop.ui.detailItemOf
import de.vincentsberger.iptvplayer.shared.Tab

/** Fensterweite Tasten: merkt Strg und Tastaturbedienung, führt [globalActionFor] aus. True = Taste verbraucht. */
private fun handleWindowKey(ev: KeyEvent, app: AppState, fullscreen: FullscreenController, keyboard: KeyboardState): Boolean {
    keyboard.ctrlHeld = ev.isCtrlPressed
    if (ev.type != KeyEventType.KeyDown) return false
    keyboard.keyboardMode = true
    when (val action = globalActionFor(app.screen, ev.key, ev.isCtrlPressed, ev.isAltPressed, fullscreen.isFullscreen)) {
        null -> return false
        GlobalAction.ExitFullscreen -> fullscreen.set(false)
        GlobalAction.Back -> app.back()
        GlobalAction.FocusSearch -> { closeDetail(app); app.selectTab(Tab.SEARCH); keyboard.searchFocusRequests++ }
        is GlobalAction.SelectTab -> { closeDetail(app); app.selectTab(action.tab) }
    }
    return true
}

/** Bereichswechsel per Strg bei offenem Detail-Fenster: erst schließen, sonst wechselt der Bereich unsichtbar darunter. */
private fun closeDetail(app: AppState) {
    if (detailItemOf(app.screen) != null) app.back()
}

private const val APP_TITLE = "IPTV Player"

/** Fenstertitel: beim Abspielen der Sender- oder Filmname vorn (Taskleiste zeigt den Anfang). */
internal fun windowTitle(playing: String?): String = playing?.takeIf { it.isNotBlank() }?.let { "$it – $APP_TITLE" } ?: APP_TITLE

private fun Rectangle.toBounds() = Bounds(x, y, width, height)

@OptIn(ExperimentalComposeUiApi::class) // onPointerEvent: Zurück-Taste der Maus
fun main() {
    // Linux: Xlib vor dem ersten AWT-Aufruf threadfest machen – VLC zeichnet nativ aus eigenen Threads in unser Fenster.
    initNativeVideoThreads()
    application {
        val kv = remember { FileKeyValueStore() }
        val memory = remember { screenMemory(WindowPrefs(kv)) }
        val windowState = rememberWindowState(memory.start)
        val app = remember { AppState(Storage(kv), playlistCache = FilePlaylistCache::forAccount, prewarm = Dispatchers.Default) }
        val fullscreen = remember { FullscreenController(windowState) }
        val keyboard = remember { KeyboardState() }
        var playing by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(windowState) { snapshotFlow { windowState.floatingBounds() }.collect(memory::track) }
        Window(
            onCloseRequest = {
                memory.save(maximized = windowState.placement == WindowPlacement.Maximized)
                app.close(); exitApplication()
            },
            title = windowTitle(playing),
            state = windowState,
            // Fensterweite Tasten (globalActionFor) wirken auch, wenn kein Element den Tastaturfokus hat – z. B. Esc nach dem
            // Vollbild-Wechsel (Linux und Windows bauen das Fenster dabei neu auf, der Compose-Fokus geht verloren).
            onPreviewKeyEvent = { ev -> handleWindowKey(ev, app, fullscreen, keyboard) },
        ) {
            fullscreen.window = window
            minimumSize(memory.work).let { (w, h) -> window.minimumSize = Dimension(w, h) }
            // Fenster selbst dunkel statt Weiß: Beim Überblenden, Starten und Vergrößern blitzt sonst kurz Weiß durch.
            java.awt.Color(Anthrazit.bg.toArgb()).let { window.background = it; window.contentPane.background = it }
            // Fenster verlässt den Vordergrund (z. B. Strg+Alt+…): Das Loslassen von Strg kommt hier nie an.
            val active = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(active) { if (!active) keyboard.ctrlHeld = false }
            CompositionLocalProvider(
                LocalKeyboard provides keyboard, LocalMainWindow provides window, LocalWindowRebuild provides fullscreen.beforeRebuild,
            ) { AppTheme {
                Box(
                    Modifier.fillMaxSize()
                        // Klick → Mausbedienung (Fokusrahmen aus); Zurück-Taste der Maus schließt das Detail-Fenster wie Esc.
                        .onPointerEvent(PointerEventType.Press, PassThrough.Initial) { ev ->
                            keyboard.keyboardMode = false
                            if (ev.buttons.isBackPressed && detailItemOf(app.screen) != null) app.back()
                        },
                ) {
                    ScreenLayers(app) { s ->
                        PlayerScreen(
                            app, s.items, s.index, s.startPosition,
                            fullscreen = fullscreen.isFullscreen,
                            onToggleFullscreen = fullscreen::toggle,
                            onBack = {
                                if (fullscreen.isFullscreen) fullscreen.set(false)
                                app.back()
                            },
                            onTitle = { playing = it },
                        )
                    }
                }
            } }
        }
    }
}

/** Arbeitsbereich (ohne Taskleiste) und alle Bildschirme vom System. */
private fun screenMemory(prefs: WindowPrefs): WindowMemory {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return WindowMemory(prefs, env.maximumWindowBounds.toBounds(), env.screenDevices.map { it.defaultConfiguration.bounds.toBounds() })
}

@Composable
private fun rememberWindowState(start: SavedWindow): WindowState = with(start.bounds) {
    rememberWindowState(
        placement = if (start.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = WindowPosition(x.dp, y.dp),
        size = DpSize(width.dp, height.dp),
    )
}

/** Lage und Größe im normalen Zustand; null, solange das Fenster maximiert, im Vollbild oder noch nicht platziert ist. */
private fun WindowState.floatingBounds(): Bounds? {
    if (placement != WindowPlacement.Floating || !position.isSpecified || !size.isSpecified) return null
    return Bounds(position.x.value.toInt(), position.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt())
}
