package de.vincentsberger.iptvplayer.shared.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.ChannelNumberInput
import de.vincentsberger.iptvplayer.shared.SeekTarget
import de.vincentsberger.iptvplayer.shared.StreamRecovery
import de.vincentsberger.iptvplayer.shared.ZapCard
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.wrapIndex
import kotlinx.coroutines.delay

/** Sekunden Countdown bis zur nächsten Folge. */
private const val NEXT_COUNTDOWN_S = 10

/** Pause nach der letzten Ziffer, dann wird umgeschaltet. */
private const val DIGITS_COMMIT_MS = 1_500L

private const val SECOND_MS = 1_000L

/** Takt der Uhr (Position, Schlaf-Timer). */
private const val CLOCK_TICK_MS = 500L

/** Alle so viele Takte (= 15 s) wird die Position gesichert. */
private const val SAVE_EVERY_TICKS = 30

/**
 * Gemeinsamer Ablauf der drei Player-Oberflächen: Playlist (Index, Quellen, Zappen), Startposition, Verlauf sichern,
 * Ziffernwahl, nächste Folge und Uhr-Takt. Die Oberflächen starten die `suspend`-Funktionen als `LaunchedEffect`
 * (Schlüssel siehe jeweils dort) und zeigen nur den Zustand an; abgespielt wird über die [PlaybackEngine].
 */
@Suppress("LongParameterList", "TooManyFunctions") // ein Ablauf für drei Oberflächen: feste Schnittstelle, bewusst eine Klasse
class PlaybackController(
    private val app: AppState,
    val engine: PlaybackEngine,
    startItems: List<Item>,
    startIndex: Int,
    private val startPosition: Long,
    /** Wanduhr (ZapCard, SeekTarget). */
    clock: () -> Long,
    /** Monotone Uhr (Stream-Überwachung). */
    @Suppress("unused") private val monotonic: () -> Long,
    private val resolveUrl: (Item) -> String = { it.url },
) {
    var items by mutableStateOf(startItems); private set
    var index by mutableStateOf(startIndex); private set
    val item: Item get() = items[index]
    val isLive: Boolean get() = item.section == Section.LIVE
    val isEpisodes: Boolean get() = item.section == Section.SERIES

    val sources: List<Item> get() = app.sourcesOf(item)
    var sourceIndex by mutableStateOf(0); private set
    val source: Item get() = sources.getOrElse(sourceIndex) { item }

    /** Vorheriger Sender zum Zurückschalten. */
    var lastIndex by mutableStateOf<Int?>(null); private set
    var epg by mutableStateOf<List<EpgEntry>>(emptyList()); private set

    /** Sekunden bis zur nächsten Folge, null = kein Countdown. */
    var nextIn by mutableStateOf<Int?>(null); private set
    var sleepLeft by mutableStateOf(app.sleepMinutesLeft()); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set

    /** Startposition des laufenden Titels (0 = von vorn) – die Oberflächen zeigen die Leiste nach einem Fortsetzen länger. */
    var startedFromMs by mutableStateOf(0L); private set

    /** Der Nutzer hat den Countdown abgebrochen; gilt bis zum nächsten Titelwechsel. */
    var nextCancelled by mutableStateOf(false); private set

    var reconnecting by mutableStateOf(false); private set
    var connectionLost by mutableStateOf(false); private set

    val zap = ZapCard(clock)
    val seek = SeekTarget(clock)
    val numberInput = ChannelNumberInput()

    private val recovery = StreamRecovery()
    private var startPending = true
    private var previous: Item? = null

    /** Neue Liste/Stelle: alles zurücksetzen, was zum einzelnen Titel gehört. */
    private fun moveTo(newItems: List<Item>, newIndex: Int) {
        seek.cancel()
        items = newItems
        index = newIndex
        sourceIndex = 0
        nextCancelled = false
    }

    fun switchTo(i: Int) {
        val target = wrapIndex(i, items.size)
        lastIndex = index
        moveTo(items, target)
    }

    fun lastChannel() {
        lastIndex?.let(::switchTo)
    }

    /** Andere Quelle desselben Titels: Live von der Live-Kante, Filme an der aktuellen Stelle. */
    fun selectSource(i: Int) {
        if (i == sourceIndex || i !in sources.indices) return
        sourceIndex = i
        recovery.reset()
        reconnecting = false
        connectionLost = false
        engine.play(item, resolveUrl(sources[i]), if (isLive) 0 else engine.timeMs, keepFrame = true)
    }

    fun cancelNext() {
        nextCancelled = true
        nextIn = null
    }

    fun saveNow() = app.saveProgress(item, seek.positionOf(engine.timeMs), engine.lengthMs)

    /** Schlüssel (items, index): vorigen Titel sichern, neuen starten, Zapp-Karte/Infos/EPG. */
    suspend fun startCurrent() {
        previous?.let { app.saveProgress(it, engine.timeMs, engine.lengthMs) }
        previous = item
        reconnecting = false
        connectionLost = false
        recovery.reset()
        epg = emptyList()
        val start = if (startPending) startPosition else 0L
        startPending = false
        startedFromMs = start
        engine.play(item, resolveUrl(source), start)
        if (isLive) zap.onChannelChanged(item) else { zap.hide(); app.requestMediaInfo(item) }
        if (isLive) epg = app.nowPlaying(item)
    }

    /** Schlüssel (engine.finished, index, nextCancelled): nächste Folge mit Countdown, abbrechbar, abschaltbar. */
    suspend fun runAutoNext() {
        nextIn = null
        val next = app.nextEpisodeIndex(items, index)
        if (next == null || !engine.finished || !autoNextAllowed()) return
        for (s in NEXT_COUNTDOWN_S downTo 1) { nextIn = s; delay(SECOND_MS) }
        nextIn = null
        switchTo(next)
    }

    private fun autoNextAllowed() = app.autoNextEpisode && !nextCancelled

    /** Schlüssel numberInput.digits: kurz nach der letzten Ziffer umschalten, sonst [onNotFound] mit der Nummer. */
    suspend fun commitDigits(onNotFound: (Int) -> Unit) {
        if (numberInput.digits.isEmpty()) return
        delay(DIGITS_COMMIT_MS)
        val n = numberInput.take() ?: return
        val pick = ChannelNumberInput.find(n, items, if (isLive) app.section(Section.LIVE).items else items)
        when {
            pick == null -> onNotFound(n)
            pick.list == items -> switchTo(pick.index)
            else -> { lastIndex = null; moveTo(pick.list, pick.index) }
        }
    }

    /** Läuft, solange der Player offen ist: Position, Schlaf-Timer, alle 15 s Position sichern (nur Filme/Folgen). */
    suspend fun runClock(onSleep: () -> Unit, onTick: () -> Unit = {}) {
        var ticks = 0
        while (true) {
            positionMs = engine.timeMs
            durationMs = engine.lengthMs
            sleepLeft = app.sleepMinutesLeft()
            onTick()
            if (app.consumeSleepTimer()) { onSleep(); return }
            if (++ticks % SAVE_EVERY_TICKS == 0 && !isLive) saveNow()
            delay(CLOCK_TICK_MS)
        }
    }
}
