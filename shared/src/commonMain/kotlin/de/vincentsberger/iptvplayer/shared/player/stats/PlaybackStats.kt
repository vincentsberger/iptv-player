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
    /** Film/Folge läuft in den letzten Sekunden einer bekannten Länge: leerer Puffer ist dort normal. */
    val nearEnd: Boolean = false,
)

/** Zeiten der Engine beim Messen: Wiedergabezeit, letzter Fortschritt und Start des Streams (monotone ms), jetzt (monotone ms). */
data class EnginePulse(val timeMs: Long, val lastProgressAt: Long, val startedAt: Long, val now: Long)

/** Wie lange ein Fortschritt der Engine als „gerade eben“ gilt: ein Messtakt plus Spielraum für Zeitversatz. */
const val STATS_FRESH_MS = 1_500L

/** Rest einer bekannten Filmlänge, ab dem der Puffer nicht mehr zählt. */
const val NEAR_END_MS = 5_000L

/** Merker „kein Fortschritt gemeldet“ (wie bei `PlaybackEngine.lastProgressAt`). */
private const val NEVER = Long.MIN_VALUE / 2

/** Hält den [StatsMeter] und die Zeit des vorigen Takts; der `PlaybackController` bleibt dadurch schlank. */
class StatsSampler {
    private val meter = StatsMeter()
    private var lastTimeMs: Long? = null
    private var seekAt = NEVER

    /** Verlauf und Zähler verwerfen (neuer Titel oder neue Quelle). */
    fun reset() {
        meter.reset()
        lastTimeMs = null
        seekAt = NEVER
    }

    /** Der Nutzer spult: das Nachpuffern danach ist wie ein Start keine Stockung, bis wieder Fortschritt kommt. */
    fun noteSeek(now: Long) {
        meter.restartProgress()
        lastTimeMs = null
        seekAt = now
    }

    /**
     * Ein Messpunkt mit den Zeiten der Engine ([pulse]).
     * Fortschritt zählt nur, wenn sich die Zeit seit dem vorigen Punkt bewegt hat oder die Engine nach Start/Spulen Fortschritt meldete –
     * der erste Punkt nach [reset] ist nie Fortschritt, sonst gälte das Anfangspuffern als Stockung.
     */
    fun sample(raw: EngineStats, pulse: EnginePulse, ctx: StatsContext): PlaybackStats {
        val fresh = pulse.lastProgressAt > maxOf(pulse.startedAt, seekAt) && pulse.now - pulse.lastProgressAt < STATS_FRESH_MS
        val progressed = (lastTimeMs != null && pulse.timeMs != lastTimeMs) || fresh
        lastTimeMs = pulse.timeMs
        meter.sample(raw, progressed, ctx.shouldPlay, ctx.reconnects)
        val history = meter.history
        return PlaybackStats(
            quality = connectionQuality(history, ctx.reconnecting, ctx.connectionLost, ctx.isLive, ctx.nearEnd),
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
