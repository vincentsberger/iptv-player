package de.vincentsberger.iptvplayer.ios

import androidx.compose.ui.window.ComposeUIViewController
import de.vincentsberger.iptvplayer.ios.player.VlcController
import de.vincentsberger.iptvplayer.ios.ui.IosApp
import platform.UIKit.UIViewController

/** Einstieg für Swift (`MainViewControllerKt.MainViewController()`): die ganze Oberfläche als ein ViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController { IosApp(newPlayer = ::VlcController) }
