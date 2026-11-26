package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.derivedStateOf
import de.vincentsberger.iptvplayer.shared.AppState.Companion.BROWSE_ALL
import de.vincentsberger.iptvplayer.shared.AppState.Companion.BROWSE_CONTINUE
import de.vincentsberger.iptvplayer.shared.AppState.Companion.BROWSE_FAV
import de.vincentsberger.iptvplayer.shared.AppState.Companion.BROWSE_GENRE
import de.vincentsberger.iptvplayer.shared.AppState.Companion.BROWSE_NEW
import de.vincentsberger.iptvplayer.shared.AppState.Companion.GENRE_ROWS
import de.vincentsberger.iptvplayer.shared.AppState.Companion.GENRE_ROW_SIZE
import de.vincentsberger.iptvplayer.shared.AppState.Companion.MAX_GENRES
import de.vincentsberger.iptvplayer.shared.AppState.Companion.MIN_GENRE_TITLES
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.splitGenres

/** Kataloge Filme/Serien: Kategorien (inkl. Weiterschauen, Neu, Favoriten, Genres), Titelbild, Genre-Reihen, Ähnliches. */
interface Catalog {
    fun browseCategory(s: Section): String?
    fun rememberBrowseCategory(s: Section, key: String)
    fun browseCategories(s: Section): List<Category>
    fun genreCategories(s: Section): List<Category>
    fun heroItem(s: Section): Item?
    fun genreRows(s: Section, max: Int = GENRE_ROWS): List<Pair<String, List<Item>>>
    fun similarTitles(item: Item, info: MediaInfo?, limit: Int = 12): List<Item>
    fun categoryOf(item: Item): Category?
    fun browseItems(s: Section, key: String): List<Item>
    fun initialBrowseCategory(s: Section): String
}

