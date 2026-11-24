package de.vincentsberger.iptvplayer.shared.player.stats

/** Alles, was das Statistik-Fenster und die Verbindungsanzeige darstellen; Sekundentakt, siehe `PlaybackController.runStats`. */
data class PlaybackStats(
    val quality: ConnectionQuality,
    val history: List<StatsSample>,
    val reconnects: Int,
    /** Gestockte Sekunden im Verlauf. */
    val stalledSeconds: Int,
    val droppedFrames: Long?,
    val engine: EngineStats,
    val streamKind: String,
    /** „2/3“ = zweite von drei Quellen. */
    val source: String,
    /** Nur `host:port`. */
    val server: String,
)

/** „HLS (m3u8) · Quelle 1/1“ – Art des Streams und welche Quelle spielt. */
fun streamLine(s: PlaybackStats): String = "${s.streamKind} · Quelle ${s.source}"

/** Stand von Titel/Quelle beim Messen; Eingaben für [StatsSampler.sample]. */
data class StatsContext(
    val url: String,
    val sourceLabel: String,
    val reconnects: Int,
    val reconnecting: Boolean,
    val connectionLost: Boolean,
    val isLive: Boolean,
    val shouldPlay: Boolean,
)

/** Hält den [StatsMeter] und die Zeit des vorigen Takts; der `PlaybackController` bleibt dadurch schlank. */
class StatsSampler {
    private val meter = StatsMeter()
    private var lastTimeMs: Long? = null

    /** Verlauf und Zähler verwerfen (neuer Titel oder neue Quelle). */
    fun reset() {
        meter.reset()
        lastTimeMs = null
    }

    /** Ein Messpunkt: [timeMs] = Wiedergabezeit, [fresh] = die Engine hat vor Kurzem Fortschritt gemeldet. */
    fun sample(raw: EngineStats, timeMs: Long, fresh: Boolean, ctx: StatsContext): PlaybackStats {
        val progressed = timeMs != lastTimeMs || fresh
        lastTimeMs = timeMs
        meter.sample(raw, progressed, ctx.shouldPlay, ctx.reconnects)
        val history = meter.history
        return PlaybackStats(
            quality = connectionQuality(history, ctx.reconnecting, ctx.connectionLost, ctx.isLive),
            history = history,
            reconnects = ctx.reconnects,
            stalledSeconds = history.count { it.stalled },
            droppedFrames = meter.droppedTotal,
            engine = raw,
            streamKind = streamKind(ctx.url),
            source = ctx.sourceLabel,
            server = hostAndPort(ctx.url),
        )
    }
}
