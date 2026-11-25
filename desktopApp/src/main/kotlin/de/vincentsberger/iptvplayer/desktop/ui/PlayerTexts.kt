package de.vincentsberger.iptvplayer.desktop.ui

/** Beschriftung des Wiedergabe-Knopfs: was ein Klick tut. */
internal fun playPauseLabel(playing: Boolean): String = if (playing) "Pause" else "Abspielen"

/** Tooltip-Text eines Knopfs: „Vollbild (F)“. */
internal fun ctrlTooltip(label: String, shortcut: String?): String = if (shortcut == null) label else "$label ($shortcut)"

/** Schrittweite und Grenzen der Ton-Verzögerung (VLC verkraftet mehr, sinnvoll sind ein paar hundert ms). */
internal const val AUDIO_DELAY_STEP_MS = 50
internal const val AUDIO_DELAY_MAX_MS = 1000

/** Ton-Verzögerung einen Schritt früher (−1) oder später (+1), auf ganze Schritte und ±1 s begrenzt. */
internal fun stepAudioDelay(current: Int, direction: Int): Int {
    val snapped = Math.round(current.toFloat() / AUDIO_DELAY_STEP_MS) * AUDIO_DELAY_STEP_MS
    return (snapped + direction * AUDIO_DELAY_STEP_MS).coerceIn(-AUDIO_DELAY_MAX_MS, AUDIO_DELAY_MAX_MS)
}

/** „Aus“, „+150 ms (Ton später)“, „−100 ms (Ton früher)“. */
internal fun audioDelayLabel(ms: Int): String = when {
    ms == 0 -> "Aus"
    ms > 0 -> "+$ms ms (Ton später)"
    else -> "−${-ms} ms (Ton früher)"
}

/** Anteil der Zeitleiste unter der Maus: [x] in px auf einer Fläche der Breite [width], deren Spur [inset] vom Rand beginnt. */
internal fun timelineFraction(x: Float, width: Float, inset: Float): Float {
    val track = width - 2 * inset
    return if (track <= 0f) 0f else ((x - inset) / track).coerceIn(0f, 1f)
}
