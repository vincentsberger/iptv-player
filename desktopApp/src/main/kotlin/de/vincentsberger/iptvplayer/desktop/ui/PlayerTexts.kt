package de.vincentsberger.iptvplayer.desktop.ui

/** Beschriftung des Wiedergabe-Knopfs: was ein Klick tut. */
internal fun playPauseLabel(playing: Boolean): String = if (playing) "Pause" else "Abspielen"

/** Tooltip-Text eines Knopfs: „Vollbild (F)“. */
internal fun ctrlTooltip(label: String, shortcut: String?): String = if (shortcut == null) label else "$label ($shortcut)"
