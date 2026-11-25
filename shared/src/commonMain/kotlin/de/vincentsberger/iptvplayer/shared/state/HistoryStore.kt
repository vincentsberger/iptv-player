package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage

/** Was der Nutzer je Anbieter sammelt: Favoriten, Verlauf (Weiterschauen, zuletzt gesehene Sender), letzte Suchen. */
interface Favorites {
    val favorites: Set<String>
    val history: List<HistoryEntry>
    val recentSearches: List<String>
    fun rememberSearch(term: String)
    fun clearRecentSearches()
    fun setFavorite(item: Item, favorite: Boolean)
    fun toggleFavorite(item: Item)
    fun saveProgress(item: Item, position: Long, duration: Long)
    fun clearHistory()

    /** „Rückgängig“ nach [clearHistory]: den vorher gemerkten Verlauf wieder einsetzen. */
    fun restoreHistory(entries: List<HistoryEntry>)
    val continueWatchingAll: List<HistoryEntry>
    val recentChannels: List<Item>
    fun continueWatching(s: Section): List<HistoryEntry>
    fun favoriteItems(s: Section): List<Item>
    fun progressOf(item: Item): Float
    fun resumePositionOf(item: Item): Long
    fun lastEpisodeOf(series: Item): HistoryEntry?
}

class HistoryStore(
    private val store: Storage,
    private val library: LibraryStore,
    private val clock: () -> Long,
) : Favorites {

    override var favorites by mutableStateOf(store.favorites()); private set
    override var history by mutableStateOf(store.history()); private set

    /** Zuletzt gesuchte Begriffe des Anbieters (neueste zuerst, höchstens [MAX_RECENT_SEARCHES]) – als Chips im leeren Suchfeld. */
    override var recentSearches by mutableStateOf(store.recentSearches()); private set

    /** Aktuellen Suchbegriff merken (Suche abgeschickt oder Treffer geöffnet); doppelte – auch anders geschrieben – nur einmal. */
    override fun rememberSearch(term: String) {
        val t = term.trim()
        if (t.length < 2) return
        recentSearches = (listOf(t) + recentSearches.filterNot { it.equals(t, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        store.saveRecentSearches(recentSearches)
    }

    override fun clearRecentSearches() {
        recentSearches = emptyList()
        store.saveRecentSearches(emptyList())
    }

    /** Favorit gezielt setzen oder entfernen (für „Rückgängig“: schaltet nie versehentlich zurück). */
    override fun setFavorite(item: Item, favorite: Boolean) {
        if ((item.favKey in favorites) != favorite) toggleFavorite(item)
    }

    override fun toggleFavorite(item: Item) {
        favorites = if (item.favKey in favorites) favorites - item.favKey else favorites + item.favKey
        store.saveFavorites(favorites)
    }

    override fun saveProgress(item: Item, position: Long, duration: Long) {
        if (item.url.isEmpty() || item.catchup) return // Archiv-Adressen gelten nur wenige Tage
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

    override fun clearHistory() {
        history = emptyList()
        store.saveHistory(emptyList())
    }

    override fun restoreHistory(entries: List<HistoryEntry>) {
        history = entries
        store.saveHistory(entries)
    }

    /** War eine der zusammengefassten Doppelten Favorit, wird es der angezeigte Eintrag – nichts geht verloren. */
    internal fun keepFavoritesOfDuplicates(alts: Map<String, List<Item>>) {
        val add = alts.filter { (primary, others) -> primary !in favorites && others.any { it.favKey in favorites } }.keys
        if (add.isEmpty()) return
        favorites = favorites + add
        store.saveFavorites(favorites)
    }

    /** Angefangene Filme und Folgen, zuletzt gesehen zuerst. */
    override val continueWatchingAll: List<HistoryEntry> get() = history.filter { it.item.section != Section.LIVE && it.canResume }

    /** Zuletzt gesehene Sender. */
    override val recentChannels: List<Item> get() = history.filter { it.item.section == Section.LIVE }.map { it.item }

    override fun continueWatching(s: Section): List<HistoryEntry> = history.filter { it.item.section == s && it.canResume }

    /** Favoriten für Startseite und Favoriten-Tab: ohne Länderfilter (Ausblenden gilt weiter). */
    override fun favoriteItems(s: Section): List<Item> =
        library.sectionWithoutCountryFilter(s).items.filter { it.favKey in favorites }

    override fun progressOf(item: Item): Float =
        history.firstOrNull { it.item.url == item.url && it.canResume }?.progress ?: 0f

    /** Gespeicherte Position eines Films/einer Folge (0 = nicht angefangen) – für „Weiterschauen ab …“. */
    override fun resumePositionOf(item: Item): Long = history.firstOrNull { it.item.url == item.url && it.canResume }?.position ?: 0L

    /** Letzte angesehene Folge einer Serie (für "Weiterschauen" in der Serienansicht). */
    override fun lastEpisodeOf(series: Item): HistoryEntry? =
        history.firstOrNull { it.item.section == Section.SERIES && it.item.categoryId == series.id }

    /** Favoriten, Verlauf und Suchen des (neuen) aktiven Anbieters laden. */
    internal fun reloadForAccount() {
        favorites = store.favorites()
        history = store.history()
        recentSearches = store.recentSearches()
    }

    companion object {
        const val MAX_RECENT_SEARCHES = 8
    }
}
