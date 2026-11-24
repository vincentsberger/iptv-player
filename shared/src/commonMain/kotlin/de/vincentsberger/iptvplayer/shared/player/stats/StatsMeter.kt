package de.vincentsberger.iptvplayer.shared.player.stats

private const val BITS_PER_BYTE = 8L
private const val DEFAULT_CAPACITY = 60

/** Ein Messpunkt je Sekunde; `null` = unbekannt. */
data class StatsSample(
    val bufferMs: Long?,
    val bitrateBps: Long?,
    val stalled: Boolean,
    val droppedDelta: Long?,
    val fps: Float?,
    val reconnected: Boolean,
)

/**
 * Macht aus den Rohwerten der Engine (einmal pro Sekunde) Messpunkte: Datenrate aus der Bytes-Differenz, verworfene Bilder
 * als Differenz, Stockungen und Neuverbindungen. Hält die letzten [capacity] Punkte, älteste zuerst.
 * Ein Zähler, der kleiner wird als beim Punkt davor, gilt als Neustart des Streams: diese Sekunde bleibt ohne Wert.
 */
class StatsMeter(private val capacity: Int = DEFAULT_CAPACITY) {
    private val samples = ArrayDeque<StatsSample>()
    private var lastBytes: Long? = null
    private var lastDropped: Long? = null
    private var lastReconnects: Int? = null
    private var hadProgress = false
    private var dropped: Long? = null

    /** Letzte Messpunkte, älteste zuerst, höchstens `capacity`. */
    val history: List<StatsSample> get() = samples.toList()

    /** Summe der Deltas verworfener Bilder seit [reset]; `null`, solange noch kein Delta bekannt ist. */
    val droppedTotal: Long? get() = dropped

    /** Nimmt einen Messpunkt auf. [progressed] = in dieser Sekunde kam Zeit-/Bildfortschritt, [shouldPlay] = Wiedergabe soll laufen. */
    fun sample(raw: EngineStats, progressed: Boolean, shouldPlay: Boolean, reconnectsTotal: Int): StatsSample {
        val bitrate = delta(lastBytes, raw.bytesReceived)?.times(BITS_PER_BYTE)
        val droppedDelta = delta(lastDropped, raw.droppedFrames)
        lastBytes = raw.bytesReceived
        lastDropped = raw.droppedFrames
        droppedDelta?.let { dropped = (dropped ?: 0L) + it }
        val reconnected = lastReconnects?.let { reconnectsTotal > it } ?: false
        lastReconnects = reconnectsTotal
        val stalled = shouldPlay && !progressed && hadProgress
        if (progressed) hadProgress = true
        val s = StatsSample(raw.bufferedMs, bitrate, stalled, droppedDelta, raw.fps, reconnected)
        samples.addLast(s)
        while (samples.size > capacity) samples.removeFirst()
        return s
    }

    /** Verlauf und alle Zähler zurücksetzen (neuer Titel oder neue Quelle). */
    fun reset() {
        samples.clear()
        lastBytes = null
        lastDropped = null
        lastReconnects = null
        hadProgress = false
        dropped = null
    }

    private fun delta(prev: Long?, now: Long?): Long? =
        if (prev == null || now == null || now < prev) null else now - prev
}
