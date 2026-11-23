package de.vincentsberger.iptvplayer.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.ios.player.VlcView
import de.vincentsberger.iptvplayer.shared.data.isAllowedStreamUrl
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** Demo-Server (tools/demo-server) auf dem PC im WLAN – IP im Feld anpassen. */
private const val DEMO_TS = "http://192.168.178.41:8099/live/demo/demo/1.ts"
private const val HLS_TEST = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

/** Etappe 1: prüft auf dem iPhone, ob MobileVLCKit `.ts` und HLS abspielt (Ton, Querformat, Heimnetz). */
@Composable
fun PlayerTestScreen() {
    var input by remember { mutableStateOf(DEMO_TS) }
    var url by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf("–") }
    var showLicenses by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("IPTV Player ${appVersion()} – Wiedergabe-Test", style = MaterialTheme.typography.titleLarge)
        Text("Gespeicherte Anbieter: ${remember { savedAccountCount() }}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(input, { input = it }, label = { Text("Stream-Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button({ url = input.trim(); state = "–" }, Modifier.weight(1f)) { Text("Abspielen") }
            Button({ input = HLS_TEST; url = HLS_TEST; state = "–" }, Modifier.weight(1f)) { Text("HLS-Test") }
        }
        val current = url
        when {
            current == null -> Unit
            !isAllowedStreamUrl(current) -> Text("Nur http, https, rtmp und rtsp werden abgespielt.")
            else -> key(current) {
                VlcView(current, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { state = it.name }
            }
        }
        Text("Status: $state")
        TextButton({ showLicenses = !showLicenses }) { Text(if (showLicenses) "Lizenzen ausblenden" else "Lizenzen") }
        if (showLicenses) {
            listOf("QUELLCODE-ios", "COPYING.LIB", "COPYING").forEach { name ->
                Text(bundleText(name), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun bundleText(name: String): String =
    NSBundle.mainBundle.pathForResource(name, ofType = "txt")
        ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) }
        ?: "($name.txt fehlt)"
