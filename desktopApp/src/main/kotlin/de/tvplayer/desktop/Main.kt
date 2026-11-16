package de.tvplayer.desktop

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
import de.tvplayer.desktop.ui.AppTheme
import de.tvplayer.desktop.ui.HomeScreen
import de.tvplayer.desktop.ui.LoginScreen
import de.tvplayer.desktop.ui.PlayerScreen
import de.tvplayer.desktop.ui.SeriesScreen
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Screen
import de.tvplayer.shared.data.FileKeyValueStore
import de.tvplayer.shared.data.Storage
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 860.dp)
    val app = remember { AppState(Storage(FileKeyValueStore())) }
    Window(
        onCloseRequest = { app.close(); exitApplication() },
        title = "TV Player",
        state = windowState,
    ) {
        window.minimumSize = Dimension(1100, 680)
        AppTheme {
            val holder = rememberSaveableStateHolder()
            AnimatedContent(app.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
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
