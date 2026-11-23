package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerEngine
import de.vincentsberger.iptvplayer.ios.ui.player.PlayerScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.IosKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.IosPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.Storage

/**
 * Die ganze iPhone-Oberfläche. [newPlayer] liefert die Wiedergabe (MobileVLCKit aus :iosApp) – je Player-Bildschirm
 * eine neue Instanz, die beim Drehen erhalten bleibt.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IosApp(newPlayer: () -> PlayerEngine) {
    val app = remember { AppState(Storage(IosKeyValueStore()), playlistCache = { IosPlaylistCache.forAccount(it) }) }
    setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory()) }.build() }
    // Lange im Hintergrund (> 1 h) zählt als neuer Start: Listen frisch laden, Startseite.
    AppLifecycleEffect(onForeground = { app.onAppResumed(it) })

    AppTheme {
        Box(Modifier.fillMaxSize().background(Bg)) {
            when (val s = app.screen) {
                Screen.Login -> LoginScreen(app)
                Screen.Home -> MainScreen(app)
                is Screen.Movie -> Placeholder(s.movie.name, "Filme", app)
                is Screen.Series -> Placeholder(s.series.name, "Serien", app)
                is Screen.Player -> key(s) {
                    PlayerScreen(app, s.items, s.index, s.startPosition, newPlayer, onBack = app::back)
                }
            }
        }
    }
}

/** Film-/Serien-Detailseiten kommen mit Etappe 3. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Placeholder(title: String, area: String, app: AppState) {
    BackHandler { app.back() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = app::back)
        ComingSoon(area)
    }
}
