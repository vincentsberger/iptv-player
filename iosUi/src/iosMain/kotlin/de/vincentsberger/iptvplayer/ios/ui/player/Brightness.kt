package de.vincentsberger.iptvplayer.ios.ui.player

import platform.UIKit.UIScreen

/** Bildschirmhelligkeit 0..1 – für die Wischgeste links im Player. iOS behält sie nach dem Player bei, darum merkt
 *  sich der Player den Ausgangswert und stellt ihn beim Schließen wieder her. */
internal var screenBrightness: Float
    get() = UIScreen.mainScreen.brightness.toFloat()
    set(value) { UIScreen.mainScreen.brightness = value.coerceIn(0f, 1f).toDouble() }
