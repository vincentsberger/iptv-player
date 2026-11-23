package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.data.PlatformHttp
import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.CacheMeta
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.PlaylistCacheStore
import de.vincentsberger.iptvplayer.shared.data.SectionLoad
import de.vincentsberger.iptvplayer.shared.data.accountId
import de.vincentsberger.iptvplayer.shared.data.CountryOption
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Season
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.hostOf
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Series(val series: Item) : Screen
    data class Player(val items: List<Item>, val index: Int, val startPosition: Long = 0) : Screen
}

/** Navigationstiefe: Übergänge gleiten vorwärts (tiefer) von rechts herein, zurück von links. */
val Screen.depth: Int
    get() = when (this) {
        Screen.Login -> 0
        Screen.Home -> 1
        is Screen.Series -> 2
        is Screen.Player -> 3
    }

/** Anzeigename eines Anbieters, z. B. „Xtream Codes · user @ srv“. */
fun labelOf(account: Account): String = when (account) {
    is Account.Xtream -> "Xtream Codes · ${account.username} @ ${hostOf(account.server)}"
    is Account.M3u -> "M3U-Playlist · ${hostOf(account.url)}"
}

enum class Tab(val title: String) {
    /** Startseite: Weiterschauen, zuletzt gesehene Sender, Favoriten, Downloads. */
    HOME("Start"),
    SEARCH("Suche"),
    LIVE("Live TV"),
    MOVIES("Filme"),
    SERIES("Serien"),
    FAVORITES("Favoriten"),
    /** Heruntergeladene Filme und Folgen – nur in der TV-App. */
    DOWNLOADS("Downloads"),
    SETTINGS("Einstellungen"),
}

/** Was in der Übersicht nach dem Laden auffällt. */
enum class OverviewWarning { FAILED, EMPTY, FEWER }

/** Eine Zeile der Übersicht: Anzahl, Veränderung zum vorigen Laden, ggf. Warnung, Stand ([loadedAt]). */
data class OverviewRow(val section: Section, val count: Int, val delta: Int?, val warning: OverviewWarning?, val loadedAt: Long?)

data class SectionState(
    val loading: Boolean = false,
    val error: String? = null,
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val byCategory: Map<String, List<Item>> = emptyMap(),
) {
    val loaded: Boolean get() = !loading && error == null && (items.isNotEmpty() || categories.isNotEmpty())
}

/**
 * Gemeinsamer App-Zustand für Android TV und PC: Anmeldung, Listen, Favoriten,
 * Verlauf, Suche und Navigation. Die Oberflächen lesen nur diesen Zustand.
 */
