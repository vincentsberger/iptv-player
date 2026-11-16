package de.tvplayer.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tvplayer.app.ui.AppTheme
import de.tvplayer.app.ui.HomeScreen
import de.tvplayer.app.ui.LoginScreen
import de.tvplayer.app.ui.PlayerScreen
import de.tvplayer.app.ui.SeriesScreen
import de.tvplayer.shared.AppState
import de.tvplayer.shared.Screen
import de.tvplayer.shared.data.AndroidKeyValueStore
import de.tvplayer.shared.data.Storage

/** Hält den gemeinsamen App-Zustand über Konfigurationswechsel hinweg. */
class AppHolder(app: Application) : AndroidViewModel(app) {
    val state = AppState(Storage(AndroidKeyValueStore(app)))
    override fun onCleared() = state.close()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val vm = viewModel<AppHolder>().state
                // Merkt sich Scroll-Positionen und Auswahl, wenn man aus dem Player zurückkommt.
                val holder = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = vm.screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Login -> LoginScreen(onLogin = { vm.login(it) })
                        Screen.Home -> holder.SaveableStateProvider("home") { HomeScreen(vm) }
                        is Screen.Series -> holder.SaveableStateProvider("series:${s.series.id}") {
                            BackHandler { vm.back() }
                            SeriesScreen(vm, s.series)
                        }
                        is Screen.Player -> PlayerScreen(vm, s.items, s.index, s.startPosition, onBack = vm::back)
                    }
                }
            }
        }
    }
}
