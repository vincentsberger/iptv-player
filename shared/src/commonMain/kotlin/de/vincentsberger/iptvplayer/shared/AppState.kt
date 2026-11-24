package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.collapseDuplicates
import de.vincentsberger.iptvplayer.shared.data.isSeparatorEntry
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.CacheMeta
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.PlaylistCacheStore
import de.vincentsberger.iptvplayer.shared.data.SectionLoad
import de.vincentsberger.iptvplayer.shared.data.accountId
import de.vincentsberger.iptvplayer.shared.data.SavedAccount
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.state.Catalog
import de.vincentsberger.iptvplayer.shared.state.CatalogStore
import de.vincentsberger.iptvplayer.shared.state.Dashboard
import de.vincentsberger.iptvplayer.shared.state.DashboardStore
import de.vincentsberger.iptvplayer.shared.state.Epg
import de.vincentsberger.iptvplayer.shared.state.Favorites
import de.vincentsberger.iptvplayer.shared.state.EpgStore
import de.vincentsberger.iptvplayer.shared.state.HistoryStore
import de.vincentsberger.iptvplayer.shared.state.Library
import de.vincentsberger.iptvplayer.shared.state.LibraryStore
import de.vincentsberger.iptvplayer.shared.state.MediaDetails
import de.vincentsberger.iptvplayer.shared.state.MediaInfoStore
import de.vincentsberger.iptvplayer.shared.state.Session
import de.vincentsberger.iptvplayer.shared.state.Settings
import de.vincentsberger.iptvplayer.shared.state.SettingsStore
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime
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

