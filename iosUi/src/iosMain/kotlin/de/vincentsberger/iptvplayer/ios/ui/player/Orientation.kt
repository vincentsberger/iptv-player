package de.vincentsberger.iptvplayer.ios.ui.player

import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscapeRight
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * Dreht die App ins Quer- bzw. Hochformat (iOS 16+) – wirkt auch bei aktiver Dreh-Sperre. Danach folgt die App
 * wieder dem Gerät, sobald man es dreht.
 */
fun requestLandscape(landscape: Boolean) {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return
    val mask = if (landscape) UIInterfaceOrientationMaskLandscapeRight else UIInterfaceOrientationMaskPortrait
    scene.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(mask)) { _ -> }
}
