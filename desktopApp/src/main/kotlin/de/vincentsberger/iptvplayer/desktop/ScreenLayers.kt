package de.vincentsberger.iptvplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import de.vincentsberger.iptvplayer.desktop.ui.Bg
import de.vincentsberger.iptvplayer.desktop.ui.DetailAnchor
import de.vincentsberger.iptvplayer.desktop.ui.DetailLayer
import de.vincentsberger.iptvplayer.desktop.ui.HomeScreen
import de.vincentsberger.iptvplayer.desktop.ui.LocalDetailAnchor
import de.vincentsberger.iptvplayer.desktop.ui.LoginScreen
import de.vincentsberger.iptvplayer.desktop.ui.baseScreen
import de.vincentsberger.iptvplayer.desktop.ui.screenTransition
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen

/**
 * Bildschirme der PC-App in zwei Ebenen: unten der Bildschirmwechsel (Film und Serie zählen dort als Startseite, die
 * stehen bleibt und ihre Scrollposition behält), darüber das Detail-Fenster. [player] zeichnet den Player – Main.kt
 * reicht dort Vollbild und Fenstertitel hinein.
 */
@Composable
fun ScreenLayers(app: AppState, player: @Composable (Screen.Player) -> Unit) {
    val anchor = remember { DetailAnchor() }
    CompositionLocalProvider(LocalDetailAnchor provides anchor) {
        val holder = rememberSaveableStateHolder()
        // Fester Grund unter dem Bildschirmwechsel: während des Überblendens scheint nur Anthrazit durch.
        Box(Modifier.fillMaxSize().background(Bg)) {
            AnimatedContent(baseScreen(app.screen), transitionSpec = { screenTransition() }, label = "screen") { s ->
                when (s) {
                    Screen.Login -> LoginScreen(onLogin = { app.login(it) }, onCancel = if (app.canCancelLogin) app::back else null)
                    Screen.Home -> holder.SaveableStateProvider("home") { HomeScreen(app) }
                    is Screen.Player -> player(s)
                    // Kommen nie als Grund vor (baseScreen) – Film und Serie zeigt DetailLayer.
                    is Screen.Movie, is Screen.Series -> Unit
                }
            }
            DetailLayer(app)
        }
    }
}