/** Selbst vergebener Name, sonst die technische Bezeichnung ([labelOf]). */
fun SavedAccount.displayName(): String = name ?: labelOf(account)

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
/** [CANCELLED] = Laden im Ladebildschirm abgebrochen – der Bereich ist leer, es gibt keinen Stand. */
enum class OverviewWarning { FAILED, EMPTY, FEWER, CANCELLED }

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
 * Gemeinsamer App-Zustand für Fernseher, PC und iPhone – die eine Fassade, die die Oberflächen lesen.
 *
 * Selbst hält AppState nur, was alles zusammenführt: Anbieter (anmelden, wechseln), Navigation, Laden mit
 * Ladebildschirm und Übersicht, Suche und Öffnen/Abspielen. Alles andere liegt in fachlichen Stores (Paket `state`),
 * die AppState per Interface-Delegation unverändert nach außen reicht:
 * - [SettingsStore]: geräteweite Einstellungen, Startseiten-Reihen, Updates, Schlaf-Timer
 * - [LibraryStore]: Rohdaten je Bereich, ausgeblendete Kategorien, Länderfilter, Sortierung ([section])
 * - [EpgStore]: „Jetzt läuft“ und ganzes Programm je Sender
 * - [MediaInfoStore]: Infos zu Filmen und Serien, Staffeln der geöffneten Serie
 * - [HistoryStore]: Favoriten, Verlauf, letzte Suchen
 * - [DashboardStore]: Neues aus gewählten Kategorien, Abo-Daten
 * - [CatalogStore]: Kataloge Filme/Serien – Kategorien, Titelbild, Genre-Reihen, Ähnliches
 *
 * Den aktiven Anbieter teilen sich alle über [Session]. Anbieterbezogene Stores laden beim Wechsel über ihr
 * `reloadForAccount()` neu (siehe `reloadAccountData`).
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
    private val settings: SettingsStore = SettingsStore(store, clock),
    private val library: LibraryStore = LibraryStore(store, settings),
    private val session: Session = Session(),
    private val epg: EpgStore = EpgStore(session, scope, clock, localTime),
    private val media: MediaInfoStore = MediaInfoStore(session, library, scope),
    private val favs: HistoryStore = HistoryStore(store, library, clock),
    private val dashboard: DashboardStore = DashboardStore(session, store, library, clock, scope),
    private val catalog: CatalogStore = CatalogStore(store, library, favs, dashboard),
) : Settings by settings, Library by library, Epg by epg, MediaDetails by media, Favorites by favs,
    Dashboard by dashboard, Catalog by catalog {
    private fun repoFor(account: Account) = newRepository(account, playlistCache(accountId(account)))

    /** Aktiver Anbieter – liegt in [Session], damit die Stores ihn ohne AppState kennen. */
    private var repo: Repository?
        get() = session.repo
        set(v) { session.repo = v }

    private val stack = mutableStateListOf<Screen>(Screen.Login)
    val screen: Screen get() = stack.last()

    var tab by mutableStateOf(Tab.LIVE); private set
    /** Weitere Quellen je angezeigtem Eintrag, je Bereich (siehe [collapseDuplicates]). Steht oben: das Laden beim
     *  Erzeugen (in Tests sofort) braucht es schon. */
    private val alternates = mutableStateMapOf<Section, Map<String, List<Item>>>()
    var query by mutableStateOf("")

    /** Aktuellen Suchbegriff merken – Kurzform für die Suche (siehe [Favorites.rememberSearch]). */
    fun rememberSearch() = favs.rememberSearch(query)
    private var liveCategoryState by mutableStateOf(store.lastLiveCategory ?: LIVE_ALL)
    /** Gewählte Live-Kategorie – wird gespeichert, damit die App beim nächsten Start dort weitermacht. */
    var liveCategory: String
        get() = liveCategoryState
        set(v) { liveCategoryState = v; store.lastLiveCategory = v }


    /** Zuletzt fokussiertes Element, damit der Fokus nach dem Player an derselben Stelle steht. */
    var focusKey: String? = null

    /** Nach dem Laden von Live TV: zuletzt gesehenen Sender starten (Liste = alle Sender, damit Zappen geht). */
    private fun startLastChannelIfWanted() {
        if (!startChannelPending) return
        startChannelPending = false
        val last = history.firstOrNull { it.item.section == Section.LIVE }?.item ?: return
        val all = section(Section.LIVE).items
        val i = all.indexOfFirst { it.url == last.url }
        if (i >= 0 && stack.last() == Screen.Home) stack.add(Screen.Player(all, i))
    }

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
        dashboard.refreshAccountStatus()
        selectTab(t)
        Section.entries.forEach(::ensureLoaded)
        checkStartupDone()
    }

    // ---------- Sendung verpasst ----------

    /** Vergangene Sendung abspielen – als eigener Titel mit Zeitleiste (spulen), ohne Verlauf. */
    fun playCatchup(channel: Item, entry: EpgEntry) {
        val url = repo?.catchupUrl(channel, entry) ?: return
        val item = Item(
            "catchup:${channel.id}:${entry.startAt}", "${splitProviderTag(channel.name).name} · ${entry.title}", channel.logo,
            channel.categoryId, url, Section.MOVIES, catchup = true,
        )
        stack.add(Screen.Player(listOf(item), 0, 0))
    }

    // ---------- Startseite: Neues aus gewählten Kategorien ----------

    /** „Alle anzeigen“: Filme/Serien mit der Kategorie „Neu (7 Tage)“ öffnen. */
    fun showNewInBrowse(s: Section) {
        rememberBrowseCategory(s, BROWSE_NEW)
        selectTab(if (s == Section.SERIES) Tab.SERIES else Tab.MOVIES)
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
        val cancelled = warning == OverviewWarning.CANCELLED
        OverviewRow(s, count, previous?.takeUnless { cancelled }?.let { count - it }, warning, meta?.loadedAt?.takeUnless { cancelled })
    }

    /** Laufende Abrufe je Bereich – „Abbrechen“ im Ladebildschirm beendet sie. */
    private val loadJobs = HashMap<Section, Job>()

    /** Anteil der fertig geladenen Bereiche (Gesamtbalken im Ladebildschirm) – Teilfortschritt liefert der Anbieter nicht. */
    val startupProgress: Float
        get() = Section.entries.count { sections[it]?.loading == false }.toFloat() / Section.entries.size

    /** Live TV ist fertig (ohne Fehler), andere Bereiche laden noch – Live TV lässt sich schon öffnen. */
    val canOpenLiveEarly: Boolean
        get() = !startupDone && sections[Section.LIVE]?.let { !it.loading && it.error == null } == true

    /** Ladebildschirm verlassen und Live TV zeigen; die übrigen Bereiche laden weiter, eine Übersicht folgt nicht. */
    fun openLiveEarly() {
        if (!canOpenLiveEarly) return
        overviewPending = false
        overview = null
        startupDone = true
        selectTab(Tab.LIVE)
        startLastChannelIfWanted()
    }

    /**
     * „Abbrechen“ im Ladebildschirm: nur die noch ladenden Bereiche beenden (sie gelten als fehlgeschlagen), fertige
     * bleiben. Danach kommt die Übersicht wie gewohnt – mit „Neu laden“.
     */
    fun cancelStartup() {
        Section.entries.filter { sections[it]?.loading == true }.forEach { s ->
            // Erst den Zustand setzen, dann abbrechen: das finally des Abrufs prüft gleich, ob alles fertig ist.
            sections[s] = SectionState(error = CANCELLED)
            sectionWarnings[s] = OverviewWarning.CANCELLED
            loadJobs.remove(s)?.cancel()
        }
        checkStartupDone()
    }

    /** Wie viele doppelte Einträge zu einem zusammengefasst wurden (Übersicht: „273 doppelte Sender“). */
    fun collapsedDuplicates(s: Section): Int = alternates[s]?.values?.sumOf { it.size } ?: 0

    /** Gibt es Namenszusätze wie „ᴿᴬᵂ“ oder „4K“, die als Kennzeichen erscheinen? */
    fun hasNameBadges(s: Section): Boolean = sections[s]?.items.orEmpty().any { splitProviderTag(it.name).badges.isNotEmpty() }

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
        epg.clear()
        startLoading(tab, forceNetwork)
    }

    // Steht bewusst hinter allen Feldern: init läuft in Deklarationsreihenfolge, und das Laden
    // (in Tests sofort, Dispatchers.Unconfined) braucht views, sortAz, hidden & Co. bereits.
    init {
        // Ist die offene Live-Kategorie nach einer Länderauswahl ausgefiltert, lieber „Alle“ zeigen als eine leere Liste.
        library.afterCountriesChanged = { s ->
            val special = liveCategory == LIVE_ALL || liveCategory == LIVE_FAV || liveCategory == LIVE_RECENT
            if (s == Section.LIVE && !special && section(Section.LIVE).categories.none { it.id == liveCategory }) liveCategory = LIVE_ALL
        }
        store.loadAccount()?.let {
            repo = repoFor(it)
            stack.clear(); stack.add(Screen.Home)
            startChannelPending = store.startWithLastChannel
            // Dort weitermachen, wo man war – aber nicht in Suche/Einstellungen.
            startLoading(lastTabOrLive())
        }
    }


    val accountLabel: String
        get() = repo?.account?.let { a -> accounts.firstOrNull { it.id == accountId(a) }?.displayName() ?: labelOf(a) }.orEmpty()

    // ---------- Anbieter: anmelden, hinzufügen, wechseln, entfernen ----------

    /** Alle gespeicherten Anbieter (für die Einstellungen). */
    var accounts by mutableStateOf(store.accounts()); private set
    var activeAccountId by mutableStateOf(store.activeAccountId); private set

    /** Anmeldebildschirm über der Startseite („Anbieter hinzufügen“) – dann gibt es ein Zurück. */
    val canCancelLogin: Boolean get() = screen == Screen.Login && stack.size > 1

    /** Anmelden; [name] = optionaler eigener Name für den Anbieter (leer = bisheriger bzw. keiner). */
    suspend fun login(account: Account, name: String? = null) {
        val r = repoFor(account)
        r.login()
        store.saveAccount(r.account, name) // mit der erreichten Adresse, z. B. https://…
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
        epg.clear()
        query = ""
        stack.clear(); stack.add(Screen.Home)
        startLoading(tab)
    }

    /** Daten des (neuen) aktiven Anbieters aus dem Speicher holen. */
    private fun reloadAccountData() {
        accounts = store.accounts()
        activeAccountId = store.activeAccountId
        favs.reloadForAccount()
        library.reloadForAccount()
        dashboard.reloadForAccount()
        media.reloadForAccount() // IDs gelten nur je Anbieter
        liveCategoryState = store.lastLiveCategory ?: LIVE_ALL
        focusKey = null
    }

    /** Dort weitermachen, wo man war – aber nicht in Suche/Einstellungen; beim ersten Start die Startseite. */
    private fun lastTabOrLive(): Tab {
        val last = Tab.entries.firstOrNull { t -> t.name == store.lastTab }
        return if (last == null || last == Tab.SEARCH || last == Tab.SETTINGS) Tab.HOME else last
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
        loadJobs[s] = scope.launch {
            try {
                val (cats, loaded) = loadSection(r, s)
                if (repo !== r) return@launch // Anbieter gewechselt oder neu geladen: Ergebnis gehört nicht mehr hierher
                // Überschriften-„Sender“ fallen weg, gleichnamige Einträge einer Kategorie werden zu einem mit mehreren Quellen.
                val collapsed = collapseDuplicates(loaded.filterNot { isSeparatorEntry(it.name) })
                val items = collapsed.items
                alternates[s] = collapsed.alternates
                favs.keepFavoritesOfDuplicates(collapsed.alternates)
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

    val searchResults: Map<Section, List<Item>> by derivedStateOf {
        val q = query.trim()
        if (q.length < 2) emptyMap()
        else Section.entries
            .associateWith { s -> section(s).items.asSequence().filter { it.name.contains(q, ignoreCase = true) }.take(60).toList() }
            .filterValues { it.isNotEmpty() }
    }

    // ---------- Favoriten und Verlauf ----------

    /** Alle Quellen eines Eintrags: er selbst zuerst, dann gleichnamige aus derselben Kategorie. */
    fun sourcesOf(item: Item): List<Item> = listOf(item) + alternates[item.section]?.get(item.favKey).orEmpty()

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

    /**
     * Weiterschauen von außen (Kachel auf dem Startbildschirm des Fernsehers): den passenden Verlaufseintrag an der
     * gespeicherten Stelle abspielen, die Startseite dahinter. Kein Sender-Autostart und keine Übersicht dazwischen –
     * man wollte direkt weiterschauen. False, wenn kein Anbieter aktiv ist oder es den Eintrag nicht (mehr) gibt.
     */
    fun resumeFromHistory(match: (HistoryEntry) -> Boolean): Boolean {
        if (repo == null) return false
        val entry = history.firstOrNull { it.canResume && match(it) } ?: return false
        startChannelPending = false
        overviewPending = false
        overview = null
        stack.clear(); stack.add(Screen.Home)
        play(listOf(entry.item), 0)
        return true
    }

    private fun openSeries(series: Item) {
        stack.add(Screen.Series(series))
        media.loadSeasons(series)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Beim Beenden aufrufen, damit laufende Ladevorgänge abgebrochen werden. */
    fun close() = scope.cancel()

    companion object {
        /** Fehlertext eines Bereichs, dessen Laden im Ladebildschirm abgebrochen wurde. */
        const val CANCELLED = "Laden abgebrochen"
        /** So viele zuletzt gesuchte Begriffe bleiben gespeichert. */
        const val MAX_RECENT_SEARCHES = HistoryStore.MAX_RECENT_SEARCHES
        /** Ganzes Programm eines Senders höchstens so oft neu laden. */
        const val PROGRAMME_REFRESH_MS = EpgStore.PROGRAMME_REFRESH_MS
        const val LIVE_ALL = "__all"
        /** Sonder-Kategorien der Kataloge Filme/Serien (siehe [browseCategories]). */
        const val BROWSE_CONTINUE = "__continue"
        const val BROWSE_FAV = "__fav"
        const val BROWSE_ALL = "__all"
        /** Schlüssel-Präfix der Genre-Kategorien („__genre:Drama“). */
        const val BROWSE_GENRE = "__genre:"
        const val MIN_GENRE_TITLES = 3
        /** So viele Genre-Reihen zeigt die Übersicht. */
        const val GENRE_ROWS = 6
        /** So viele Titel zeigt eine Genre-Reihe der Übersicht. */
        const val GENRE_ROW_SIZE = 20
        const val MAX_GENRES = 30
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
        val SLEEP_STEPS = SettingsStore.SLEEP_STEPS
        /** Frühestens nach dieser Zeit neu fragen – auch wenn der Vorrat leer ist (Anbieter liefert evtl. dasselbe). */
        const val EPG_MIN_REFRESH_MS = EpgStore.EPG_MIN_REFRESH_MS
        /** Spätestens nach dieser Zeit neu laden, damit Programmänderungen (z. B. Verlängerung) ankommen. */
        const val EPG_MAX_AGE_MS = EpgStore.EPG_MAX_AGE_MS
    }
}

/** „Drama, Krimi / Thriller“ → [Drama, Krimi, Thriller]; einheitlich groß geschrieben, Doppelte nur einmal. */
internal fun splitGenres(raw: String): List<String> =
    raw.split(',', '/', '|', '&', ';').map { it.trim() }.filter { it.length >= 3 }
        .map { g -> g.lowercase().replaceFirstChar { it.uppercase() } }.distinct()
