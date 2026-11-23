package de.vincentsberger.iptvplayer.shared.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ergebnis von [Repository.load]; [fromCache] = aus dem Zwischenspeicher statt aus dem Netz. */
data class SectionLoad(val categories: List<Category>, val items: List<Item>, val fromCache: Boolean = false)

class Repository(
    account: Account,
    private val http: HttpSource = PlatformHttp,
    private val playlistCache: PlaylistCacheStore = NoPlaylistCache,
    /** Hier laufen Zwischenspeicher und Parsen (große Dateien, gzip, DPAPI) – nicht auf dem UI-Thread. */
    private val work: CoroutineDispatcher = Dispatchers.Default,
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
    suspend fun load(
        section: Section,
        fromCache: Boolean = false,
        /** Einträge je Bereich beim letzten Laden – bei M3U bleibt die alte Liste, wenn ein Bereich plötzlich leer ist. */
        previousCount: (Section) -> Int = { 0 },
    ): SectionLoad = withContext(work) {
        mutex.withLock {
            cache[section]?.takeIf { !fromCache || it.fromCache }?.let { return@withLock it }
            val client = xtream
            val result = if (client != null) loadXtream(client, section, fromCache) else {
                loadM3u(fromCache, previousCount)
                cache[section] ?: SectionLoad(emptyList(), emptyList())
            }
            cache[section] = result
            result
        }
    }

    /** Nur den gespeicherten Stand lesen – null, wenn es keinen lesbaren gibt (kein Ersatzabruf übers Netz). */
    suspend fun loadCached(section: Section): SectionLoad? = withContext(work) {
        mutex.withLock {
            cache[section]?.takeIf { it.fromCache }?.let { return@withLock it }
            val client = xtream
            val result = if (client != null) readXtreamCache(client, section) else {
                val parsed = playlistCache.readLines(M3U_KEY) { M3uParser.parse(it) }?.takeIf { it.items.isNotEmpty() }
                    ?: return@withLock null
                splitM3u(parsed, fromCache = true)
                cache[section]
            }
            result?.also { cache[section] = it }
        }
    }

    /** Abo-Daten frisch vom Server (nur Xtream; M3U → null). Wirft bei Netzfehlern. */
    suspend fun accountInfo(): AccountInfo? = withContext(work) { xtream?.fetchAccountInfo() }

    /** Zwischenspeicher dieses Anbieters löschen (Abmelden). */
    fun clearCache() = playlistCache.clear()

    private suspend fun loadXtream(client: XtreamClient, section: Section, fromCache: Boolean): SectionLoad {
        val (catAction, itemAction) = client.listActions(section)
        val catKey = "$catAction.json"
        val itemKey = "$itemAction.json"
        if (fromCache) readXtreamCache(client, section)?.let { return it }
        val catText = client.fetch(catAction)
        val itemText = client.fetch(itemAction)
        val result = SectionLoad(client.parseCategories(catText), client.parseItems(section, itemText))
        if (result.items.isNotEmpty()) {
            // Der Zwischenspeicher ist nur eine Abkürzung: Scheitert das Schreiben (Speicher voll, gesperrte Datei),
            // gelten die frisch geladenen Daten trotzdem.
            runCatching {
                playlistCache.writeText(catKey, catText)
                playlistCache.writeText(itemKey, itemText)
            }
        }
        return result
    }

    private fun readXtreamCache(client: XtreamClient, section: Section): SectionLoad? {
        val (catAction, itemAction) = client.listActions(section)
        val cats = playlistCache.readText("$catAction.json") ?: return null
        val items = playlistCache.readText("$itemAction.json") ?: return null
        return try {
            SectionLoad(client.parseCategories(cats), client.parseItems(section, items), fromCache = true)
        } catch (e: Exception) {
            null // unlesbarer Stand
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

    private suspend fun loadM3u(fromCache: Boolean = false, previousCount: (Section) -> Int = { 0 }) {
        if (cache.isNotEmpty() && !fromCache) return
        val cached = if (fromCache) playlistCache.readLines(M3U_KEY) { M3uParser.parse(it) }?.takeIf { it.items.isNotEmpty() } else null
        val result = cached ?: run {
            val url = (account as Account.M3u).url.trim()
            // Gestreamt parsen und dabei mitschreiben. Gespeichert wird nur eine gültige Liste, in der kein Bereich
            // plötzlich fehlt – sonst bleibt der alte Stand für den Rückgriff erhalten.
            http.readLines(url) { lines ->
                playlistCache.teeLines(M3U_KEY, lines, M3uParser::parse) { r ->
                    r.items.isNotEmpty() && Section.entries.none { s -> previousCount(s) > 0 && r.items.none { it.section == s } }
                }
            }
        }
        if (result.items.isEmpty()) throw TvException("Die Playlist ist leer oder ungültig")
        splitM3u(result, fromCache = cached != null)
    }

    private fun splitM3u(result: M3uParser.Result, fromCache: Boolean) {
        Section.entries.forEach { s ->
            val items = result.items.filter { it.section == s }
            val ids = items.mapTo(HashSet()) { it.categoryId }
            cache[s] = SectionLoad(result.categories.filter { it.id in ids }, items, fromCache = fromCache)
        }
    }

    private companion object {
        const val M3U_KEY = "playlist.m3u"
    }
}
