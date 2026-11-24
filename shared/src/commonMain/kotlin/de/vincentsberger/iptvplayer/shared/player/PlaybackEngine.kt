package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.player.stats.EngineStats

/** Schmale Sicht auf den Player der jeweiligen Plattform (ExoPlayer, VLC, AVPlayer) – der [PlaybackController] kennt nur sie. */
interface PlaybackEngine {
    /** Aktuelle Position in ms (≥ 0). */
    val timeMs: Long

    /** Länge in ms (≥ 0, 0 = unbekannt). */
    val lengthMs: Long
    val playing: Boolean

    /** Vom Nutzer angehalten. */
    val paused: Boolean
    val finished: Boolean
    val failed: Boolean

    /** Letzter Bild-/Zeitfortschritt, monotone ms; `Long.MIN_VALUE / 2` = Engine meldet das nicht (Controller misst selbst). */
    val lastProgressAt: Long

    /** Start des aktuellen Streams, monotone ms. */
    val startedAt: Long

    /** [keepFrame] = Neuverbinden: letztes Bild stehen lassen, Live an der Live-Kante. */
    fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean = false)
    fun seekTo(ms: Long)

    /** Rohwerte für die Verbindungsanzeige; Engines, die nichts messen, liefern lauter `null`. */
    fun stats(): EngineStats = EngineStats()
}
