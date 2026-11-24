package de.vincentsberger.iptvplayer.shared.testing

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.player.PlaybackEngine
import de.vincentsberger.iptvplayer.shared.player.stats.EngineStats

/** Ein aufgezeichneter [PlaybackEngine.play]-Aufruf. */
data class Play(val item: Item, val url: String, val startMs: Long, val keepFrame: Boolean)

/** Attrappe für Controller-Tests: alle Eigenschaften sind frei setzbar, `play`/`seekTo` werden aufgezeichnet. */
class FakePlaybackEngine(private val clock: () -> Long) : PlaybackEngine {
    override var timeMs: Long = 0
    override var lengthMs: Long = 0
    override var playing: Boolean = false
    override var paused: Boolean = false
    override var finished: Boolean = false
    override var failed: Boolean = false
    override var lastProgressAt: Long = Long.MIN_VALUE / 2
    override var startedAt: Long = 0
    var stats = EngineStats()
    val plays: MutableList<Play> = mutableListOf()

    override fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean) {
        plays += Play(item, url, startMs, keepFrame)
        startedAt = clock()
        failed = false
        finished = false
        timeMs = startMs
    }

    override fun stats() = stats

    override fun seekTo(ms: Long) {
        timeMs = ms
    }
}
