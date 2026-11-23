package de.vincentsberger.iptvplayer.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import de.vincentsberger.iptvplayer.shared.data.IosKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.Storage
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/** Einstieg für Swift (`MainViewControllerKt.MainViewController()`): die ganze Oberfläche als ein ViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) { PlayerTestScreen() }
    }
}

/** Version aus dem App-Bundle (MARKETING_VERSION = appVersion aus gradle.properties). */
internal fun appVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"

/** Anzahl gespeicherter Anbieter – beweist, dass `shared` und der iOS-Speicher im App-Kontext laufen (-1 = Fehler). */
internal fun savedAccountCount(): Int = runCatching { Storage(IosKeyValueStore()).accounts().size }.getOrDefault(-1)
