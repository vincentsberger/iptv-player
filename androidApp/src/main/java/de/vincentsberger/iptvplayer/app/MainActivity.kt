package de.vincentsberger.iptvplayer.app

import de.vincentsberger.iptvplayer.shared.UpdatePlatform
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import de.vincentsberger.iptvplayer.app.download.Downloads
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.TvChrome
import de.vincentsberger.iptvplayer.app.ui.Feedback
import de.vincentsberger.iptvplayer.app.update.Updater
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import de.vincentsberger.iptvplayer.app.ui.screenTransition
import de.vincentsberger.iptvplayer.app.ui.HomeScreen
import de.vincentsberger.iptvplayer.app.ui.LoginScreen
import de.vincentsberger.iptvplayer.app.ui.PlayerScreen
import de.vincentsberger.iptvplayer.app.ui.MovieScreen
import de.vincentsberger.iptvplayer.app.ui.SeriesScreen
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.AndroidKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.AndroidPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.Storage

/** Hält den gemeinsamen App-Zustand über Konfigurationswechsel hinweg. */
class AppHolder(app: Application) : AndroidViewModel(app) {
    val state = AppState(Storage(AndroidKeyValueStore.secure(app)), playlistCache = { AndroidPlaylistCache.forAccount(app, it) })
    override fun onCleared() = state.close()
}

class MainActivity : ComponentActivity() {
    /** Wann die App in den Hintergrund ging – nach langer Pause gilt die Rückkehr als neuer Start. */
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = android.os.SystemClock.elapsedRealtime()
    }

    /** Kennung aus einem Link vom Startbildschirm (Watch Next), die noch abgespielt werden soll. */
    private var pendingLink: String? = null

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0) ViewModelProvider(this)[AppHolder::class.java].state.onAppResumed(android.os.SystemClock.elapsedRealtime() - stoppedAt)
        stoppedAt = 0L
    }

    /**
     * Link vom Startbildschirm abspielen – hier statt in onStart: Bei einer gestoppten Activity kommt onNewIntent erst
     * nach onStart (onRestart → onStart → onNewIntent → onResume). Und nach onAppResumed, das nach langer Pause die
     * Ansicht zurücksetzt und den Player sonst gleich wieder schlösse.
     */
    override fun onResume() {
        super.onResume()
        val link = pendingLink ?: return
        pendingLink = null
        val vm = ViewModelProvider(this)[AppHolder::class.java].state
        vm.resumeFromHistory { WatchNext.id(vm.activeAccountId, it.item) == link }
    }

    // Läuft die App schon, landet der Link hier (singleTask) statt in einer zweiten Instanz.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = WatchNext.linkId(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingLink = WatchNext.linkId(intent)
        Downloads.init(this)
        // Insets (v. a. Bildschirmtastatur) selbst in Compose behandeln, auf allen API-Stufen gleich.
        // Auf dem TV gibt es keine Systemleisten, optisch ändert sich dadurch nichts.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppTheme { TvChrome {
                val vm = viewModel<AppHolder>().state
                // Einmal am Tag im Hintergrund nach Updates suchen (nur selbst installierte Versionen).
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    if (!Updater.allowed(context)) return@LaunchedEffect
                    vm.checkForUpdate(Updater.currentVersion(context), UpdatePlatform.ANDROID)?.let { Feedback.show("Update auf Version ${it.version} verfügbar – siehe Start") }
                }
                // Weiterschauen auf dem Startbildschirm des Fernsehers aktuell halten (kurz gesammelt, Verlauf ändert sich in Schüben).
                LaunchedEffect(Unit) {
                    snapshotFlow { Triple(vm.activeAccountId, vm.history, vm.showOnSystemHome) }.collectLatest { (account, history, on) ->
                        delay(1_000)
                        WatchNext.sync(context, account, if (account == null) emptyList() else WatchNext.entries(history, on))
                    }
                }
                // Merkt sich Scroll-Positionen und Auswahl, wenn man aus dem Player zurückkommt.
                val holder = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = vm.screen,
                    // Bildschirmtastatur: Inhalt endet über der Tastatur, statt von ihr verdeckt zu werden.
                    modifier = Modifier.imePadding(),
                    transitionSpec = { screenTransition() },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Login -> LoginScreen(onLogin = { vm.login(it) }, onCancel = if (vm.canCancelLogin) vm::back else null)
                        Screen.Home -> holder.SaveableStateProvider("home") { HomeScreen(vm) }
                        is Screen.Series -> holder.SaveableStateProvider("series:${s.series.id}") {
                            BackHandler { vm.back() }
                            SeriesScreen(vm, s.series)
                        }
                        is Screen.Movie -> {
                            BackHandler { vm.back() }
                            MovieScreen(vm, s.movie)
                        }
                        is Screen.Player -> PlayerScreen(vm, s.items, s.index, s.startPosition, onBack = vm::back)
                    }
                }
            } }
        }
    }
}
