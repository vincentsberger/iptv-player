package de.vincentsberger.iptvplayer.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import de.vincentsberger.iptvplayer.shared.data.IosKeyValueStore
import de.vincentsberger.iptvplayer.shared.data.Storage
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/** Einstieg für Swift (`MainViewControllerKt.MainViewController()`): die ganze Oberfläche als ein ViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) { PlaceholderScreen() }
    }
}

/** Version aus dem App-Bundle (MARKETING_VERSION = appVersion aus gradle.properties). */
internal fun appVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"

/** Etappe 1: beweist, dass Compose, `shared` und der iOS-Speicher im App-Kontext laufen. */
@Composable
private fun PlaceholderScreen() {
    val accounts = remember { runCatching { Storage(IosKeyValueStore()).accounts().size }.getOrDefault(-1) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("IPTV Player ${appVersion()}", style = MaterialTheme.typography.headlineMedium)
        Text("Gespeicherte Anbieter: $accounts")
    }
}
