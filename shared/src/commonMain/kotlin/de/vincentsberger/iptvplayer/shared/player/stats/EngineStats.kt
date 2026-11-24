package de.vincentsberger.iptvplayer.shared.player.stats

/** Momentaufnahme der Rohwerte einer Engine; `null` = diese Engine kennt den Wert nicht. Zähler sind je Medium kumuliert. */
data class EngineStats(
    val bytesReceived: Long? = null,
    val bufferedMs: Long? = null,
    val droppedFrames: Long? = null,
    val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Float? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioLanguage: String? = null,
)