class CatalogStore(
    private val store: Storage,
    private val library: Library,
    private val history: Favorites,
    private val dashboard: Dashboard,
) : Catalog {

    /** Zuletzt gewählte Kategorie in Filme/Serien (Schlüssel bestimmt die jeweilige Oberfläche). */
    override fun browseCategory(s: Section): String? = store.lastBrowseCategory(s)
    override fun rememberBrowseCategory(s: Section, key: String) = store.saveLastBrowseCategory(s, key)

    /** Kategorien in Filme/Serien: Weiterschauen und Neu (nur wenn es etwas gibt), Favoriten, Alle, dann die des Anbieters. */
    override fun browseCategories(s: Section): List<Category> = buildList {
        if (history.continueWatching(s).isNotEmpty()) add(Category(BROWSE_CONTINUE, "Weiterschauen"))
        if (dashboard.newItemsOf(s).isNotEmpty()) add(Category(BROWSE_NEW, "Neu (7 Tage)"))
        add(Category(BROWSE_FAV, "Favoriten"))
        add(Category(BROWSE_ALL, "Alle ${s.title}"))
        addAll(library.section(s).categories)
        addAll(genreCategories(s))
    }

    /** Genres je Titel (Schlüssel [Item.favKey]) – gecacht, die Zerlegung bei 30 000 Serien nicht je Zeichnen. */
    private val itemGenres = Section.entries.associateWith { s ->
        derivedStateOf {
            if (s == Section.LIVE) emptyMap()
            else library.section(s).items.mapNotNull { item -> item.genre?.let { item.favKey to splitGenres(it) } }.toMap()
        }
    }

    /**
     * Genres als zusätzliche Kategorien („Genre · Drama“), häufigste zuerst – nur solche mit mindestens
     * [MIN_GENRE_TITLES] Titeln, höchstens [MAX_GENRES]. Liefert der Anbieter keine Genres: leer.
     */
    override fun genreCategories(s: Section): List<Category> =
        itemGenres.getValue(s).value.values.flatten().groupingBy { it }.eachCount()
            .filterValues { it >= MIN_GENRE_TITLES }.entries.sortedByDescending { it.value }.take(MAX_GENRES)
            .map { Category(BROWSE_GENRE + it.key, "Genre · ${it.key}") }

    /** Erwachsenen-Kategorien je Bereich („ADULT“ im Namen) – gecacht wie [itemGenres]. */
    private val adultCategoryIds = Section.entries.associateWith { s ->
        derivedStateOf { library.section(s).categories.filter(::isAdultCategory).mapTo(HashSet()) { it.id } }
    }

    /** Darf auf Titelbild, in Genre-Reihen und „Ähnliche Titel“: kein Erwachseneninhalt (Markierung oder Kategorie). */
    /**
     * Prüfung für einen ganzen Bereich: liest die Erwachsenen-Kategorien **einmal**. Je Titel gelesen, kostete der
     * Zugriff auf den abgeleiteten Zustand (Compose protokolliert jeden) bei 60 000 Titeln Hunderte Millisekunden.
     */
    private fun browsable(s: Section): (Item) -> Boolean {
        val adult = adultCategoryIds.getValue(s).value
        return { !it.isAdult && it.categoryId !in adult }
    }

    // Titelbild und Genre-Reihen einmal je Listenstand berechnen – nicht bei jedem Zeichnen über 30 000 Titel laufen.
    private val heroState = Section.entries.associateWith { s ->
        derivedStateOf { val ok = browsable(s); library.section(s).items.filter { it.addedAt != null && ok(it) }.maxByOrNull { it.addedAt!! } }
    }
    private val genreRowsState = Section.entries.associateWith { s ->
        derivedStateOf {
            genreCategories(s).take(GENRE_ROWS).map { c ->
                c.id.removePrefix(BROWSE_GENRE) to browseItems(s, c.id).asSequence().filter(browsable(s)).take(GENRE_ROW_SIZE).toList()
            }.filter { it.second.isNotEmpty() }
        }
    }

    /** Titelbild der Übersicht: der neueste Titel ohne Erwachseneninhalt; ohne Datum beim Anbieter null. */
    override fun heroItem(s: Section): Item? = heroState.getValue(s).value

    /** Reihen je Genre (häufigste zuerst, höchstens [GENRE_ROWS]), je höchstens [GENRE_ROW_SIZE] Titel; Name ohne „Genre · “. */
    override fun genreRows(s: Section, max: Int): List<Pair<String, List<Item>>> = genreRowsState.getValue(s).value.take(max)

    /**
     * „Ähnliche Titel“: gleiche Genres (aus der Liste, sonst aus [info]) im selben Bereich; ohne Genre-Treffer dieselbe
     * Anbieter-Kategorie. Nie der Titel selbst; leer, wenn nichts passt.
     */
    override fun similarTitles(item: Item, info: MediaInfo?, limit: Int): List<Item> {
        val s = item.section
        val all = library.section(s).items
        val genresOf = itemGenres.getValue(s).value
        val wanted = (genresOf[item.favKey] ?: item.genre?.let(::splitGenres) ?: info?.genres?.joinToString(",")?.let(::splitGenres))
            .orEmpty().toSet()
        val ok = browsable(s)
        val byGenre = if (wanted.isEmpty()) emptyList()
        else all.filter { it.favKey != item.favKey && ok(it) && genresOf[it.favKey]?.any { g -> g in wanted } == true }
        if (byGenre.isNotEmpty()) return byGenre.take(limit)
        return library.section(s).byCategory[item.categoryId].orEmpty().filter { it.favKey != item.favKey && ok(it) }.take(limit)
    }

    /**
     * Anbieter-Kategorie eines Titels (Kontextmenü „… ausblenden“). Folgen tragen die Serien-ID als categoryId –
     * für sie zählt die Kategorie ihrer Serie. Unbekannt → null.
     */
    override fun categoryOf(item: Item): Category? {
        val cats = library.sections[item.section]?.categories.orEmpty()
        val isEpisode = item.section == Section.SERIES && !item.isSeriesFolder
        // M3U-Serien haben eine Adresse, tragen aber die echte Gruppe – ohne passende Serie gilt ihre eigene categoryId.
        val series = if (isEpisode) library.sections[Section.SERIES]?.items?.firstOrNull { it.id == item.categoryId && it.isSeriesFolder } else null
        val catId = series?.categoryId ?: item.categoryId
        return cats.firstOrNull { it.id == catId }
    }

    /** Inhalt einer Kategorie aus [browseCategories]. */
    override fun browseItems(s: Section, key: String): List<Item> = when (key) {
        BROWSE_CONTINUE -> history.continueWatching(s).map { it.item }
        BROWSE_FAV -> history.favoriteItems(s)
        BROWSE_NEW -> dashboard.newItemsOf(s)
        BROWSE_ALL -> library.section(s).items
        else -> if (key.startsWith(BROWSE_GENRE)) {
            val genre = key.removePrefix(BROWSE_GENRE)
            val genres = itemGenres.getValue(s).value
            library.section(s).items.filter { genres[it.favKey]?.contains(genre) == true }
        } else library.section(s).byCategory[key].orEmpty()
    }

    /** Startkategorie: die gemerkte, sofern es sie noch gibt; sonst Weiterschauen, die erste des Anbieters oder Alle. */
    override fun initialBrowseCategory(s: Section): String {
        val cats = browseCategories(s)
        browseCategory(s)?.takeIf { k -> cats.any { it.id == k } }?.let { return it }
        return when {
            cats.any { it.id == BROWSE_CONTINUE } -> BROWSE_CONTINUE
            else -> library.section(s).categories.firstOrNull()?.id ?: BROWSE_ALL
        }
    }
}
