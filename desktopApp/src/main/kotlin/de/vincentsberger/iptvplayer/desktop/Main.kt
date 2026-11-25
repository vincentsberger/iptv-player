package de.vincentsberger.iptvplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.vincentsberger.iptvplayer.desktop.ui.AppTheme
import de.vincentsberger.iptvplayer.desktop.ui.Bg
import de.vincentsberger.iptvplayer.desktop.ui.Anthrazit
import de.vincentsberger.iptvplayer.desktop.ui.screenTransition
import de.vincentsberger.iptvplayer.desktop.ui.HomeScreen
import de.vincentsberger.iptvplayer.desktop.ui.MovieScreen
import de.vincentsberger.iptvplayer.desktop.ui.LoginScreen
import de.vincentsberger.iptvplayer.desktop.ui.PlayerScreen
import de.vincentsberger.iptvplayer.desktop.ui.SeriesScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
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
        GlobalAction.FocusSearch -> { app.selectTab(Tab.SEARCH); keyboard.searchFocusRequests++ }
        is GlobalAction.SelectTab -> app.selectTab(action.tab)
    }
    return true
}

private const val APP_TITLE = "IPTV Player"

/** Fenstertitel: beim Abspielen der Sender- oder Filmname vorn (Taskleiste zeigt den Anfang). */
internal fun windowTitle(playing: String?): String = playing?.takeIf { it.isNotBlank() }?.let { "$it – $APP_TITLE" } ?: APP_TITLE

private fun Rectangle.toBounds() = Bounds(x, y, width, height)

@OptIn(ExperimentalComposeUiApi::class) // onPointerEvent: Zurück-Taste der Maus
fun main() = application {
    val kv = remember { FileKeyValueStore() }
    val memory = remember { screenMemory(WindowPrefs(kv)) }
    val windowState = rememberWindowState(memory.start)
    val app = remember { AppState(Storage(kv), playlistCache = FilePlaylistCache::forAccount) }
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
        CompositionLocalProvider(LocalKeyboard provides keyboard) { AppTheme {
            val holder = rememberSaveableStateHolder()
            // Fester Grund unter dem Bildschirmwechsel: während des Überblendens scheint nur Anthrazit durch.
            Box(
                Modifier.fillMaxSize().background(Bg)
                    // Klick → Mausbedienung (Fokusrahmen aus); Zurück-Taste der Maus wie Esc auf den Detailseiten.
                    .onPointerEvent(PointerEventType.Press, PassThrough.Initial) { ev ->
                        keyboard.keyboardMode = false
                        if (ev.buttons.isBackPressed && (app.screen is Screen.Movie || app.screen is Screen.Series)) app.back()
                    },
            ) {
                AnimatedContent(app.screen, transitionSpec = { screenTransition() }, label = "screen") { s ->
                    when (s) {
                        Screen.Login -> LoginScreen(onLogin = { app.login(it) }, onCancel = if (app.canCancelLogin) app::back else null)
                        Screen.Home -> holder.SaveableStateProvider("home") { HomeScreen(app) }
                        is Screen.Series -> holder.SaveableStateProvider("series:${s.series.id}") { SeriesScreen(app, s.series) }
                        is Screen.Movie -> MovieScreen(app, s.movie)
                        is Screen.Player -> PlayerScreen(
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
            }
        } }
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
