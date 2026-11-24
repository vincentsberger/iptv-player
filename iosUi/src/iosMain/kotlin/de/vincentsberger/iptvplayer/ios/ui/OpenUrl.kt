package de.vincentsberger.iptvplayer.ios.ui

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Adresse außerhalb der App öffnen (Trailer → YouTube-App bzw. Safari). Ungültige Adressen tun nichts. */
fun openExternalUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}
