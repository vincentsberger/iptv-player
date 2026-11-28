package de.vincentsberger.iptvplayer.desktop.player

import uk.co.caprica.vlcj.binding.LibX11
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment

/**
 * Natives Video (VLC zeichnet direkt per GPU in ein Fenster, Bedienung in einem durchsichtigen Overlay-Fenster) oder
 * kompatibel (jedes Bild über den Callback in Compose, deutlich mehr CPU). Nativ braucht durchsichtige Fenster –
 * ohne (Linux ohne Compositor, Tests ohne Bildschirm) bleibt es beim kompatiblen Weg.
 */
fun useNativeVideo(compatibleMode: Boolean, translucentWindows: Boolean = translucentWindowsSupported()): Boolean =
    !compatibleMode && translucentWindows

/** Kann das System Fenster mit Alpha je Pixel? Ohne Bildschirm (headless) nie. */
fun translucentWindowsSupported(): Boolean = runCatching {
    !GraphicsEnvironment.isHeadless() &&
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
}.getOrDefault(false)

/** Seitenverhältnis bzw. Zuschnitt für VLC; null = VLC-Standard (einpassen mit Rand). */
data class VlcScaling(val aspect: String?, val crop: String?)

/**
 * Bildeinpassung der Oberfläche („fit“/„zoom“/„fill“, siehe VideoFit) für die native Ausgabe: Strecken setzt das
 * Seitenverhältnis auf die Fensterform, Füllen schneidet das Bild auf die Fensterform zu. Ohne Fenstergröße: Standard.
 */
fun vlcScaling(fit: String, width: Int, height: Int): VlcScaling {
    if (width <= 0 || height <= 0) return VlcScaling(null, null)
    return when (fit) {
        "fill" -> VlcScaling(aspect = "$width:$height", crop = null)
        "zoom" -> VlcScaling(aspect = null, crop = "$width:$height")
        else -> VlcScaling(null, null)
    }
}

/** Linux: XInitThreads() vor jedem AWT-Aufruf – vlcj verlangt das für die native Ausgabe in ein AWT-Fenster. */
fun initNativeVideoThreads() {
    if (System.getProperty("os.name").orEmpty().startsWith("Linux")) runCatching { LibX11.INSTANCE.XInitThreads() }
}
