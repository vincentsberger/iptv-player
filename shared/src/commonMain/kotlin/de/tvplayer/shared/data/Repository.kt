package de.tvplayer.shared.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repository(val account: Account, private val http: HttpSource = PlatformHttp) {
    private val xtream = (account as? Account.Xtream)?.let { XtreamClient(it, http) }
    private val cache = HashMap<Section, Pair<List<Category>, List<Item>>>()
    private val seasonCache = HashMap<String, List<Season>>()
    private val mutex = Mutex()

    suspend fun login() {
        if (xtream != null) xtream.login() else mutex.withLock { loadM3u() }
    }

    suspend fun load(section: Section): Pair<List<Category>, List<Item>> = mutex.withLock {
        cache[section]?.let { return@withLock it }
        val client = xtream
        if (client != null) {
            val result = client.categories(section) to client.items(section)
            cache[section] = result
            result
        } else {
            loadM3u()
            cache[section] ?: (emptyList<Category>() to emptyList())
        }
    }

    /** Staffeln einer Serie. Bei M3U-Listen gibt es keine Serien-Ordner, dort sind Folgen direkt abspielbar. */
    suspend fun seasons(series: Item): List<Season> {
        val client = xtream ?: return emptyList()
        seasonCache[series.id]?.let { return it }
        return client.seasons(series).also { seasonCache[series.id] = it }
    }

    suspend fun nowPlaying(item: Item): List<EpgEntry> {
        val client = xtream ?: return emptyList()
        if (item.section != Section.LIVE) return emptyList()
        return try {
            client.shortEpg(item.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadM3u() {
        if (cache.isNotEmpty()) return
        val url = (account as Account.M3u).url.trim()
        val result = http.readLines(url) { M3uParser.parse(it) }
        if (result.items.isEmpty()) throw TvException("Die Playlist ist leer oder ungültig")
        Section.entries.forEach { s ->
            val items = result.items.filter { it.section == s }
            val ids = items.mapTo(HashSet()) { it.categoryId }
            cache[s] = result.categories.filter { it.id in ids } to items
        }
    }
}
