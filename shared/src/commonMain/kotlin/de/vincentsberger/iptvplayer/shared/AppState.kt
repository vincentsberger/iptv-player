package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.isSeparatorEntry
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
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.data.AccountStatus
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.PlaylistCacheStore
import de.vincentsberger.iptvplayer.shared.data.SectionLoad
import de.vincentsberger.iptvplayer.shared.data.accountId
import de.vincentsberger.iptvplayer.shared.data.CountryOption
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
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
import de.vincentsberger.iptvplayer.shared.data.localDateTime
import de.vincentsberger.iptvplayer.shared.data.notEndedAt
import de.vincentsberger.iptvplayer.shared.data.hostOf
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Series(val series: Item) : Screen
    /** Film-Detailseite: erst entscheiden, dann abspielen. */
    data class Movie(val movie: Item) : Screen
    data class Player(val items: List<Item>, val index: Int, val startPosition: Long = 0) : Screen
}

/** Navigationstiefe: Übergänge gleiten vorwärts (tiefer) von rechts herein, zurück von links. */
val Screen.depth: Int
    get() = when (this) {
        Screen.Login -> 0
        Screen.Home -> 1
        is Screen.Series, is Screen.Movie -> 2
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
    /** Ortszeit zu einem Zeitpunkt (EPG: was ist schon vorbei?); Tests setzen sie fest. */
    private val localTime: (Long) -> String = ::localDateTime,
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

    /** Zuletzt gesuchte Begriffe des Anbieters (neueste zuerst, höchstens [MAX_RECENT_SEARCHES]) – als Chips im leeren Suchfeld. */
    var recentSearches by mutableStateOf(store.recentSearches()); private set

    /** Aktuellen Suchbegriff merken (Suche abgeschickt oder Treffer geöffnet); doppelte – auch anders geschrieben – nur einmal. */
    fun rememberSearch(term: String = query) {
        val t = term.trim()
        if (t.length < 2) return
        recentSearches = (listOf(t) + recentSearches.filterNot { it.equals(t, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        store.saveRecentSearches(recentSearches)
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
        store.saveRecentSearches(emptyList())
    }
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

    private var playerLandscapeState by mutableStateOf(store.playerLandscape)
    /** iPhone: Player immer im Querformat öffnen (auch bei Dreh-Sperre), danach zurück ins Hochformat. */
    var playerLandscape: Boolean get() = playerLandscapeState; set(v) { playerLandscapeState = v; store.playerLandscape = v }

    private var sortByYearState by mutableStateOf(store.sortByYear)
    /** Filme und Serien nach Jahr im Titel, neueste zuerst (ohne Jahr ans Ende); bei Gleichstand gilt A–Z bzw. Anbieter. */
    var sortByYear: Boolean get() = sortByYearState; set(v) { sortByYearState = v; store.sortByYear = v }

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
     * Liefert die neuere Version oder null. [platform]: Version der eigenen Release-Datei zählt (siehe [Updates.parse]).
     */
    suspend fun checkForUpdate(
        currentVersion: String, platform: UpdatePlatform? = null, force: Boolean = false, http: HttpSource = PlatformHttp,
    ): LatestRelease? {
        val last = store.lastUpdateCheck
        val recent = last != 0L && clock() - last < Updates.CHECK_INTERVAL_MS // 0 = noch nie gesucht → fällig
        if (!force && (!updateCheck || recent)) return availableUpdate
        store.lastUpdateCheck = clock()
        availableUpdate = Updates.newerThan(currentVersion, platform, http)
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

    // ---------- Länderfilter (Live TV, Filme, Serien) ----------

    /** Gewählte Länderkürzel je Bereich des aktiven Anbieters; leer = alle Länder. */
    private val countriesState = mutableStateMapOf<Section, Set<String>>().apply { Section.entries.forEach { put(it, store.countries(it)) } }

    fun countries(s: Section): Set<String> = countriesState[s].orEmpty()

    /** Gewählte Kürzel, die der Anbieter auch liefert. Leer = Filter aus – nie eine leere Liste wegen veralteter Auswahl. */
    /**
     * Länderkürzel je Film/Serie (Schlüssel [Item.favKey]): zuerst aus dem Titel („EN - …“, „DE - …“), sonst aus der
     * Kategorie. Viele Anbieter kennzeichnen Filme und Serien nur im Titel – Kategorien wie „NETFLIX MOVIES“ haben
     * keins. Live TV filtert weiter nach Kategorie („DE | Sport“). Gecacht: bei 140 000 Filmen nicht je Zeichnen neu.
     */
    private val itemTags = Section.entries.associateWith { s ->
        derivedStateOf {
            val raw = sections[s]
            if (s == Section.LIVE || raw == null) emptyMap()
            else {
                val categoryTag = raw.categories.associate { it.id to splitProviderTag(it.name).tag }
                raw.items.mapNotNull { item -> (splitProviderTag(item.name).tag ?: categoryTag[item.categoryId])?.let { item.favKey to it } }.toMap()
            }
        }
    }

    private fun tagsIn(s: Section): Set<String> =
        if (s == Section.LIVE) sections[s]?.categories.orEmpty().mapNotNullTo(HashSet()) { splitProviderTag(it.name).tag }
        else itemTags.getValue(s).value.values.toHashSet()

    private val activeCountriesState = Section.entries.associateWith { s ->
        derivedStateOf {
            val chosen = countries(s)
            if (chosen.isEmpty()) emptySet() else chosen intersect tagsIn(s)
        }
    }

    fun activeCountries(s: Section): Set<String> = activeCountriesState.getValue(s).value

    /**
     * Wählbare Kürzel mit Anzahl (häufigste zuerst); gewählte, aber fehlende Kürzel mit 0 am Ende.
     * Live TV zählt Kategorien, Filme und Serien zählen Titel.
     */
    fun availableCountries(s: Section): List<CountryOption> {
        val counts = if (s == Section.LIVE) sections[s]?.categories.orEmpty().mapNotNull { splitProviderTag(it.name).tag }.groupingBy { it }.eachCount()
        else itemTags.getValue(s).value.values.groupingBy { it }.eachCount()
        val present = counts.map { (tag, n) -> CountryOption(tag, n) }.sortedWith(compareByDescending<CountryOption> { it.count }.thenBy { it.tag })
        return present + (countries(s) - counts.keys).sorted().map { CountryOption(it, 0) }
    }

    fun toggleCountry(s: Section, tag: String) = saveCountries(s, countries(s).let { if (tag in it) it - tag else it + tag })

    fun clearCountries(s: Section) = saveCountries(s, emptySet())

    private fun saveCountries(s: Section, tags: Set<String>) {
        countriesState[s] = tags
        store.saveCountries(s, tags)
        // Ist die offene Live-Kategorie jetzt ausgefiltert, lieber „Alle“ zeigen als eine leere Liste.
        if (s != Section.LIVE) return
        val special = liveCategory == LIVE_ALL || liveCategory == LIVE_FAV || liveCategory == LIVE_RECENT
        if (!special && section(Section.LIVE).categories.none { it.id == liveCategory }) liveCategory = LIVE_ALL
    }

    // Kurzformen für Live TV (so von allen Apps und Tests genutzt).
    val liveCountries: Set<String> get() = countries(Section.LIVE)
    val activeLiveCountries: Set<String> get() = activeCountries(Section.LIVE)
    fun availableLiveCountries(): List<CountryOption> = availableCountries(Section.LIVE)
    fun toggleLiveCountry(tag: String) = toggleCountry(Section.LIVE, tag)
    fun clearLiveCountries() = clearCountries(Section.LIVE)

    /** Nur Live TV: ganze Kategorien nach ihrem Kürzel (Filme/Serien filtern Titel, siehe [applyPrefs]). */
    private fun isFilteredCountry(s: Section, category: Category): Boolean {
        if (s != Section.LIVE) return false
        val active = activeCountries(s)
        if (active.isEmpty()) return false
        val tag = splitProviderTag(category.name).tag ?: return false
        return tag !in active
    }

    // Gecacht je Bereich: rechnet nur neu, wenn sich Rohdaten, Sortierung, Ausblendungen oder Länderfilter ändern.
    private val views = Section.entries.associateWith { s -> derivedStateOf { applyPrefs(s, sections[s] ?: SectionState()) } }

    // Ohne Länderfilter – für Favoriten auf Startseite/Favoriten-Tab (dort steht, was man bewusst gewählt hat).
    private val withoutCountryFilter = Section.entries.associateWith { s ->
        derivedStateOf { applyPrefs(s, sections[s] ?: SectionState(), withCountryFilter = false) }
    }

    private fun applyPrefs(s: Section, raw: SectionState, withCountryFilter: Boolean = true): SectionState {
        val countryFilter = withCountryFilter && activeCountries(s).isNotEmpty()
        val byYear = sortByYear && s != Section.LIVE
        if (!sortAz && !byYear && !countryFilter && hidden.none { it.startsWith("${s.name}:") }) return raw
        fun List<Item>.sorted(): List<Item> {
            val az = if (sortAz) sortedBy { splitProviderTag(it.name).name.lowercase() } else this
            return if (byYear) az.sortedByDescending { splitProviderTag(it.name).year?.toIntOrNull() ?: 0 } else az
        }
        val visible = raw.categories.filterNot { isHidden(s, it.id) || (countryFilter && isFilteredCountry(s, it)) }
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        // Filme/Serien: einzelne Titel nach ihrem Kürzel; ohne Kürzel bleiben sie sichtbar (wie Kategorien ohne Kürzel).
        val titleFilter = countryFilter && s != Section.LIVE
        val active = activeCountries(s)
        val tags = if (titleFilter) itemTags.getValue(s).value else emptyMap()
        fun keep(item: Item) = item.categoryId in visibleIds && (!titleFilter || tags[item.favKey].let { it == null || it in active })
        val byCategory = if (titleFilter) raw.items.filter(::keep).groupBy { it.categoryId }
        else raw.byCategory.filterKeys { it in visibleIds }
        val categories = if (titleFilter) visible.filter { byCategory.containsKey(it.id) } else visible
        return raw.copy(
            categories = if (sortAz) categories.sortedBy { splitProviderTag(it.name).name.lowercase() } else categories,
            items = raw.items.filter(::keep).sorted(),
            byCategory = byCategory.mapValues { it.value.sorted() },
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
        refreshAccountStatus()
        selectTab(t)
        Section.entries.forEach(::ensureLoaded)
        checkStartupDone()
    }

    // ---------- Pause-Info: Infos zu Film bzw. Serie ----------

    /** Geladene Infos je Film („MOVIES:id“) bzw. Serie („SERIES:id“); null = Anbieter liefert keine. */
    private val mediaInfos = mutableStateMapOf<String, MediaInfo?>()
    /** Staffeln von Serien, deren Folgen direkt geöffnet wurden (z. B. aus „Weiterschauen“) – für das Folgen-Label. */
    private val episodeSeasons = mutableStateMapOf<String, List<Season>>()

    private fun mediaKey(item: Item): String? = when {
        item.section == Section.MOVIES -> "MOVIES:${item.id}"
        item.section == Section.SERIES && !item.isSeriesFolder -> "SERIES:${item.categoryId}" // Folge → ihre Serie
        else -> null
    }

    fun mediaInfoFor(item: Item): MediaInfo? = mediaKey(item)?.let { mediaInfos[it] }

    /** Handlung und Dauer einer Folge aus den geladenen Staffeln (Folgenliste der Serienseite). */
    fun episodeDetail(episode: Item): EpisodeDetail? = seasons.firstNotNullOfOrNull { it.details[episode.url] }

    /** Infos (Beschreibung, Jahr, Genre …) einer Serie für die Serienseite – kommen mit den Staffeln. */
    fun seriesInfoFor(series: Item): MediaInfo? = mediaInfos["SERIES:${series.id}"]

    /** Beim Öffnen des Players im Hintergrund laden, damit die Info beim Pausieren schon da ist. */
    fun requestMediaInfo(item: Item) {
        val key = mediaKey(item) ?: return
        if (key in mediaInfos) return
        val r = repo ?: return
        scope.launch {
            val info = if (item.section == Section.MOVIES) r.vodInfo(item) else {
                val series = sections[Section.SERIES]?.items?.firstOrNull { it.id == item.categoryId && it.isSeriesFolder }
                    ?: Item(item.categoryId, "", null, "", "", Section.SERIES)
                r.seriesInfo(series).also { r.cachedSeasons(item.categoryId)?.let { s -> if (repo === r) episodeSeasons[item.categoryId] = s } }
            }
            if (repo === r) mediaInfos[key] = info
        }
    }

    /** „S2 · F3 · Der Zug“ – aus den geladenen Staffeln; null, wenn die Folge dort nicht steht. */
    fun episodeLabel(item: Item): String? {
        (seasons + episodeSeasons[item.categoryId].orEmpty()).forEach { season ->
            val i = season.episodes.indexOfFirst { it.url == item.url }
            if (i >= 0) return "S${season.number} · F${i + 1} · ${item.name}"
        }
        return null
    }

    /**
     * Titel und Untertitel der Pause-Info: Filme ihr Name; Folgen der Serienname mit „S2 · F3 · Titel“ – ist die
     * Folge in keiner geladenen Staffel zu finden, wenigstens ihr eigener Titel.
     */
    fun pauseTitles(item: Item): Pair<String, String?> {
        if (item.section != Section.SERIES) return item.name to null
        val series = seriesNameOf(item) ?: return item.name to episodeLabel(item)
        return series to (episodeLabel(item) ?: item.name)
    }

    /** Name der Serie zu einer Folge (über die Serien-ID der Folge). */
    fun seriesNameOf(item: Item): String? =
        sections[Section.SERIES]?.items?.firstOrNull { it.id == item.categoryId && it.isSeriesFolder }?.name

    // ---------- Startseite: Neues aus gewählten Kategorien, Abo ----------

    /** Neuheiten und Abo-Daten gibt es nur bei Xtream (M3U kennt kein Hinzugefügt-Datum). */
    val supportsDashboard: Boolean get() = repo?.account is Account.Xtream

    var dashboardCategories by mutableStateOf(store.dashboardCategories()); private set

    val dashboardConfigured: Boolean get() = dashboardCategories.isNotEmpty()

    fun toggleDashboardCategory(s: Section, categoryId: String) {
        val key = "${s.name}:$categoryId"
        dashboardCategories = if (key in dashboardCategories) dashboardCategories - key else dashboardCategories + key
        store.saveDashboardCategories(dashboardCategories)
    }

    /** Wählbare Kategorien (Filme/Serien) in Anbieter-Reihenfolge – ohne Erwachsenen-Kategorien. */
    fun dashboardCategoryOptions(s: Section): List<Category> =
        sections[s]?.categories.orEmpty().filterNot { isAdultCategory(it) }

    private fun isAdultCategory(c: Category) = c.name.contains("ADULT", ignoreCase = true)

    val newMovies: List<Item> by derivedStateOf { newItems(Section.MOVIES) }
    val newEpisodes: List<Item> by derivedStateOf { newItems(Section.SERIES) }

    /** Einträge der gewählten Kategorien, die in den letzten 7 Tagen hinzukamen – neueste zuerst. */
    private fun newItems(s: Section): List<Item> {
        val chosen = dashboardCategories.mapNotNullTo(HashSet()) { it.removePrefix("${s.name}:").takeIf { id -> id != it } }
        if (chosen.isEmpty()) return emptyList()
        val view = section(s)
        val adultCats = view.categories.filter(::isAdultCategory).mapTo(HashSet()) { it.id }
        val since = clock() - NEW_WINDOW_MS
        return chosen.flatMap { view.byCategory[it].orEmpty() }
            // Keine Obergrenze: Geht die Uhr des Anbieters oder der TV-Box vor, liegt „gerade hinzugefügt“ in der Zukunft.
            .filter { !it.isAdult && it.categoryId !in adultCats && (it.addedAt ?: 0) >= since }
            .distinctBy { it.favKey }
            .sortedByDescending { it.addedAt }
    }

    /** „Alle anzeigen“: Filme/Serien mit der Kategorie „Neu (7 Tage)“ öffnen. */
    fun showNewInBrowse(s: Section) {
        rememberBrowseCategory(s, BROWSE_NEW)
        selectTab(if (s == Section.SERIES) Tab.SERIES else Tab.MOVIES)
    }

    /** Neue Einträge für die Kategorie „Neu (7 Tage)“ in Filme/Serien. */
    fun newItemsOf(s: Section): List<Item> = if (s == Section.SERIES) newEpisodes else newMovies

    // ---------- Kataloge Filme/Serien ----------

    /** Kategorien in Filme/Serien: Weiterschauen und Neu (nur wenn es etwas gibt), Favoriten, Alle, dann die des Anbieters. */
    fun browseCategories(s: Section): List<Category> = buildList {
        if (continueWatching(s).isNotEmpty()) add(Category(BROWSE_CONTINUE, "Weiterschauen"))
        if (newItemsOf(s).isNotEmpty()) add(Category(BROWSE_NEW, "Neu (7 Tage)"))
        add(Category(BROWSE_FAV, "Favoriten"))
        add(Category(BROWSE_ALL, "Alle ${s.title}"))
        addAll(section(s).categories)
    }

    /** Inhalt einer Kategorie aus [browseCategories]. */
    fun browseItems(s: Section, key: String): List<Item> = when (key) {
        BROWSE_CONTINUE -> continueWatching(s).map { it.item }
        BROWSE_FAV -> favoriteItems(s)
        BROWSE_NEW -> newItemsOf(s)
        BROWSE_ALL -> section(s).items
        else -> section(s).byCategory[key].orEmpty()
    }

    /** Startkategorie: die gemerkte, sofern es sie noch gibt; sonst Weiterschauen, die erste des Anbieters oder Alle. */
    fun initialBrowseCategory(s: Section): String {
        val cats = browseCategories(s)
        browseCategory(s)?.takeIf { k -> cats.any { it.id == k } }?.let { return it }
        return when {
            cats.any { it.id == BROWSE_CONTINUE } -> BROWSE_CONTINUE
            else -> section(s).categories.firstOrNull()?.id ?: BROWSE_ALL
        }
    }

    /** Abo-Daten (zuletzt erfolgreich abgerufen); [accountStatusStale] = der Abruf bei diesem Start scheiterte. */
    var accountStatus by mutableStateOf(store.accountStatus()); private set
    var accountStatusStale by mutableStateOf(false); private set

    fun accountExpiresSoon(): Boolean =
        accountStatus?.info?.expiresAt?.let { it - clock() <= EXPIRY_WARNING_MS } == true

    private fun refreshAccountStatus() {
        val r = repo ?: return
        if (r.account !is Account.Xtream) return
        accountStatusStale = false
        scope.launch {
            try {
                val info = r.accountInfo() ?: return@launch
                if (repo !== r) return@launch
                accountStatus = AccountStatus(info, clock()).also(store::saveAccountStatus)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (repo === r) accountStatusStale = true // Start läuft trotzdem weiter
            }
        }
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
    fun nowFor(item: Item): EpgEntry? = epgCache[item.url]?.entries?.notEndedAt(localTime(clock()))?.firstOrNull()

    /** Programmdaten für einen sichtbaren Sender anfordern – lädt nur, wenn nichts Frisches da ist. */
    fun requestEpg(item: Item) {
        // Ohne EPG-Kennung liefert der Anbieter nie Programmdaten – nicht fragen (spart beim Scrollen Tausende Anfragen).
        if (item.section != Section.LIVE || !item.hasEpg || item.url in epgPending || isFresh(item)) return
        epgPending += item.url
        scope.launch {
            try { fetchEpg(item) } finally { epgPending -= item.url }
        }
    }

    /**
     * Gespeichertes Programm reicht noch: jünger als [EPG_MIN_REFRESH_MS], oder jünger als [EPG_MAX_AGE_MS] und es
     * sind noch „Jetzt“ und „Danach“ übrig (bzw. so viele, wie der Anbieter überhaupt geliefert hat – Sender ohne EPG
     * werden so nur alle 3 Stunden gefragt).
     */
    private fun isFresh(item: Item): Boolean {
        val cached = epgCache[item.url] ?: return false
        val age = clock() - cached.fetchedAt
        if (age < EPG_MIN_REFRESH_MS) return true
        if (age >= EPG_MAX_AGE_MS) return false
        return cached.entries.notEndedAt(localTime(clock())).size >= minOf(2, cached.entries.size)
    }

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
        Section.entries.forEach { countriesState[it] = store.countries(it) }
        recentSearches = store.recentSearches()
        dashboardCategories = store.dashboardCategories()
        mediaInfos.clear(); episodeSeasons.clear() // IDs gelten nur je Anbieter
        accountStatus = store.accountStatus()
        accountStatusStale = false
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
        // Stand gehört zum Anbieter, der beim Start dieses Ladevorgangs aktiv war. Wurde inzwischen gewechselt oder
        // neu geladen, schreibt ein verspäteter Abruf nichts mehr.
        fun current() = repo === r
        val result = try {
            r.load(s, fromCache = loadFromCache, previousCount = { store.cacheMeta(it)?.count ?: 0 })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!hadEntries) throw e
            val old = r.loadCached(s) ?: throw e // nur gespeicherter Stand – kein zweites Warten auf das Netz
            if (current()) {
                sectionWarnings[s] = OverviewWarning.FAILED
                store.saveCacheMeta(s, before!!.copy(failed = true))
            }
            return old
        }
        if (result.fromCache || !current()) return result
        if (result.items.isEmpty() && hadEntries) {
            val old = r.loadCached(s)
            if (old != null && current()) {
                sectionWarnings[s] = OverviewWarning.EMPTY
                store.saveCacheMeta(s, before!!.copy(failed = true))
                return old
            }
        }
        if (current()) store.saveCacheMeta(s, CacheMeta(clock(), result.items.size, before?.count, failed = false))
        return result
    }

    private fun load(s: Section) {
        val r = repo ?: return
        sections[s] = SectionState(loading = true)
        scope.launch {
            try {
                val (cats, loaded) = loadSection(r, s)
                if (repo !== r) return@launch // Anbieter gewechselt oder neu geladen: Ergebnis gehört nicht mehr hierher
                val items = loaded.filterNot { isSeparatorEntry(it.name) } // Überschriften-„Sender“ der Anbieter
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
                if (repo === r) sections[s] = SectionState(error = e.message ?: "Unbekannter Fehler")
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
        withoutCountryFilter.getValue(s).value.items.filter { it.favKey in favorites }

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

    /** Auswahl aus einer Liste: Serie → Serienseite, Film → Detailseite, sonst gleich abspielen. */
    fun open(list: List<Item>, index: Int) {
        val item = list.getOrNull(index) ?: return
        when {
            item.isSeriesFolder -> openSeries(item)
            item.section == Section.MOVIES -> {
                stack.add(Screen.Movie(item))
                requestMediaInfo(item)
            }
            else -> play(list, index)
        }
    }

    /** Direkt abspielen (Detailseite, „Weiterschauen“): an der gespeicherten Stelle, mit [fromStart] von vorn. */
    fun play(list: List<Item>, index: Int, fromStart: Boolean = false) {
        val item = list.getOrNull(index) ?: return
        val start = if (fromStart) 0L else history.firstOrNull { it.item.url == item.url && it.canResume }?.position ?: 0L
        stack.add(Screen.Player(list, index, start))
    }

    /** Gespeicherte Position eines Films/einer Folge (0 = nicht angefangen) – für „Weiterschauen ab …“. */
    fun resumePositionOf(item: Item): Long = history.firstOrNull { it.item.url == item.url && it.canResume }?.position ?: 0L

    private fun openSeries(series: Item) {
        stack.add(Screen.Series(series))
        seasons = emptyList(); seasonsError = null; seasonsLoading = true
        scope.launch {
            try {
                val r = repo
                seasons = r?.seasons(series).orEmpty()
                if (seasons.isEmpty()) seasonsError = "Für diese Serie sind keine Folgen verfügbar."
                // Schon mit den Staffeln geladen – kein weiterer Abruf.
                if (r != null && repo === r) mediaInfos["SERIES:${series.id}"] = r.seriesInfo(series)
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
        (if (isFresh(item)) epgCache.getValue(item.url).entries else fetchEpg(item)).notEndedAt(localTime(clock()))

    /** Beim Beenden aufrufen, damit laufende Ladevorgänge abgebrochen werden. */
    fun close() = scope.cancel()

    companion object {
        /** So viele zuletzt gesuchte Begriffe bleiben gespeichert. */
        const val MAX_RECENT_SEARCHES = 8
        const val LIVE_ALL = "__all"
        /** Sonder-Kategorien der Kataloge Filme/Serien (siehe [browseCategories]). */
        const val BROWSE_CONTINUE = "__continue"
        const val BROWSE_FAV = "__fav"
        const val BROWSE_ALL = "__all"
        /** Zwischenspeicher gilt 24 Stunden. */
        const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        /** Android TV: Rückkehr nach mehr als einer Stunde im Hintergrund zählt als neuer Start. */
        const val RESUME_RESTART_MS = 60 * 60 * 1000L
        /** „Neu“ auf der Startseite: in den letzten 7 Tagen hinzugekommen. */
        const val NEW_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
        /** Abo-Ablauf hervorheben, wenn er höchstens 7 Tage entfernt ist. */
        const val EXPIRY_WARNING_MS = 7 * 24 * 60 * 60 * 1000L
        /** Kategorie „Neu (7 Tage)“ in Filme/Serien. */
        const val BROWSE_NEW = "__new"
        const val LIVE_FAV = "__fav"
        const val LIVE_RECENT = "__recent"
        val SLEEP_STEPS = listOf(30, 60, 90)
        /** Frühestens nach dieser Zeit neu fragen – auch wenn der Vorrat leer ist (Anbieter liefert evtl. dasselbe). */
        const val EPG_MIN_REFRESH_MS = 10 * 60_000L
        /** Spätestens nach dieser Zeit neu laden, damit Programmänderungen (z. B. Verlängerung) ankommen. */
        const val EPG_MAX_AGE_MS = 3 * 60 * 60_000L
    }
}
