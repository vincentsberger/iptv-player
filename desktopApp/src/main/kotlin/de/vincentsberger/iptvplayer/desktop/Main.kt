package de.vincentsberger.iptvplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.vincentsberger.iptvplayer.desktop.ui.AppTheme
import de.vincentsberger.iptvplayer.desktop.ui.screenTransition
import de.vincentsberger.iptvplayer.desktop.ui.HomeScreen
import de.vincentsberger.iptvplayer.desktop.ui.LoginScreen
import de.vincentsberger.iptvplayer.desktop.ui.PlayerScreen
import de.vincentsberger.iptvplayer.desktop.ui.SeriesScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.FileKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.Storage
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 860.dp)
    val app = remember { AppState(Storage(FileKeyValueStore())) }
    Window(
        onCloseRequest = { app.close(); exitApplication() },
        title = "FreeIPTVPlayer",
        state = windowState,
    ) {
        window.minimumSize = Dimension(1100, 680)
        AppTheme {
            val holder = rememberSaveableStateHolder()
            AnimatedContent(app.screen, transitionSpec = { screenTransition() }, label = "screen") { s ->
                when (s) {
                    Screen.Login -> LoginScreen(onLogin = { app.login(it) })
                    Screen.Home -> holder.SaveableStateProvider("home") { HomeScreen(app) }
                    is Screen.Series -> holder.SaveableStateProvider("series:${s.series.id}") { SeriesScreen(app, s.series) }
                    is Screen.Player -> PlayerScreen(
                        app, s.items, s.index, s.startPosition,
                        fullscreen = windowState.placement == WindowPlacement.Fullscreen,
                        onToggleFullscreen = {
                            windowState.placement =
                                if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                        },
                        onBack = {
                            if (windowState.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating
                            app.back()
                        },
                    )
                }
            }
        }
    }
}
