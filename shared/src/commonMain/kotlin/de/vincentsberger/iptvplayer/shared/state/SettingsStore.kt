package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.HomeRow
import de.vincentsberger.iptvplayer.shared.LatestRelease
import de.vincentsberger.iptvplayer.shared.UpdatePlatform
import de.vincentsberger.iptvplayer.shared.Updates
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.PlatformHttp
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.orderedHomeRows

/** Geräteweite Einstellungen: Komfort, Wiedergabe, Startseiten-Reihen, Updates, Schlaf-Timer. */
interface Settings {
    var sortAz: Boolean
    var sortByYear: Boolean
    var playerLandscape: Boolean
    var startWithLastChannel: Boolean
    var autoNextEpisode: Boolean
    var showOnSystemHome: Boolean
    var showConnectionIndicator: Boolean
    var updateCheck: Boolean
    val availableUpdate: LatestRelease?
    suspend fun checkForUpdate(
        currentVersion: String, platform: UpdatePlatform? = null, force: Boolean = false, http: HttpSource = PlatformHttp,
    ): LatestRelease?
    var audioDelayMs: Int
    var videoFit: String
    var audioLanguage: String?
    var subtitleLanguage: String?
    var pauseDownloadsWhilePlaying: Boolean
    var volume: Int
    val homeRowOrder: List<HomeRow>
    val hiddenHomeRows: Set<HomeRow>
    val homeRows: List<HomeRow>
    fun toggleHomeRow(row: HomeRow)
    fun moveHomeRow(row: HomeRow, up: Boolean)
    fun resetHomeRows()
    val sleepAt: Long?
    fun cycleSleepTimer()
    fun sleepMinutesLeft(): Int?
    fun consumeSleepTimer(): Boolean
    fun nextEpisodeIndex(items: List<Item>, index: Int): Int?
}

/** Einstellungen: Compose-State fürs Neuzeichnen, der Setter speichert sofort. Gelten für alle Anbieter. */
class SettingsStore(private val store: Storage, private val clock: () -> Long) : Settings {

    private var sortAzState by mutableStateOf(store.sortAz)
    override var sortAz: Boolean get() = sortAzState; set(v) { sortAzState = v; store.sortAz = v }

    // ---------- Startseite: Reihenfolge und Sichtbarkeit der Reihen ----------

    /** Alle Reihen in der gewählten Reihenfolge (auch ausgeblendete – für die Einstellungen). */
    override var homeRowOrder by mutableStateOf(orderedHomeRows(store.homeRowOrder)); private set
    override var hiddenHomeRows by mutableStateOf(store.hiddenHomeRows.mapNotNull { n -> HomeRow.entries.firstOrNull { it.name == n } }.toSet())
        private set

    /** Die Reihen, die die Startseite zeigt – in dieser Reihenfolge (leere lässt die Oberfläche weg). */
    override val homeRows: List<HomeRow> get() = homeRowOrder.filterNot { it in hiddenHomeRows }

    override fun toggleHomeRow(row: HomeRow) {
        hiddenHomeRows = if (row in hiddenHomeRows) hiddenHomeRows - row else hiddenHomeRows + row
        store.hiddenHomeRows = hiddenHomeRows.mapTo(HashSet()) { it.name }
    }

