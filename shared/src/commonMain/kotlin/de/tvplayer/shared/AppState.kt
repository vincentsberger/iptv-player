package de.tvplayer.shared

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.tvplayer.shared.data.Account
import de.tvplayer.shared.data.Storage
import de.tvplayer.shared.data.Category
import de.tvplayer.shared.data.EpgEntry
import de.tvplayer.shared.data.HistoryEntry
import de.tvplayer.shared.data.Item
import de.tvplayer.shared.data.Repository
import de.tvplayer.shared.data.Season
import de.tvplayer.shared.data.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import de.tvplayer.shared.data.currentTimeMillis
import de.tvplayer.shared.data.hostOf

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Series(val series: Item) : Screen
    data class Player(val items: List<Item>, val index: Int, val startPosition: Long = 0) : Screen
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
    val sections = mutableStateMapOf<Section, SectionState>()
    var favorites by mutableStateOf(store.favorites()); private set
    var history by mutableStateOf(store.history()); private set
    var query by mutableStateOf("")
    var liveCategory by mutableStateOf(LIVE_ALL)

    /** Zuletzt fokussiertes Element, damit der Fokus nach dem Player an derselben Stelle steht. */
    var focusKey: String? = null

    var seasons by mutableStateOf<List<Season>>(emptyList()); private set
    var seasonsLoading by mutableStateOf(false); private set
    var seasonsError by mutableStateOf<String?>(null); private set

    init {
        store.loadAccount()?.let {
            repo = newRepository(it)
            stack.clear(); stack.add(Screen.Home)
            selectTab(Tab.LIVE)
        }
    }

    fun section(s: Section): SectionState = sections[s] ?: SectionState()

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
        stack.clear(); stack.add(Screen.Home)
        selectTab(Tab.LIVE)
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
        selectTab(tab)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sections[s] = SectionState(error = e.message ?: "Unbekannter Fehler")
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

    suspend fun nowPlaying(item: Item): List<EpgEntry> = repo?.nowPlaying(item).orEmpty()

    /** Beim Beenden aufrufen, damit laufende Ladevorgänge abgebrochen werden. */
    fun close() = scope.cancel()

    companion object {
        const val LIVE_ALL = "__all"
        const val LIVE_FAV = "__fav"
        const val LIVE_RECENT = "__recent"
    }
}
