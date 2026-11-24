package de.vincentsberger.iptvplayer.shared.player

import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime

/** Ab so vielen Tasten-Wiederholungen springt das Spulen dreifach. */
private const val FAST_REPEAT = 10

/** Ab so vielen Tasten-Wiederholungen springt das Spulen sechsfach. */
private const val FASTEST_REPEAT = 30
private const val FAST_FACTOR = 3
private const val FASTEST_FACTOR = 6

/** Ausschnitt „HH:mm“ aus dem Format „yyyy-MM-dd HH:mm…“ von `localDateTime`. */
private const val CLOCK_START = 11
private const val CLOCK_END = 16

/** Sprungfaktor für gehaltene Spultasten: je länger gehalten, desto weiter. */
fun seekMultiplier(repeatCount: Int): Int = when {
    repeatCount < FAST_REPEAT -> 1
    repeatCount < FASTEST_REPEAT -> FAST_FACTOR
    else -> FASTEST_FACTOR
}

/** Uhrzeit, zu der der Film bei normaler Wiedergabe endet („21:45“). */
fun endsAt(remainingMs: Long, now: Long = currentTimeMillis()): String =
    localDateTime(now + remainingMs.coerceAtLeast(0)).substring(CLOCK_START, CLOCK_END)
