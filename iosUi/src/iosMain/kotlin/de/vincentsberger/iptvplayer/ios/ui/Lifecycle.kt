package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/** Monotone Uhr in ms (läuft auch bei Uhrzeit-Umstellung gleichmäßig) – für Hänger-Erkennung und Dauer im Hintergrund. */
fun monotonicMs(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

/**
 * Meldet, wenn die App in den Hintergrund geht (Home-Taste, Anruf, Sperre) und wann sie zurückkommt – mit der Dauer
 * im Hintergrund. Die Beobachter werden beim Verlassen der Komposition wieder abgemeldet.
 */
@Composable
fun AppLifecycleEffect(onBackground: () -> Unit = {}, onForeground: (backgroundMs: Long) -> Unit = {}) {
    val background by rememberUpdatedState(onBackground)
    val foreground by rememberUpdatedState(onForeground)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        var leftAt = 0L
        val bg = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            leftAt = monotonicMs()
            background()
        }
        val fg = center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            foreground(if (leftAt == 0L) 0L else monotonicMs() - leftAt)
        }
        onDispose {
            center.removeObserver(bg)
            center.removeObserver(fg)
        }
    }
}
