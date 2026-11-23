package de.vincentsberger.iptvplayer.ios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** LGPL-Hinweise für MobileVLCKit und die App selbst – liegen als Texte im App-Bundle. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val texts = remember { listOf("QUELLCODE-ios", "COPYING.LIB", "COPYING").map(::bundleText) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Lizenzen", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)) {
            texts.forEach { Text(it, color = Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

private fun bundleText(name: String): String =
    NSBundle.mainBundle.pathForResource(name, ofType = "txt")
        ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) }
        ?: "($name.txt fehlt)"
