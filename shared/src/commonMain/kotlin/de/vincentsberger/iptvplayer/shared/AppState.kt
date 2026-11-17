package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.data.Category
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

enum class Tab(val title: String) {
    SEARCH("Suche"),
    LIVE("Live TV"),
    MOVIES("Filme"),
    SERIES("Serien"),
    FAVORITES("Favoriten"),
    SETTINGS("Einstellungen"),
}

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
    private val newRepository: (Account) -> Repository = { Repository(it) },
) {
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

    // Gecacht je Bereich: rechnet nur neu, wenn sich Rohdaten, Sortierung oder Ausblendungen ändern.
    private val views = Section.entries.associateWith { s -> derivedStateOf { applyPrefs(s, sections[s] ?: SectionState()) } }

    private fun applyPrefs(s: Section, raw: SectionState): SectionState {
        if (!sortAz && hidden.none { it.startsWith("${s.name}:") }) return raw
        fun List<Item>.sorted() = if (sortAz) sortedBy { splitProviderTag(it.name).name.lowercase() } else this
        val visible = raw.categories.filterNot { isHidden(s, it.id) }
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
        Tab.SEARCH, Tab.FAVORITES -> Section.entries
        Tab.SETTINGS -> emptyList()
    }

    private fun checkStartupDone() {
        if (!startupDone && sectionsNeededFor(tab).all { sections[it]?.loading == false }) startupDone = true
    }

    /** Alle Bereiche parallel vorladen (Suche und Favoriten brauchen sie ohnehin) und den Ladebildschirm zeigen. */
    private fun startLoading(t: Tab) {
        startupDone = false
        selectTab(t)
        Section.entries.forEach(::ensureLoaded)
        checkStartupDone()
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
            repo = newRepository(it)
            stack.clear(); stack.add(Screen.Home)
            startChannelPending = store.startWithLastChannel
            // Dort weitermachen, wo man war – aber nicht in Suche/Einstellungen.
            val last = Tab.entries.firstOrNull { t -> t.name == store.lastTab }
            startLoading(if (last == null || last == Tab.SEARCH || last == Tab.SETTINGS) Tab.LIVE else last)
        }
    }


    val accountLabel: String
        get() = when (val a = repo?.account) {
            is Account.Xtream -> "Xtream Codes · ${a.username} @ ${hostOf(a.server)}"
            is Account.M3u -> "M3U-Playlist · ${hostOf(a.url)}"
            null -> ""
        }

    // ---------- Anmeldung ----------

    suspend fun login(account: Account) {
        val r = newRepository(account)
        r.login()
        store.saveAccount(r.account) // mit der erreichten Adresse, z. B. https://…
        repo = r
        sections.clear()
        liveCategory = LIVE_ALL
        focusKey = null
        epgCache.clear()
        stack.clear(); stack.add(Screen.Home)
        startLoading(Tab.LIVE)
    }

    fun logout() {
        store.clear()
        repo = null
        sections.clear()
        favorites = emptySet()
        history = emptyList()
        query = ""
        focusKey = null
        stack.clear(); stack.add(Screen.Login)
    }

    // ---------- Navigation und Laden ----------

    fun selectTab(t: Tab) {
        tab = t
        store.lastTab = t.name
        focusKey = null
        when (t) {
            Tab.LIVE -> ensureLoaded(Section.LIVE)
            Tab.MOVIES -> ensureLoaded(Section.MOVIES)
            Tab.SERIES -> ensureLoaded(Section.SERIES)
            Tab.SEARCH, Tab.FAVORITES -> Section.entries.forEach(::ensureLoaded)
            Tab.SETTINGS -> Unit
        }
    }

    fun ensureLoaded(s: Section) {
        val st = sections[s]
        if (st != null && (st.loading || st.error == null)) return
        load(s)
    }

    fun reloadAll() {
        val acc = repo?.account ?: return
        repo = newRepository(acc)
        sections.clear()
        epgCache.clear()
        startLoading(tab)
    }

    private fun load(s: Section) {
        val r = repo ?: return
        sections[s] = SectionState(loading = true)
        scope.launch {
            try {
                val (cats, items) = r.load(s)
                val byCategory = items.groupBy { it.categoryId }
                val known = cats.distinctBy { it.id }.filter { byCategory.containsKey(it.id) }
                val knownIds = known.mapTo(HashSet()) { it.id }
                val orphans = byCategory.keys.filter { it !in knownIds }.map { Category(it, "Sonstige") }
                sections[s] = SectionState(categories = known + orphans, items = items, byCategory = byCategory)
                if (s == Section.LIVE) {
                    // Gemerkte Kategorie gibt es nicht mehr (oder sie ist ausgeblendet) → „Alle Sender“.
                    val special = liveCategory == LIVE_ALL || liveCategory == LIVE_FAV || liveCategory == LIVE_RECENT
                    if (!special && section(Section.LIVE).categories.none { it.id == liveCategory }) liveCategory = LIVE_ALL
                    startLastChannelIfWanted()
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

    val liveChannels: List<Item> by derivedStateOf {
        val s = section(Section.LIVE)
        when (liveCategory) {
            LIVE_ALL -> s.items
            LIVE_FAV -> s.items.filter { it.favKey in favorites }
            LIVE_RECENT -> history.filter { it.item.section == Section.LIVE }.map { it.item }
            else -> s.byCategory[liveCategory].orEmpty()
        }
    }

    fun continueWatching(s: Section): List<HistoryEntry> = history.filter { it.item.section == s && it.canResume }

    fun favoriteItems(s: Section): List<Item> = section(s).items.filter { it.favKey in favorites }

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
        const val LIVE_FAV = "__fav"
        const val LIVE_RECENT = "__recent"
        val SLEEP_STEPS = listOf(30, 60, 90)
        const val EPG_TTL_MS = 10 * 60_000L
    }
}
