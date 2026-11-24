package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import de.vincentsberger.iptvplayer.ios.ui.download.IosDownloads
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
@Composable
fun IosApp(newPlayer: () -> PlayerEngine) {
    val app = remember { AppState(Storage(IosKeyValueStore()), playlistCache = { IosPlaylistCache.forAccount(it) }) }
    setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(KtorNetworkFetcherFactory()) }.build() }
    // Lange im Hintergrund (> 1 h) zählt als neuer Start: Listen frisch laden, Startseite.
    AppLifecycleEffect(onForeground = { app.onAppResumed(it) })
    // Downloads der Hintergrund-Sitzung abholen und die Warteschlange fortsetzen.
    LaunchedEffect(Unit) { IosDownloads.start() }

    AppTheme {
        Box(Modifier.fillMaxSize().background(Bg)) {
            when (val s = app.screen) {
                Screen.Login -> LoginScreen(app)
                Screen.Home -> MainScreen(app)
                is Screen.Movie -> MovieScreen(app, s.movie)
                is Screen.Series -> SeriesScreen(app, s.series)
                is Screen.Player -> key(s) {
                    PlayerScreen(app, s.items, s.index, s.startPosition, newPlayer, onBack = app::back)
                }
            }
            // Rückmeldungen über allem, oberhalb der Tab-Leiste.
            // 96 pt über dem unteren Rand wie im Entwurf: Sicherheitsabstand (34) + 62.
            FeedbackHost(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 62.dp))
        }
    }
}
