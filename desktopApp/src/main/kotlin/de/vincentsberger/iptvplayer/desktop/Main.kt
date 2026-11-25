package de.vincentsberger.iptvplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalComposeUiApi::class) // onPointerEvent: Zurück-Taste der Maus
fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 860.dp)
    val app = remember { AppState(Storage(FileKeyValueStore()), playlistCache = FilePlaylistCache::forAccount) }
    val fullscreen = remember { FullscreenController(windowState) }
    val keyboard = remember { KeyboardState() }
    Window(
        onCloseRequest = { app.close(); exitApplication() },
        title = "IPTV Player",
        state = windowState,
        // Fensterweite Tasten (globalActionFor) wirken auch, wenn kein Element den Tastaturfokus hat – z. B. Esc nach dem
        // Vollbild-Wechsel (Linux und Windows bauen das Fenster dabei neu auf, der Compose-Fokus geht verloren).
        onPreviewKeyEvent = { ev -> handleWindowKey(ev, app, fullscreen, keyboard) },
    ) {
        fullscreen.window = window
        window.minimumSize = Dimension(1100, 680)
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
                        )
                    }
                }
            }
        } }
    }
}