class AppState(
    private val store: Storage,
    // Die folgenden Parameter haben Defaults für die App; Tests ersetzen sie
    // (eigener Coroutine-Scope, feste Uhrzeit, Repository mit Attrappen-HTTP).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val clock: () -> Long = ::currentTimeMillis,
    private val newRepository: (Account, PlaylistCacheStore) -> Repository = { a, c -> Repository(a, playlistCache = c) },
    /** Zwischenspeicher je Anbieter-ID; die Apps reichen hier ihre Datei-Implementierung herein. */
    private val playlistCache: (accountId: String) -> PlaylistCacheStore = { NoPlaylistCache },
) {
    private fun repoFor(account: Account) = newRepository(account, playlistCache(accountId(account)))

    private var repo: Repository? = null

    private val stack = mutableStateListOf<Screen>(Screen.Login)
    val screen: Screen get() = stack.last()

    var tab by mutableStateOf(Tab.LIVE); private set
    /** Rohdaten je Bereich, so wie der Anbieter sie liefert. Die Oberflächen lesen [section] (sortiert, gefiltert). */
    val sections = mutableStateMapOf<Section, SectionState>()
    var favorites by mutableStateOf(store.favorites()); private set
    var history by mutableStateOf(store.history()); private set
    var query by mutableStateOf("")
    private var liveCategoryState by mutableStateOf(store.lastLiveCategory ?: LIVE_ALL)
    /** Gewählte Live-Kategorie – wird gespeichert, damit die App beim nächsten Start dort weitermacht. */
    var liveCategory: String
        get() = liveCategoryState
        set(v) { liveCategoryState = v; store.lastLiveCategory = v }

    /** Zuletzt gewählte Kategorie in Filme/Serien (Schlüssel bestimmt die jeweilige Oberfläche). */
    fun browseCategory(s: Section): String? = store.lastBrowseCategory(s)
    fun rememberBrowseCategory(s: Section, key: String) = store.saveLastBrowseCategory(s, key)

    /** Zuletzt fokussiertes Element, damit der Fokus nach dem Player an derselben Stelle steht. */
    var focusKey: String? = null

    var seasons by mutableStateOf<List<Season>>(emptyList()); private set
    var seasonsLoading by mutableStateOf(false); private set
    var seasonsError by mutableStateOf<String?>(null); private set

    /** Nach dem Laden von Live TV: zuletzt gesehenen Sender starten (Liste = alle Sender, damit Zappen geht). */
    private fun startLastChannelIfWanted() {
        if (!startChannelPending) return
        startChannelPending = false
        val last = history.firstOrNull { it.item.section == Section.LIVE }?.item ?: return
        val all = section(Section.LIVE).items
        val i = all.indexOfFirst { it.url == last.url }
        if (i >= 0 && stack.last() == Screen.Home) stack.add(Screen.Player(all, i))
    }

    // ---------- Komfort: Sortieren, Ausblenden, Start mit letztem Sender, nächste Folge, Schlaf-Timer ----------

    // Einstellungen: Compose-State fürs Neuzeichnen, der Setter speichert sofort.
    private var sortAzState by mutableStateOf(store.sortAz)
    var sortAz: Boolean get() = sortAzState; set(v) { sortAzState = v; store.sortAz = v }

    private var startLastState by mutableStateOf(store.startWithLastChannel)
    var startWithLastChannel: Boolean get() = startLastState; set(v) { startLastState = v; store.startWithLastChannel = v }

    private var autoNextState by mutableStateOf(store.autoNextEpisode)
    var autoNextEpisode: Boolean get() = autoNextState; set(v) { autoNextState = v; store.autoNextEpisode = v }

    // ---------- Updates ----------

    private var updateCheckState by mutableStateOf(store.updateCheck)
    /** Von selbst nach Updates suchen – abschaltbar in den Einstellungen. */
    var updateCheck: Boolean get() = updateCheckState; set(v) { updateCheckState = v; store.updateCheck = v }

    /** Neuere Version, falls eine gefunden wurde (Hinweis auf Startseite und in den Einstellungen). */
    var availableUpdate by mutableStateOf<LatestRelease?>(null); private set

    /**
     * Nach Updates suchen. Ohne [force] nur, wenn eingeschaltet und die letzte Suche über einen Tag her ist.
     * Liefert die neuere Version oder null.
     */
    suspend fun checkForUpdate(currentVersion: String, force: Boolean = false, http: HttpSource = PlatformHttp): LatestRelease? {
        val last = store.lastUpdateCheck
        val recent = last != 0L && clock() - last < Updates.CHECK_INTERVAL_MS // 0 = noch nie gesucht → fällig
        if (!force && (!updateCheck || recent)) return availableUpdate
        store.lastUpdateCheck = clock()
        availableUpdate = Updates.newerThan(currentVersion, http)
        return availableUpdate
    }

    /** Ton-Verzögerung in ms (Desktop-Player). */
    var audioDelayMs: Int
        get() = store.audioDelayMs
        set(v) { store.audioDelayMs = v }

    /** Bildformat im Player (fit / zoom / fill) – gilt für alle Titel. */
    var videoFit: String
        get() = store.videoFit
        set(v) { store.videoFit = v }

    /** Zuletzt gewählte Tonspur-Sprache – gilt auch für den nächsten Film. */
    var audioLanguage: String?
        get() = store.audioLanguage
        set(v) { store.audioLanguage = v }

    /** Zuletzt gewählte Untertitel-Sprache bzw. [Storage.SUBTITLES_OFF]. */
    var subtitleLanguage: String?
        get() = store.subtitleLanguage
        set(v) { store.subtitleLanguage = v }

    private var pauseDownloadsState by mutableStateOf(store.pauseDownloadsWhilePlaying)
    /** Downloads ruhen lassen, solange etwas aus dem Netz läuft (viele Anbieter erlauben nur eine Verbindung). */
    var pauseDownloadsWhilePlaying: Boolean
        get() = pauseDownloadsState
        set(v) { pauseDownloadsState = v; store.pauseDownloadsWhilePlaying = v }

    var hidden by mutableStateOf(store.hiddenCategories()); private set

    private fun hiddenKey(s: Section, categoryId: String) = "${s.name}:$categoryId"

    fun isHidden(s: Section, categoryId: String) = hiddenKey(s, categoryId) in hidden

    /** Kategorie aus- bzw. wieder einblenden. Ausgeblendete Kategorien fehlen auch in „Alle“ und in der Suche. */
    fun toggleHidden(s: Section, categoryId: String) {
        val k = hiddenKey(s, categoryId)
        hidden = if (k in hidden) hidden - k else hidden + k
        store.saveHiddenCategories(hidden)
    }

    /** Ausgeblendete Kategorien eines Bereichs (für die Einstellungen). */
    fun hiddenCategories(s: Section): List<Category> =
        sections[s]?.categories.orEmpty().filter { isHidden(s, it.id) }

    // ---------- Länderfilter (nur Live-TV) ----------

    /** Gewählte Länderkürzel des aktiven Anbieters; leer = alle Länder. */
    var liveCountries by mutableStateOf(store.liveCountries()); private set

    /** Gewählte Kürzel, die der Anbieter auch liefert. Leer = Filter aus – nie eine leere Liste wegen veralteter Auswahl. */
    val activeLiveCountries: Set<String> by derivedStateOf {
        if (liveCountries.isEmpty()) emptySet()
        else liveCountries intersect sections[Section.LIVE]?.categories.orEmpty().mapNotNullTo(HashSet()) { splitProviderTag(it.name).tag }
    }

    /** Kürzel der Live-Kategorien mit Anzahl (häufigste zuerst); gewählte, aber fehlende Kürzel mit 0 am Ende. */
    fun availableLiveCountries(): List<CountryOption> {
        val counts = sections[Section.LIVE]?.categories.orEmpty().mapNotNull { splitProviderTag(it.name).tag }.groupingBy { it }.eachCount()
        val present = counts.map { (tag, n) -> CountryOption(tag, n) }.sortedWith(compareByDescending<CountryOption> { it.count }.thenBy { it.tag })
        return present + (liveCountries - counts.keys).sorted().map { CountryOption(it, 0) }
    }

    fun toggleLiveCountry(tag: String) = saveLiveCountries(if (tag in liveCountries) liveCountries - tag else liveCountries + tag)

    fun clearLiveCountries() = saveLiveCountries(emptySet())

    private fun saveLiveCountries(tags: Set<String>) {
        liveCountries = tags
        store.saveLiveCountries(tags)
        // Ist die offene Kategorie jetzt ausgefiltert, lieber „Alle“ zeigen als eine leere Liste.
        val special = liveCategory == LIVE_ALL || liveCategory == LIVE_FAV || liveCategory == LIVE_RECENT
        if (!special && section(Section.LIVE).categories.none { it.id == liveCategory }) liveCategory = LIVE_ALL
    }

    private fun isFilteredCountry(s: Section, category: Category): Boolean {
        if (s != Section.LIVE || activeLiveCountries.isEmpty()) return false
        val tag = splitProviderTag(category.name).tag ?: return false
        return tag !in activeLiveCountries
    }

    // Gecacht je Bereich: rechnet nur neu, wenn sich Rohdaten, Sortierung, Ausblendungen oder Länderfilter ändern.
    private val views = Section.entries.associateWith { s -> derivedStateOf { applyPrefs(s, sections[s] ?: SectionState()) } }

    // Live ohne Länderfilter – für Favoriten auf Startseite/Favoriten-Tab (dort steht, was man bewusst gewählt hat).
    private val liveWithoutCountryFilter = derivedStateOf { applyPrefs(Section.LIVE, sections[Section.LIVE] ?: SectionState(), withCountryFilter = false) }

    private fun applyPrefs(s: Section, raw: SectionState, withCountryFilter: Boolean = true): SectionState {
        val countryFilter = withCountryFilter && s == Section.LIVE && activeLiveCountries.isNotEmpty()
        if (!sortAz && !countryFilter && hidden.none { it.startsWith("${s.name}:") }) return raw
        fun List<Item>.sorted() = if (sortAz) sortedBy { splitProviderTag(it.name).name.lowercase() } else this
        val visible = raw.categories.filterNot { isHidden(s, it.id) || (countryFilter && isFilteredCountry(s, it)) }
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        return raw.copy(
            categories = if (sortAz) visible.sortedBy { splitProviderTag(it.name).name.lowercase() } else visible,
            items = raw.items.filter { it.categoryId in visibleIds }.sorted(),
            byCategory = raw.byCategory.filterKeys { it in visibleIds }.mapValues { it.value.sorted() },
        )
    }

    /** Bereich so, wie die Oberfläche ihn zeigt: ausgeblendete Kategorien entfernt, ggf. A–Z sortiert. */
    fun section(s: Section): SectionState = views.getValue(s).value

    /** Schlaf-Timer: Zeitpunkt (ms), zu dem die Wiedergabe stoppt; null = aus. */
    var sleepAt by mutableStateOf<Long?>(null); private set

    /** Schaltet aus → 30 → 60 → 90 Minuten → aus. */
    fun cycleSleepTimer() {
        val remaining = sleepMinutesLeft()
        val next = SLEEP_STEPS.firstOrNull { it > (remaining ?: 0) + 1 }
        sleepAt = next?.let { clock() + it * 60_000L }
    }

    /** Verbleibende Minuten (aufgerundet) oder null, wenn kein Timer läuft. */
    fun sleepMinutesLeft(): Int? = sleepAt?.let { ((it - clock() + 59_999) / 60_000).toInt().coerceAtLeast(0) }

    /** True, wenn der Timer abgelaufen ist – setzt ihn dann zurück (der Player beendet die Wiedergabe). */
    fun consumeSleepTimer(): Boolean {
        val at = sleepAt ?: return false
        if (clock() < at) return false
        sleepAt = null
        return true
    }

    /** Index der nächsten Folge in der Liste des Players, sofern es eine gibt. */
    fun nextEpisodeIndex(items: List<Item>, index: Int): Int? =
        if (items.getOrNull(index)?.section == Section.SERIES && index < items.lastIndex) index + 1 else null

    private var startChannelPending = false

    // ---------- Ladebildschirm beim Start ----------

    /**
     * False, solange nach Anmeldung/Start (oder „Listen neu laden“) die Daten für den aktuellen Bereich
     * noch laden – die Oberflächen zeigen dann den Ladebildschirm „Playlist wird geladen“.
     */
    var startupDone by mutableStateOf(true); private set

    private fun sectionsNeededFor(t: Tab): List<Section> = when (t) {
        Tab.LIVE -> listOf(Section.LIVE)
        Tab.MOVIES -> listOf(Section.MOVIES)
        Tab.SERIES -> listOf(Section.SERIES)
        Tab.SEARCH, Tab.FAVORITES, Tab.HOME -> Section.entries
        Tab.SETTINGS, Tab.DOWNLOADS -> emptyList()
    }

    private fun checkStartupDone() {
        // Mit anstehender Übersicht wartet der Ladebildschirm auf alle Bereiche – die Übersicht zeigt alle drei.
        val needed = if (overviewPending) Section.entries else sectionsNeededFor(tab)
        if (!startupDone && needed.all { sections[it]?.loading == false }) startupDone = true
        if (overviewPending && Section.entries.all { sections[it]?.loading == false }) {
            overviewPending = false
            overview = buildOverview()
        }
    }

    /**
     * Alle Bereiche parallel laden und den Ladebildschirm zeigen, danach die Übersicht. Aus dem Zwischenspeicher nur,
     * wenn alle Bereiche frisch (< 24 h) und beim letzten Mal fehlerfrei waren – sonst alles aus dem Netz.
     */
    private fun startLoading(t: Tab, forceNetwork: Boolean = false) {
        startupDone = false
        overview = null
        overviewPending = true
        sectionWarnings.clear()
        loadFromCache = !forceNetwork && Section.entries.all { isFresh(store.cacheMeta(it)) }
        selectTab(t)
        Section.entries.forEach(::ensureLoaded)
        checkStartupDone()
    }

    // ---------- Übersicht nach dem Laden ----------

    /** Nicht null, solange die Übersicht nach dem Laden angezeigt wird (bis „Weiter“). */
    var overview by mutableStateOf<List<OverviewRow>?>(null); private set
    private var overviewPending = false
    private var loadFromCache = false
    private val sectionWarnings = HashMap<Section, OverviewWarning>()

    private fun isFresh(meta: CacheMeta?): Boolean =
        meta != null && !meta.failed && (clock() - meta.loadedAt) in 0 until CACHE_MAX_AGE_MS

    private fun buildOverview(): List<OverviewRow> = Section.entries.map { s ->
        val state = sections[s]
        val meta = store.cacheMeta(s)
        val count = state?.items?.size ?: 0
        val previous = meta?.previousCount
        val warning = sectionWarnings[s]
            ?: if (state?.error != null) OverviewWarning.FAILED
            else if (previous != null && count < previous * 0.9) OverviewWarning.FEWER
            else null
        OverviewRow(s, count, previous?.let { count - it }, warning, meta?.loadedAt)
    }

    /** „Weiter“: Übersicht schließen; erst jetzt ggf. den zuletzt gesehenen Sender starten. */
    fun continueFromOverview() {
        overview = null
        startLastChannelIfWanted()
    }

    /** „Neu laden“: alle Listen frisch aus dem Netz, danach wieder die Übersicht. */
    fun reloadFromNetwork() = restartLoading(forceNetwork = true)

    /** Rückkehr in die App (Android TV): nach mehr als einer Stunde im Hintergrund wie ein neuer Start behandeln. */
    fun onAppResumed(backgroundMillis: Long) {
        if (backgroundMillis <= RESUME_RESTART_MS || repo == null) return
        stack.clear(); stack.add(Screen.Home)
        startChannelPending = store.startWithLastChannel
        restartLoading(forceNetwork = false)
    }

    private fun restartLoading(forceNetwork: Boolean) {
        val acc = repo?.account ?: return
        repo = repoFor(acc)
        sections.clear()
        epgCache.clear()
        startLoading(tab, forceNetwork)
    }

    // ---------- „Jetzt läuft“: Programmdaten je Sender, zwischengespeichert ----------

    private class EpgCacheEntry(val entries: List<EpgEntry>, val fetchedAt: Long)
    private val epgCache = mutableStateMapOf<String, EpgCacheEntry>()
    private val epgPending = HashSet<String>()

    /** Laufende Sendung aus dem Zwischenspeicher (oder null, solange nichts geladen ist). */
    fun nowFor(item: Item): EpgEntry? = epgCache[item.url]?.entries?.firstOrNull()

    /** Programmdaten für einen sichtbaren Sender anfordern – lädt nur, wenn nichts Frisches da ist. */
    fun requestEpg(item: Item) {
        if (item.section != Section.LIVE || item.url in epgPending || isFresh(item)) return
        epgPending += item.url
        scope.launch {
            try { fetchEpg(item) } finally { epgPending -= item.url }
        }
    }

    private fun isFresh(item: Item) = epgCache[item.url]?.let { clock() - it.fetchedAt < EPG_TTL_MS } == true

    private suspend fun fetchEpg(item: Item): List<EpgEntry> {
        val entries = repo?.nowPlaying(item).orEmpty()
        epgCache[item.url] = EpgCacheEntry(entries, clock())
        return entries
    }

    // Steht bewusst hinter allen Feldern: init läuft in Deklarationsreihenfolge, und das Laden
    // (in Tests sofort, Dispatchers.Unconfined) braucht views, sortAz, hidden & Co. bereits.
    init {
        store.loadAccount()?.let {
            repo = repoFor(it)
            stack.clear(); stack.add(Screen.Home)
            startChannelPending = store.startWithLastChannel
            // Dort weitermachen, wo man war – aber nicht in Suche/Einstellungen.
            startLoading(lastTabOrLive())
        }
    }


    val accountLabel: String
        get() = repo?.account?.let(::labelOf).orEmpty()

    // ---------- Anbieter: anmelden, hinzufügen, wechseln, entfernen ----------

    /** Alle gespeicherten Anbieter (für die Einstellungen). */
    var accounts by mutableStateOf(store.accounts()); private set
    var activeAccountId by mutableStateOf(store.activeAccountId); private set

    /** Anmeldebildschirm über der Startseite („Anbieter hinzufügen“) – dann gibt es ein Zurück. */
    val canCancelLogin: Boolean get() = screen == Screen.Login && stack.size > 1

    suspend fun login(account: Account) {
        val r = repoFor(account)
        r.login()
        store.saveAccount(r.account) // mit der erreichten Adresse, z. B. https://…
        activate(r, Tab.LIVE)
    }

    /** Weiteren Anbieter hinzufügen: Anmeldung öffnen, Zurück führt zur Startseite. */
    fun addAccount() {
        if (screen != Screen.Login) stack.add(Screen.Login)
    }

    /** Zu einem gespeicherten Anbieter wechseln – mit seinen Favoriten, seinem Verlauf und seinen Listen. */
    fun switchAccount(id: String) {
        if (id == activeAccountId && repo != null) return
        val target = store.accounts().firstOrNull { it.id == id } ?: return
        store.activeAccountId = id
        activate(repoFor(target.account), lastTabOrLive())
    }

    /** Aktiven Anbieter entfernen (samt seiner Daten). Gibt es weitere, geht es mit dem nächsten weiter. */
    fun logout() {
        repo?.clearCache()
        activeAccountId?.let(store::removeAccount)
        val next = store.loadAccount()
        if (next != null) { activate(repoFor(next), lastTabOrLive()); return }
        repo = null
        reloadAccountData()
        sections.clear()
        query = ""
        stack.clear(); stack.add(Screen.Login)
    }

    private fun activate(r: Repository, tab: Tab) {
        repo = r
        reloadAccountData()
        sections.clear()
        epgCache.clear()
        query = ""
        stack.clear(); stack.add(Screen.Home)
        startLoading(tab)
    }

    /** Daten des (neuen) aktiven Anbieters aus dem Speicher holen. */
    private fun reloadAccountData() {
        accounts = store.accounts()
        activeAccountId = store.activeAccountId
        favorites = store.favorites()
        history = store.history()
        hidden = store.hiddenCategories()
        liveCountries = store.liveCountries()
        liveCategoryState = store.lastLiveCategory ?: LIVE_ALL
        focusKey = null
    }

    /** Dort weitermachen, wo man war – aber nicht in Suche/Einstellungen; beim ersten Start die Startseite. */
    private fun lastTabOrLive(): Tab {
        val last = Tab.entries.firstOrNull { t -> t.name == store.lastTab }
        return if (last == null || last == Tab.SEARCH || last == Tab.SETTINGS) Tab.HOME else last
    }

    // ---------- Startseite ----------

    /** Angefangene Filme und Folgen, zuletzt gesehen zuerst. */
    val continueWatchingAll: List<HistoryEntry> get() = history.filter { it.item.section != Section.LIVE && it.canResume }

    /** Zuletzt gesehene Sender. */
    val recentChannels: List<Item> get() = history.filter { it.item.section == Section.LIVE }.map { it.item }


    // ---------- Navigation und Laden ----------

    fun selectTab(t: Tab) {
        tab = t
        store.lastTab = t.name
        focusKey = null
        when (t) {
            Tab.LIVE -> ensureLoaded(Section.LIVE)
            Tab.MOVIES -> ensureLoaded(Section.MOVIES)
            Tab.SERIES -> ensureLoaded(Section.SERIES)
            Tab.SEARCH, Tab.FAVORITES, Tab.HOME -> Section.entries.forEach(::ensureLoaded)
            Tab.SETTINGS, Tab.DOWNLOADS -> Unit
        }
    }

    fun ensureLoaded(s: Section) {
        val st = sections[s]
        if (st != null && (st.loading || st.error == null)) return
        load(s)
    }

    /** „Listen neu laden“ (Einstellungen, Fehlerbildschirm): wie „Neu laden“ in der Übersicht. */
    fun reloadAll() = reloadFromNetwork()

    /**
     * Einen Bereich laden und den Stand des Zwischenspeichers fortschreiben. Scheitert das Netz oder liefert es eine
     * leere Liste, obwohl es zuletzt Einträge gab, kommt der alte Stand aus dem Zwischenspeicher (mit Warnung).
     */
    private suspend fun loadSection(r: Repository, s: Section): SectionLoad {
        val before = store.cacheMeta(s)
        val hadEntries = (before?.count ?: 0) > 0
        val result = try {
            r.load(s, fromCache = loadFromCache)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!hadEntries) throw e
            val old = r.load(s, fromCache = true).takeIf { it.fromCache } ?: throw e
            sectionWarnings[s] = OverviewWarning.FAILED
            store.saveCacheMeta(s, before!!.copy(failed = true))
            return old
        }
        if (result.fromCache) return result
        if (result.items.isEmpty() && hadEntries) {
            val old = r.load(s, fromCache = true).takeIf { it.fromCache }
            if (old != null) {
                sectionWarnings[s] = OverviewWarning.EMPTY
                store.saveCacheMeta(s, before!!.copy(failed = true))
                return old
            }
        }
        store.saveCacheMeta(s, CacheMeta(clock(), result.items.size, before?.count, failed = false))
        return result
    }

    private fun load(s: Section) {
        val r = repo ?: return
        sections[s] = SectionState(loading = true)
        scope.launch {
            try {
                val (cats, items) = loadSection(r, s)
                val byCategory = items.groupBy { it.categoryId }
                val known = cats.distinctBy { it.id }.filter { byCategory.containsKey(it.id) }
                val knownIds = known.mapTo(HashSet()) { it.id }
                val orphans = byCategory.keys.filter { it !in knownIds }.map { Category(it, "Sonstige") }
                sections[s] = SectionState(categories = known + orphans, items = items, byCategory = byCategory)
                if (s == Section.LIVE) {
                    // Gemerkte Kategorie gibt es nicht mehr (oder sie ist ausgeblendet) → „Alle Sender“.
                    val special = liveCategory == LIVE_ALL || liveCategory == LIVE_FAV || liveCategory == LIVE_RECENT
                    if (!special && section(Section.LIVE).categories.none { it.id == liveCategory }) liveCategory = LIVE_ALL
                    if (!overviewPending && overview == null) startLastChannelIfWanted()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sections[s] = SectionState(error = e.message ?: "Unbekannter Fehler")
            } finally {
                checkStartupDone()
            }
        }
    }

    // ---------- Listen ----------

    val liveChannels: List<Item> by derivedStateOf { liveChannelsOf(liveCategory) }

    /** Sender einer Live-Kategorie (auch „Alle“, „Favoriten“, „Zuletzt gesehen“) – z. B. für die Senderliste im Player. */
    fun liveChannelsOf(categoryId: String): List<Item> {
        val s = section(Section.LIVE)
        return when (categoryId) {
            LIVE_ALL -> s.items
            LIVE_FAV -> s.items.filter { it.favKey in favorites }
            LIVE_RECENT -> history.filter { it.item.section == Section.LIVE }.map { it.item }
            else -> s.byCategory[categoryId].orEmpty()
        }
    }

    fun continueWatching(s: Section): List<HistoryEntry> = history.filter { it.item.section == s && it.canResume }

    /** Favoriten für Startseite und Favoriten-Tab: ohne Länderfilter (Ausblenden gilt weiter). */
    fun favoriteItems(s: Section): List<Item> =
        (if (s == Section.LIVE) liveWithoutCountryFilter.value else section(s)).items.filter { it.favKey in favorites }

    val searchResults: Map<Section, List<Item>> by derivedStateOf {
        val q = query.trim()
        if (q.length < 2) emptyMap()
        else Section.entries
            .associateWith { s -> section(s).items.asSequence().filter { it.name.contains(q, ignoreCase = true) }.take(60).toList() }
            .filterValues { it.isNotEmpty() }
    }

    fun progressOf(item: Item): Float =
        history.firstOrNull { it.item.url == item.url && it.canResume }?.progress ?: 0f

    // ---------- Favoriten und Verlauf ----------

    fun toggleFavorite(item: Item) {
        favorites = if (item.favKey in favorites) favorites - item.favKey else favorites + item.favKey
        store.saveFavorites(favorites)
    }

    fun saveProgress(item: Item, position: Long, duration: Long) {
        if (item.url.isEmpty()) return
        val live = item.section == Section.LIVE
        val finished = !live && duration > 0 && position > duration * 0.95
        val entry = HistoryEntry(
            item,
            if (live || finished) 0 else position.coerceAtLeast(0),
            if (live) 0 else duration.coerceAtLeast(0),
            clock(),
        )
        history = (listOf(entry) + history.filterNot { it.item.url == item.url }).take(50)
        store.saveHistory(history)
    }

    fun clearHistory() {
        history = emptyList()
        store.saveHistory(emptyList())
    }

    var volume: Int
        get() = store.volume
        set(v) { store.volume = v }

    // ---------- Öffnen und Abspielen ----------

    fun open(list: List<Item>, index: Int) {
        val item = list.getOrNull(index) ?: return
        if (item.isSeriesFolder) {
            openSeries(item)
        } else {
            val start = history.firstOrNull { it.item.url == item.url && it.canResume }?.position ?: 0L
            stack.add(Screen.Player(list, index, start))
        }
    }

    private fun openSeries(series: Item) {
        stack.add(Screen.Series(series))
        seasons = emptyList(); seasonsError = null; seasonsLoading = true
        scope.launch {
            try {
                seasons = repo?.seasons(series).orEmpty()
                if (seasons.isEmpty()) seasonsError = "Für diese Serie sind keine Folgen verfügbar."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                seasonsError = e.message ?: "Folgen konnten nicht geladen werden"
            } finally {
                seasonsLoading = false
            }
        }
    }

    /** Letzte angesehene Folge einer Serie (für "Weiterschauen" in der Serienansicht). */
    fun lastEpisodeOf(series: Item): HistoryEntry? =
        history.firstOrNull { it.item.section == Section.SERIES && it.item.categoryId == series.id }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Programm eines Senders (für Info-Spalte und Player) – nutzt den Zwischenspeicher, wenn er frisch ist. */
    suspend fun nowPlaying(item: Item): List<EpgEntry> =
        if (isFresh(item)) epgCache.getValue(item.url).entries else fetchEpg(item)

    /** Beim Beenden aufrufen, damit laufende Ladevorgänge abgebrochen werden. */
    fun close() = scope.cancel()

    companion object {
        const val LIVE_ALL = "__all"
        /** Zwischenspeicher gilt 24 Stunden. */
        const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        /** Android TV: Rückkehr nach mehr als einer Stunde im Hintergrund zählt als neuer Start. */
        const val RESUME_RESTART_MS = 60 * 60 * 1000L
        const val LIVE_FAV = "__fav"
        const val LIVE_RECENT = "__recent"
        val SLEEP_STEPS = listOf(30, 60, 90)
        const val EPG_TTL_MS = 10 * 60_000L
    }
}