    /** Reihe eine Position nach oben ([up]) bzw. unten schieben. */
    override fun moveHomeRow(row: HomeRow, up: Boolean) {
        val list = homeRowOrder.toMutableList()
        val i = list.indexOf(row)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j]; list[j] = row
        homeRowOrder = list
        store.homeRowOrder = list.map { it.name }
    }

    override fun resetHomeRows() {
        homeRowOrder = HomeRow.entries.toList(); hiddenHomeRows = emptySet()
        store.homeRowOrder = emptyList(); store.hiddenHomeRows = emptySet()
    }

    private var playerLandscapeState by mutableStateOf(store.playerLandscape)
    /** iPhone: Player immer im Querformat öffnen (auch bei Dreh-Sperre), danach zurück ins Hochformat. */
    override var playerLandscape: Boolean get() = playerLandscapeState; set(v) { playerLandscapeState = v; store.playerLandscape = v }

    private var sortByYearState by mutableStateOf(store.sortByYear)
    /** Filme und Serien nach Jahr im Titel, neueste zuerst (ohne Jahr ans Ende); bei Gleichstand gilt A–Z bzw. Anbieter. */
    override var sortByYear: Boolean get() = sortByYearState; set(v) { sortByYearState = v; store.sortByYear = v }

    private var startLastState by mutableStateOf(store.startWithLastChannel)
    override var startWithLastChannel: Boolean get() = startLastState; set(v) { startLastState = v; store.startWithLastChannel = v }

    private var connIndicatorState by mutableStateOf(store.showConnectionIndicator)
    override var showConnectionIndicator: Boolean
        get() = connIndicatorState
        set(v) { connIndicatorState = v; store.showConnectionIndicator = v }
    private var autoNextState by mutableStateOf(store.autoNextEpisode)
    override var autoNextEpisode: Boolean get() = autoNextState; set(v) { autoNextState = v; store.autoNextEpisode = v }
    private var systemHomeState by mutableStateOf(store.showOnSystemHome)
    /** Weiterschauen auch auf dem Startbildschirm des Fernsehers (Android TV); die App gleicht danach ab. */
    override var showOnSystemHome: Boolean get() = systemHomeState; set(v) { systemHomeState = v; store.showOnSystemHome = v }

    // ---------- Updates ----------

    private var updateCheckState by mutableStateOf(store.updateCheck)
    /** Von selbst nach Updates suchen – abschaltbar in den Einstellungen. */
    override var updateCheck: Boolean get() = updateCheckState; set(v) { updateCheckState = v; store.updateCheck = v }

    /** Neuere Version, falls eine gefunden wurde (Hinweis auf Startseite und in den Einstellungen). */
    override var availableUpdate by mutableStateOf<LatestRelease?>(null); private set

    /**
     * Nach Updates suchen. Ohne [force] nur, wenn eingeschaltet und die letzte Suche über einen Tag her ist.
     * Liefert die neuere Version oder null. [platform]: Version der eigenen Release-Datei zählt (siehe [Updates.parse]).
     */
    override suspend fun checkForUpdate(currentVersion: String, platform: UpdatePlatform?, force: Boolean, http: HttpSource): LatestRelease? {
        val last = store.lastUpdateCheck
        val recent = last != 0L && clock() - last < Updates.CHECK_INTERVAL_MS // 0 = noch nie gesucht → fällig
        if (!force && (!updateCheck || recent)) return availableUpdate
        store.lastUpdateCheck = clock()
        availableUpdate = Updates.newerThan(currentVersion, platform, http)
        return availableUpdate
    }

    /** Ton-Verzögerung in ms (Desktop-Player). */
    override var audioDelayMs: Int
        get() = store.audioDelayMs
        set(v) { store.audioDelayMs = v }

    /** Bildformat im Player (fit / zoom / fill) – gilt für alle Titel. */
    override var videoFit: String
        get() = store.videoFit
        set(v) { store.videoFit = v }

    /** Zuletzt gewählte Tonspur-Sprache – gilt auch für den nächsten Film. */
    override var audioLanguage: String?
        get() = store.audioLanguage
        set(v) { store.audioLanguage = v }

    /** Zuletzt gewählte Untertitel-Sprache bzw. [Storage.SUBTITLES_OFF]. */
    override var subtitleLanguage: String?
        get() = store.subtitleLanguage
        set(v) { store.subtitleLanguage = v }

    private var pauseDownloadsState by mutableStateOf(store.pauseDownloadsWhilePlaying)
    /** Downloads ruhen lassen, solange etwas aus dem Netz läuft (viele Anbieter erlauben nur eine Verbindung). */
    override var pauseDownloadsWhilePlaying: Boolean
        get() = pauseDownloadsState
        set(v) { pauseDownloadsState = v; store.pauseDownloadsWhilePlaying = v }

    override var volume: Int
        get() = store.volume
        set(v) { store.volume = v }

    // ---------- Schlaf-Timer, nächste Folge ----------

    /** Schlaf-Timer: Zeitpunkt (ms), zu dem die Wiedergabe stoppt; null = aus. */
    override var sleepAt by mutableStateOf<Long?>(null); private set

    /** Schaltet aus → 30 → 60 → 90 Minuten → aus. */
    override fun cycleSleepTimer() {
        val remaining = sleepMinutesLeft()
        val next = SLEEP_STEPS.firstOrNull { it > (remaining ?: 0) + 1 }
        sleepAt = next?.let { clock() + it * 60_000L }
    }

    /** Verbleibende Minuten (aufgerundet) oder null, wenn kein Timer läuft. */
    override fun sleepMinutesLeft(): Int? = sleepAt?.let { ((it - clock() + 59_999) / 60_000).toInt().coerceAtLeast(0) }

    /** True, wenn der Timer abgelaufen ist – setzt ihn dann zurück (der Player beendet die Wiedergabe). */
    override fun consumeSleepTimer(): Boolean {
        val at = sleepAt ?: return false
        if (clock() < at) return false
        sleepAt = null
        return true
    }

    /**
     * Index der nächsten Folge in der Liste des Players, sofern sie zur selben Serie gehört. „Weiterschauen“ mischt
     * Folgen verschiedener Serien und Filme – dort ist die nächste Kachel nicht die nächste Folge (siehe
     * [MediaDetails.nextEpisodeAfter]).
     */
    override fun nextEpisodeIndex(items: List<Item>, index: Int): Int? {
        val current = items.getOrNull(index) ?: return null
        val next = items.getOrNull(index + 1) ?: return null
        val sameSeries = current.section == Section.SERIES && next.section == Section.SERIES && !next.isSeriesFolder && next.categoryId == current.categoryId
        return if (sameSeries) index + 1 else null
    }

    companion object {
        val SLEEP_STEPS = listOf(30, 60, 90)
    }
}
