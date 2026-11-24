package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime

/** Sprungfaktor für gehaltene Spultasten: je länger gehalten, desto weiter. */
fun seekMultiplier(repeatCount: Int): Int = when {
    repeatCount < 10 -> 1
    repeatCount < 30 -> 3
    else -> 6
}

/** Uhrzeit, zu der der Film bei normaler Wiedergabe endet („21:45“). */
fun endsAt(remainingMs: Long, now: Long = currentTimeMillis()): String =
    localDateTime(now + remainingMs.coerceAtLeast(0)).substring(11, 16)
