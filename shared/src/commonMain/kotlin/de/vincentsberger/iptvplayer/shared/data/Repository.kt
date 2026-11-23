package de.vincentsberger.iptvplayer.shared.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ergebnis von [Repository.load]; [fromCache] = aus dem Zwischenspeicher statt aus dem Netz. */
data class SectionLoad(val categories: List<Category>, val items: List<Item>, val fromCache: Boolean = false)

class Repository(
    account: Account,
    private val http: HttpSource = PlatformHttp,
    private val playlistCache: PlaylistCacheStore = NoPlaylistCache,
) {
    /** Nach [login] mit der tatsächlich erreichten Server-Adresse (inkl. https/http). */
    var account: Account = account
        private set

    private val xtream = (account as? Account.Xtream)?.let { XtreamClient(it, http) }
    private val cache = HashMap<Section, SectionLoad>()
    private val seasonCache = HashMap<String, List<Season>>()
    private val mutex = Mutex()

    suspend fun login() {
        if (xtream != null) {
            xtream.login()
            account = (account as Account.Xtream).copy(server = xtream.base)
        } else {
            mutex.withLock { loadM3u() }
        }
    }

    /**
     * Listen eines Bereichs. [fromCache]: aus dem Zwischenspeicher lesen (fehlt er oder ist er unlesbar → Netz).
     * Aus dem Netz geladene Listen werden gespeichert – aber nur, wenn sie Einträge haben: Eine leere Antwort
     * (gedrosselter oder gestörter Anbieter) soll den letzten guten Stand nicht überschreiben.
     */
    suspend fun load(section: Section, fromCache: Boolean = false): SectionLoad = mutex.withLock {
        // Schon geladen: wiederverwenden – außer der Aufrufer will ausdrücklich den gespeicherten Stand (z. B. weil
        // das Netz gerade eine leere Liste lieferte).
        cache[section]?.takeIf { !fromCache || it.fromCache }?.let { return@withLock it }
        val client = xtream
        val result = if (client != null) loadXtream(client, section, fromCache) else {
            loadM3u(fromCache)
            cache[section] ?: SectionLoad(emptyList(), emptyList())
        }
        cache[section] = result
        result
    }

    /** Zwischenspeicher dieses Anbieters löschen (Abmelden). */
    fun clearCache() = playlistCache.clear()

    private suspend fun loadXtream(client: XtreamClient, section: Section, fromCache: Boolean): SectionLoad {
        val (catAction, itemAction) = client.listActions(section)
        val catKey = "$catAction.json"
        val itemKey = "$itemAction.json"
        if (fromCache) {
            val cats = playlistCache.readText(catKey)
            val items = playlistCache.readText(itemKey)
            if (cats != null && items != null) {
                try {
                    return SectionLoad(client.parseCategories(cats), client.parseItems(section, items), fromCache = true)
                } catch (e: Exception) {
                    // unlesbarer Stand → aus dem Netz
                }
            }
        }
        val catText = client.fetch(catAction)
        val itemText = client.fetch(itemAction)
        val result = SectionLoad(client.parseCategories(catText), client.parseItems(section, itemText))
        if (result.items.isNotEmpty()) {
            playlistCache.writeText(catKey, catText)
            playlistCache.writeText(itemKey, itemText)
        }
        return result
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

    private suspend fun loadM3u(fromCache: Boolean = false) {
        if (cache.isNotEmpty() && !fromCache) return
        val cached = if (fromCache) playlistCache.readLines(M3U_KEY) { M3uParser.parse(it) }?.takeIf { it.items.isNotEmpty() } else null
        val result = cached ?: run {
            val url = (account as Account.M3u).url.trim()
            // Eine M3U-Liste ist ein einziger Abruf: Zeilen einmal sammeln, parsen und (wenn gültig) speichern.
            val lines = http.readLines(url) { it.toList() }
            M3uParser.parse(lines.asSequence()).also { if (it.items.isNotEmpty()) playlistCache.writeLines(M3U_KEY, lines.asSequence()) }
        }
        if (result.items.isEmpty()) throw TvException("Die Playlist ist leer oder ungültig")
        Section.entries.forEach { s ->
            val items = result.items.filter { it.section == s }
            val ids = items.mapTo(HashSet()) { it.categoryId }
            cache[s] = SectionLoad(result.categories.filter { it.id in ids }, items, fromCache = cached != null)
        }
    }

    private companion object {
        const val M3U_KEY = "playlist.m3u"
    }
}
